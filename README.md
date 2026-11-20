# RustCraft Launcher

RustCraft is an Android-first launcher foundation with a native Kotlin/Compose
interface and a Rust core exposed over JNI. Its current working features are
Mojang version-manifest browsing, checksum-verified vanilla client downloads,
offline profiles, launcher settings, and app-private mod/resource-pack/shader
file management.

> **Compatibility status:** This repository does not yet launch Minecraft.
> A Java runtime for Android, game argument/classpath construction, Android
> graphics/input integration, and native LWJGL support still need to be added.
> Microsoft/Xbox authentication and Fabric/Forge installation are also not
> implemented. The UI intentionally does not advertise these as working.

## Architecture

- `app/`: native Android application (minimum Android 10 / API 29), Compose UI,
  coroutine-based I/O, and app-scoped storage (no broad storage permission).
- `rust/`: small synchronous Rust library; JNI commands handle version metadata,
  verified client downloads, offline profiles, settings, and managed files.
- The game directory lives in the app's external files directory. Users can
  import files through Android's Storage Access Framework.

The design follows the general separation of UI, account/profile management,
version metadata, and game files used by public Android launchers such as
[Amethyst](https://github.com/AngelAuraMC/Amethyst-Android) and
[Fold Craft Launcher](https://github.com/FCL-Team/FoldCraftLauncher), without
copying their implementation. Mojang's public version manifest is the source
of vanilla version metadata. RustCraft is independent and is not affiliated
with Mojang or Microsoft. Its visual design is original; it does not use
Apple logos or imply Apple affiliation.

## Build

Prerequisites:

- JDK 17
- Android SDK with platform 35 and NDK 27.2.12479018
- Rust stable and `cargo-ndk` (`cargo install cargo-ndk`)
- Rust targets `aarch64-linux-android`, `armv7-linux-androideabi`,
  `x86_64-linux-android`, and `i686-linux-android`
- Android SDK environment configured (for example, `ANDROID_HOME`)

```sh
rustup target add aarch64-linux-android armv7-linux-androideabi \
  x86_64-linux-android i686-linux-android
./gradlew assembleDebug
```

The Android `preBuild` task compiles the Rust library for the four Android ABIs
and packages it into the APK. Install the debug build with
`adb install app/build/outputs/apk/debug/app-debug.apk`.

Rust core tests can be run on a host with `cargo test --manifest-path rust/Cargo.toml`.

## Storage and downloads

The app writes only to its own Android files directory, under `games/`; Android
removes these files if the app is uninstalled. No
`MANAGE_EXTERNAL_STORAGE` permission is requested. Imported files are copied
from the user's chosen document provider into the appropriate managed folder.
Vanilla installation downloads the version JSON and client JAR and verifies
the SHA-1 hashes published by Mojang before moving files into place. It does
not download libraries/assets or make a version launchable yet.

## License

Project source is licensed under the MIT License. Minecraft is a trademark of
Mojang Studios. This project is not approved by or associated with Mojang or
Microsoft.
