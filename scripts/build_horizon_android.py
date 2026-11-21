#!/usr/bin/env python3
"""Build Horizon's public ARM64 engine and stage its redistributable payload."""

import argparse
import importlib.util
import os
from pathlib import Path
import shutil
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
ANDROID_BUILD_PROFILE = "fast"

BRANDING_REPLACEMENTS = {
    "packaging/android/java/dev/horizon/android/HorizonActivity.java": [
        (
            "@Override protected void onCreate(android.os.Bundle savedInstanceState) {\n"
            "        loadGame();\n"
            "        super.onCreate(savedInstanceState);\n"
            "        applyHighRefreshRate(this, 0);\n"
            "    }",
            "private void applyRustCraftImmersive() {\n"
            "        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);\n"
            "        androidx.core.view.WindowInsetsControllerCompat controller =\n"
            "                new androidx.core.view.WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());\n"
            "        controller.setSystemBarsBehavior(\n"
            "                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);\n"
            "        controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars());\n"
            "    }\n\n"
            "    @Override protected void onCreate(android.os.Bundle savedInstanceState) {\n"
            "        loadGame();\n"
            "        super.onCreate(savedInstanceState);\n"
            "        applyHighRefreshRate(this, 0);\n"
            "        applyRustCraftImmersive();\n"
            "    }",
        ),
        (
            "    @Override protected void onResume() {\n"
            "        super.onResume();\n"
            "        applyHighRefreshRate(this, 0);\n"
            "    }",
            "    @Override protected void onResume() {\n"
            "        super.onResume();\n"
            "        applyHighRefreshRate(this, 0);\n"
            "        applyRustCraftImmersive();\n"
            "    }",
        ),
        (
            "    @Override public void onWindowFocusChanged(boolean hasFocus) {\n"
            "        super.onWindowFocusChanged(hasFocus);\n"
            "        if (hasFocus) {\n"
            "            applyHighRefreshRate(this, 0);\n"
            "        }\n"
            "    }",
            "    @Override public void onWindowFocusChanged(boolean hasFocus) {\n"
            "        super.onWindowFocusChanged(hasFocus);\n"
            "        if (hasFocus) {\n"
            "            applyHighRefreshRate(this, 0);\n"
            "            applyRustCraftImmersive();\n"
            "        }\n"
            "    }",
        ),
    ],
    "crates/horizon-client/src/android/launcher_pages.rs": [
        (
            '        // ---- Top Header Bar (brand lockup ... account pill) ----\n'
            '        ui.horizon_logo(18.0, 12.0, 28.0);',
            '        // RustCraft block mark, drawn by the engine UI renderer.\n'
            '        ui.rounded(18.0, 12.0, 28.0, 28.0, 7.0, my3_accent());\n'
            '        ui.fill(23.0, 18.0, 18.0, 6.0, my3_on_accent());\n'
            '        ui.fill(23.0, 24.0, 18.0, 10.0, my3_surface());',
        ),
        (
            'ui.text(Face::SemiBold, 16.0, 54.0, 10.0, my3_text(), "Horizon");',
            'ui.text(Face::SemiBold, 16.0, 54.0, 10.0, my3_text(), "RustCraft");',
        ),
        (
            '                ui.icon(\n'
            '                    Icon::HorizonLogo,\n'
            '                    content_x + 28.0,\n'
            '                    card_y + 26.0,\n'
            '                    22.0,\n'
            '                    my3_accent(),\n'
            '                );',
            '                ui.rounded(content_x + 27.0, card_y + 25.0, 24.0, 24.0, 6.0, my3_accent());\n'
            '                ui.fill(content_x + 31.0, card_y + 30.0, 15.0, 5.0, my3_on_accent());\n'
            '                ui.fill(content_x + 31.0, card_y + 36.0, 15.0, 8.0, my3_surface());',
        ),
        ('"Exit Horizon Android?"', '"Exit RustCraft?"'),
        ('"Horizon Android Update"', '"RustCraft Update"'),
        (
            'let show_corner_links = page == Page::Settings\n'
            '                && self.catalog.settings_category == SettingsCategory::Overview;',
            'let show_corner_links = false;',
        ),
        ('"Horizon Mod Engine"', '"RustCraft Mod Engine"'),
        ('"Official Horizon Mods • In-Process Engine"', '"Compatible Mods • In-Process Engine"'),
        ('"Horizon Built-in • Tap for versions, Modrinth info & screenshots"', '"Built-in • Tap for versions, Modrinth info & screenshots"'),
        ('"Horizon BG"', '"Voxel Sky"'),
        (
            '"CHANGELOG — MINECRAFT 26.3 • HORIZON ANDROID v{} (DOWNLOADED)",\n'
            "                        info.version",
            '"CHANGELOG — MINECRAFT 26.3 • RUSTCRAFT v{} (DOWNLOADED)",\n'
            "                        info.version",
        ),
        (
            '"CHANGELOG — MINECRAFT 26.3 • HORIZON ANDROID v{} (DOWNLOADED)",\n'
            "                        crate::updater::CURRENT_VERSION",
            '"CHANGELOG — MINECRAFT 26.3 • RUSTCRAFT v{} (DOWNLOADED)",\n'
            "                        crate::updater::CURRENT_VERSION",
        ),
        (
            '"RELEASE HIGHLIGHTS — MINECRAFT 26.3 • HORIZON ANDROID"',
            '"RUSTCRAFT ENGINE — MINECRAFT 26.3"',
        ),
        (
            '"• Google Sans typography, Jetpack Compose Material You UI & GoyControl snap-grid"',
            '"• Native Rust engine, mobile controls and Android-specific rendering"',
        ),
        (
            '"Pick a control .json file — Horizon will auto-detect MCPE or GoyControl and save it in the right folder."',
            '"Pick a control .json file — RustCraft will detect the supported control format and save it in the right folder."',
        ),
        (
            '"Open system Files > Horizon Android."',
            '"Open system Files > RustCraft."',
        ),
        (
            '"Open system Files > Horizon Android > Worlds. Copy complete world folders while the game is closed."',
            '"Open system Files > RustCraft > Worlds. Copy complete world folders while the game is closed."',
        ),
        (
            '"Reports are saved under Horizon Android > Reports."',
            '"Reports are saved under RustCraft > Reports."',
        ),
        (
            '"Theme reset to Horizon default (Obsidian Violet)."',
            '"Theme reset to the RustCraft default (Obsidian Violet)."',
        ),
        (
            '        ui.icon(\n'
            '            Icon::HorizonLogo,\n'
            '            mx + 31.0,\n'
            '            head_y + 11.0,\n'
            '            22.0,\n'
            '            my3_accent(),\n'
            '        );',
            '        ui.rounded(mx + 30.0, head_y + 10.0, 24.0, 24.0, 6.0, my3_accent());\n'
            '        ui.fill(mx + 34.0, head_y + 15.0, 15.0, 5.0, my3_on_accent());\n'
            '        ui.fill(mx + 34.0, head_y + 21.0, 15.0, 8.0, my3_surface());',
        ),
        (
            '                // Single integrated Community Tonal Pill Row inside Left Bento Card (no duplicate Discord!)\n'
            '                let comm_y = bento_y + bento_h - 36.0;\n'
            '                button(\n'
            '                    &mut ui,\n'
            '                    &mut self.targets,\n'
            '                    [content_x + 14.0, comm_y, 92.0, 26.0],\n'
            '                    "Discord",\n'
            '                    Action::Discord,\n'
            '                    false,\n'
            '                );\n'
            '                button(\n'
            '                    &mut ui,\n'
            '                    &mut self.targets,\n'
            '                    [content_x + 112.0, comm_y, 92.0, 26.0],\n'
            '                    "GitHub",\n'
            '                    Action::Repository,\n'
            '                    false,\n'
            '                );',
            '                // RustCraft keeps the game dashboard free of outbound promotions.',
        ),
    ],
    "crates/horizon-client/src/ui_theme.rs": [
        (
            '        self.draw_animated_horizon_mark(cx, cy, mark_size, elapsed, dissolve);',
            '        let block = mark_size * 0.64;\n'
            '        let left = cx - block * 0.5;\n'
            '        let top = cy - block * 0.5;\n'
            '        self.fill(left, top, block, block * 0.30, color::with_alpha(color::hex(0x69A84F, 255), dissolve));\n'
            '        self.fill(left, top + block * 0.30, block, block * 0.70, color::with_alpha(color::hex(0x79502D, 255), dissolve));\n'
            '        self.fill(left + block * 0.18, top + block * 0.48, block * 0.22, block * 0.22, color::with_alpha(color::hex(0x8B633D, 255), dissolve));\n'
            '        self.fill(left + block * 0.60, top + block * 0.70, block * 0.22, block * 0.18, color::with_alpha(color::hex(0x8B633D, 255), dissolve));',
        ),
        ('                "HORIZON",', '                "RUSTCRAFT",'),
    ],
    "crates/horizon-client/src/android/launcher.rs": [
        (
            'events.create_window(Window::default_attributes().with_title("Horizon Android"))?',
            'events.create_window(Window::default_attributes().with_title("RustCraft"))?',
        ),
    ],
    "crates/horizon-client/src/android/document_store.rs": [
        ('"_display_name": "Horizon Android"', '"_display_name": "RustCraft"'),
        ('"title": "Horizon Android"', '"title": "RustCraft"'),
    ],
    "crates/horizon-client/src/android/performance.rs": [
        (
            '"Open the system Files app, then Horizon Android / Reports."',
            '"Open the system Files app, then RustCraft / Reports."',
        ),
    ],
    "crates/horizon-client/src/android_main.rs": [
        ('pub const VERSION: &str = "1.0.1";', 'pub const VERSION: &str = "1.2.0";'),
    ],
    "crates/horizon-client/src/android/native_ime.rs": [
        (
            "fn apply_screen(&self, env: &mut JNIEnv<'_>, immersive: bool)",
            "fn apply_screen(&self, env: &mut JNIEnv<'_>, _immersive: bool)",
        ),
        (
            '                    if immersive { "hide" } else { "show" },\n'
            '                    "(I)V",\n'
            '                    &[JValue::Int(2)],',
            '                    "hide",\n'
            '                    "(I)V",\n'
            '                    &[JValue::Int(2)],',
        ),
        (
            '&[JValue::Int(if immersive { 0x1706 } else { 0x0704 })],',
            '&[JValue::Int(0x1706)],',
        ),
        (
            "self.immersive.store(immersive, Ordering::Release);",
            "self.immersive.store(true, Ordering::Release);",
        ),
        (
            "self.apply_screen(env, immersive)",
            "self.apply_screen(env, true)",
        ),
    ],
}

SOURCE_REGION_REPLACEMENTS = [
    (
        "crates/horizon-client/src/official_install.rs",
        "fn hash(mut file: impl Read) -> Result<String> {",
        "fn relative(name: &str) -> Result<&Path> {",
        r'''fn hashes(mut file: impl Read) -> Result<(String, String)> {
    let mut sha1 = Sha1::new();
    let mut sha256 = Sha256::new();
    let mut buffer = [0; 65536];
    loop {
        let n = file.read(&mut buffer)?;
        if n == 0 {
            break;
        }
        sha1.update(&buffer[..n]);
        sha256.update(&buffer[..n]);
    }
    Ok((
        format!("{:x}", sha1.finalize()),
        format!("{:x}", sha256.finalize()),
    ))
}

fn save_sha256_receipt(path: &Path, digest: &str) -> Result<()> {
    let receipt = path.with_extension("sha256");
    let temporary = path.with_extension("sha256.partial");
    fs::write(&temporary, digest)?;
    fs::rename(temporary, receipt)?;
    Ok(())
}

fn valid_partial_response(offset: u64, status: u16, content_range: Option<&str>) -> bool {
    offset > 0
        && status == 206
        && content_range.is_some_and(|range| range.starts_with(&format!("bytes {offset}-")))
}

struct Download {
    url: String,
    sha1: String,
    size: u64,
    path: PathBuf,
}

impl Download {
    fn from(value: &Value, path: PathBuf) -> Result<Self> {
        let file = Self {
            url: value["url"].as_str().ok_or("Missing download URL")?.into(),
            sha1: value["sha1"]
                .as_str()
                .ok_or("Missing download hash")?
                .into(),
            size: value["size"].as_u64().ok_or("Missing download size")?,
            path,
        };
        official_url(&file.url)?;
        if file.sha1.len() != 40
            || !file.sha1.bytes().all(|c| c.is_ascii_hexdigit())
            || file.size > 1024 * 1024 * 1024
        {
            return Err("Invalid Mojang download metadata".into());
        }
        Ok(file)
    }

    fn get(&self) -> Result<()> {
        self.get_with_progress(|_| {})
    }

    fn get_with_progress(&self, mut progress: impl FnMut(u64)) -> Result<()> {
        match fs::File::open(&self.path) {
            Ok(file) if file.metadata()?.len() == self.size => {
                let (sha1, sha256) = hashes(&file)?;
                if sha1 == self.sha1 {
                    let receipt = self.path.with_extension("sha256");
                    if fs::read_to_string(&receipt).ok().as_deref() != Some(sha256.as_str()) {
                        save_sha256_receipt(&self.path, &sha256)?;
                    }
                    return Ok(());
                }
            }
            Ok(_) => {}
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
            Err(error) => {
                return Err(format!(
                    "Could not read cached Minecraft file {}: {error}",
                    self.path.display()
                )
                .into());
            }
        }

        fs::create_dir_all(self.path.parent().ok_or("Missing download directory")?)?;
        let partial = self.path.with_extension("partial");
        let mut last_error = "Download did not complete".to_string();
        for attempt in 0..5 {
            let offset = match fs::metadata(&partial) {
                Ok(metadata) if metadata.len() <= self.size => metadata.len(),
                Ok(_) => {
                    fs::remove_file(&partial)?;
                    0
                }
                Err(error) if error.kind() == std::io::ErrorKind::NotFound => 0,
                Err(error) => return Err(error.into()),
            };
            if offset == self.size && partial.is_file() {
                let (sha1, sha256) = hashes(fs::File::open(&partial)?)?;
                if sha1 == self.sha1 {
                    fs::rename(&partial, &self.path)?;
                    save_sha256_receipt(&self.path, &sha256)?;
                    return Ok(());
                }
                fs::remove_file(&partial)?;
                continue;
            }
            let mut request = agent().get(&self.url);
            if offset > 0 {
                request = request.set("Range", &format!("bytes={offset}-"));
            }
            let response = match request.call() {
                Ok(response) => response,
                Err(error) => {
                    last_error = error.to_string();
                    if attempt < 4 {
                        std::thread::sleep(std::time::Duration::from_secs(1 << attempt));
                    }
                    continue;
                }
            };
            let content_range = response.header("Content-Range").unwrap_or_default();
            let resumed = valid_partial_response(
                offset,
                response.status(),
                Some(content_range),
            );
            if offset > 0 && response.status() == 206 && !resumed {
                drop(response);
                fs::remove_file(&partial)?;
                last_error = "Server returned an invalid partial-download range".into();
                continue;
            }
            let start = if resumed { offset } else { 0 };
            let result = (|| -> Result<u64> {
                let mut input = response
                    .into_reader()
                    .take(self.size.saturating_sub(start).saturating_add(1));
                let mut options = OpenOptions::new();
                options.create(true).write(true);
                if resumed {
                    options.append(true);
                } else {
                    options.truncate(true);
                }
                let mut output = options.open(&partial)?;
                let mut downloaded = start;
                progress(downloaded);
                let mut buffer = [0; 65536];
                loop {
                    let count = input.read(&mut buffer)?;
                    if count == 0 {
                        break;
                    }
                    output.write_all(&buffer[..count])?;
                    downloaded = downloaded.saturating_add(count as u64);
                    progress(downloaded);
                }
                output.flush()?;
                Ok(downloaded)
            })();

            match result {
                Ok(downloaded) if downloaded == self.size => {
                    let (sha1, sha256) = hashes(fs::File::open(&partial)?)?;
                    if sha1 != self.sha1 {
                        let _ = fs::remove_file(&partial);
                        last_error = "Mojang SHA-1 verification failed".into();
                    } else {
                        fs::rename(&partial, &self.path)?;
                        save_sha256_receipt(&self.path, &sha256)?;
                        return Ok(());
                    }
                }
                Ok(downloaded) if downloaded > self.size => {
                    let _ = fs::remove_file(&partial);
                    last_error = "Download exceeded the verified file size".into();
                }
                Ok(_) => last_error = "Connection ended before the file finished".into(),
                Err(error) => last_error = error.to_string(),
            }
            if attempt < 4 {
                std::thread::sleep(std::time::Duration::from_secs(1 << attempt));
            }
        }
        Err(format!(
            "Could not download Minecraft file {}: {last_error}",
            self.path.display()
        )
        .into())
    }

    fn open(&self) -> Result<fs::File> {
        cache_input::open(&self.path, || self.get())
    }
}

''',
    ),
]

SOURCE_TEXT_REPLACEMENTS = [
    (
        "crates/horizon-client/src/official_install.rs",
        [
            (
                "/// Android supplies Context.getCacheDir(), keeping temporary downloads out of files/.",
                "/// Keep installer inputs in app-private files so interrupted transfers survive cache eviction.",
            ),
            (
                "use sha1::{Digest, Sha1};",
                "use sha1::{Digest, Sha1};\nuse sha2::Sha256;",
            ),
            (
                "#[cfg(test)]\nmod tests {\n    use super::*;",
                "#[cfg(test)]\nmod tests {\n    use super::*;\n\n"
                "    #[test]\n"
                "    fn partial_response_must_match_requested_range() {\n"
                "        assert!(valid_partial_response(12, 206, Some(\"bytes 12-19/20\")));\n"
                "        assert!(!valid_partial_response(12, 200, Some(\"bytes 12-19/20\")));\n"
                "        assert!(!valid_partial_response(12, 206, Some(\"bytes 0-19/20\")));\n"
                "        assert!(!valid_partial_response(0, 206, Some(\"bytes 0-19/20\")));\n"
                "    }\n\n"
                "    #[test]\n"
                "    fn downloads_compute_sha1_and_sha256_receipts() {\n"
                "        let (sha1, sha256) = hashes(b\"abc\".as_slice()).unwrap();\n"
                "        assert_eq!(sha1, \"a9993e364706816aba3e25717850c26c9cd0d89d\");\n"
                "        assert_eq!(sha256, \"ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad\");\n"
                "        let stamp = std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_nanos();\n"
                "        let root = std::env::temp_dir().join(format!(\"rustcraft-sha256-{}-{stamp}\", std::process::id()));\n"
                "        fs::create_dir(&root).unwrap();\n"
                "        let file = root.join(\"client.jar\");\n"
                "        fs::write(&file, b\"abc\").unwrap();\n"
                "        save_sha256_receipt(&file, &sha256).unwrap();\n"
                "        assert_eq!(fs::read_to_string(root.join(\"client.sha256\")).unwrap(), sha256);\n"
                "        fs::remove_dir_all(root).unwrap();\n"
                "    }",
            ),
            (
                "static DOWNLOAD_CACHE_DIR: OnceLock<PathBuf> = OnceLock::new();",
                "static DOWNLOAD_CACHE_DIR: OnceLock<PathBuf> = OnceLock::new();\n"
                "static LEGACY_DOWNLOAD_CACHE_DIR: OnceLock<PathBuf> = OnceLock::new();",
            ),
            (
                "pub fn configure_download_cache_dir(directory: PathBuf) {\n"
                "    let _ = DOWNLOAD_CACHE_DIR.set(directory.join(\"minecraft-downloads\"));\n"
                "}",
                "pub fn configure_download_cache_dir(home: PathBuf, previous_cache: PathBuf) {\n"
                "    let _ = DOWNLOAD_CACHE_DIR.set(home.join(\".local/downloads\"));\n"
                "    let _ = LEGACY_DOWNLOAD_CACHE_DIR.set(previous_cache.join(\"minecraft-downloads\"));\n"
                "}",
            ),
            (
                "fn cleanup_system_download_cache() -> Result<()> {\n"
                "    let Some(directory) = DOWNLOAD_CACHE_DIR.get() else {\n"
                "        return Ok(());\n"
                "    };",
                "fn cleanup_system_download_cache() -> Result<()> {\n"
                "    let Some(directory) = LEGACY_DOWNLOAD_CACHE_DIR.get() else {\n"
                "        return Ok(());\n"
                "    };",
            ),
            (
                "    fs,\n    io::{Read, Write},",
                "    fs::{self, OpenOptions},\n    io::{Read, Write},",
            ),
            (
                "#[derive(Clone, Copy, Debug, Default)]\npub struct InstallProgress {\n"
                "    pub stage: InstallStage,\n    pub done: u64,\n    pub total: u64,\n}",
                "#[derive(Clone, Debug, Default)]\npub struct InstallProgress {\n"
                "    pub stage: InstallStage,\n    pub done: u64,\n    pub total: u64,\n"
                "    pub component: String,\n}",
            ),
            (
                "    let report = |stage, done, total| progress(InstallProgress { stage, done, total });",
                "    let current_component = Mutex::new(String::new());\n"
                "    let report = |stage, done, total| {\n"
                "        let component = current_component.lock().unwrap_or_else(|e| e.into_inner());\n"
                "        progress(InstallProgress {\n"
                "            stage,\n            done,\n            total,\n"
                "            component: if stage == InstallStage::Download && !component.is_empty() {\n"
                "                component.clone()\n            } else {\n"
                "                stage.label().to_string()\n            },\n        })\n    };",
            ),
            (
                "    if cache != legacy && legacy.is_dir() && !cache.exists() {\n"
                "        let root = fs::canonicalize(home)?;\n"
                "        if fs::canonicalize(&legacy)? != root.join(\".local/downloads\").join(version) {\n"
                "            return Err(\"Refusing redirected legacy installer cache\".into());\n"
                "        }\n"
                "        fs::create_dir_all(cache.parent().ok_or(\"Missing cache parent\")?)?;\n"
                "        fs::rename(&legacy, &cache)?;\n"
                "    }",
                "    if cache != legacy && legacy.is_dir() && !cache.exists() {\n"
                "        let root = fs::canonicalize(home)?;\n"
                "        if fs::canonicalize(&legacy)? != root.join(\".local/downloads\").join(version) {\n"
                "            return Err(\"Refusing redirected legacy installer cache\".into());\n"
                "        }\n"
                "        fs::create_dir_all(cache.parent().ok_or(\"Missing cache parent\")?)?;\n"
                "        fs::rename(&legacy, &cache)?;\n"
                "    }\n"
                "    if !cache.exists()\n"
                "        && let Some(previous_cache) = LEGACY_DOWNLOAD_CACHE_DIR.get()\n"
                "    {\n"
                "        let previous = previous_cache.join(version);\n"
                "        if previous.is_dir() {\n"
                "            let expected = fs::canonicalize(previous_cache)?.join(version);\n"
                "            if fs::canonicalize(&previous)? != expected {\n"
                "                return Err(\"Refusing redirected previous installer cache\".into());\n"
                "            }\n"
                "            fs::create_dir_all(cache.parent().ok_or(\"Missing cache parent\")?)?;\n"
                "            fs::rename(previous, &cache)?;\n"
                "        }\n"
                "    }",
            ),
            (
                "    let next = AtomicUsize::new(0);\n"
                "    let done = AtomicU64::new(0);\n"
                "    let error = Mutex::new(None);\n"
                "    std::thread::scope(|scope| {\n"
                "        for _ in 0..8 {\n"
                "            scope.spawn(|| {\n"
                "                loop {\n"
                "                    if error.lock().unwrap().is_some() {\n"
                "                        break;\n"
                "                    }\n"
                "                    let i = next.fetch_add(1, Ordering::Relaxed);\n"
                "                    let Some(file) = files.get(i) else { break };\n"
                "                    if let Err(e) = file.get() {\n"
                "                        *error.lock().unwrap() = Some(e.to_string());\n"
                "                        break;\n"
                "                    }\n"
                "                    report(\n"
                "                        InstallStage::Download,\n"
                "                        done.fetch_add(file.size, Ordering::Relaxed) + file.size,\n"
                "                        total,\n"
                "                    );\n"
                "                }\n"
                "            });\n"
                "        }\n"
                "    });",
                "    let next = AtomicUsize::new(0);\n"
                "    let done = AtomicU64::new(0);\n"
                "    let in_flight = AtomicU64::new(0);\n"
                "    let error = Mutex::new(None);\n"
                "    std::thread::scope(|scope| {\n"
                "        for _ in 0..8 {\n"
                "            scope.spawn(|| {\n"
                "                loop {\n"
                "                    if error.lock().unwrap().is_some() {\n"
                "                        break;\n"
                "                    }\n"
                "                    let i = next.fetch_add(1, Ordering::Relaxed);\n"
                "                    let Some(file) = files.get(i) else { break };\n"
                "                    let label = file.path.file_name().map(|name| name.to_string_lossy().into_owned()).unwrap_or_else(|| \"Minecraft resource\".into());\n"
                "                    *current_component.lock().unwrap_or_else(|e| e.into_inner()) = label;\n"
                "                    let observed = AtomicU64::new(0);\n"
                "                    let result = file.get_with_progress(|bytes| {\n"
                "                        let previous = observed.swap(bytes, Ordering::Relaxed);\n"
                "                        if bytes >= previous {\n"
                "                            in_flight.fetch_add(bytes - previous, Ordering::Relaxed);\n"
                "                        } else {\n"
                "                            in_flight.fetch_sub(previous - bytes, Ordering::Relaxed);\n"
                "                        }\n"
                "                        let downloaded = done.load(Ordering::Relaxed)\n"
                "                            .saturating_add(in_flight.load(Ordering::Relaxed)).min(total);\n"
                "                        report(InstallStage::Download, downloaded, total);\n"
                "                    });\n"
                "                    let transferred = observed.swap(0, Ordering::Relaxed);\n"
                "                    if transferred > 0 {\n"
                "                        in_flight.fetch_sub(transferred, Ordering::Relaxed);\n"
                "                    }\n"
                "                    if let Err(e) = result {\n"
                "                        *error.lock().unwrap() = Some(e.to_string());\n"
                "                        break;\n"
                "                    }\n"
                "                    let completed = done.fetch_add(file.size, Ordering::Relaxed) + file.size;\n"
                "                    let downloaded = completed.saturating_add(in_flight.load(Ordering::Relaxed)).min(total);\n"
                "                    report(InstallStage::Download, downloaded, total);\n"
                "                }\n"
                "            });\n"
                "        }\n"
                "    });",
            ),
        ],
    ),
    (
        "crates/horizon-client/src/android/launcher.rs",
        [
            (
                '                if message.contains("Dns Failed") || message.contains("lookup address") {\n'
                '                    self.fail("Minecraft download interrupted: DNS could not resolve the download host. Check your connection, VPN or Private DNS, then retry Download. Completed files are kept.");\n'
                "                } else {",
                '                if message.contains("Dns Failed")\n'
                '                    || message.contains("lookup address")\n'
                '                    || message.contains("connection")\n'
                '                    || message.contains("Connection")\n'
                '                    || message.contains("timed out")\n'
                '                    || message.contains("failed to connect")\n'
                '                    || message.contains("network is unreachable")\n'
                '                {\n'
                '                    self.fail(format!("CONNECTION LOST — {message}. Verified files and partial downloads are kept; select Retry installation to resume."));\n'
                "                } else {",
            ),
        ],
    ),
    (
        "crates/horizon-client/src/android/launcher_pages.rs",
        [
            (
                "            let progress = *self\n"
                "                .install\n"
                "                .lock()\n"
                "                .unwrap_or_else(std::sync::PoisonError::into_inner);",
                "            let progress = self\n"
                "                .install\n"
                "                .lock()\n"
                "                .unwrap_or_else(std::sync::PoisonError::into_inner)\n"
                "                .clone();",
            ),
            (
                "                let (done_mib, total_mib) =\n"
                "                    if progress.stage == InstallStage::Download && progress.total > 0 {\n"
                "                        (\n"
                "                            progress.done as f64 / 1_048_576.0,\n"
                "                            progress.total as f64 / 1_048_576.0,\n"
                "                        )\n"
                "                    } else {\n"
                "                        (55.3 * f64::from(overall), 55.3)\n"
                "                    };",
                "                let downloading = progress.stage == InstallStage::Download && progress.total > 0;\n"
                "                let (done_mib, total_mib) = if downloading {\n"
                "                    (\n"
                "                        progress.done as f64 / 1_048_576.0,\n"
                "                        progress.total as f64 / 1_048_576.0,\n"
                "                    )\n"
                "                } else {\n"
                "                    (0.0, 0.0)\n"
                "                };",
            ),
            (
                "                let sub_right = format!(\n"
                "                    \"{done_mib:.1} / {total_mib:.1} MiB ({:.0}%)\",\n"
                "                    overall * 100.0\n"
                "                );\n"
                "                let stage_text = match progress.stage {\n"
                "                    InstallStage::Download => \"Downloading Java Edition 26.3 client.jar\",\n"
                "                    other => other.label(),\n"
                "                };",
                "                let sub_right = if downloading {\n"
                "                    format!(\"{done_mib:.1} / {total_mib:.1} MiB ({:.0}%)\", progress.done as f64 / progress.total as f64 * 100.0)\n"
                "                } else {\n"
                "                    format!(\"Install stage {} / {}\", current + 1, InstallStage::ALL.len())\n"
                "                };\n"
                "                let stage_text = if downloading {\n"
                "                    progress.component.as_str()\n"
                "                } else {\n"
                "                    progress.stage.label()\n"
                "                };\n"
                "                let speed = if downloading { self.install_transfer.speed_label() } else { \"—\".to_owned() };\n"
                "                let eta = if downloading { self.install_transfer.eta_label(done_mib, total_mib) } else { \"—\".to_owned() };",
            ),
            (
                "                    &self.install_transfer.speed_label(),\n"
                "                    &self.install_transfer.eta_label(done_mib, total_mib),",
                "                    &speed,\n"
                "                    &eta,",
            ),
        ],
    ),
    (
        "crates/horizon-client/src/android_main.rs",
        [
            (
                "horizon_client::official_install::configure_download_cache_dir(cache);",
                "horizon_client::official_install::configure_download_cache_dir(home.clone(), cache);",
            ),
        ],
    ),
]


def run(command, cwd, env=None):
    print("+", " ".join(map(str, command)), flush=True)
    subprocess.run(command, cwd=cwd, env=env, check=True)


def apply_branding(engine):
    changed = []
    try:
        for relative, replacements in BRANDING_REPLACEMENTS.items():
            path = engine / relative
            text = path.read_text(encoding="utf-8")
            for old, new in replacements:
                if text.count(old) != 1:
                    raise RuntimeError(
                        f"Expected exactly one branding target in {relative}: {old[:80]!r}"
                    )
                text = text.replace(old, new, 1)
                changed.append((path, old, new))
            path.write_text(text, encoding="utf-8")
        for relative, replacements in SOURCE_TEXT_REPLACEMENTS:
            path = engine / relative
            text = path.read_text(encoding="utf-8")
            for old, new in replacements:
                if text.count(old) != 1:
                    raise RuntimeError(
                        f"Expected exactly one source patch target in {relative}: {old[:80]!r}"
                    )
                text = text.replace(old, new, 1)
                changed.append((path, old, new))
            path.write_text(text, encoding="utf-8")
        for relative, start, end, replacement in SOURCE_REGION_REPLACEMENTS:
            path = engine / relative
            text = path.read_text(encoding="utf-8")
            if text.count(start) != 1 or text.count(end) != 1:
                raise RuntimeError(f"Expected one source patch region in {relative}")
            begin = text.index(start)
            finish = text.index(end, begin)
            old = text[begin:finish]
            updated = text[:begin] + replacement + text[finish:]
            path.write_text(updated, encoding="utf-8")
            changed.append((path, old, replacement))
    except Exception:
        restore_branding(changed)
        raise
    return changed


def restore_branding(changed):
    for path, old, new in reversed(changed):
        text = path.read_text(encoding="utf-8")
        if text.count(new) != 1:
            raise RuntimeError(f"Cannot safely restore the branding patch in {path}")
        path.write_text(text.replace(new, old, 1), encoding="utf-8")


def stage_java_sources(engine, output):
    destination = output / "java"
    shutil.rmtree(destination, ignore_errors=True)
    shutil.copytree(engine / "packaging/android/java", destination)
    return destination


def stage_engine(engine, output):
    library = engine / f"target/aarch64-linux-android/{ANDROID_BUILD_PROFILE}/libhorizon_android.so"
    if not library.is_file():
        raise FileNotFoundError(f"Expected ARM64 engine library at {library}")

    jni = output / "jniLibs/arm64-v8a"
    assets = output / "assets"
    shutil.rmtree(output, ignore_errors=True)
    jni.mkdir(parents=True)
    assets.mkdir(parents=True)
    stage_java_sources(engine, output)
    shutil.copy2(library, jni / "libhorizon_android.so")

    build_script = engine / "tools/android_build.py"
    spec = importlib.util.spec_from_file_location("horizon_android_build", build_script)
    if spec is None or spec.loader is None:
        raise RuntimeError("Cannot load Horizon's Android packaging helpers")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    engine_data = module.engine_data(public=True)
    shutil.copy2(engine_data, assets / "engine-data.zip")
    shutil.copy2(engine / "LICENSE", assets / "HORIZON-LICENSE.txt")
    shutil.copy2(engine / "THIRD_PARTY_NOTICES.md", assets / "HORIZON-THIRD-PARTY-NOTICES.md")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--engine", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    engine = args.engine.resolve()
    output = args.output.resolve()

    if not (engine / ".git").exists() or not (engine / "tools/android_prepare.py").is_file():
        parser.error("--engine must point to the initialized Horizon Android source submodule")
    status = subprocess.check_output(
        ["git", "-C", str(engine), "status", "--porcelain", "--untracked-files=no"],
        text=True,
    )
    if status.strip():
        raise RuntimeError("Horizon source has local tracked changes; refusing to patch it")
    lockfile = engine / "Cargo.lock"
    original_lockfile = lockfile.read_bytes()

    environment = dict(os.environ)
    changed = []
    try:
        changed = apply_branding(engine)
        run([sys.executable, engine / "tools/android_prepare.py"], engine, environment)
        run(
            [
                sys.executable,
                engine / "tools/android_build.py",
                "--abi",
                "arm64-v8a",
                "--public",
                "--rust-only",
                "--profile",
                ANDROID_BUILD_PROFILE,
            ],
            engine,
            environment,
        )
        stage_engine(engine, output)
    finally:
        try:
            if changed:
                restore_branding(changed)
        finally:
            if lockfile.read_bytes() != original_lockfile:
                lockfile.write_bytes(original_lockfile)


if __name__ == "__main__":
    main()
