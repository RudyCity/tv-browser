# Changelog

Semua perubahan penting pada proyek ini dicatat di file ini.
Format mengikuti [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) dan penomoran mengikuti [Semantic Versioning](https://semver.org/).

## [0.8.4] - 2026-11-27

### Added
- `CHANGELOG.md` sebagai ledger perubahan tunggal untuk Android, desktop, dan dokumentasi.

### Changed
- `AGENTS.md`: aturan wajib pada setiap perubahan (CHANGELOG, versi, commit, README) dan standar koding modular serta scalable.
- `README.md`: nomor versi Development disegarkan ke 0.8.4 dan CHANGELOG ditautkan pada bagian Technical docs.
- `app/build.gradle`: `versionName` 0.8.3 menjadi 0.8.4 dan `versionCode` 15 menjadi 16.

## [0.8.3] - 2026-11-19

### Changed
- Toolbar menampilkan tombol Forward dan navigasi 3-tombol Android diaktifkan.

## [0.8.2] - 2026-11-19

### Fixed
- Home legacy tidak lagi muncul sebagai fallback saat pengguna sedang menelusuri halaman atau menekan Back.

## [0.8.1] - 2026-11-19

### Changed
- Rudy Design System v2 dengan UI edge-to-edge, modal dan Tab Manager yang diperbarui.

Entri 0.8.1 sampai 0.8.3 direkonstruksi dari riwayat commit `app/build.gradle`. Rilis sebelum 0.8.1 dibaca dari tag `v0.8.0-beta.db0f0e71` dan `v0.7.1-preview.67001288`.
