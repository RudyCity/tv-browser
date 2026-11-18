import hashlib
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
from zipfile import ZipFile

HERE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(HERE))
import build_native
import configure_tv
import verify_apk

def elf_arm64_fixture():
    header = bytearray(64)
    header[:4] = b"\x7fELF"
    header[4:7] = bytes([2, 1, 1])
    header[18:20] = (183).to_bytes(2, "little")
    return bytes(header)


class BuildPolicyTests(unittest.TestCase):
    def test_pinned_upstream(self):
        v = build_native.locked()
        self.assertEqual(v["chromium_version"], "157.0.8103.78")
        self.assertEqual(len(v["upstream_commit"]), 40)
        self.assertEqual(v["minimum_android_api"], 29)

    def test_mutable_extension_url_replaced(self):
        old = build_native.MUTABLE_EXTENSION_URL
        x = build_native.patch_gclient("'" + old + "'", "file:///verified/titanium.crx")
        self.assertNotIn("releases/latest/download", x)
        self.assertIn("file:///verified/titanium.crx", x)
        for raw, url in [("no match", "file:///ok"),
                         (old, "https://untrusted.example/x.crx")]:
            with self.assertRaises(ValueError):
                build_native.patch_gclient(raw, url)

    def test_build_hook_fail_closed(self):
        output = build_native.patch_build(
            "source $SCRIPT_DIR/patch.sh\ngn gen out/Default\n")
        self.assertLess(output.index("configure_tv.py"), output.index("gn gen"))
        with self.assertRaises(ValueError):
            build_native.patch_build("echo no-hook")

    def test_gn_package_override(self):
        old = 'chrome_public_manifest_package = "io.github.jqssun.helium"\n'
        new = build_native.patch_args(old + "is_desktop_android = true\n",
                                      "io.rudycity.tvbrowser.chromium")
        self.assertIn('io.rudycity.tvbrowser.chromium"', new)
        self.assertNotIn('io.github.jqssun.helium', new)
        with self.assertRaises(ValueError):
            build_native.patch_args(old, "io.rudycity.tvbrowser.chromium")

    def test_leanback_manifest(self):
        xml = ('<manifest xmlns:android="http://schemas.android.com/apk/res/android">'
               '<application><activity-alias><intent-filter>'
               '<category android:name="android.intent.category.LAUNCHER" />'
               '</intent-filter></activity-alias></application></manifest>')
        result = configure_tv.rewrite(xml)
        self.assertIn("android.intent.category.LEANBACK_LAUNCHER", result)
        self.assertIn('android.hardware.touchscreen" android:required="false"', result)
        with self.assertRaises(ValueError):
            configure_tv.rewrite(result)
        with self.assertRaises(ValueError):
            configure_tv.rewrite("<manifest><application /></manifest>")

    def test_native_apk_requires_binary_and_known_package(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / "native.apk"
            with ZipFile(apk, "w") as z:
                z.writestr("AndroidManifest.xml", b"xml")
                z.writestr("classes.dex", b"dex")
                z.writestr("lib/arm64-v8a/libmonochrome.so", elf_arm64_fixture())
            with patch.object(verify_apk.subprocess, "run", return_value=SimpleNamespace(
                    stdout="package: name='io.rudycity.tvbrowser.chromium'\nsdkVersion:'29'\nleanback\n")):
                sha = verify_apk.verify(apk, "io.rudycity.tvbrowser.chromium", "aapt")
            self.assertEqual(sha, hashlib.sha256(apk.read_bytes()).hexdigest())
            with patch.object(verify_apk.subprocess, "run", return_value=SimpleNamespace(
                    stdout="package: name='wrong.package'\nsdkVersion:'29'\nleanback\n")):
                with self.assertRaises(ValueError):
                    verify_apk.verify(apk, "io.rudycity.tvbrowser.chromium", "aapt")

    def test_reject_fake_or_wrong_architecture_native_binaries(self):
        self.assertTrue(verify_apk.valid_arm64_elf(elf_arm64_fixture()))
        for invalid in [b"native", b"\\x7fELF", elf_arm64_fixture()[:63]]:
            self.assertFalse(verify_apk.valid_arm64_elf(invalid))
        x86_64 = bytearray(elf_arm64_fixture())
        x86_64[18:20] = (62).to_bytes(2, "little")
        self.assertFalse(verify_apk.valid_arm64_elf(x86_64))
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / "fake-native.apk"
            with ZipFile(apk, "w") as z:
                z.writestr("AndroidManifest.xml", b"xml")
                z.writestr("classes.dex", b"dex")
                z.writestr("lib/arm64-v8a/libfake.so", b"not-an-elf")
            with self.assertRaisesRegex(ValueError, "ELF64 AArch64"):
                verify_apk.verify(apk, "io.rudycity.tvbrowser.chromium", "aapt")

    def test_reject_fake_apk_without_native_engine(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / "webview.apk"
            with ZipFile(apk, "w") as z:
                z.writestr("AndroidManifest.xml", b"xml")
                z.writestr("classes.dex", b"dex")
            with self.assertRaises(ValueError):
                verify_apk.verify(apk, "io.rudycity.tvbrowser.chromium", "aapt")

if __name__ == "__main__":
    unittest.main()
