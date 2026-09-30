pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
 repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
 repositories {
   google(); mavenCentral()
   maven { url = uri(providers.gradleProperty("gifsnapRepository").orElse("../build/repository").get())
     content { includeGroup("com.rowix.gifsnap") }
   }
 }
}
rootProject.name = "gifsnap-distribution-consumer"
include(":app")
