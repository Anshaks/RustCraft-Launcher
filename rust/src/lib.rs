use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use sha1::{Digest, Sha1};
use std::fs::{self, File};
use std::io::{Read, Write};
use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

const MANIFEST_URL: &str = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
const DOWNLOAD_HOSTS: &[&str] = &[
    "piston-meta.mojang.com",
    "piston-data.mojang.com",
    "libraries.minecraft.net",
    "resources.download.minecraft.net",
    "cdn.modrinth.com",
];
const VANILLA_VERSION: &str = "26.3";

#[derive(Debug, Deserialize)]
struct Request {
    action: String,
    #[serde(default)]
    root: Option<PathBuf>,
    #[serde(default)]
    id: Option<String>,
    #[serde(default)]
    category: Option<String>,
    #[serde(default)]
    name: Option<String>,
    #[serde(default)]
    profile: Option<String>,
    #[serde(default)]
    provider: Option<String>,
    #[serde(default)]
    settings: Option<Settings>,
}

#[derive(Debug, Serialize, Deserialize, Default)]
struct Settings {
    ram_mb: Option<u32>,
    java_path: Option<String>,
    reduce_motion: Option<bool>,
    selected_profile: Option<String>,
    touch_controls: Option<bool>,
    touch_size: Option<u8>,
    touch_transparency: Option<u8>,
}

#[derive(Debug, Serialize, Deserialize, Clone, PartialEq, Eq)]
struct Profile {
    name: String,
    provider: String,
}

#[derive(Debug, Serialize, Deserialize, Default)]
struct InstallProgress {
    version: String,
    status: String,
    message: String,
    percent: u8,
    downloaded_bytes: u64,
    total_bytes: u64,
    bytes_per_second: u64,
}

fn success(data: Value) -> Value {
    json!({ "ok": true, "data": data })
}

fn failure(message: impl Into<String>) -> Value {
    json!({ "ok": false, "error": message.into() })
}

fn required_root(req: &Request) -> Result<&Path, String> {
    req.root
        .as_deref()
        .ok_or_else(|| "Missing application storage directory".to_string())
}

fn ensure_root(root: &Path) -> Result<(), String> {
    fs::create_dir_all(root).map_err(|e| format!("Cannot create launcher directory: {e}"))
}

fn progress_path(root: &Path) -> PathBuf {
    root.join("install-progress.json")
}

fn write_progress(root: &Path, progress: &InstallProgress) -> Result<(), String> {
    let path = progress_path(root);
    let temporary = root.join("install-progress.tmp");
    let bytes = serde_json::to_vec(progress).map_err(|e| format!("Cannot encode progress: {e}"))?;
    fs::write(&temporary, bytes).map_err(|e| format!("Cannot save install progress: {e}"))?;
    fs::rename(&temporary, path).map_err(|e| format!("Cannot update install progress: {e}"))
}

fn read_progress(root: &Path) -> Result<Value, String> {
    let path = progress_path(root);
    if !path.exists() {
        return Ok(json!(InstallProgress::default()));
    }
    let bytes = fs::read(path).map_err(|e| format!("Cannot read install progress: {e}"))?;
    serde_json::from_slice(&bytes).map_err(|e| format!("Invalid install progress: {e}"))
}

fn set_progress(
    root: &Path,
    version: &str,
    status: &str,
    message: &str,
    percent: u8,
    downloaded_bytes: u64,
    total_bytes: u64,
    bytes_per_second: u64,
) -> Result<(), String> {
    write_progress(
        root,
        &InstallProgress {
            version: version.to_string(),
            status: status.to_string(),
            message: message.to_string(),
            percent,
            downloaded_bytes,
            total_bytes,
            bytes_per_second,
        },
    )
}

fn get_json(url: &str) -> Result<Value, String> {
    let response = ureq::get(url)
        .set("User-Agent", "RustCraftLauncher/0.1")
        .call()
        .map_err(|e| format!("Request failed: {e}"))?;
    let mut body = String::new();
    response
        .into_reader()
        .read_to_string(&mut body)
        .map_err(|e| format!("Cannot read JSON response: {e}"))?;
    serde_json::from_str(&body).map_err(|e| format!("Invalid JSON response: {e}"))
}

fn version_manifest() -> Result<Value, String> {
    let manifest = get_json(MANIFEST_URL)?;
    let releases: Vec<Value> = manifest["versions"]
        .as_array()
        .unwrap_or(&Vec::new())
        .iter()
        .filter(|v| {
            v["id"].as_str() == Some(VANILLA_VERSION) && v["type"].as_str() == Some("release")
        })
        .map(|v| json!({ "id": v["id"], "type": "release", "releaseTime": v["releaseTime"] }))
        .collect();
    if releases.is_empty() {
        return Err(
            "Minecraft Java Edition 26.3 is not currently in Mojang's manifest".to_string(),
        );
    }
    Ok(json!({
        "latest": VANILLA_VERSION,
        "versions": releases
    }))
}

fn valid_version_id(id: &str) -> bool {
    !id.is_empty()
        && id.len() <= 100
        && id
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || b"._-".contains(&b))
        && id != "."
        && id != ".."
}

fn download_file(
    url: &str,
    path: &Path,
    expected_sha1: &str,
    progress: Option<(&Path, &str, &str, u64, u64, u8, u8)>,
) -> Result<u64, String> {
    let host = url
        .split_once("://")
        .and_then(|(_, rest)| rest.split('/').next())
        .ok_or_else(|| "Invalid download URL".to_string())?;
    if !url.starts_with("https://") || !DOWNLOAD_HOSTS.contains(&host) {
        return Err("Refusing download from an untrusted host".to_string());
    }

    let parent = path
        .parent()
        .ok_or_else(|| "Invalid destination path".to_string())?;
    fs::create_dir_all(parent).map_err(|e| format!("Cannot create destination: {e}"))?;
    let temporary = path.with_extension("download");
    let result = (|| {
        let response = ureq::get(url)
            .set("User-Agent", "RustCraftLauncher/0.1")
            .call()
            .map_err(|e| format!("Download failed: {e}"))?;
        let expected_size = response
            .header("Content-Length")
            .and_then(|length| length.parse::<u64>().ok())
            .unwrap_or(0);
        let (base_bytes, requested_total) = progress
            .as_ref()
            .map(|(_, _, _, base, total, _, _)| (*base, *total))
            .unwrap_or((0, expected_size));
        let total_bytes = if requested_total == 0 {
            base_bytes.saturating_add(expected_size)
        } else {
            requested_total
        };
        let mut reader = response.into_reader();
        let mut output =
            File::create(&temporary).map_err(|e| format!("Cannot create temporary file: {e}"))?;
        let mut hasher = Sha1::new();
        let mut buffer = [0_u8; 64 * 1024];
        let start = Instant::now();
        let mut last_update = start;
        let mut downloaded = 0_u64;
        loop {
            let count = reader
                .read(&mut buffer)
                .map_err(|e| format!("Download read failed: {e}"))?;
            if count == 0 {
                break;
            }
            downloaded = downloaded.saturating_add(count as u64);
            hasher.update(&buffer[..count]);
            output
                .write_all(&buffer[..count])
                .map_err(|e| format!("Cannot write downloaded file: {e}"))?;
            if let Some((root, version, message, base, _, start_percent, end_percent)) =
                progress.as_ref()
            {
                let now = Instant::now();
                if now.duration_since(last_update) >= Duration::from_millis(250)
                    || (expected_size > 0 && downloaded >= expected_size)
                {
                    let fraction = if expected_size == 0 {
                        0.0
                    } else {
                        (downloaded as f64 / expected_size as f64).clamp(0.0, 1.0)
                    };
                    let percent =
                        *start_percent as f64 + (*end_percent - *start_percent) as f64 * fraction;
                    let elapsed = now.duration_since(start).as_secs_f64().max(0.001);
                    set_progress(
                        root,
                        version,
                        "downloading",
                        message,
                        percent.round() as u8,
                        base.saturating_add(downloaded),
                        total_bytes,
                        (downloaded as f64 / elapsed) as u64,
                    )?;
                    last_update = now;
                }
            }
        }
        output
            .flush()
            .map_err(|e| format!("Cannot flush downloaded file: {e}"))?;
        let actual = format!("{:x}", hasher.finalize());
        if !actual.eq_ignore_ascii_case(expected_sha1) {
            return Err(format!(
                "SHA-1 mismatch (expected {expected_sha1}, got {actual})"
            ));
        }
        if let Some((root, version, message, base, total, _, end_percent)) = progress.as_ref() {
            set_progress(
                root,
                version,
                "verifying",
                message,
                *end_percent,
                base.saturating_add(downloaded),
                if *total == 0 { downloaded } else { *total },
                0,
            )?;
        }
        fs::rename(&temporary, path).map_err(|e| format!("Cannot finalize download: {e}"))?;
        Ok(downloaded)
    })();
    if result.is_err() {
        let _ = fs::remove_file(temporary);
    }
    result
}

fn install_version(root: &Path, id: &str) -> Result<Value, String> {
    if id != VANILLA_VERSION {
        return Err("Only Minecraft Java Edition 26.3 is supported".to_string());
    }
    set_progress(
        root,
        id,
        "preparing",
        "Reading Mojang version manifest",
        0,
        0,
        0,
        0,
    )?;
    let manifest = get_json(MANIFEST_URL)?;
    let version = manifest["versions"]
        .as_array()
        .and_then(|versions| versions.iter().find(|v| v["id"].as_str() == Some(id)))
        .ok_or_else(|| format!("Version {id} is not in Mojang's manifest"))?;
    let metadata_url = version["url"]
        .as_str()
        .ok_or_else(|| "Version metadata URL is missing".to_string())?;
    let metadata_hash = version["sha1"]
        .as_str()
        .ok_or_else(|| "Version metadata checksum is missing".to_string())?;

    let version_dir = root.join("versions").join(id);
    let metadata_path = version_dir.join(format!("{id}.json"));
    set_progress(
        root,
        id,
        "downloading",
        "Downloading version metadata",
        0,
        0,
        0,
        0,
    )?;
    let metadata_bytes = download_file(
        metadata_url,
        &metadata_path,
        metadata_hash,
        Some((root, id, "Downloading version metadata", 0, 0, 0, 5)),
    )?;

    let metadata: Value = serde_json::from_slice(
        &fs::read(&metadata_path).map_err(|e| format!("Cannot read version metadata: {e}"))?,
    )
    .map_err(|e| format!("Invalid version metadata: {e}"))?;
    let client = metadata["downloads"]["client"]
        .as_object()
        .ok_or_else(|| "This version has no downloadable client JAR".to_string())?;
    let client_url = client["url"]
        .as_str()
        .ok_or_else(|| "Client download URL is missing".to_string())?;
    let client_hash = client["sha1"]
        .as_str()
        .ok_or_else(|| "Client checksum is missing".to_string())?;
    let client_size = client["size"].as_u64().unwrap_or(0);
    let total_bytes = metadata_bytes.saturating_add(client_size);
    let client_path = version_dir.join(format!("{id}.jar"));
    let _client_bytes = download_file(
        client_url,
        &client_path,
        client_hash,
        Some((
            root,
            id,
            "Downloading vanilla client",
            metadata_bytes,
            total_bytes,
            5,
            99,
        )),
    )?;
    set_progress(
        root,
        id,
        "complete",
        "Client files verified. Game runtime is not installed.",
        100,
        total_bytes,
        total_bytes,
        0,
    )?;

    Ok(json!({
        "id": id,
        "metadata": metadata_path.to_string_lossy(),
        "client": client_path.to_string_lossy(),
        "complete": false
    }))
}

fn installed_versions(root: &Path) -> Result<Value, String> {
    let versions_dir = root.join("versions");
    if !versions_dir.exists() {
        return Ok(json!([]));
    }
    let entries =
        fs::read_dir(versions_dir).map_err(|e| format!("Cannot list installed versions: {e}"))?;
    let mut installed = Vec::new();
    for entry in entries {
        let entry = entry.map_err(|e| format!("Cannot read version entry: {e}"))?;
        if !entry
            .file_type()
            .map_err(|e| format!("Cannot inspect version entry: {e}"))?
            .is_dir()
        {
            continue;
        }
        let id = entry.file_name().to_string_lossy().into_owned();
        if valid_version_id(&id)
            && entry.path().join(format!("{id}.json")).is_file()
            && entry.path().join(format!("{id}.jar")).is_file()
        {
            installed.push(id);
        }
    }
    installed.sort();
    Ok(json!(installed))
}

fn category_dir(root: &Path, category: &str) -> Result<PathBuf, String> {
    let dir = match category {
        "mods" => root.join("mods"),
        "resourcepacks" => root.join("resourcepacks"),
        "shaderpacks" => root.join("shaderpacks"),
        _ => return Err("Unsupported managed-file category".to_string()),
    };
    fs::create_dir_all(&dir).map_err(|e| format!("Cannot create managed folder: {e}"))?;
    Ok(dir)
}

fn list_files(root: &Path, category: &str) -> Result<Value, String> {
    let dir = category_dir(root, category)?;
    let mut files = Vec::new();
    for entry in fs::read_dir(dir).map_err(|e| format!("Cannot list managed files: {e}"))? {
        let entry = entry.map_err(|e| format!("Cannot read directory entry: {e}"))?;
        let metadata = entry
            .metadata()
            .map_err(|e| format!("Cannot read file metadata: {e}"))?;
        if metadata.is_file() {
            files.push(json!({
                "name": entry.file_name().to_string_lossy(),
                "bytes": metadata.len(),
            }));
        }
    }
    files.sort_by(|a, b| {
        a["name"]
            .as_str()
            .unwrap_or_default()
            .cmp(b["name"].as_str().unwrap_or_default())
    });
    Ok(Value::Array(files))
}

fn update_settings(root: &Path, settings: &Settings) -> Result<Value, String> {
    let path = root.join("settings.json");
    let mut current: Settings = if path.exists() {
        serde_json::from_slice(&fs::read(&path).map_err(|e| format!("Cannot read settings: {e}"))?)
            .map_err(|e| format!("Invalid saved settings: {e}"))?
    } else {
        Settings::default()
    };
    if let Some(ram) = settings.ram_mb {
        if !(512..=65536).contains(&ram) {
            return Err("RAM allocation must be between 512 and 65536 MB".to_string());
        }
        current.ram_mb = Some(ram);
    }
    if let Some(java) = &settings.java_path {
        if java.len() > 4096 || java.contains('\0') {
            return Err("Invalid Java runtime path".to_string());
        }
        current.java_path = Some(java.clone());
    }
    if let Some(reduce_motion) = settings.reduce_motion {
        current.reduce_motion = Some(reduce_motion);
    }
    if let Some(selected_profile) = &settings.selected_profile {
        current.selected_profile = Some(selected_profile.clone());
    }
    if let Some(enabled) = settings.touch_controls {
        current.touch_controls = Some(enabled);
    }
    if let Some(size) = settings.touch_size {
        if !(50..=100).contains(&size) {
            return Err("Touch control size must be between 50 and 100 percent".to_string());
        }
        current.touch_size = Some(size);
    }
    if let Some(transparency) = settings.touch_transparency {
        if !(20..=100).contains(&transparency) {
            return Err(
                "Touch control transparency must be between 20 and 100 percent".to_string(),
            );
        }
        current.touch_transparency = Some(transparency);
    }
    fs::write(
        &path,
        serde_json::to_vec_pretty(&current).map_err(|e| e.to_string())?,
    )
    .map_err(|e| format!("Cannot save settings: {e}"))?;
    serde_json::to_value(current).map_err(|e| e.to_string())
}

fn profile_path(root: &Path) -> PathBuf {
    root.join("profiles.json")
}

fn load_profiles(root: &Path) -> Result<Vec<Profile>, String> {
    let path = profile_path(root);
    if !path.exists() {
        return Ok(Vec::new());
    }
    let bytes = fs::read(path).map_err(|e| format!("Cannot read profiles: {e}"))?;
    let value: Value =
        serde_json::from_slice(&bytes).map_err(|e| format!("Invalid saved profiles: {e}"))?;
    let values = value
        .as_array()
        .ok_or_else(|| "Saved profiles must be a list".to_string())?;
    values
        .iter()
        .map(|profile| {
            if let Some(name) = profile.as_str() {
                Ok(Profile {
                    name: name.to_string(),
                    provider: "offline".to_string(),
                })
            } else {
                serde_json::from_value(profile.clone())
                    .map_err(|e| format!("Invalid saved profile: {e}"))
            }
        })
        .collect()
}

fn save_profiles(root: &Path, profiles: &[Profile]) -> Result<(), String> {
    let bytes = serde_json::to_vec_pretty(profiles).map_err(|e| e.to_string())?;
    fs::write(profile_path(root), bytes).map_err(|e| format!("Cannot save profiles: {e}"))
}

fn handle(req: Request) -> Result<Value, String> {
    match req.action.as_str() {
        "versions" => version_manifest(),
        "install" => install_version(
            required_root(&req)?,
            req.id
                .as_deref()
                .ok_or_else(|| "Missing version ID".to_string())?,
        ),
        "install_status" => read_progress(required_root(&req)?),
        "installed_versions" => installed_versions(required_root(&req)?),
        "list_files" => list_files(
            required_root(&req)?,
            req.category
                .as_deref()
                .ok_or_else(|| "Missing category".to_string())?,
        ),
        "add_profile" => {
            let root = required_root(&req)?;
            let name = req.profile.as_deref().unwrap_or_default();
            if !(3..=16).contains(&name.len())
                || !name.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'_')
            {
                return Err(
                    "Offline player names must be 3–16 letters, numbers, or underscores"
                        .to_string(),
                );
            }
            let provider = req.provider.as_deref().unwrap_or("offline");
            if provider != "offline" {
                return Err(format!(
                    "{} sign-in is not configured in this build; use an offline profile",
                    provider
                ));
            }
            let mut profiles = load_profiles(root)?;
            if profiles.len() >= 3 {
                return Err("You can create up to three profiles".to_string());
            }
            if profiles
                .iter()
                .any(|existing| existing.name.eq_ignore_ascii_case(name))
            {
                return Err("A profile with this name already exists".to_string());
            }
            profiles.push(Profile {
                name: name.to_string(),
                provider: provider.to_string(),
            });
            save_profiles(root, &profiles)?;
            Ok(json!(profiles))
        }
        "profiles" => {
            let profiles = load_profiles(required_root(&req)?)?;
            Ok(json!(profiles))
        }
        "remove_profile" => {
            let root = required_root(&req)?;
            let name = req
                .profile
                .as_deref()
                .ok_or_else(|| "Missing profile name".to_string())?;
            let mut profiles = load_profiles(root)?;
            let original_len = profiles.len();
            profiles.retain(|profile| profile.name != name);
            if profiles.len() == original_len {
                return Err("Profile was not found".to_string());
            }
            save_profiles(root, &profiles)?;
            Ok(json!(profiles))
        }
        "settings" => {
            let root = req
                .root
                .ok_or_else(|| "Missing application storage directory".to_string())?;
            let settings = req.settings.unwrap_or_default();
            update_settings(&root, &settings)
        }
        "remove_file" => {
            let root = required_root(&req)?;
            let category = req
                .category
                .as_deref()
                .ok_or_else(|| "Missing category".to_string())?;
            let name = req
                .name
                .as_deref()
                .ok_or_else(|| "Missing file name".to_string())?;
            if name.is_empty()
                || name == "."
                || name == ".."
                || name.contains('/')
                || name.contains('\\')
            {
                return Err("Invalid file name".to_string());
            }
            let path = category_dir(root, category)?.join(name);
            fs::remove_file(&path).map_err(|e| format!("Cannot remove file: {e}"))?;
            Ok(json!({ "removed": name }))
        }
        _ => Err("Unknown launcher action".to_string()),
    }
}

fn dispatch(input: &str) -> String {
    let parsed = serde_json::from_str::<Request>(input);
    let result = match parsed {
        Ok(req) => {
            let root = req.root.clone();
            let installing = req.action == "install";
            let version = req.id.clone().unwrap_or_default();
            if let Some(path) = root.as_deref() {
                if let Err(error) = ensure_root(path) {
                    return failure(error).to_string();
                }
            }
            match handle(req) {
                Ok(data) => success(data),
                Err(error) => {
                    if installing {
                        if let Some(path) = root.as_deref() {
                            let _ = set_progress(path, &version, "failed", &error, 0, 0, 0, 0);
                        }
                    }
                    failure(error)
                }
            }
        }
        Err(error) => failure(format!("Invalid request: {error}")),
    };
    result.to_string()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn version_ids_cannot_escape_the_versions_directory() {
        assert!(valid_version_id("1.21.4"));
        assert!(valid_version_id("23w14a"));
        assert!(!valid_version_id("../outside"));
        assert!(!valid_version_id(""));
        assert!(!valid_version_id("a/b"));
    }

    #[test]
    fn rejects_unknown_actions() {
        let response: Value = serde_json::from_str(&dispatch(r#"{"action":"not_real"}"#)).unwrap();
        assert_eq!(response["ok"], false);
    }

    #[test]
    fn profiles_migrate_and_enforce_three_profile_limit() {
        let root = std::env::temp_dir().join(format!("rustcraft-profiles-{}", std::process::id()));
        let _ = fs::remove_dir_all(&root);
        ensure_root(&root).unwrap();
        fs::write(profile_path(&root), br#"["Alex"]"#).unwrap();
        let mut profiles = load_profiles(&root).unwrap();
        assert_eq!(profiles[0].provider, "offline");
        profiles.push(Profile {
            name: "Sam".into(),
            provider: "offline".into(),
        });
        profiles.push(Profile {
            name: "Casey".into(),
            provider: "offline".into(),
        });
        save_profiles(&root, &profiles).unwrap();
        assert_eq!(load_profiles(&root).unwrap().len(), 3);
        let response: Value = serde_json::from_str(&dispatch(
            &json!({"action":"add_profile","root":root,"profile":"Taylor"}).to_string(),
        ))
        .unwrap();
        assert_eq!(response["ok"], false);
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn non_offline_accounts_are_never_saved_as_authenticated() {
        let root = std::env::temp_dir().join(format!("rustcraft-auth-{}", std::process::id()));
        let _ = fs::remove_dir_all(&root);
        let response: Value = serde_json::from_str(&dispatch(
            &json!({"action":"add_profile","root":root,"profile":"Alex","provider":"microsoft"})
                .to_string(),
        ))
        .unwrap();
        assert_eq!(response["ok"], false);
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn only_26_3_can_be_installed() {
        let root = std::env::temp_dir().join(format!("rustcraft-version-{}", std::process::id()));
        let _ = fs::remove_dir_all(&root);
        let wrong: Value = serde_json::from_str(&dispatch(
            &json!({"action":"install","root":root,"id":"1.21.11"}).to_string(),
        ))
        .unwrap();
        assert_eq!(wrong["ok"], false);
        assert!(wrong["error"].as_str().unwrap().contains("26.3"));
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn install_progress_is_readable_while_downloads_run() {
        let root = std::env::temp_dir().join(format!("rustcraft-progress-{}", std::process::id()));
        let _ = fs::remove_dir_all(&root);
        ensure_root(&root).unwrap();
        set_progress(
            &root,
            "26.3",
            "downloading",
            "Downloading vanilla client",
            42,
            4_200,
            10_000,
            1_024,
        )
        .unwrap();
        let progress = read_progress(&root).unwrap();
        assert_eq!(progress["version"], "26.3");
        assert_eq!(progress["status"], "downloading");
        assert_eq!(progress["percent"], 42);
        assert_eq!(progress["downloaded_bytes"], 4_200);
        assert_eq!(progress["bytes_per_second"], 1_024);
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn download_hashing_uses_sha1() {
        let digest = format!("{:x}", Sha1::digest(b"abc"));
        assert_eq!(digest, "a9993e364706816aba3e25717850c26c9cd0d89d");
    }
}

#[no_mangle]
pub extern "system" fn Java_com_rustcraft_launcher_NativeBridge_execute<'local>(
    mut env: jni::JNIEnv<'local>,
    _receiver: jni::objects::JObject<'local>,
    request: jni::objects::JString<'local>,
) -> jni::sys::jstring {
    let input: String = match env.get_string(&request) {
        Ok(value) => value.into(),
        Err(error) => {
            return env
                .new_string(failure(format!("JNI input error: {error}")).to_string())
                .map_or(std::ptr::null_mut(), |s| s.into_raw())
        }
    };
    env.new_string(dispatch(&input))
        .map_or(std::ptr::null_mut(), |s| s.into_raw())
}
