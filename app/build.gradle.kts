plugins {
    id("com.android.application")
    jacoco
}

android {
    namespace = "edu.usc.splitsmart"
    compileSdk = 34

    defaultConfig {
        applicationId = "edu.usc.splitsmart"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Adds createDebugUnitTestCoverageReport (JaCoCo) for the Project 2.4 coverage numbers.
            enableUnitTestCoverage = true
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Espresso is more reliable with system animations off on the test device.
        animationsDisabled = true
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // Receipt scanning: edge detection, cropping, and cleanup run inside Google Play services,
    // so the app needs no camera permission of its own.
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")

    // Local unit tests (src/test)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")

    // Instrumented UI tests (src/androidTest)
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    // Stubs the system photo picker in the receipt tests.
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.7.0")
}

// Robolectric loads app classes through its own class loader, which JaCoCo skips unless told
// otherwise; without this the Robolectric tests would report 0% coverage.
tasks.withType<Test> {
    configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}
