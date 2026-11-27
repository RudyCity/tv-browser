# AGENTS.md — Workspace Rules

Dokumen ini WAJIB dipatuhi oleh setiap agent/AI (Master Agent, Subagents, Swarm Workers, atau CLI Bridges) yang bekerja pada project ini.

## 1. Aturan Wajib Skill (Mandatory Skills)
Setiap pengerjaan tugas di project ini WAJIB memuat dan menerapkan:
1. **Instruction Fidelity**: Gunakan dan terapkan `/instruction-fidelity` (`C:\Users\USER\.gemini\config\skills\instruction-fidelity\SKILL.md`) untuk setiap pengerjaan tugas, investigasi, implementasi, perbaikan bug, refactoring, audit, dan delegasi subagent.
2. **Anti-Slop**: Gunakan dan muat `/antislop` (`C:\Users\USER\.gemini\config\skills\antislop\SKILL.md`) untuk mencegah generic AI fluff/slop pada kode, komentar, dan konten.
3. **Frontend Taste**: Gunakan dan muat `/design-taste-frontend-v1` (`C:\Users\USER\.gemini\config\skills\design-taste-frontend-v1\SKILL.md`) untuk seluruh pekerjaan UI/UX, frontend, styling, layout, dan desain.

## 2. Environment & CLI Rules
- Selalu gunakan `;` sebagai pengganti `&&` dalam command PowerShell di sistem operasi Windows.

## 3. Deteksi Agent Lain & Perlindungan Perubahan (Concurrent Agent Protection)
- **Mandatory Git Status Preflight**: Selalu jalankan `git status` sebelum memodifikasi atau membuat file.
- Jika terdapat file uncommitted atau untracked yang tidak Anda buat, **asumsikan ada agent lain yang sedang aktif bekerja di workspace**.
- **DILARANG KERAS** menjalankan `git restore`, `git checkout .`, `git clean -fd`, atau menimpa perubahan milik agent lain.
- **DILARANG KERAS** menggunakan blanket staging seperti `git add .` atau `git add -A`. Hanya stage file yang secara spesifik Anda kerjakan.

## 4. Wajib pada Setiap Perubahan: CHANGELOG, Versi, Commit, README
Satu perubahan tidak dianggap selesai sebelum keempat hal di bawah ini dikerjakan dalam commit yang sama.

### 4.1 CHANGELOG
- `CHANGELOG.md` di root adalah ledger perubahan tunggal, format [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
- Setiap commit wajib menambah entri di bawah versi terbaru: tanggal (`YYYY-MM-DD`), kategori `Added` / `Changed` / `Fixed` / `Removed`, file yang berubah, dan alasan perubahannya.
- Entri ditulis dari sudut pandang pengguna atau maintainer, bukan salinan pesan commit.

### 4.2 Versi
- Versi proyek mengikuti `versionName` Android di `app/build.gradle`. Setiap perubahan menaikkan patch, `versionCode` naik satu, dan keduanya diubah bersama.
- Build desktop memakai versi sendiri: `desktop/package.json` dan `desktop/src-tauri/tauri.conf.json` wajib selalu bernilai sama. Naikkan hanya bila kode desktop ikut berubah.
- Tag rilis mengikuti riwayat repo: `v<versi>-beta.<sha>` untuk Android dan `desktop-v<versi>` untuk desktop. Tag dibuat setelah commit versi, tidak sebelumnya.

### 4.3 Commit
- Format Conventional Commits: `type(scope): ringkasan imperatif`, contoh `fix(desktop): keep download path on retry`.
- Satu commit satu perubahan logis. Refactor besar dipisah dari perubahan perilaku.
- Commit yang menaikkan versi wajib menyebut versi barunya di ringkasan, contoh `...; bump 0.8.4`.
- Hanya stage file yang dikerjakan sendiri (lihat bagian 3). Dilarang `git add .` atau `git add -A`.

### 4.4 README
Perbarui `README.md` pada commit yang sama bila perubahan menyentuh salah satu dari ini:
- cara build, menjalankan, atau menguji
- fitur, varian, atau batasan yang sudah didokumentasikan
- nomor versi yang disebut di README
- dokumen baru yang perlu ditautkan

## 5. Standar Koding: Modular & Scalable
Struktur modul yang ada (`app/`, `compose-ui/`, `engine-api/`, `engine-gecko/`, `desktop/src/`, `desktop/src-tauri/`) adalah batas yang sudah ditetapkan. Aturan berikut mengikat penambahan kode baru.

### 5.1 Batas modul
- Satu file, kelas, atau modul memegang satu tanggung jawab. Bila sebuah file mulai menangani dua hal, pecah dulu sebelum menambah fitur.
- Arah dependensi searah: UI bergantung pada `engine-api`, bukan pada internal engine. Import silang yang membalik arah ini dilarang.
- API publik modul dijaga minimal dan stabil. Detail internal tidak diekspor.

### 5.2 Skalabilitas
- Tidak ada global mutable state baru. Dependensi masuk lewat parameter, constructor, atau command Tauri yang sudah ada.
- Konfigurasi berasal dari satu sumber (`gradle.properties`, `package.json`, environment), bukan path atau angka yang di-hardcode di banyak tempat.
- Logika yang dipakai dua kali atau lebih diekstrak ke helper atau modul, bukan disalin.
- Perubahan signature publik wajib memperbarui semua call site dan dicatat di CHANGELOG bagian `Changed`.

### 5.3 Kualitas
- Setiap modul baru wajib punya unit test di lokasi yang sudah dipakai (`desktop/test/`, `app/src/test/`).
- Dilarang stub, `TODO`, atau placeholder yang menggantikan implementasi.
- Komentar menjelaskan alasan yang tidak terbaca dari kode, bukan mengulang isi baris.

## 6. Standar Referensi Desain UI/UX
Gunakan referensi berikut sebagai standar rujukan utama untuk pekerjaan UI/UX, frontend, dan web design:
- [jiro.build](https://jiro.build): 1,400+ designs (MCP `https://mcp.jiro.build`) untuk komponen, bento blocks, dan layout modern.
- [designspells.com](https://designspells.com): Referensi micro-interactions, delight moments, dan fluid spring physics.
- [styles.refero.design](https://styles.refero.design): Real color and type systems, token palet warna nyata dari real products.
- [supahero.io](https://supahero.io): Referensi layout hero section dan value-prop visual framing.
- [mobbin.com](https://mobbin.com): Referensi real app flows, UX journeys, onboarding, dan state transitions.
- Panduan lengkap: skill `curated-design-references` (`C:\Users\USER\.gemini\config\skills\curated-design-references\SKILL.md`).
