#!/usr/bin/env python3
"""Fail-closed GeckoView APK ABI verification and stable asset naming.

Rejects universal, mixed, missing, duplicate and unknown-native-ABI APKs.
Copies original signed APK bytes unchanged and prints exact sizes/checksums.
"""
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import shutil
import zipfile

ABIS = ("arm64-v8a", "armeabi-v7a", "x86_64")
PREFIX = "RudyBrowser-GeckoView-Android8"


def native_abis(apk: Path) -> set[str]:
    try:
        with zipfile.ZipFile(apk) as archive:
            files = archive.namelist()
    except (OSError, zipfile.BadZipFile) as error:
        raise ValueError(f"{apk}: unreadable APK: {error}") from error
    if "AndroidManifest.xml" not in files:
        raise ValueError(f"{apk}: missing AndroidManifest.xml")
    abis = set()
    for path in files:
        parts = path.split("/")
        if len(parts) == 3 and parts[0] == "lib" and parts[2].endswith(".so"):
            abis.add(parts[1])
    if not abis:
        raise ValueError(f"{apk}: no native .so libraries found")
    return abis


def select_apks(source: Path) -> dict[str, Path]:
    apks = sorted(source.glob("*.apk"))
    if len(apks) != len(ABIS):
        raise ValueError(f"{source}: expected {len(ABIS)} APKs, got {len(apks)}")
    selected = {}
    for apk in apks:
        native = native_abis(apk)
        if len(native) != 1:
            raise ValueError(f"{apk.name}: universal/mixed native ABIs: {sorted(native)}")
        abi = next(iter(native))
        if abi not in ABIS:
            raise ValueError(f"{apk.name}: unsupported native ABI {abi}")
        if abi in selected:
            raise ValueError(f"duplicate {abi} APKs: {selected[abi].name}, {apk.name}")
        selected[abi] = apk
    missing = set(ABIS) - set(selected)
    if missing:
        raise ValueError(f"missing Gecko APK for: {sorted(missing)}")
    return selected


def package_apks(source: Path, target_dir: Path) -> dict[str, Path]:
    selected = select_apks(source)
    target_dir.mkdir(parents=True, exist_ok=True)
    output = {}
    for abi in ABIS:
        destination = target_dir / f"{PREFIX}-{abi}-debug.apk"
        if selected[abi].resolve() != destination.resolve():
            shutil.copyfile(selected[abi], destination)
        digest = hashlib.sha256()
        with destination.open("rb") as data:
            for block in iter(lambda: data.read(1048576), b""):
                digest.update(block)
        bytesize = destination.stat().st_size
        print(f"{abi}: {destination.name}: {bytesize} bytes ({bytesize/1048576:.1f} MiB) sha256={digest.hexdigest()}")
        output[abi] = destination
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        package_apks(args.input, args.output)
    except ValueError as error:
        parser.error(str(error))


if __name__ == "__main__":
    main()
