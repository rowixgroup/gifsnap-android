plugins { id("com.android.library"); `maven-publish` }
android {
    namespace = "com.rowix.gifsnap.client"
    compileSdk = 36
    defaultConfig { minSdk = 23 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    publishing { singleVariant("release") { withSourcesJar() } }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
afterEvaluate {
    publishing {
        publications { create<MavenPublication>("release") {
            from(components["release"]); artifactId = "gifsnap-client"
            pom { name.set("GifSnap Kotlin client"); description.set("Typed coroutine client for the GifSnap API"); url.set("https://gifsnap.com/docs/android")
                licenses { license { name.set("MIT"); url.set("https://opensource.org/license/mit") } }
            }
        } }
        repositories { maven { name = "Distribution"; url = uri(rootProject.layout.buildDirectory.dir("repository")) } }
    }
}
