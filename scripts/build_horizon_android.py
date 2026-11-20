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
        ('"Exit Horizon Android?"', '"Exit RustCraft?"'),
        ('"Horizon Android Update"', '"RustCraft Update"'),
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
        ('pub const VERSION: &str = "1.0.1";', 'pub const VERSION: &str = "1.1.0";'),
    ],
}


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


def stage_engine(engine, output):
    library = engine / f"target/aarch64-linux-android/{ANDROID_BUILD_PROFILE}/libhorizon_android.so"
    if not library.is_file():
        raise FileNotFoundError(f"Expected ARM64 engine library at {library}")

    jni = output / "jniLibs/arm64-v8a"
    assets = output / "assets"
    shutil.rmtree(output, ignore_errors=True)
    jni.mkdir(parents=True)
    assets.mkdir(parents=True)
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
