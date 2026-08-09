package com.example.sensenav.data

import android.text.Html
import com.example.sensenav.model.LandmarkImage
import com.example.sensenav.model.Refuge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * Wikimedia serves 403 to clients that do not identify themselves - a bare
 * "okhttp/4.x" is refused by the image CDN outright - so this is sent on both
 * the API calls and the image downloads. Coil uses its own HTTP client, which
 * is why the value has to be shared rather than kept inside the interceptor.
 */
const val WIKIMEDIA_USER_AGENT = "SenseNav/1.0 (Android sensory-navigation project)"

/**
 * Finds a freely-licensed photo of a refuge on Wikimedia.
 *
 * Two calls, both against en.wikipedia.org (Commons files resolve there too, so
 * this needs only one host and no API key):
 *
 *  1. a geo search for articles near the refuge, to identify the right subject
 *  2. an image lookup on that article's lead photo, for the URL and the credit
 *
 * The subject is chosen by matching names, never by taking the nearest article.
 * Measured against the live dataset, "nearest" is wrong most of the time - the
 * closest article to Church of Christ is Coop's Shot Tower, and to Welsh
 * Presbyterian Church is an office block. Showing a stranger's building as
 * somebody's sensory refuge is worse than showing no photo, so a landmark with
 * no confident name match returns null and the UI draws artwork instead.
 */
class WikimediaImageRepository(
    private val api: WikimediaApi = defaultApi()
) {

    // Wikimedia rate-limits anonymous clients - firing one request per visible
    // card in parallel earns a 429 and no images at all. Requests are therefore
    // serialised and spaced, and every answer (including "no match") is cached
    // so a card that scrolls back into view never refetches.
    private val mutex = Mutex()
    private val cache = mutableMapOf<String, LandmarkImage?>()

    suspend fun imageFor(refuge: Refuge): LandmarkImage? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (cache.containsKey(refuge.id)) return@withLock cache[refuge.id]

            val result = runCatching { lookup(refuge) }
            delay(REQUEST_SPACING_MS)

            // Only a completed lookup is remembered. A throttled or dropped
            // request is not evidence that the place has no photograph, and
            // caching it as one would strand that card on artwork for the rest
            // of the session with no way to retry.
            if (result.isSuccess) cache[refuge.id] = result.getOrNull()
            result.getOrNull()
        }
    }

    private suspend fun lookup(refuge: Refuge): LandmarkImage? {
        val nearby = api.geoSearch(coordinate = "${refuge.latitude}|${refuge.longitude}")
            .query
            ?.pages
            ?.values
            .orEmpty()

        val match = nearby
            .filter { !it.pageImage.isNullOrBlank() }
            .map { it to matchStrength(refuge.name, it.title.orEmpty()) }
            .filter { (_, strength) -> strength > 0 }
            // Ties go to the most specifically-named article.
            .maxByOrNull { (_, strength) -> strength }
            ?.first
            ?: return null

        val info = api.imageInfo(titles = "File:${match.pageImage}")
            .query
            ?.pages
            ?.values
            ?.firstOrNull()
            ?.imageInfo
            ?.firstOrNull()

        val url = info?.thumbUrl ?: match.thumbnail?.source ?: return null

        return LandmarkImage(
            url = url,
            artist = info?.extMetadata?.artist?.value?.stripHtml()?.take(MAX_CREDIT_CHARS),
            license = info?.extMetadata?.licenseShortName?.value?.stripHtml(),
            descriptionUrl = info?.descriptionUrl
        )
    }

    /**
     * How strongly a Wikipedia article title claims to name this landmark, as a
     * count of matched distinguishing words - 0 meaning "not the same place".
     *
     * The test is one-directional on purpose: every distinguishing word in the
     * *article's* name must appear in the landmark's, while the landmark may say
     * more than the article does. That asymmetry is what separates the two cases
     * that actually occur in this dataset:
     *
     *  - "Melbourne Athenaeum Library" over the article "Melbourne Athenaeum" -
     *    the landmark is wordier, and it is still the same building.
     *  - "South Yarra Presbyterian Church" over the article "Christ Church,
     *    South Yarra" - shares a suburb and a building type, but the article
     *    brings its own name, "Christ", so it is a different church.
     *
     * A symmetric overlap score accepts that second case, which is how the app
     * ended up captioning one congregation's church with another's photograph.
     */
    private fun matchStrength(datasetName: String, articleTitle: String): Int {
        val landmarkWords = significantWords(datasetName)
        // Wikipedia disambiguates by appending a locality - "Christ Church,
        // South Yarra" - which is not part of the name being compared.
        val articleWords = significantWords(articleTitle.substringBefore(','))

        val landmark = landmarkWords - PLACE_TYPE_WORDS
        val article = articleWords - PLACE_TYPE_WORDS
        if (landmark.isEmpty() || article.isEmpty()) return 0
        if (!landmark.containsAll(article)) return 0

        // An article naming strictly less than the landmark is the dangerous
        // case: the suburb article "South Yarra" is a perfect subset of "South
        // Yarra Presbyterian Church", and its photo is a skyline. Accept a
        // partial name only when both sides also agree on what kind of place it
        // is, which "South Yarra" cannot do and "Melbourne Welsh Church" can.
        if (landmark != article) {
            val sharedType = (landmarkWords intersect articleWords) intersect PLACE_TYPE_WORDS
            if (sharedType.isEmpty()) return 0
        }

        return article.size
    }

    private fun significantWords(value: String): Set<String> =
        value.lowercase()
            // Fold possessives and punctuation so "St Michael's" meets "St Michaels".
            .replace("'", "")
            .replace("’", "")
            .split(*WORD_SEPARATORS)
            .map { it.trim() }
            .filter { it.isNotEmpty() && it !in IGNORED_WORDS }
            .toSet()

    @Suppress("DEPRECATION") // Html.fromHtml(String, int) is API 24+, but the
    // one-argument overload is what the project's minSdk 26 baseline allows
    // without a compat dependency; behaviour is identical for this markup.
    private fun String.stripHtml(): String =
        Html.fromHtml(this).toString().trim()

    private companion object {
        const val BASE_URL = "https://en.wikipedia.org/"

        /**
         * Metres around the refuge to consider an article as describing it.
         * Generous because a large site's article is pinned at its centroid,
         * which can sit most of a kilometre from the dataset's point - at 300m
         * the Royal Botanic Gardens found nothing. Widening this is safe only
         * because [matchStrength] decides the subject, not proximity.
         */
        const val SEARCH_RADIUS_M = 1000

        /** Nearest-first, so this has to be deep enough to reach past a dense CBD block. */
        const val RESULT_LIMIT = 20
        const val THUMBNAIL_PX = 500

        /**
         * Paced rather than fast: each lookup is two calls, and hammering the
         * API earns a 429 that costs every remaining card its photo.
         */
        const val REQUEST_SPACING_MS = 600L
        const val MAX_CREDIT_CHARS = 60

        val WORD_SEPARATORS = arrayOf(" ", ",", "-", ":", "(", ")", ".", "/", "&")

        /** Carry no identifying weight in a Melbourne landmark name. */
        val IGNORED_WORDS = setOf(
            "the", "of", "and", "a", "at", "in", "melbourne", "vic", "victoria",
            "australia", "inc", "ltd", "cbd"
        )

        /**
         * Shared by too many entries to identify one on their own - a match on
         * only these words means the two names have nothing real in common.
         */
        val PLACE_TYPE_WORDS = setOf(
            "church", "cathedral", "chapel", "park", "gardens", "garden",
            "library", "museum", "gallery", "hall", "reserve", "centre",
            "center", "college", "school", "gaol", "st", "saint", "square",
            "building", "house", "place"
        )

        fun defaultApi(): WikimediaApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("User-Agent", WIKIMEDIA_USER_AGENT)
                            .build()
                    )
                }
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(WikimediaApi::class.java)
        }
    }

    interface WikimediaApi {

        /** Articles with coordinates near a point, with each one's lead image. */
        @GET("w/api.php?action=query&generator=geosearch&prop=pageimages&piprop=thumbnail|name&format=json")
        suspend fun geoSearch(
            @Query("ggscoord") coordinate: String,
            @Query("ggsradius") radiusMetres: Int = SEARCH_RADIUS_M,
            @Query("ggslimit") limit: Int = RESULT_LIMIT,
            @Query("pithumbsize") thumbnailSize: Int = THUMBNAIL_PX
        ): WikiResponseDto

        /** A file's URL plus the authorship and licence needed to credit it. */
        @GET("w/api.php?action=query&prop=imageinfo&iiprop=url|extmetadata&format=json")
        suspend fun imageInfo(
            @Query("titles") titles: String,
            @Query("iiurlwidth") width: Int = THUMBNAIL_PX
        ): WikiResponseDto
    }
}

data class WikiResponseDto(val query: WikiQueryDto? = null)

data class WikiQueryDto(val pages: Map<String, WikiPageDto>? = null)

data class WikiPageDto(
    val title: String? = null,
    val pageimage: String? = null,
    val thumbnail: WikiThumbnailDto? = null,
    val imageinfo: List<WikiImageInfoDto>? = null
) {
    val pageImage: String? get() = pageimage
    val imageInfo: List<WikiImageInfoDto>? get() = imageinfo
}

data class WikiThumbnailDto(val source: String? = null)

data class WikiImageInfoDto(
    val thumburl: String? = null,
    val descriptionurl: String? = null,
    val extmetadata: WikiExtMetadataDto? = null
) {
    val thumbUrl: String? get() = thumburl
    val descriptionUrl: String? get() = descriptionurl
    val extMetadata: WikiExtMetadataDto? get() = extmetadata
}

data class WikiExtMetadataDto(
    @com.google.gson.annotations.SerializedName("Artist") val artist: WikiMetadataValueDto? = null,
    @com.google.gson.annotations.SerializedName("LicenseShortName") val licenseShortName: WikiMetadataValueDto? = null
)

data class WikiMetadataValueDto(val value: String? = null)
