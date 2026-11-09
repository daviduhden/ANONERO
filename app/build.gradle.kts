/*
 * Copyright (c) 2026, David Uhden Collado
 * Copyright (c) 2022-2026, r4v3r23
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.compose.compiler)
}

// Connected Android tests install the debug APK directly; ABI splits produce
// several APKs and make that installation ambiguous. The test workflow sets this
// property, while production releases keep the normal per-ABI splits.
val disableAbiSplitsForTests = providers.gradleProperty("anonDisableSplits").isPresent

android {
    namespace = "io.anonero"
    // compile/target are independent of the minimum. Android 10 (API 29) is the
    // supported floor; raising targetSdk changes runtime behavior and needs tests.
    // Compile against the latest stable API 37 minor release; target stays 37.0.
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "io.anonero"
        minSdk = 29
        targetSdk = 37
        versionCode = 3
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        ndk {
            // Only ABIs for which external-libs builds Monero archives. x86_64 is
            // intentionally absent; do not add it without a native build target.
            //noinspection ChromeOsAbiSupport
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Production APKs are intentionally unsigned here. GitHub Actions signs
            // them with the protected release Keystore after verifying every artifact.
            // Never fall back to the debug key for a release build.
            signingConfig = null
        }
        create("stageNet") {
            isMinifyEnabled = false
            isDebuggable = true
            applicationIdSuffix = ".stagenet"
            versionNameSuffix = "-stagenet"
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
        // AGP 9 disables resValues by default; the flavors below use resValue().
        resValues = true
    }

    // The JNI adapter is compiled here; Monero itself is prebuilt by external-libs.
    // Use the current Android SDK CMake; app/CMakeLists.txt requires >= 3.22.
    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
            version = "4.1.2"
        }
    }

    splits {
        abi {
            isEnable = !disableAbiSplitsForTests
            reset()
            // These are the only ABIs for which external-libs/Makefile builds the
            // Monero static archives. Do not advertise x86_64 without native support.
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }

    // The app changes locale at runtime. Play language splits would remove the
    // resources the user switches to, so keep every locale in the bundle.
    bundle {
        language {
            enableSplit = false
        }
    }

    packaging {
        jniLibs.useLegacyPackaging = true
        resources {
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }

    }

    flavorDimensions += "anon_mode"

    productFlavors {
        create("anon") {
            applicationIdSuffix = ".anon"
            resValue("string", "app_name", "ANON")
            dimension = "anon_mode"
            buildConfigField("String", "FLAVOR", "\"anon\"")
            buildConfigField("boolean", "VIEW_ONLY", "false")
        }
        create("nero") {
            applicationIdSuffix = ".nero"
            resValue("string", "app_name", "NERO")
            dimension = "anon_mode"
            buildConfigField("String", "FLAVOR", "\"nero\"")
            buildConfigField("boolean", "VIEW_ONLY", "true")
        }

    }
    // Keep these in sync with ANDROID_BUILD_TOOLS / ANDROID_NDK in the workflows and
    // with NDK_REVISION in external-libs/build.sh. The native archives are built there.
    buildToolsVersion = "37.0.0"
    ndkVersion = "30.0.16248370"
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.compose)
    implementation(libs.androidx.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.compose.animation)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)


    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.camera.view)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.org.boofcv.boofcv.core)
    implementation(libs.com.google.zxing.core)
    implementation(libs.com.google.accompanist.permissions)

    implementation(libs.com.jakewharton.timber)
    implementation(libs.org.bouncycastle.bcprov)
    implementation(libs.com.sparrowwallet.hummingbird)

    implementation(libs.io.matthewnelson.kmp.tor.runtime)
    implementation(libs.io.matthewnelson.kmp.resource.exec.tor)
    implementation(libs.io.github.dokar3.sonner)
    implementation(libs.dev.chrisbanes.haze)
    implementation(libs.dev.chrisbanes.haze.blur)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
