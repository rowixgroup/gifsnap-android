plugins { id("com.android.application"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "com.rowix.gifsnap.consumer"
    compileSdk = 36
    defaultConfig { applicationId = "com.rowix.gifsnap.consumer"; minSdk = 28; targetSdk = 36; versionCode = 1; versionName = "0.1.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies { implementation("com.rowix.gifsnap:gifsnap-compose:0.1.0"); implementation("androidx.activity:activity-compose:1.10.1") }
