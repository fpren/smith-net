import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") version "1.9.24-1.0.20"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24"
}

// Release signing -- reads android/keystore.properties (gitignored).
// Absent file (e.g. CI) leaves release unsigned rather than failing the build.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) load(FileInputStream(keystorePropsFile))
}

android {
    namespace = "com.guildofsmiths.trademesh"
    compileSdk = 34
    
    // NDK configuration for llama.cpp
    ndkVersion = "25.2.9519653"

    defaultConfig {
        applicationId = "com.guildofsmiths.trademesh"
        minSdk = 26
        targetSdk = 34
        versionCode = 20
        versionName = "0.7.0-design-v2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Backend relay URLs. Primary is the Hetzner relay exposed publicly via Tailscale Funnel
        // (HTTPS, auto-renewed cert, reachable from any network). Fallback is the Mac Mini on LAN.
        buildConfigField("String", "BACKEND_URL_PRIMARY", "\"https://ubuntu-8gb-ash-1.tail2523e7.ts.net\"")
        buildConfigField("String", "BACKEND_URL_FALLBACK", "\"http://192.168.8.169:3030\"")
        // Unified BACKEND_URL: emulator uses 10.0.2.2 to reach host machine
        buildConfigField("String", "BACKEND_URL", "\"http://10.0.2.2:3030\"")

        // Native build configuration for llama.cpp
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-fexceptions", "-frtti")
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_ARM_NEON=TRUE",
                    "-DLLAMA_NATIVE=OFF"
                )
            }
        }
        
        // ABI filters - support arm64 and arm32
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }
    
    // CMake build for JNI layer
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = rootProject.file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            // DEMO OVERRIDE (2026-06-11, 2-party e2e): point debug at the LOCAL backend
            // via device loopback + `adb reverse tcp:3030 tcp:3030`. Works on BOTH the
            // emulator and USB-attached physical phones. Revert to the Funnel block to
            // restore.
            buildConfigField("String", "BACKEND_URL_PRIMARY", "\"http://127.0.0.1:3030\"")
            buildConfigField("String", "BACKEND_URL_FALLBACK", "\"http://127.0.0.1:3030\"")
            buildConfigField("String", "BACKEND_URL", "\"http://127.0.0.1:3030\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Release builds use the Hetzner relay via public Tailscale Funnel URL.
            // Fallback must NOT be a LAN address: off-LAN beta devices would hang
            // trying to reach a private IP. Point it at the same public host until
            // a second public endpoint exists.
            buildConfigField("String", "BACKEND_URL_PRIMARY", "\"https://ubuntu-8gb-ash-1.tail2523e7.ts.net\"")
            buildConfigField("String", "BACKEND_URL_FALLBACK", "\"https://ubuntu-8gb-ash-1.tail2523e7.ts.net\"")
            // Production endpoint. api.smithnet.app is not registered yet --
            // point at the live Funnel host until the domain decision lands.
            buildConfigField("String", "BACKEND_URL", "\"https://ubuntu-8gb-ash-1.tail2523e7.ts.net\"")
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    // Core AndroidX
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.2")
    
    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")
    
    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // WorkManager -- drains the InvoicesOutbox in the background, survives
    // process death. See docs/superpowers/specs/2026-05-17-android-invoice-wiring-design.md.
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // OkHttp for WebSocket
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Supabase - retained ONLY for the password-reset email flow (gotrue/Auth).
    // W3 moved auth/session, profiles, media and presence to Hetzner; the
    // postgrest/realtime/storage modules are no longer used.
    implementation(platform("io.github.jan-tennert.supabase:bom:2.0.4"))
    implementation("io.github.jan-tennert.supabase:gotrue-kt")
    // Ktor with OkHttp engine (required by the Supabase gotrue client)
    implementation("io.ktor:ktor-client-okhttp:2.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    
    // OpenStreetMap
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // Comm redesign deps (pinned for Compose BOM 2023.10.01 / Kotlin 1.9.24):
    // Coil for avatar photos (2.6.x — NOT 3.x, which targets newer Compose).
    implementation("io.coil-kt:coil-compose:2.6.0")
    // zxing core: generate the user's id QR (BitMatrix -> ImageBitmap; no UI lib).
    implementation("com.google.zxing:core:3.5.3")
    // Scan a peer's id QR: CameraX preview + ML Kit barcode (in-Compose).
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mlkit:barcode-scanning:17.2.0")

    // Core library desugaring
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("app.cash.turbine:turbine:1.0.0")
    // Real org.json on test classpath; otherwise android.jar's stubs return null
    // for toString() under unitTests.isReturnDefaultValues = true.
    testImplementation("org.json:json:20231013")
    // Robolectric for Room unit tests (PendingInvoicePushDaoTest and future DAO tests)
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.test.ext:junit:1.1.5")
    testImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
