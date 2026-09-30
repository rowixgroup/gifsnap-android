plugins { id("com.android.library"); id("org.jetbrains.kotlin.plugin.compose"); `maven-publish` }
android {
    namespace = "com.rowix.gifsnap.compose"
    compileSdk = 36
    defaultConfig { minSdk = 28; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true }
    publishing { singleVariant("release") { withSourcesJar() } }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    api(project(":client"))
    api("androidx.compose.ui:ui:1.9.3")
    api("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.compose.foundation:foundation:1.9.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("io.coil-kt.coil3:coil-compose:3.4.0")
    implementation("io.coil-kt.coil3:coil-gif:3.4.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.4.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.9.3")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.9.3")
}
afterEvaluate {
    publishing {
        publications { create<MavenPublication>("release") {
            from(components["release"]); artifactId = "gifsnap-compose"
            pom { name.set("GifSnap Compose picker"); description.set("Animated GIF and sticker picker for Jetpack Compose"); url.set("https://gifsnap.com/docs/android")
                licenses { license { name.set("MIT"); url.set("https://opensource.org/license/mit") } }
            }
        } }
        repositories { maven { name = "Distribution"; url = uri(rootProject.layout.buildDirectory.dir("repository")) } }
    }
}
