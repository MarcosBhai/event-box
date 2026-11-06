plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

android {
    namespace = "com.marocsbhai.eventbox"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        // Wide on purpose: the library only needs core-ktx and coroutines.
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            // Obfuscates and shrinks the library's own internals before it is packaged, so a
            // consumer gets only the public API in readable form. See proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = false // not supported for libraries; the app shrinks them
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // StateFlow is part of the public API (NextGenEventLog.events / isActiveFlow).
    api(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.marocsbhai"
                artifactId = "event-box"
                // The publishing workflow sets VERSION to the git tag (e.g. 1.0.0).
                version = System.getenv("VERSION") ?: "1.0.1"

                pom {
                    name.set("Event Box")
                    description.set("On-device event log overlay for testing Firebase Analytics events.")
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }
                }
            }
        }
        repositories {
            // Private GitHub Packages registry of this repo.
            // In GitHub Actions: GITHUB_ACTOR / GITHUB_TOKEN are provided automatically.
            // Locally: put gpr.user / gpr.key in ~/.gradle/gradle.properties (never in the project).
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/MarocsBhai/event-box")
                credentials {
                    username = System.getenv("GITHUB_ACTOR")
                        ?: providers.gradleProperty("gpr.user").orNull
                    password = System.getenv("GITHUB_TOKEN")
                        ?: providers.gradleProperty("gpr.key").orNull
                }
            }
        }
    }
}
