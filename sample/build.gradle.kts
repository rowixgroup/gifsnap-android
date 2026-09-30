plugins { id("com.android.application"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "com.rowix.gifsnap.sample"
    compileSdk = 36
    defaultConfig { applicationId = "com.rowix.gifsnap.sample"; minSdk = 28; targetSdk = 36; versionCode = 1; versionName = "0.1.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies { implementation(project(":compose")); implementation("androidx.activity:activity-compose:1.10.1") }
