#!/usr/bin/env python3
"""Check a Chromium-native APK structurally; not a runtime extensions test."""
import argparse
import hashlib
import re
import subprocess
from pathlib import Path
from zipfile import ZipFile


def valid_arm64_elf(header):
    """A native Chromium binary must be an ELF64 AArch64, not a renamed WebView stub."""
    return (len(header) >= 64 and header[:4] == b"\x7fELF"
            and header[4] == 2 and header[5] == 1 and header[6] == 1
            and int.from_bytes(header[18:20], "little") == 183)


def verify(apk, package, aapt, apksigner=None):
    if not apk.is_file():
        raise ValueError("Native APK file missing")
    with ZipFile(apk) as archive:
        if archive.testzip():
            raise ValueError("Corrupt APK ZIP")
        members = archive.namelist()
        if len(members) != len(set(members)):
            raise ValueError("Ambiguous duplicate APK archive entries")
        if "AndroidManifest.xml" not in members or "classes.dex" not in members:
            raise ValueError("Missing Android manifest or DEX")
        arm64_libs = [name for name in members
                      if name.startswith("lib/arm64-v8a/") and name.endswith(".so")]
        if not arm64_libs:
            raise ValueError("Native Chromium needs ARM64 shared libraries")
        if not any(valid_arm64_elf(archive.open(name).read(64)) for name in arm64_libs):
            raise ValueError("Native shared libraries are not ELF64 AArch64 binaries")
    badging = subprocess.run([aapt, "dump", "badging", str(apk)],
                            capture_output=True, text=True, check=True).stdout
    if not re.search(r"package: name='" + re.escape(package) + r"'", badging):
        raise ValueError("Unexpected Chromium Android package")
    sdk = re.search(r"sdkVersion:'([0-9]+)'", badging)
    if not sdk or int(sdk.group(1)) < 29:
        raise ValueError("Native Chromium must declare Android 10+ minimum SDK")
    if apksigner:
        subprocess.run([str(apksigner), "verify", "--verbose", str(apk)],
                       capture_output=True, text=True, check=True)
    if "leanback" not in badging.lower():
        tree = subprocess.run([aapt, "dump", "xmltree", str(apk), "AndroidManifest.xml"],
                              capture_output=True, text=True, check=True).stdout
        if "android.intent.category.LEANBACK_LAUNCHER" not in tree:
            raise ValueError("No Android TV launcher")
    digest = hashlib.sha256()
    with apk.open("rb") as fd:
        for block in iter(lambda: fd.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    parser.add_argument("--package", required=True)
    parser.add_argument("--aapt", required=True)
    parser.add_argument("--apksigner", required=True)
    args = parser.parse_args()
    print("Verified native Chromium APK; SHA256=" +
          verify(args.apk, args.package, args.aapt, args.apksigner))

if __name__ == "__main__":
    main()
