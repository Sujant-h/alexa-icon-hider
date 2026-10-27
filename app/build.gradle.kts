import java.util.Properties

plugins {
    id("com.android.application")
}

// Release signing comes from keystore.properties (not in git), so updates keep the same key.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.minnallab.alexaiconhider"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.minnallab.alexaiconhider"
        minSdk = 36
        targetSdk = 36
        versionCode = 5
        versionName = "1.4"
    }

    // English is the default (res/resources.properties); values-de/ adds German. The generated
    // LocaleConfig lists both under Settings > Apps > Language, and other languages fall back
    // to English.
    androidResources {
        generateLocaleConfig = true
        localeFilters += listOf("en", "de")
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
