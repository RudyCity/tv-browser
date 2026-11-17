#!/usr/bin/env python3
"""Check a Chromium-native APK structurally; not a runtime extensions test."""
import argparse
import hashlib
import re
import subprocess
from pathlib import Path
from zipfile import ZipFile

def verify(apk, package, aapt):
    if not apk.is_file():
        raise ValueError("Native APK file missing")
    with ZipFile(apk) as archive:
        if archive.testzip():
            raise ValueError("Corrupt APK ZIP")
        members = archive.namelist()
        if "AndroidManifest.xml" not in members or "classes.dex" not in members:
            raise ValueError("Missing Android manifest or DEX")
        if not any(x.startswith("lib/") and x.endswith(".so") for x in members):
            raise ValueError("Not a bundled native Chromium APK")
    badging = subprocess.run([aapt, "dump", "badging", str(apk)],
                            capture_output=True, text=True, check=True).stdout
    if not re.search(r"package: name='" + re.escape(package) + r"'", badging):
        raise ValueError("Unexpected Chromium Android package")
    if "leanback" not in badging.lower():
        tree = subprocess.run([aapt, "dump", "xmltree", str(apk), "AndroidManifest.xml"],
                              capture_output=True, text=True, check=True).stdout
        if "android.intent.category.LEANBACK_LAUNCHER" not in tree:
            raise ValueError("No Android TV launcher")
    return hashlib.sha256(apk.read_bytes()).hexdigest()

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    parser.add_argument("--package", required=True)
    parser.add_argument("--aapt", required=True)
    args = parser.parse_args()
    print("Verified native Chromium APK; SHA256=" +
          verify(args.apk, args.package, args.aapt))

if __name__ == "__main__":
    main()
