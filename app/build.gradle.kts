plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.legacy.kapt)
}

// Allow stax-api for Apache POI on Android

android {
    namespace = "com.example.techyfipos"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.techyfi.pos"
        minSdk = 24
        targetSdk = 36
        versionCode = 220942
        versionName = "2.6.66-V15-09-2026"

        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Existing public APKs were signed with this certificate. Keep it for this
            // upgrade-compatible line; changing it would make Android reject the update.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }
    bundle {
      language {
        // Bengali and English are switched inside the app, so both stay in the base APK/AAB.
        enableSplit = false
      }
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
        excludes += "META-INF/DEPENDENCIES"
        excludes += "META-INF/LICENSE"
        excludes += "META-INF/LICENSE.txt"
        excludes += "META-INF/license.txt"
        excludes += "META-INF/NOTICE"
        excludes += "META-INF/NOTICE.txt"
        excludes += "META-INF/notice.txt"
        excludes += "META-INF/ASL2.0"
        excludes += "META-INF/*.kotlin_module"
      }
    }

    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

tasks.register<Copy>("packageDatedReleaseApk") {
    dependsOn("assembleRelease")
    from(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
    into(layout.buildDirectory.dir("outputs/apk/final"))
    rename { "Techyfi-POS-Final-V05-09-2026.apk" }
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation("androidx.compose.material:material-icons-extended")
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation("org.json:json:20240303")

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Room Database
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.sqlite.wrapper)
  implementation(libs.androidx.sqlite.bundled)
  kapt(libs.androidx.room.compiler)

  // CameraX
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)

  // ML Kit Barcode Scanning
  implementation(libs.play.services.mlkit.barcode.scanning)

  // Preferences DataStore
  implementation(libs.androidx.datastore.preferences)

  // ZXing QR Code Generation
  implementation(libs.zxing.core)

  // Apache POI & StAX XML Provider for Android
  implementation(libs.poi.core)
  implementation(libs.poi.ooxml)
  implementation("org.apache.xmlbeans:xmlbeans:3.1.0")
  implementation("javax.xml.stream:stax-api:1.0-2")
  implementation("com.fasterxml.woodstox:woodstox-core:6.6.0")
  implementation("org.codehaus.woodstox:stax2-api:4.2.2")

  // Google Sign-In & Drive APIs
  implementation(libs.google.play.services.auth)
  implementation(libs.google.api.client.android)
  implementation(libs.google.api.services.drive)

  // WorkManager (for auto-backup)
  implementation(libs.androidx.work.runtime)

}
