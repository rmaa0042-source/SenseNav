import com.android.build.api.artifact.SingleArtifact
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// google-services.json comes from the Firebase console and is gitignored, so it
// is absent on a fresh clone and on any machine not set up for Firebase. The
// Google Services plugin hard-fails when it cannot find that file, which would
// make the whole project unbuildable, so the Firebase plugins are applied only
// once it is actually there. Drop the file into app/ and they light up.
val firebaseConfig = file("google-services.json")
val hasFirebaseConfig = firebaseConfig.exists()

if (hasFirebaseConfig) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
    apply(plugin = "com.google.firebase.appdistribution")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

/**
 * Build-time secrets, from the gitignored properties files first and the
 * environment second, so a CI runner can supply the same names without a file
 * on disk. Never defaulted to a real value - an absent secret has to stay
 * visibly absent.
 */
fun secret(key: String): String =
    (localProperties.getProperty(key) ?: keystoreProperties.getProperty(key)
        ?: System.getenv(key)).orEmpty().trim()

// Absent key -> empty string; the map screen detects that and shows setup
// instructions instead of a blank grey tile. verifyReleaseConfig treats it as a
// release blocker, since a published build has no such excuse.
val mapsApiKey: String = secret("MAPS_API_KEY")

// The scoring API is served over plain HTTP at a fixed address. Both build types
// use it; override via local.properties or the environment to point at another
// host. Cleartext to this one host is allowed explicitly in
// src/main/res/xml/network_security_config.xml, and verifyReleaseConfig checks
// the two stay in agreement.
val routingApiBaseUrl: String = secret("ROUTING_API_BASE_URL")
    .ifEmpty { "http://34.172.95.142:8000/" }

val releaseStoreFile = secret("RELEASE_STORE_FILE")
val releaseStorePassword = secret("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = secret("RELEASE_KEY_ALIAS")
val releaseKeyPassword = secret("RELEASE_KEY_PASSWORD")
val hasReleaseSigning = releaseStoreFile.isNotEmpty() &&
    rootProject.file(releaseStoreFile).exists() &&
    releaseStorePassword.isNotEmpty() &&
    releaseKeyAlias.isNotEmpty() &&
    releaseKeyPassword.isNotEmpty()

android {
    namespace = "com.flip6.sensenav"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.flip6.sensenav"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")
        buildConfigField("String", "ROUTING_API_BASE_URL", "\"$routingApiBaseUrl\"")
    }

    signingConfigs {
        // Registered only when the material is actually present. Without it the
        // release variant has no signing config at all, which verifyReleaseConfig
        // reports as a blocker instead of quietly producing an unsigned artifact.
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// The checks that separate "it compiles" from "it can be published". Wired to
// the release assemble/bundle tasks only, and run at execution time rather than
// during configuration, so a missing release secret never blocks a debug build.
val verifyReleaseConfig = tasks.register("verifyReleaseConfig") {
    group = "verification"
    description = "Refuses a release build that would ship unsigned, mapless, or over cleartext HTTP."
    doLast {
        val problems = buildList {
            if (!hasReleaseSigning) {
                add(
                    "No release signing key. Copy keystore.properties.example to " +
                        "keystore.properties and fill it in, or set the same four names " +
                        "as environment variables on the build machine."
                )
            }
            // The API is plain HTTP by design, so cleartext is allowed for its host
            // and denied everywhere else. That allow-list lives in XML and the URL
            // lives in Gradle, so they can drift; if they do, every call fails at
            // runtime with nothing in the build to hint at why.
            if (routingApiBaseUrl.startsWith("http://")) {
                val host = routingApiBaseUrl
                    .removePrefix("http://")
                    .substringBefore("/")
                    .substringBefore(":")
                val configFile = file("src/main/res/xml/network_security_config.xml")
                if (!configFile.readText().contains(">$host<")) {
                    add(
                        "ROUTING_API_BASE_URL is cleartext at \"$host\", but that host has " +
                            "no <domain> entry in ${configFile.name}. A release build blocks " +
                            "cleartext by default, so every API call would fail. Add the host " +
                            "there, or move the API to HTTPS."
                    )
                }
            }
            if (mapsApiKey.isEmpty()) {
                add("MAPS_API_KEY is not set, so the build would ship with no map.")
            }
        }
        if (problems.isNotEmpty()) {
            throw GradleException(
                "This release is not publishable yet:\n" +
                    problems.joinToString("\n") { "  - $it" }
            )
        }
    }
}

tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }
    .configureEach { dependsOn(verifyReleaseConfig) }

// Mirrors the newest APK of each variant to <repo>/builds, so the current build
// is easy to find and hand to someone without digging through app/build/outputs
// (which is gitignored and wiped by `clean`). Runs automatically after assemble.
val buildsDir = rootProject.layout.projectDirectory.dir("builds")

androidComponents {
    onVariants { variant ->
        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val copyApk = tasks.register<Copy>("copy${variantName}ApkToBuilds") {
            group = "build"
            description = "Copies the ${variant.name} APK into builds/."
            from(variant.artifacts.get(SingleArtifact.APK)) {
                include("*.apk")
            }
            into(buildsDir)
            rename { "sensenav-${variant.name}-latest.apk" }
        }
        // matching{}.configureEach is lazy - the assemble tasks are not created
        // yet at the point onVariants runs, so tasks.named() would fail here.
        tasks.matching { it.name == "assemble$variantName" }.configureEach {
            finalizedBy(copyApk)
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    // Used directly by HistoryStore, so declared rather than left transitive.
    implementation(libs.gson)
    implementation(libs.coil.compose)
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
    implementation(libs.android.maps.utils)
    implementation(libs.kotlinx.coroutines.play.services)

    // Crash reporting only - Analytics is deliberately left out. This app keeps
    // the user's location and history on the device, and a general analytics SDK
    // would start sending behavioural data off it. A stack trace from a crash is
    // a narrower trade, and without it a published build gives no way to find out
    // why it broke for someone.
    if (hasFirebaseConfig) {
        implementation(platform(libs.firebase.bom))
        implementation(libs.firebase.crashlytics)
    }
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
