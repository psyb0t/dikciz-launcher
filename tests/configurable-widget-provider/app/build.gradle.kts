plugins {
    alias(libs.plugins.android.application)
}

val defaultFixtureApplicationId = "eu.psyb0t.dikciz.fixture"
val defaultFixtureBuildDirectory = "build"
val fixtureApplicationId = providers.environmentVariable("DIKCIZ_MEDIA_SESSION_FIXTURE_APPLICATION_ID")
    .map { value -> value.ifBlank { defaultFixtureApplicationId } }
    .getOrElse(defaultFixtureApplicationId)
val fixtureBuildDirectory = providers.environmentVariable("DIKCIZ_MEDIA_SESSION_FIXTURE_BUILD_DIRECTORY")
    .map { value -> value.ifBlank { defaultFixtureBuildDirectory } }
    .getOrElse(defaultFixtureBuildDirectory)

layout.buildDirectory.set(layout.projectDirectory.dir(fixtureBuildDirectory))

tasks.configureEach {
    inputs.property("dikcizMediaSessionFixtureApplicationId", fixtureApplicationId)
    inputs.property("dikcizMediaSessionFixtureBuildDirectory", fixtureBuildDirectory)
}

android {
    namespace = "eu.psyb0t.dikciz.fixture"
    compileSdk = 36

    defaultConfig {
        applicationId = fixtureApplicationId
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
}
