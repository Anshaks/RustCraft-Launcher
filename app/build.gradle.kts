plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val rustRoot = layout.projectDirectory.dir("../rust").asFile
val rustJniLibs = rustRoot.resolve("jniLibs")

android {
    namespace = "com.rustcraft.launcher"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.rustcraft.launcher"
        minSdk = 29
        targetSdk = 35
        versionCode = 3
        versionName = "1.0.2"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
        }
        getByName("release") {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    sourceSets["main"].jniLibs.srcDir(rustJniLibs)

    packaging {
        jniLibs.useLegacyPackaging = true
    }
}

val buildRustAndroid = tasks.register<Exec>("buildRustAndroid") {
    group = "build"
    description = "Compile the Rust JNI core for supported Android ABIs."
    workingDir = rustRoot
    commandLine(
        "cargo", "ndk",
        "-t", "arm64-v8a",
        "-t", "armeabi-v7a",
        "-t", "x86_64",
        "-t", "x86",
        "-o", rustJniLibs.absolutePath,
        "build", "--release",
    )
    inputs.files(fileTree(rustRoot.resolve("src")), rustRoot.resolve("Cargo.toml"))
    outputs.dir(rustJniLibs)
}

tasks.named("preBuild").configure {
    dependsOn(buildRustAndroid)
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui:1.7.6")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.material:material-icons-core:1.7.6")
}
