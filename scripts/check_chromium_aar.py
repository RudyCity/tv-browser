#!/usr/bin/env python3
"""Reject SDK artifacts that cannot possibly be an embedded Chromium AAR.

This is a packaging gate, NOT proof that the included native engine is Chromium,
secure, or usable on physical TVs. Real runtime verification remains mandatory.
"""
import io
import sys
from pathlib import Path
from zipfile import BadZipFile, ZipFile

PROVIDER = 'io/rudycity/tvbrowser/chromium/EmbeddedChromiumProvider.class'


def check(path: Path):
    if not path.is_file():
        raise ValueError('Missing Chromium AAR')
    with ZipFile(path) as aar:
        names = set(aar.namelist())
        if 'classes.jar' not in names or 'AndroidManifest.xml' not in names:
            raise ValueError('Invalid AAR: classes.jar or AndroidManifest.xml missing')
        with ZipFile(io.BytesIO(aar.read('classes.jar'))) as code:
            if PROVIDER not in code.namelist():
                raise ValueError('Missing required EmbeddedChromiumProvider implementation')
        abis = set()
        for name in names:
            if name.startswith('jni/') and name.endswith('.so'):
                parts = name.split('/')
                if len(parts) == 3:
                    abis.add(parts[1])
        if not abis:
            raise ValueError('No packaged native renderer libraries (jni/<abi>/*.so)')
        print('AAR structural check passed. Native ABI(s):', ', '.join(sorted(abis)))
        print('NOTE: ABI correctness, sandboxing, Chromium identity, and runtime are NOT verified.')

if __name__ == '__main__':
    try:
        if len(sys.argv) != 2:
            raise ValueError('Usage: check_chromium_aar.py path/to/chromium-engine.aar')
        check(Path(sys.argv[1]))
    except (OSError, BadZipFile, ValueError) as err:
        sys.exit('ERROR: ' + str(err))
