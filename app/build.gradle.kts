plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.kevinluo.autoglm"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kevinluo.autoglm"
        minSdk = 24
        targetSdk = 34
        versionCode = 5
        versionName = "0.0.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystoreFile = file("release.keystore")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: ""
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        debug {
            // 不再使用 applicationIdSuffix，与发行版使用相同包名
            resValue("string", "app_name", "AutoGLM Dev")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (file("release.keystore").exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    buildFeatures {
        buildConfig = true
        aidl = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    
    // Enable JUnit 5 for Kotest property-based testing
    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
        unitTests.isReturnDefaultValues = true
    }
}

// Copy dev_profiles.json to assets for debug builds only
android.applicationVariants.all {
    val variant = this
    
    // Custom APK file name
    outputs.all {
        val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
        output.outputFileName = "AutoGLM-${variant.versionName}-${variant.buildType.name}.apk"
    }
    
    if (variant.buildType.name == "debug") {
        val copyDevProfiles = tasks.register("copyDevProfiles${variant.name.replaceFirstChar { it.uppercase() }}") {
            val devProfilesFile = rootProject.file("dev_profiles.json")
            // Use debug-specific assets directory to avoid polluting release builds
            val assetsDir = file("src/debug/assets")
            
            doLast {
                if (devProfilesFile.exists()) {
                    assetsDir.mkdirs()
                    devProfilesFile.copyTo(File(assetsDir, "dev_profiles.json"), overwrite = true)
                    println("Copied dev_profiles.json to debug assets")
                } else {
                    println("dev_profiles.json not found, skipping")
                }
            }
        }
        
        tasks.named("merge${variant.name.replaceFirstChar { it.uppercase() }}Assets") {
            dependsOn(copyDevProfiles)
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.activity:activity:1.8.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Shizuku for system-level operations
    implementation("dev.rikka.shizuku:api:13.5.4")
    implementation("dev.rikka.shizuku:provider:13.5.4")

    // Kotlin Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Lifecycle & ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // Security for encrypted preferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // OkHttp for API communication
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Retrofit for API communication
    implementation("com.squareup.retrofit2:retrofit:2.9.0")

    // Kotlin Serialization for JSON parsing
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
    testImplementation("io.kotest:kotest-property:5.8.0")
    testImplementation("io.kotest:kotest-assertions-core:5.8.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}