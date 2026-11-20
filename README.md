# RustCraft Launcher

RustCraft is an ARM64-only Android application with an original Compose launcher
and a native Rust game engine. The launcher opens an integrated Android build
of [Horizon Android](https://github.com/GoyDevv/Horizon-Android), pinned as a
source submodule at `ae4c85dc5b31da2078ee1e1009a53ced7d7ed1ba`. RustCraft's
launcher has its own dark field-console visual identity, geometric R mark,
navigation dock, screens, controls and transitions; it does not copy Horizon's
launcher layouts, illustrations, typography or assets. The game engine remains
an integrated NativeActivity using its Rust/wgpu renderer.

Immersive sticky fullscreen is enabled in the RustCraft launcher and game
activity. System bars can be temporarily revealed with an edge swipe and hide
again automatically. The native engine's Android screen hook reapplies the
same behavior when it changes display settings or regains focus.

Minecraft 26.3 is the only engine game version. Horizon is a Rust reimplementation
and does not execute the Java client or install a JVM/JRE on the Android device.
The engine downloads and verifies the resources it needs from Mojang when the
player installs them from the in-app engine screen. A Java toolchain may be used
on the build host by Horizon's asset-preparation tools. Its preparation step
downloads a Mojang Java runtime to run resource exporters; this is build-only,
is not copied into the APK, and is not used to start the game.

## Features and current boundaries

- RustCraft package: `com.rustcraft.launcher`; Android 10+; ARM64-v8a only.
- Original RustCraft launcher UI, adaptive icon, system splash entry, settings,
  navigation and credits. Native engine strings and mark are branded at build
  time; the Compose app does not reuse Horizon launcher visual assets or layouts.
- Immersive sticky hiding of Android status/navigation bars in the launcher and
  native game activity, with transient swipe-to-reveal behavior.
- Native Rust Horizon Android engine with Vulkan and GLES paths, engine-managed
  26.3 resource installation, touch controls, game settings, offline accounts,
  Ely.by authentication, resource packs and Horizon-native mods.
- RustCraft's offline profile selection is handed to the native engine through
  its documented app-private launcher configuration file.
- Horizon Android code and all third-party notices are included in the source
  submodule and APK assets. The integrated engine source is MIT-licensed at the
  pinned revision; dependencies retain their own licenses. See
  `engine/horizon-android/LICENSE` and `engine/horizon-android/THIRD_PARTY_NOTICES.md`.

**Authentication limitation:** Horizon Android documents Ely.by password and
2FA authentication and Microsoft browser/device-code login. Its current Android
docs identify the Microsoft client ID as a temporary development registration,
pending project-owned registration and Mojang approval; live Microsoft login
is not claimed as production-ready. No credentials or access tokens are added
to this project. Authentication must be tested with account-owner consent.

**Validation limitation:** A successful APK build and source-level tests do
not prove gameplay on physical Android hardware. GPU-driver compatibility,
thermal performance and real account sign-in need device/owner testing. This
repository does not claim those tests until they are actually run.

## Source and licensing

The engine source is a pinned Git submodule, not a copied APK or bundled
third-party binary:

```sh
git submodule update --init --recursive
git -C engine/horizon-android rev-parse HEAD
```

The expected source revision is
`ae4c85dc5b31da2078ee1e1009a53ced7d7ed1ba`. Build-time branding and immersive
system-bar edits are applied temporarily by `scripts/build_horizon_android.py`;
the required Java bridge is copied into Gradle's generated source directory and
the submodule is restored after staging. The engine's public packaging helper
includes only allowlisted generated engine tables in the APK; downloaded
Minecraft game inputs, host preparation files and signing keys are not staged
as app assets.

The APK includes the Horizon license and complete third-party notice file in
its assets. Keep those notices with redistributed builds. RustCraft's own
project license remains in `LICENSE`.

## Build requirements

- JDK 17 or 21 for Android/Gradle (Horizon also prepares Java-based export
  inputs on the build host; no JVM is installed on the game device).
- Android SDK platform 35, build-tools 35.0.0, NDK 28.2.13676358.
- Python 3, Rust 1.95 (installed from the engine's `rust-toolchain.toml`),
  `aarch64-linux-android` Rust target and `cargo-ndk`.
- Git submodules initialized and `ANDROID_HOME` configured.
- Disk space and network access to download the pinned Mojang inputs used for
  local engine preparation. These stay under the ignored
  `engine/horizon-android/.local/` directory.

```sh
git submodule update --init --recursive
rustup toolchain install 1.95.0 --profile minimal --component clippy,rustfmt \
  --target aarch64-linux-android
cargo +1.95.0 install cargo-ndk
./gradlew :app:assembleRelease
```

The build prepares Horizon's pinned inputs on the host, compiles the Horizon
native library for ARM64 with Horizon's optimized `fast` profile (opt-level 3,
without expensive ThinLTO), stages the public engine data and license notices,
and builds RustCraft's APK. Only `arm64-v8a` native libraries are packaged.
The local release build uses the Android debug signing key for testing; use a
private, persistent release keystore for production updates.

## Tests and CI

```sh
cargo test --manifest-path rust/Cargo.toml
python3 -m unittest discover -s engine/horizon-android/tests -p 'test_android*.py'
./gradlew :app:assembleRelease
```

`.github/workflows/android-arm64.yml` builds and uploads the APK as a workflow
artifact on pushes, pull requests and manual runs. It verifies the APK signature,
package identity, and that no non-ARM64 native library is included. Workflow
artifacts are not published as GitHub Releases automatically.

## Account and game data

Horizon stores accounts, profiles, worlds, settings, caches and game resources
in the app's private Android storage. Offline profile names are local. Microsoft
and Ely.by credentials are handled by the engine's account flows; RustCraft
does not store passwords or tokens itself. The first engine install downloads
approximately 500 MiB according to the Android port documentation.

Minecraft is a trademark of Mojang Studios. RustCraft is independent and is not
approved by or associated with Mojang or Microsoft.

---

## About

### Project and purpose

**RustCraft Launcher** is an independent Android launcher and native Minecraft:
Java Edition client experience for ARM64 Android devices. Its purpose is to
combine a lightweight, original RustCraft interface with the native game engine
port pinned in this repository, while keeping profiles, 26.3 installation,
touch and hardware input, content management, graphics choices and launcher
settings available in one application.

This project is not a recolored copy of an upstream launcher. The RustCraft
launcher uses its own Compose hierarchy, dark field-console visual identity,
navigation dock, controls, iconography and screen transitions. Horizon's native
engine implementation remains under the engine's own license and source
attribution, but it is not used as the RustCraft launcher UI.

### Original launcher and port lineage

- **Original launcher name:** Horizon.
- **Modified/ported launcher:** [Horizon Android](https://github.com/GoyDevv/Horizon-Android),
  described by that repository as an unofficial Android port of Horizon that
  rewrites Minecraft Java Edition 26.3 in Rust.
- **Port purpose:** The Android-compatible native engine is integrated into
  RustCraft's existing application package and original launcher experience.
  RustCraft is not presented as an official Horizon, Mojang or Microsoft product.
- **Engine/license lineage:** The pinned engine source is MIT-licensed and
  identifies Ludovic Marie and the Horizon contributors as copyright holders.
  See `engine/horizon-android/LICENSE` and
  `engine/horizon-android/THIRD_PARTY_NOTICES.md` for the upstream and
  third-party license notices.

### Credits

**Original/upstream engine credit**

- **Horizon original author/contributor:** Ludovic Marie is named by the Horizon
  MIT license and Horizon contributor records. GitHub account `ludovic111` is
  the largest verifiable contributor to the Horizon Android repository.
- **Horizon Android port maintainer:** [GoyDevv](https://github.com/GoyDevv)
  maintains the `GoyDevv/Horizon-Android` repository used as this project's
  engine source fork.
- The Horizon Android repository and the engine's third-party notice file credit
  additional listed contributors and dependency authors; they are retained
  rather than represented as RustCraft employees or original RustCraft authors.

**RustCraft maintainer and port integration**

- **RustCraft repository owner and maintainer:** [Anshaks](https://github.com/Anshaks),
  owner of `Anshaks/RustCraft-Launcher`. Git commit metadata identifies
  `Anshaks` as the author of the RustCraft launcher source and the Horizon
  integration commits.
- **Port developer:** Work committed under the `Anshaks` repository identity
  integrated the pinned Horizon Android engine, created the RustCraft launcher
  integration, configured ARM64 build paths and implemented the custom launcher
  redesign.

No other person or organization is identified here as a maintainer unless it
is supported by repository metadata or a project license/notice file.

### Technologies and libraries

- **Android UI:** Kotlin and Jetpack Compose, AndroidX Activity Compose,
  Compose Material 3 and AndroidX Core.
- **Android native bridge:** Java `NativeActivity` integration and Android JNI.
- **Launcher core:** Rust 2021 core library using `jni`, `serde`, `serde_json`,
  `sha1` and `ureq`; built as an ARM64 Android shared library.
- **Game engine:** Pinned Rust Horizon Android source using `wgpu`, with Vulkan
  and OpenGL ES graphics paths.
- **Build system:** Gradle 8.9, Android Gradle Plugin 8.7.3, Kotlin plugin
  2.0.21, Android SDK platform 35, build-tools 35.0.0 and NDK 28.2.13676358.
- **Native toolchain:** Rust 1.95, Android `aarch64-linux-android` target and
  `cargo-ndk`; Cargo ThinLTO is not used by the project build's `fast` profile.
- **Build automation:** Python 3 build/staging scripts and GitHub Actions.
- **Source management:** Git submodule at Horizon Android commit
  `ae4c85dc5b31da2078ee1e1009a53ced7d7ed1ba`.
- **Licenses and notices:** MIT license for the integrated Horizon source,
  plus the complete third-party notices packaged in the APK assets.

### Existing features preserved in the port

The RustCraft integration preserves the upstream engine's existing feature
architecture and the existing RustCraft application functionality:

- Minecraft Java Edition 26.3 as the only supported game version.
- Engine-managed Minecraft resource installation and verification.
- Offline profiles, Microsoft/Ely.by account flows exposed by the engine,
  with up to three RustCraft launcher profiles where implemented.
- Native touch controls and hardware input support for physical keyboards,
  mice and compatible gamepads/controllers.
- Editable touch layout support, including control position, size and opacity
  preferences maintained by the engine.
- Graphics and rendering choices, resolution scaling and performance settings
  exposed by Horizon Android.
- Native Horizon `.hmod` mods, Horizon-native mod management, resource packs,
  shader packs and installed-content file management.
- SAF/document-provider file operations and app-private game storage.
- Java multiplayer/LAN/server functionality provided by the integrated engine.
- Rust launcher-core profile, settings and managed-file operations.
- The original RustCraft launcher flows for the dashboard, build selection,
  content library, profile management and launcher preferences.

Authentication, GPU, gameplay and account behavior should not be considered
device-verified until it has been tested on physical Android hardware.

### RustCraft modifications and fixes

- Replaced the RustCraft launcher presentation with a completely original,
  independent Compose interface rather than copying or recoloring Horizon's
  launcher UI.
- Added an original RustCraft field-console visual identity, adaptive icon,
  navigation dock, build/content/settings screens and page transitions.
- Integrated the pinned Horizon Android native engine under the RustCraft
  package identity `com.rustcraft.launcher`.
- Added ARM64-only ABI packaging and verified the native `libhorizon_android.so`
  and `librustcraft_core.so` package entries.
- Added build-time staging for the licensed engine library, public 26.3 engine
  tables, Horizon license and third-party notices.
- Added temporary build-time Horizon branding and reverted the upstream submodule
  after staging so the engine source remains pinned and clean.
- Preserved the existing keyboard, mouse, touch, renderer and native engine input
  code instead of replacing the Horizon runtime pipeline.
- **Android navigation fix:** added `WindowCompat.setDecorFitsSystemWindows`,
  `WindowInsetsControllerCompat` and
  `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` to hide system status/navigation bars
  in the RustCraft launcher and NativeActivity game activity.
- The navigation fix reapplies immersive sticky mode when the app resumes,
  regains window focus and the native engine changes or reapplies display
  settings. Edge swipe can temporarily reveal Android system bars, and they
  hide again automatically. This covers supported edge-swipe and
  three-button-navigation environments without installing fake navigation
  buttons in the game UI.

### Development and build details

Verifiable RustCraft repository history starts with the initial commit at
`2026-11-20T04:32:13Z` and reaches the redesign commit at
`2026-11-20T14:13:49Z`. This represents a recorded commit span of approximately
**9 hours and 41 minutes**, not a certified count of active engineering hours.

The build process prepares the pinned Minecraft 26.3 engine resources on the
build host, compiles Horizon and the RustCraft core for ARM64, stages generated
public engine tables and licenses, then assembles the Android package. GitHub
Actions runs the Rust and Horizon Android test suites, builds `app-release.apk`,
verifies the signature/package identity and rejects packaged native libraries
outside `arm64-v8a`.

The repository previously published a debug-signed preview APK. The release
download uses that preview signing certificate and is separate from any existing
app-store or production-signed update. Preview signing and in-place-update
limitations are identified in the existing Release notes.

### Android support

- **Minimum Android:** Android 10 / API 29, as configured by RustCraft
  `minSdk = 29`.
- **Target Android:** Android 15 / API 35, as configured by
  `targetSdk = 35` and `compileSdk = 35`.
- **Horizon upstream configuration:** the pinned Android source documents API
  28 through API 35 support.
- **Architecture:** ARM64-v8a only. ARM32, x86 and x86_64 ABI output is excluded
  by RustCraft's NDK ABI filter.
- **Graphics:** the engine uses Vulkan and OpenGL ES paths and requires an
  appropriate device GPU/driver.
- **Android 15 status:** Android 15/API 35 is not unsupported by this project
  configuration; it is included in the confirmed compile and target API level.
  Android 16/API 36 and later versions are not explicitly confirmed by the
  project's SDK configuration or repository testing, so compatibility beyond
  Android 15 should not be claimed.
