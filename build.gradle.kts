// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Material3 is a library, not a plugin
    //alias(libs.material3.android) apply false

    id("com.google.gms.google-services") version "4.4.2" apply false


}
/*
dependencies {
    implementation(project("license"))
}
*/
