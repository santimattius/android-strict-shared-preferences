import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.mavenPublish)
}

val androidMinSdkVersion: String by project
val androidTargetSdkVersion: String by project

val libraryGroupId: String by project
val libraryArtifactId: String by project
val libraryVersion: String by project

android {
    namespace = "com.santimattius.android.strict.preferences"
    compileSdk = androidTargetSdkVersion.toInt()

    defaultConfig {
        minSdk = androidMinSdkVersion.toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.startup.runtime)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

    signAllPublications()

    coordinates(libraryGroupId, libraryArtifactId, libraryVersion)

    pom {
        name = "strict-preferences"
        description =
            "StrictPreferences is an Android library designed to help developers detect and diagnose SharedPreferences access on the main application thread."
        inceptionYear = "2025"
        url = "https://github.com/santimattius/android-strict-shared-preferences/"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        developers {
            developer {
                id = "santiago-mattiauda"
                name = "Santiago Mattiauda"
                url = "https://github.com/santimattius"
            }
        }
        scm {
            url = "https://github.com/santimattius/android-strict-shared-preferences/"
            connection =
                "scm:git:git://github.com/santimattius/android-strict-shared-preferences.git"
            developerConnection =
                "scm:git:ssh://git@github.com/santimattius/android-strict-shared-preferences.git"
        }
    }
}