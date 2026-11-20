use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use sha1::{Digest, Sha1};
use std::fs::{self, File};
use std::io::{Read, Write};
use std::path::{Path, PathBuf};

const MANIFEST_URL: &str = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
const DOWNLOAD_HOSTS: &[&str] = &["piston-meta.mojang.com", "piston-data.mojang.com"];

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
    settings: Option<Settings>,
}

#[derive(Debug, Serialize, Deserialize, Default)]
struct Settings {
    ram_mb: Option<u32>,
    java_path: Option<String>,
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
    let versions = manifest["versions"]
        .as_array()
        .ok_or_else(|| "Mojang version manifest has no version list".to_string())?;
    let releases: Vec<Value> = versions
        .iter()
        .map(|v| {
            json!({
                "id": v["id"],
                "type": v["type"],
                "releaseTime": v["releaseTime"],
            })
        })
        .collect();
    Ok(json!({
        "latest": manifest["latest"]["release"],
        "versions": releases
    }))
}

fn valid_version_id(id: &str) -> bool {
    !id.is_empty()
        && id.len() <= 100
        && id.bytes().all(|b| b.is_ascii_alphanumeric() || b"._-".contains(&b))
        && id != "."
        && id != ".."
}

fn download_file(url: &str, path: &Path, expected_sha1: &str) -> Result<(), String> {
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
        let mut reader = response.into_reader();
        let mut output = File::create(&temporary)
            .map_err(|e| format!("Cannot create temporary file: {e}"))?;
        let mut hasher = Sha1::new();
        let mut buffer = [0_u8; 64 * 1024];
        loop {
            let count = reader
                .read(&mut buffer)
                .map_err(|e| format!("Download read failed: {e}"))?;
            if count == 0 {
                break;
            }
            hasher.update(&buffer[..count]);
            output
                .write_all(&buffer[..count])
                .map_err(|e| format!("Cannot write downloaded file: {e}"))?;
        }
        output
            .flush()
            .map_err(|e| format!("Cannot flush downloaded file: {e}"))?;
        let actual = format!("{:x}", hasher.finalize());
        if !actual.eq_ignore_ascii_case(expected_sha1) {
            return Err(format!("SHA-1 mismatch (expected {expected_sha1}, got {actual})"));
        }
        fs::rename(&temporary, path).map_err(|e| format!("Cannot finalize download: {e}"))?;
        Ok(())
    })();
    if result.is_err() {
        let _ = fs::remove_file(temporary);
    }
    result
}

fn install_version(root: &Path, id: &str) -> Result<Value, String> {
    if !valid_version_id(id) {
        return Err("Invalid Minecraft version identifier".to_string());
    }
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
    download_file(metadata_url, &metadata_path, metadata_hash)?;

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
    let client_path = version_dir.join(format!("{id}.jar"));
    download_file(client_url, &client_path, client_hash)?;

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
        serde_json::from_slice(
            &fs::read(&path).map_err(|e| format!("Cannot read settings: {e}"))?,
        )
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
    fs::write(&path, serde_json::to_vec_pretty(&current).map_err(|e| e.to_string())?)
        .map_err(|e| format!("Cannot save settings: {e}"))?;
    serde_json::to_value(current).map_err(|e| e.to_string())
}

fn profile_path(root: &Path) -> PathBuf {
    root.join("profiles.json")
}

fn handle(req: Request) -> Result<Value, String> {
    match req.action.as_str() {
        "versions" => version_manifest(),
        "install" => install_version(
            required_root(&req)?,
            req.id.as_deref().ok_or_else(|| "Missing version ID".to_string())?,
        ),
        "installed_versions" => installed_versions(required_root(&req)?),
        "list_files" => list_files(
            required_root(&req)?,
            req.category.as_deref().ok_or_else(|| "Missing category".to_string())?,
        ),
        "add_profile" => {
            let root = required_root(&req)?;
            let name = req.profile.as_deref().unwrap_or_default();
            if !(3..=16).contains(&name.len())
                || !name.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'_')
            {
                return Err("Offline player names must be 3–16 letters, numbers, or underscores".to_string());
            }
            let path = profile_path(root);
            let mut profiles: Vec<String> = if path.exists() {
                serde_json::from_slice(
                    &fs::read(&path).map_err(|e| format!("Cannot read profiles: {e}"))?,
                )
                .map_err(|e| format!("Invalid saved profiles: {e}"))?
            } else {
                Vec::new()
            };
            if !profiles.iter().any(|existing| existing.eq_ignore_ascii_case(name)) {
                profiles.push(name.to_string());
            }
            fs::write(&path, serde_json::to_vec_pretty(&profiles).map_err(|e| e.to_string())?)
                .map_err(|e| format!("Cannot save profiles: {e}"))?;
            Ok(json!(profiles))
        }
        "profiles" => {
            let path = profile_path(required_root(&req)?);
            let profiles: Vec<String> = if path.exists() {
                serde_json::from_slice(
                    &fs::read(path).map_err(|e| format!("Cannot read profiles: {e}"))?,
                )
                .map_err(|e| format!("Invalid saved profiles: {e}"))?
            } else {
                Vec::new()
            };
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
            let category = req.category.as_deref().ok_or_else(|| "Missing category".to_string())?;
            let name = req.name.as_deref().ok_or_else(|| "Missing file name".to_string())?;
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
            if let Some(path) = root.as_deref() {
                if let Err(error) = ensure_root(path) {
                    return failure(error).to_string();
                }
            }
            match handle(req) {
                Ok(data) => success(data),
                Err(error) => failure(error),
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
        Err(error) => return env.new_string(failure(format!("JNI input error: {error}")).to_string())
            .map_or(std::ptr::null_mut(), |s| s.into_raw()),
    };
    env.new_string(dispatch(&input))
        .map_or(std::ptr::null_mut(), |s| s.into_raw())
}
