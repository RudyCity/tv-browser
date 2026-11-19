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

## 4. Standar Referensi Desain UI/UX
Gunakan referensi berikut sebagai standar rujukan utama untuk pekerjaan UI/UX, frontend, dan web design:
- [jiro.build](https://jiro.build): 1,400+ designs (MCP `https://mcp.jiro.build`) untuk komponen, bento blocks, dan layout modern.
- [designspells.com](https://designspells.com): Referensi micro-interactions, delight moments, dan fluid spring physics.
- [styles.refero.design](https://styles.refero.design): Real color and type systems, token palet warna nyata dari real products.
- [supahero.io](https://supahero.io): Referensi layout hero section dan value-prop visual framing.
- [mobbin.com](https://mobbin.com): Referensi real app flows, UX journeys, onboarding, dan state transitions.
- Panduan lengkap: skill `curated-design-references` (`C:\Users\USER\.gemini\config\skills\curated-design-references\SKILL.md`).
