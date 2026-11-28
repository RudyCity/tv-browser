# Changelog

Semua perubahan penting pada proyek ini dicatat di file ini.
Format mengikuti [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) dan penomoran mengikuti [Semantic Versioning](https://semver.org/).

## [0.8.9] - 2026-11-28

### Changed
- `desktop/src-tauri/tauri.conf.json`: installer Windows memakai `installMode: currentUser`, sehingga instalasi berjalan per pengguna tanpa permintaan elevasi UAC.
- `desktop/package.json`, `desktop/src-tauri/tauri.conf.json`, `desktop/src-tauri/Cargo.toml`: versi 0.1.4 menjadi 0.1.5.
- `app/build.gradle`: `versionName` 0.8.8 menjadi 0.8.9 dan `versionCode` 20 menjadi 21.
- `README.md`: nomor versi Development disegarkan ke 0.8.9.
- `desktop/README.md`: catatan instalasi per pengguna, penyebab pesan "Error writing temporary file" pada NSIS, dan jalur cadangan berupa exe portable.

### Notes
- Laporan pengguna tentang "Error writing temporary file" saat menjalankan installer diselidiki: installer tidak memiliki tanda file dari internet, Smart App Control mati, tidak ada aturan ASR, Controlled Folder Access mati, dan `%TEMP%` pengguna bisa ditulis. Versi terpasang tetap 0.1.0 setelah percobaan, jadi instalasi memang tidak selesai.
- Percobaan menjalankan installer dari sesi agen tidak menghasilkan bukti karena pembuatan proses GUI di sesi ini menggantung, sehingga perbaikan mode `currentUser` belum terverifikasi lewat instalasi nyata. Exe portable disediakan sebagai jalur yang tidak bergantung pada NSIS.

## [0.8.8] - 2026-11-28

### Added
- `desktop/src/browser-data.js`: riwayat unduhan kini menyimpan status, byte diterima, ukuran total, dan alasan gagal, dengan `upsertDownload` untuk pembaruan dan `markInterruptedDownloads` untuk pemulihan saat aplikasi dibuka kembali.
- `desktop/`: status `interrupted` untuk unduhan yang belum selesai saat aplikasi ditutup, lengkap dengan label Inggris dan Indonesia serta warna status tersendiri.

### Changed
- `desktop/src/main.js`: progres unduhan disimpan paling sering setiap 1,5 detik, sedangkan status terminal disimpan langsung; unduhan dari tab privat tetap tidak dicatat.
- `desktop/package.json`, `desktop/src-tauri/tauri.conf.json`, `desktop/src-tauri/Cargo.toml`: versi 0.1.3 menjadi 0.1.4.
- `app/build.gradle`: `versionName` 0.8.7 menjadi 0.8.8 dan `versionCode` 19 menjadi 20.
- `README.md`: nomor versi Development disegarkan ke 0.8.8.
- `desktop/README.md`: dokumentasi riwayat unduhan persisten, batas resume setelah restart, dan catatan bahwa paket MSI memerlukan Windows Installer service.

### Fixed
- Daftar unduhan tidak lagi kosong setelah aplikasi ditutup dan dibuka kembali; unduhan yang terhenti muncul sebagai `interrupted`, bukan hilang.

### Notes
- Build lokal 0.1.3 menghasilkan installer NSIS `Rudy Browser_0.1.3_x64-setup.exe` (2,27 MB). Paket MSI gagal pada sesi ini karena WiX ICE validation membutuhkan Windows Installer service (`LGHT0217`/`LGHT0216`), sementara NSIS berhasil.

## [0.8.7] - 2026-11-28

### Added
- `desktop/src/strings.js` dan `desktop/test/strings.test.js`: kamus antarmuka Inggris dan Indonesia berisi 199 kunci dengan `translate`, fallback bahasa, dan interpolasi placeholder.
- `desktop/src/tab-model.js` dan `desktop/test/tab-model.test.js`: logika tab murni yang dipakai bersama oleh tampilan, mencakup tutup tab, siklus tab, urutan tab, buka ulang tab, dan payload sesi.
- `desktop/src-tauri/src/downloads.rs`: manajer unduhan dengan progres, jeda, lanjut lewat HTTP range, dan batal, beserta 25 unit test tanpa jaringan.
- Pilihan bahasa di Settings. Seluruh teks antarmuka, pesan status, dan dialog mengikuti bahasa terpilih dan tersimpan bersama pengaturan lain.
- Progres unduhan di daftar Downloads: persen, status, tombol jeda, lanjut, dan batal, termasuk state kosong, gagal, dan progres tak tentu saat ukuran tidak diketahui.
- Notifikasi permintaan izin situs dengan tombol Allow, sehingga permintaan berikutnya untuk origin dan jenis yang sama diberikan.

### Changed
- `desktop/package.json`, `desktop/src-tauri/tauri.conf.json`, `desktop/src-tauri/Cargo.toml`: versi 0.1.2 menjadi 0.1.3.
- `app/build.gradle`: `versionName` 0.8.6 menjadi 0.8.7 dan `versionCode` 18 menjadi 19.
- `README.md`: nomor versi Development disegarkan ke 0.8.7.
- `desktop/src-tauri/Cargo.toml`: dependency `reqwest` 0.13 dengan fitur `stream` dan `native-tls` untuk transfer unduhan terkelola.
- `desktop/src-tauri/build.rs`, `desktop/src-tauri/capabilities/desktop-shell.json`: mendaftarkan empat command unduhan baru.
- `desktop/README.md` dan `.github/workflows/desktop.yml`: dokumentasi bahasa, unduhan berprogres, notifikasi izin, dan catatan rilis diperbarui sesuai kemampuan baru.

### Removed
- `desktop/README.md`: klaim lama bahwa antarmuka hanya berbahasa Inggris, serta catatan bahwa unduhan tidak punya progres, jeda, atau batal.

## [0.8.6] - 2026-11-28

### Added
- `desktop/`: buka ulang tab tertutup dengan `Ctrl+Shift+T`, pengurutan tab lewat drag, dan perpindahan tab dengan `ArrowLeft`, `ArrowRight`, `Home`, serta `End` saat strip tab mendapat fokus.
- `desktop/src-tauri/src/main.rs`: command `browser_tab_move` dan helper `TabCollection::close` yang dipakai bersama oleh command tutup tab.
- `desktop/src-tauri/src/main.rs`: enam uji baru untuk pemilihan tab tetangga saat menutup tab dan pemindahan urutan tab.

### Changed
- `desktop/`: pemulihan sesi kini menyimpan judul halaman dan zoom tiap tab, bukan hanya URL.
- `desktop/package.json`, `desktop/src-tauri/tauri.conf.json`, `desktop/src-tauri/Cargo.toml`: versi 0.1.1 menjadi 0.1.2.
- `app/build.gradle`: `versionName` 0.8.5 menjadi 0.8.6 dan `versionCode` 17 menjadi 18.
- `README.md`: nomor versi Development disegarkan ke 0.8.6 dan perintah guard memakai Bun.
- `scripts/check-changelog.mjs`, `.github/workflows/changelog-guard.yml`: guard changelog dijalankan dengan Bun 1.4.2, bukan Node.
- `desktop/README.md`: dokumentasi buka ulang tab, pengurutan tab, navigasi panah, pemulihan judul dan zoom, serta batas bahasa antarmuka.

### Fixed
- `desktop/src-tauri/src/main.rs`: permintaan `window.open` sekarang dibuka pada tab yang memintanya, bukan pada tab yang kebetulan aktif saat event diproses.

### Removed
- `desktop/src-tauri/src/main.rs`: command `browser_current_url` dihapus karena tidak dipakai antarmuka, termasuk entri capability dan manifest permission-nya.

## [0.8.5] - 2026-11-27

### Added
- `desktop/`: tab strip dengan riwayat navigasi per tab, tab privat, pemulihan sesi, zoom native, find in page, tema gelap dan terang, panel Settings, serta izin per-origin.
- `desktop/src-tauri/src/main.rs`: command untuk tab, zoom, find, izin, hapus data situs, buka path unduhan, dan fullscreen.
- `desktop/test/`: pengujian untuk mesin pencari, tangga zoom, indikator keamanan, pengaturan, dan pemulihan sesi.
- `desktop/bun.lock`: lockfile Bun sebagai pengganti `package-lock.json` yang tidak pernah masuk repo.
- Tampilan error lokal saat halaman selesai dimuat sebagai halaman gagal webview, lengkap dengan aksi coba lagi.
- `scripts/check-changelog.mjs` dan `scripts/check-changelog.test.mjs`: guard yang menolak perubahan sumber tanpa entri CHANGELOG dan menolak versi changelog, Android, atau desktop yang tidak sinkron.
- `.github/workflows/changelog-guard.yml`: menjalankan guard pada pull request dan push ke `main`.

### Changed
- `README.md`: nomor versi Development disegarkan ke 0.8.5 dan perintah guard disebut pada bagian Build and tests.
- `app/build.gradle`: `versionName` 0.8.4 menjadi 0.8.5 dan `versionCode` 16 menjadi 17.
- `AGENTS.md`: penanda `[skip changelog]` didokumentasikan pada bagian 4.1 untuk commit yang entri changelog-nya sudah dibuat terpisah.
- `scripts/check-changelog.mjs` dan `.github/workflows/changelog-guard.yml`: pemeriksaan CHANGELOG bisa dilewati lewat penanda `[skip changelog]` pada pesan commit kepala, sementara pemeriksaan versi tetap berjalan.
- `desktop/package.json`, `desktop/src-tauri/tauri.conf.json`, `desktop/src-tauri/Cargo.toml`: versi 0.1.0 menjadi 0.1.1.
- `desktop/`: toolchain dari npm dan Node ke Bun 1.4.2, mencakup `bun install`, `bun run test`, dan `bun run build`.
- `.github/workflows/desktop.yml`: `actions/setup-node` diganti `oven-sh/setup-bun`, semua perintah npm menjadi bun, instalasi dependency memakai `--frozen-lockfile`, dan catatan rilis disesuaikan dengan fitur yang sudah ada.
- `desktop/README.md`: dokumentasi fitur baru, batasan deteksi gagal muat, dan perintah Bun.
- `desktop/.gitignore`: mengecualikan ikon hasil generate, permission hasil build, dan lockfile Cargo.

### Fixed
- `desktop/src-tauri/src/main.rs`: riwayat navigasi tidak lagi global sehingga tombol Back dan Forward mengikuti tab yang aktif, bukan tab terakhir yang memuat halaman.
- `desktop/src/main.js`: halaman yang gagal dimuat tidak lagi dibiarkan pada halaman error bawaan webview tanpa penjelasan.

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
