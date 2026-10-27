import java.util.Properties

plugins {
    id("com.android.application")
}

// Release signing comes from keystore.properties (not in git), so updates keep the same key.
// In CI the same four values come from SIGNING_* environment variables and take precedence.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
    mapOf(
        "storeFile" to "SIGNING_STORE_FILE",
        "storePassword" to "SIGNING_STORE_PASSWORD",
        "keyAlias" to "SIGNING_KEY_ALIAS",
        "keyPassword" to "SIGNING_KEY_PASSWORD",
    ).forEach { (key, env) -> System.getenv(env)?.takeIf { it.isNotEmpty() }?.let { setProperty(key, it) } }
}

android {
    namespace = "com.minnallab.alexaiconhider"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.minnallab.alexaiconhider"
        minSdk = 36
        targetSdk = 36
        versionCode = 6
        versionName = "1.5"
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
