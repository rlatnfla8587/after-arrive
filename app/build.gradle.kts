plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val withPleos = providers.gradleProperty("pleos").orNull == "true"

android {
    namespace = "dev.arrival300"
    compileSdk = 35
    useLibrary("android.car", false)

    defaultConfig {
        applicationId = "dev.arrival300"
        minSdk = 28
        targetSdk = 34
        versionCode = 4
        versionName = "4.0-demo"
    }

    if (withPleos) {
        flavorDimensions += "runtime"
        productFlavors {
            create("demo") { dimension = "runtime" }
            create("pleos") {
                dimension = "runtime"
                applicationIdSuffix = ".pleos"
            }
        }
    } else {
        sourceSets["main"].java.srcDir("src/demo/java")
    }

    buildFeatures { compose = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation(platform("androidx.compose:compose-bom:2025.05.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("org.robolectric:robolectric:4.14.1")
    if (withPleos) add("pleosImplementation", "ai.pleos.playground:NaviHelper:3.0.5")
}






