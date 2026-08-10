# R8 rules for the release build. Retrofit, OkHttp and Gson all ship their own
# consumer rules; what follows is the part specific to this app.

# Generic signatures are load-bearing here, not optional: HistoryStore and
# SavedRouteStore deserialise through TypeToken (List<SearchResult>, and so on),
# and R8 strips Signature by default, which turns those reads into a raw
# LinkedHashMap at runtime rather than an outright failure.
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes *Annotation*

# Everything that crosses Gson's reflection boundary. Field names are the wire
# format - for the API's JSON and for the JSON in SharedPreferences alike - so
# obfuscating them would rename the keys and orphan data written by an earlier
# build.
-keep class com.example.sensenav.api.** { *; }
-keep class com.example.sensenav.model.** { *; }
-keep class com.example.sensenav.data.Wiki*Dto { *; }
-keep class com.example.sensenav.data.WikimediaImageRepository$CachedLookup { *; }

# Belt and braces for any model added later without a keep rule of its own.
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Retrofit reads the suspend functions' return types by reflection.
-keep,allowobfuscation,allowshrinking interface com.example.sensenav.api.SenseNavApi
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Crash reports are only worth having if the frames name real files and lines.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Location coordinates get written to logcat while loading refuges, which is not
# something a published build should do. Dropped at the two levels that carry
# them; w and e survive, because a failed API call is worth reporting.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}
