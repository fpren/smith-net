plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") version "1.9.24-1.0.20"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24"
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
        versionCode = 18
        versionName = "0.6.1-map-jobs"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Backend relay URLs. Primary is the Hetzner relay exposed publicly via Tailscale Funnel
        // (HTTPS, auto-renewed cert, reachable from any network). Fallback is the Mac Mini on LAN.
        buildConfigField("String", "BACKEND_URL_PRIMARY", "\"https://ubuntu-8gb-ash-1.tail2523e7.ts.net\"")
        buildConfigField("String", "BACKEND_URL_FALLBACK", "\"http://192.168.8.169:3000\"")

        // Kill switch for legacy Supabase Realtime path. Default OFF — relay is Hetzner/Postgres now.
        // Flip to true only if you explicitly want the old global-chat path active.
        buildConfigField("boolean", "SUPABASE_ENABLED", "false")
        
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

    buildTypes {
        debug {
            // Debug builds prefer the Hetzner relay via public Tailscale Funnel URL; Mac Mini LAN is fallback.
            buildConfigField("String", "BACKEND_URL_PRIMARY", "\"https://ubuntu-8gb-ash-1.tail2523e7.ts.net\"")
            buildConfigField("String", "BACKEND_URL_FALLBACK", "\"http://192.168.8.169:3000\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Release builds use the Hetzner relay via public Tailscale Funnel URL.
            buildConfigField("String", "BACKEND_URL_PRIMARY", "\"https://ubuntu-8gb-ash-1.tail2523e7.ts.net\"")
            buildConfigField("String", "BACKEND_URL_FALLBACK", "\"http://192.168.8.169:3000\"")
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
    
    // OkHttp for WebSocket
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Supabase - Cloud Backend
    implementation(platform("io.github.jan-tennert.supabase:bom:2.0.4"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:gotrue-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    // Ktor with OkHttp engine for WebSocket support (required by Supabase Realtime)
    implementation("io.ktor:ktor-client-okhttp:2.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    
    // OpenStreetMap
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // Core library desugaring
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("app.cash.turbine:turbine:1.0.0")
    // Real org.json on test classpath; otherwise android.jar's stubs return null
    // for toString() under unitTests.isReturnDefaultValues = true.
    testImplementation("org.json:json:20231013")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
