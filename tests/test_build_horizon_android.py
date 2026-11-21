import importlib.util
import shutil
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.dont_write_bytecode = True
SPEC = importlib.util.spec_from_file_location(
    "build_horizon_android", ROOT / "scripts/build_horizon_android.py"
)
BUILD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(BUILD)


class AndroidEnginePatchTests(unittest.TestCase):
    def test_engine_patch_applies_and_restores_exactly(self):
        relative_paths = set(BUILD.BRANDING_REPLACEMENTS)
        relative_paths.update(path for path, _ in BUILD.SOURCE_TEXT_REPLACEMENTS)
        relative_paths.update(path for path, *_ in BUILD.SOURCE_REGION_REPLACEMENTS)

        with tempfile.TemporaryDirectory() as temporary:
            engine = Path(temporary)
            originals = {}
            for relative in relative_paths:
                source = ROOT / "engine/horizon-android" / relative
                destination = engine / relative
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(source, destination)
                originals[relative] = destination.read_bytes()

            changed = BUILD.apply_branding(engine)
            installer = (
                engine / "crates/horizon-client/src/official_install.rs"
            ).read_text(encoding="utf-8")
            launcher = (
                engine / "crates/horizon-client/src/android/launcher.rs"
            ).read_text(encoding="utf-8")
            pages = (
                engine / "crates/horizon-client/src/android/launcher_pages.rs"
            ).read_text(encoding="utf-8")

            self.assertIn("fn get_with_progress", installer)
            self.assertIn("valid_partial_response", installer)
            self.assertIn("LEGACY_DOWNLOAD_CACHE_DIR", installer)
            self.assertIn("pub component: String", installer)
            self.assertIn("downloads_compute_sha1_and_sha256_receipts", installer)
            self.assertIn("CONNECTION LOST", launcher)
            self.assertIn("progress.component.as_str()", pages)
            self.assertNotIn("Icon::HorizonLogo", pages)
            dashboard = pages[
                pages.find("// RustCraft keeps the game dashboard free"):
                pages.find("// Right Bento Card")
            ]
            self.assertNotIn("Action::Discord", dashboard)
            self.assertNotIn("Action::Repository", dashboard)
            self.assertIn("let show_corner_links = false;", pages)

            BUILD.restore_branding(changed)
            for relative, expected in originals.items():
                self.assertEqual((engine / relative).read_bytes(), expected)


if __name__ == "__main__":
    unittest.main()
