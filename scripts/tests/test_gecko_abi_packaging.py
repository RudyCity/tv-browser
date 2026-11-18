"""ABI packaging tests using tiny synthetic APK ZIPs; no Android SDK required."""
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from package_gecko_abis import ABIS, PREFIX, package_apks, select_apks


def make_apk(path: Path, abis, has_manifest=True):
    with zipfile.ZipFile(path, "w") as z:
        if has_manifest:
            z.writestr("AndroidManifest.xml", b"fixture")
        for abi in abis:
            z.writestr(f"lib/{abi}/libxul.so", b"fixture")


class PackageGeckoAbiTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.source = Path(temp.name) / "source"
        self.source.mkdir()
        self.output = Path(temp.name) / "output"

    def valid(self):
        for abi in ABIS:
            make_apk(self.source / f"test-{abi}.apk", (abi,))

    def test_separate_abi_packages(self):
        self.valid()
        result = package_apks(self.source, self.output)
        self.assertEqual(set(result), set(ABIS))
        for abi in ABIS:
            self.assertEqual(result[abi].name, f"{PREFIX}-{abi}-debug.apk")
            self.assertTrue(result[abi].exists())

    def test_rejects_universal_apk(self):
        self.valid()
        (self.source / f"test-{ABIS[0]}.apk").unlink()
        make_apk(self.source / "universal.apk", ABIS)
        with self.assertRaisesRegex(ValueError, "universal/mixed"):
            select_apks(self.source)

    def test_rejects_duplicates(self):
        make_apk(self.source / "a.apk", (ABIS[0],))
        make_apk(self.source / "b.apk", (ABIS[0],))
        make_apk(self.source / "c.apk", (ABIS[1],))
        with self.assertRaisesRegex(ValueError, "duplicate"):
            select_apks(self.source)

    def test_rejects_missing_or_unsupported_abi(self):
        self.valid()
        (self.source / f"test-{ABIS[2]}.apk").unlink()
        with self.assertRaisesRegex(ValueError, "expected 3 APKs"):
            select_apks(self.source)
        make_apk(self.source / "unknown.apk", ("x86",))
        with self.assertRaisesRegex(ValueError, "unsupported native ABI"):
            select_apks(self.source)

    def test_rejects_missing_manifest(self):
        self.valid()
        (self.source / f"test-{ABIS[0]}.apk").unlink()
        make_apk(self.source / "missing-manifest.apk", (ABIS[0],), False)
        with self.assertRaisesRegex(ValueError, "AndroidManifest.xml"):
            select_apks(self.source)


if __name__ == "__main__":
    unittest.main()
