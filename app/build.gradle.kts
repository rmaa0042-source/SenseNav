import com.android.build.api.artifact.SingleArtifact
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// The Maps key lives in local.properties, which is gitignored, so it never
// reaches version control. Absent key -> empty string; the map screen detects
// that and shows setup instructions instead of a blank grey tile.
val mapsApiKey: String = localProperties.getProperty("MAPS_API_KEY").orEmpty().trim()

// The routing API runs on a VM without a static IP, so the address can change.
// Override it in local.properties rather than editing source.
val routingApiBaseUrl: String = localProperties
    .getProperty("ROUTING_API_BASE_URL")
    .orEmpty()
    .trim()
    .ifEmpty { "http://34.172.95.142:8000/" }

android {
    namespace = "com.example.sensenav"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.sensenav"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")
        buildConfigField("String", "ROUTING_API_BASE_URL", "\"$routingApiBaseUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
    implementation(libs.android.maps.utils)
    implementation(libs.kotlinx.coroutines.play.services)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
