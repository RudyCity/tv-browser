#!/usr/bin/env python3
"""Manual-only native Chromium+Extensions build from pinned Titanium source.

Creates a separate APK, not an embeddable AAR. Runs upstream build.sh only on a
new isolated, purpose-built large Linux runner. Never modifies the tv-browser
working tree, performs no release, and uses a new temporary debug signing key.
"""
import argparse
import base64
import hashlib
import json
import os
import re
import secrets
import shutil
import subprocess
import tempfile
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
PROJECT = HERE.parent
MUTABLE_EXTENSION_URL = ("https://github.com/jqssun/"
    "android-titanium-extension/releases/latest/download/titanium.crx")

def locked():
    v = json.loads((HERE / "source-lock.json").read_text(encoding="utf-8"))
    for key, size in (("upstream_commit", 40), ("vanadium_commit", 40),
                      ("extension_sha256", 64)):
        if not re.fullmatch("[0-9a-f]{" + str(size) + "}", v[key]):
            raise ValueError("Invalid lock digest: " + key)
    if v["minimum_android_api"] != 29 or v["native_package"] != "io.rudycity.tvbrowser.chromium":
        raise ValueError("Unexpected native Chromium compatibility or package ID")
    if v["upstream_tag"] != "v" + v["chromium_version"]:
        raise ValueError("Chromium version and source tag mismatch")
    if not v["upstream_repo"] == "https://github.com/jqssun/android-titanium-browser.git":
        raise ValueError("Unapproved upstream source")
    if not v["extension_url"].startswith(
            "https://github.com/jqssun/android-titanium-extension/releases/download/v"):
        raise ValueError("Unpinned extension download URL")
    return v

def patch_gclient(source, crx_uri):
    if source.count(MUTABLE_EXTENSION_URL) != 1 or not crx_uri.startswith("file://"):
        raise ValueError("Mutable extension hook changed or source not local")
    return source.replace(MUTABLE_EXTENSION_URL, crx_uri)

def patch_build(source):
    anchor = "source $SCRIPT_DIR/patch.sh"
    if source.count(anchor) != 1:
        raise ValueError("Upstream patch hook changed")
    return source.replace(anchor, anchor + "\n"
        'python3 "$TV_BROWSER_PROJECT_ROOT/native-chromium/configure_tv.py" "$PWD"')

def patch_args(source, package):
    marker = 'chrome_public_manifest_package = "io.github.jqssun.helium"'
    if source.count(marker) != 1 or "is_desktop_android = true" not in source:
        raise ValueError("Unexpected upstream native Chromium GN configuration")
    return source.replace(marker, 'chrome_public_manifest_package = "' + package + '"')

def run(args, cwd=None, env=None):
    subprocess.run(args, cwd=cwd, env=env, check=True)

def require_resources(workspace):
    if not shutil.which("git") or not shutil.which("keytool"):
        raise RuntimeError("git and JDK keytool are mandatory")
    workspace.parent.mkdir(parents=True, exist_ok=True)
    if workspace.exists():
        raise RuntimeError("Refusing existing native build workspace: " + str(workspace))
    if shutil.disk_usage(workspace.parent).free < 120 * 1024 ** 3:
        raise RuntimeError("Chromium requires at least 120 GiB free disk on builder")
    meminfo = Path("/proc/meminfo")
    if meminfo.is_file():
        match = re.search(r"MemTotal:\s+(\d+) kB", meminfo.read_text())
        if match and int(match.group(1)) < 16 * 1024 ** 2:
            raise RuntimeError("Chromium build requires at least 16 GiB RAM on runner")

def fetch_pinned(url, digest, target):
    target.parent.mkdir(parents=True, exist_ok=True)
    req = urllib.request.Request(url, headers={"User-Agent":"tv-browser-native-builder/1"})
    sha = hashlib.sha256()
    size = 0
    with urllib.request.urlopen(req, timeout=60) as response, target.open("xb") as out:
        while True:
            part = response.read(65536)
            if not part:
                break
            size += len(part)
            if size > 20 * 1024 * 1024:
                raise RuntimeError("Pinned extension exceeds 20 MiB")
            sha.update(part)
            out.write(part)
    if sha.hexdigest() != digest:
        raise RuntimeError("Pinned Chromium bundled CRX SHA256 MISMATCH")

def do_build(workspace, output, lock):
    require_resources(workspace)
    workspace.mkdir(parents=True)
    upstream = workspace / "titanium"
    run(["git", "clone", "--depth", "1", "--branch", lock["upstream_tag"],
         "--recurse-submodules", lock["upstream_repo"], str(upstream)])
    sha = subprocess.check_output(["git", "rev-parse", "HEAD"],
                                  cwd=upstream, text=True).strip()
    submodule_sha = subprocess.check_output(["git", "rev-parse", "HEAD"],
                                  cwd=upstream / "vanadium", text=True).strip()
    if sha != lock["upstream_commit"] or submodule_sha != lock["vanadium_commit"]:
        raise RuntimeError("Source/Vanadium commit differs from lock: fail closed")
    version_file = (upstream / "vanadium/args.gn").read_text()
    if lock["chromium_version"] not in version_file:
        raise RuntimeError("Vanadium Chromium version does not match lock")
    args = upstream / "args.gn"
    args.write_text(patch_args(args.read_text(), lock["native_package"]))
    crx = workspace / "verified" / "titanium.crx"
    fetch_pinned(lock["extension_url"], lock["extension_sha256"], crx)
    gclient = upstream / ".gclient"
    gclient.write_text(patch_gclient(gclient.read_text(), crx.resolve().as_uri()))
    entry = upstream / "build.sh"
    entry.write_text(patch_build(entry.read_text()))
    env = os.environ.copy()
    env["TV_BROWSER_PROJECT_ROOT"] = str(PROJECT)
    with tempfile.TemporaryDirectory(prefix="tv-chromium-debug-sign-") as temp:
        passwd = secrets.token_urlsafe(20)
        key = Path(temp) / "temporary-debug.jks"
        run(["keytool", "-genkeypair", "-alias", "tvdebug", "-keyalg", "RSA",
             "-keysize", "2048", "-validity", "365", "-keystore", str(key),
             "-storepass", passwd, "-keypass", passwd,
             "-dname", "CN=TV Browser Chromium Debug", "-noprompt"])
        props = ("keyAlias=tvdebug\nkeyPassword=" + passwd
                 + "\nstorePassword=" + passwd + "\n")
        env["LOCAL_TEST_JKS"] = base64.b64encode(props.encode()).decode()
        env["STORE_TEST_JKS"] = base64.b64encode(key.read_bytes()).decode()
        # Upstream performs privileged dependency installation (apt), large
        # checkout, GN and Ninja. Only run on a dedicated, disposable builder.
        run(["bash", "./build.sh"], cwd=upstream, env=env)
    native_dir = upstream / "chromium/src/out/release"
    apks = list(native_dir.glob("*-arm64-v8a.apk"))
    if len(apks) != 1:
        raise RuntimeError("Build didn't produce exactly one ARM64 native APK")
    sdk = upstream / "chromium/src/third_party/android_sdk/public/build-tools"
    aapts = sorted(sdk.glob("*/aapt"))
    if not aapts:
        raise RuntimeError("No aapt: unable to validate native package")
    from verify_apk import verify
    digest = verify(apks[0], lock["native_package"], str(aapts[-1]))
    output.mkdir(parents=True, exist_ok=True)
    apk = output / "tv-browser-chromium-arm64-debug.apk"
    if apk.exists():
        raise RuntimeError("Refusing to overwrite pre-existing native APK")
    shutil.copyfile(apks[0], apk)
    (output / "SHA256SUMS").write_text(digest + "  " + apk.name + "\n")
    print("Native Chromium debug APK:", apk, "SHA256:", digest)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--workdir", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    lock = locked()
    if args.check:
        print("PASS pinned Chromium+Extensions source:", lock["chromium_version"])
    elif args.workdir and args.output:
        do_build(args.workdir.resolve(), args.output.resolve(), lock)
    else:
        parser.error("Use --check or --workdir PATH --output PATH")

if __name__ == "__main__":
    main()
