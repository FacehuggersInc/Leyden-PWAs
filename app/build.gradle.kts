plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "org.bond.pwaapp"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.bond.pwaapp"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "2.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
}

kotlin {
    jvmToolchain(27)
}

dependencies {
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.swiperefreshlayout)
    testImplementation(libs.junit)
}
