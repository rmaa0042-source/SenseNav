// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Declared here so the versions are pinned in one place, but applied by the
    // app module only when app/google-services.json is present. The Google
    // Services plugin fails the build outright when that file is missing, and
    // this project has no Firebase project wired up yet.
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.firebase.appdistribution) apply false
}
