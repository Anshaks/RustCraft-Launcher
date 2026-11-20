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
