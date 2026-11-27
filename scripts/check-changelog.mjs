#!/usr/bin/env bun
// Run with Bun: bun scripts/check-changelog.mjs
// Guard for the per-change rules in AGENTS.md section 4: a source change must
// update CHANGELOG.md, and the changelog version must match the Android
// versionName. Reading is done from the repository root.
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const SOURCE_DIRECTORIES = [
  'app/',
  'compose-ui/',
  'engine-api/',
  'engine-gecko/',
  'native-chromium/',
  'scripts/',
  'desktop/src/',
  'desktop/src-tauri/',
  'desktop/test/',
  '.github/workflows/',
];

const SOURCE_FILES = new Set([
  'build.gradle',
  'settings.gradle',
  'gradle.properties',
  'desktop/package.json',
  'desktop/index.html',
]);

export function normalizeChangedFiles(text) {
  return text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => line.replaceAll('\\', '/').replace(/^\.\//, ''));
}

export function isSourceFile(path) {
  return SOURCE_FILES.has(path) || SOURCE_DIRECTORIES.some((prefix) => path.startsWith(prefix));
}

export function topChangelogVersion(markdown) {
  const match = markdown.match(/^##\s*\[(\d+\.\d+\.\d+)\]/m);
  return match ? match[1] : null;
}

export function androidVersionName(gradle) {
  const match = gradle.match(/^\s*versionName\s+['"]([^'"]+)['"]/m);
  return match ? match[1] : null;
}

export function manifestVersion(text) {
  try {
    return JSON.parse(text).version ?? null;
  } catch {
    return null;
  }
}

export function evaluate({ changedFiles, changelog, gradle, packageJson, tauriConf, skipChangelog = false }) {
  const problems = [];
  const sources = changedFiles.filter(isSourceFile);

  if (sources.length > 0 && !changedFiles.includes('CHANGELOG.md') && !skipChangelog) {
    problems.push(
      `Perubahan sumber tanpa entri CHANGELOG.md: ${sources.join(', ')}. ` +
        'Tambahkan entri pada versi terbaru sesuai AGENTS.md bagian 4.1.',
    );
  }

  const changelogVersion = topChangelogVersion(changelog);
  const versionName = androidVersionName(gradle);

  if (!changelogVersion) {
    problems.push('CHANGELOG.md tidak punya heading versi "## [x.y.z]" di paling atas.');
  }
  if (!versionName) {
    problems.push('app/build.gradle tidak punya versionName.');
  }
  if (changelogVersion && versionName && changelogVersion !== versionName) {
    problems.push(
      `Versi teratas CHANGELOG (${changelogVersion}) tidak sama dengan versionName (${versionName}). ` +
        'Naikkan keduanya pada commit yang sama sesuai AGENTS.md bagian 4.2.',
    );
  }

  const desktopVersion = manifestVersion(packageJson);
  const tauriVersion = manifestVersion(tauriConf);

  if (!desktopVersion || !tauriVersion) {
    problems.push('Versi desktop tidak terbaca dari package.json atau tauri.conf.json.');
  } else if (desktopVersion !== tauriVersion) {
    problems.push(
      `Versi desktop tidak sinkron: desktop/package.json ${desktopVersion} ` +
        `versus desktop/src-tauri/tauri.conf.json ${tauriVersion}.`,
    );
  }

  return problems;
}

export function formatProblems(problems) {
  return problems.map((problem) => `- ${problem}`).join('\n');
}

function readText(path) {
  return readFileSync(resolve(process.cwd(), path), 'utf8');
}

function runCli() {
  const changedFiles = normalizeChangedFiles(readFileSync(0, 'utf8'));
  const skipChangelog = process.argv.includes('--skip-changelog') || process.env.SKIP_CHANGELOG === '1';
  const problems = evaluate({
    changedFiles,
    changelog: readText('CHANGELOG.md'),
    gradle: readText('app/build.gradle'),
    packageJson: readText('desktop/package.json'),
    tauriConf: readText('desktop/src-tauri/tauri.conf.json'),
    skipChangelog,
  });

  if (skipChangelog) {
    console.log('Pemeriksaan CHANGELOG dilewati karena penanda [skip changelog] pada pesan commit.');
  }

  if (problems.length > 0) {
    console.error('Changelog guard gagal:');
    console.error(formatProblems(problems));
    process.exitCode = 1;
    return;
  }

  console.log(`Changelog guard lulus untuk ${changedFiles.length} file yang berubah.`);
}

const invokedPath = process.argv[1] ? resolve(process.argv[1]) : '';
if (invokedPath === fileURLToPath(import.meta.url)) {
  runCli();
}
