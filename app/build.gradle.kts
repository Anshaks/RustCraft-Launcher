plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val rustRoot = layout.projectDirectory.dir("../rust").asFile
val rustJniLibs = rustRoot.resolve("jniLibs")
val horizonRoot = rootProject.layout.projectDirectory.dir("engine/horizon-android").asFile
val horizonGenerated = layout.buildDirectory.dir("generated/horizonAndroid")
val horizonJniLibs = horizonGenerated.map { it.dir("jniLibs") }
val horizonAssets = horizonGenerated.map { it.dir("assets") }

android {
    namespace = "com.rustcraft.launcher"
    compileSdk = 35
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.rustcraft.launcher"
        minSdk = 29
        targetSdk = 35
        versionCode = 5
        versionName = "1.2.0"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
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

    sourceSets["main"].jniLibs.srcDirs(rustJniLibs, horizonJniLibs)
    sourceSets["main"].java.srcDir(horizonGenerated.map { it.dir("java") })
    sourceSets["main"].assets.srcDir(horizonAssets)

    packaging {
        jniLibs.useLegacyPackaging = true
    }
}

val buildRustAndroid = tasks.register<Exec>("buildRustAndroid") {
    group = "build"
    description = "Compile the Rust launcher core for ARM64 Android."
    workingDir = rustRoot
    commandLine(
        "cargo", "ndk",
        "-t", "arm64-v8a",
        "-o", rustJniLibs.absolutePath,
        "build", "--release",
    )
    inputs.files(fileTree(rustRoot.resolve("src")), rustRoot.resolve("Cargo.toml"))
    outputs.dir(rustJniLibs)
}

val buildHorizonAndroid = tasks.register<Exec>("buildHorizonAndroid") {
    group = "build"
    description = "Build and stage the licensed Horizon Android ARM64 engine."
    workingDir = rootProject.projectDir
    commandLine(
        "python3",
        rootProject.file("scripts/build_horizon_android.py").absolutePath,
        "--engine",
        horizonRoot.absolutePath,
        "--output",
        horizonGenerated.get().asFile.absolutePath,
    )
    outputs.dir(horizonGenerated)
    outputs.upToDateWhen { false }
}

tasks.named("preBuild").configure {
    dependsOn(buildRustAndroid, buildHorizonAndroid)
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui:1.7.6")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.material:material-icons-core:1.7.6")
}
