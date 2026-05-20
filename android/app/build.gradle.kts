plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

fun gradleOrEnv(name: String, fallback: String) =
    providers.environmentVariable(name)
        .orElse(providers.gradleProperty(name))
        .orElse(fallback)

fun buildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.omniread.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.omniread.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        val apiBase = providers
            .environmentVariable("OMNIREAD_API_BASE")
            .orElse(providers.gradleProperty("OMNIREAD_API_BASE"))
            .orElse("https://109.123.244.82:8000")
        buildConfigField("String", "API_BASE_URL", "\"${apiBase.get()}\"")
        buildConfigField("String", "GOOGLE_OAUTH_CLIENT_ID", "\"\"")
    }

    buildTypes {
        release {
            val admobAppId = gradleOrEnv(
                "OMNIREAD_ADMOB_APP_ID",
                "ca-app-pub-1681671255853598~7517732244",
            ).get()
            val rewardedAdUnitId = gradleOrEnv(
                "OMNIREAD_ADMOB_REWARDED_AD_UNIT_ID",
                "ca-app-pub-1681671255853598/4826017508",
            ).get()
            val bannerAdUnitId = gradleOrEnv(
                "OMNIREAD_ADMOB_BANNER_AD_UNIT_ID",
                "ca-app-pub-1681671255853598/1418840716",
            ).get()
            val interstitialAdUnitId = gradleOrEnv(
                "OMNIREAD_ADMOB_INTERSTITIAL_AD_UNIT_ID",
                "ca-app-pub-1681671255853598/6994601029",
            ).get()

            manifestPlaceholders["admobAppId"] = admobAppId
            buildConfigField("String", "ADMOB_APP_ID", buildConfigString(admobAppId))
            buildConfigField("String", "ADMOB_REWARDED_AD_UNIT_ID", buildConfigString(rewardedAdUnitId))
            buildConfigField("String", "ADMOB_BANNER_AD_UNIT_ID", buildConfigString(bannerAdUnitId))
            buildConfigField("String", "ADMOB_INTERSTITIAL_AD_UNIT_ID", buildConfigString(interstitialAdUnitId))

            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            val admobAppId = "ca-app-pub-3940256099942544~3347511713"
            val rewardedAdUnitId = "ca-app-pub-3940256099942544/5224354917"
            val bannerAdUnitId = "ca-app-pub-3940256099942544/6300978111"
            val interstitialAdUnitId = "ca-app-pub-3940256099942544/1033173712"

            manifestPlaceholders["admobAppId"] = admobAppId
            buildConfigField("String", "ADMOB_APP_ID", buildConfigString(admobAppId))
            buildConfigField("String", "ADMOB_REWARDED_AD_UNIT_ID", buildConfigString(rewardedAdUnitId))
            buildConfigField("String", "ADMOB_BANNER_AD_UNIT_ID", buildConfigString(bannerAdUnitId))
            buildConfigField("String", "ADMOB_INTERSTITIAL_AD_UNIT_ID", buildConfigString(interstitialAdUnitId))

            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.coil.compose)

    implementation(libs.lottie.compose)
    implementation(libs.accompanist.systemuicontroller)
    implementation(libs.shimmer.compose)

    implementation(libs.billing)
    implementation(libs.play.services.auth)
    implementation(libs.play.services.ads)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    debugImplementation(libs.androidx.ui.tooling)
}
