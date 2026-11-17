#!/usr/bin/env python3
"""Fail-closed Android TV manifest patch for a *pinned* Chromium source checkout."""
import argparse
from pathlib import Path
import re

LAUNCHER = re.compile(r'(<category\s+android:name="android\.intent\.category\.LAUNCHER"\s*/>)')
APP = re.compile(r"<application\b")

def rewrite(text):
    if "android.intent.category.LEANBACK_LAUNCHER" in text:
        raise ValueError("TV intent already present")
    if not LAUNCHER.search(text) or not APP.search(text):
        raise ValueError("Upstream Chromium manifest structure changed")
    text = LAUNCHER.sub(r'\1\n            <category android:name="android.intent.category.LEANBACK_LAUNCHER" />', text, count=1)
    if 'android.hardware.touchscreen' not in text:
        text = APP.sub('<uses-feature android:name="android.hardware.touchscreen" android:required="false" />\n<application', text, count=1)
    return text

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("src", type=Path)
    args = parser.parse_args()
    path = args.src / "chrome/android/java/AndroidManifest.xml"
    path.write_text(rewrite(path.read_text(encoding="utf-8")), encoding="utf-8")
    print("TV manifest template patched:", path)

if __name__ == "__main__":
    main()
