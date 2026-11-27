import assert from 'node:assert/strict';
import test from 'node:test';

import {
  androidVersionName,
  evaluate,
  isSourceFile,
  manifestVersion,
  normalizeChangedFiles,
  topChangelogVersion,
} from './check-changelog.mjs';

const changelog = '## [0.8.5] - 2026-11-27\n\n### Changed\n- something\n';
const gradle = "defaultConfig {\n        versionCode 17\n        versionName '0.8.5'\n}\n";
const packageJson = '{"version":"0.1.0"}';
const tauriConf = '{"version":"0.1.0"}';

function check(overrides = {}) {
  return evaluate({
    changedFiles: [],
    changelog,
    gradle,
    packageJson,
    tauriConf,
    ...overrides,
  });
}

test('perubahan sumber tanpa CHANGELOG dilaporkan', () => {
  const problems = check({ changedFiles: ['app/build.gradle', 'desktop/src/main.js'] });
  assert.equal(problems.length, 1);
  assert.match(problems[0], /tanpa entri CHANGELOG\.md/);
});

test('perubahan dokumentasi saja tidak dilaporkan', () => {
  assert.deepEqual(check({ changedFiles: ['README.md', 'docs/VERIFICATION.md'] }), []);
});

test('perubahan sumber yang disertai CHANGELOG lulus', () => {
  assert.deepEqual(check({ changedFiles: ['desktop/src/main.js', 'CHANGELOG.md'] }), []);
});

test('penanda skip melewati pemeriksaan CHANGELOG tetapi bukan pemeriksaan versi', () => {
  assert.deepEqual(check({ changedFiles: ['desktop/src/main.js'], skipChangelog: true }), []);

  const stillChecked = check({
    changedFiles: ['desktop/src/main.js'],
    skipChangelog: true,
    gradle: "versionName '0.8.4'",
  });
  assert.equal(stillChecked.length, 1);
  assert.match(stillChecked[0], /tidak sama dengan versionName/);
});

test('versi CHANGELOG dan versionName yang berbeda dilaporkan', () => {
  const problems = check({ changedFiles: ['CHANGELOG.md'], gradle: "versionName '0.8.4'" });
  assert.equal(problems.length, 1);
  assert.match(problems[0], /tidak sama dengan versionName/);
});

test('versi desktop yang tidak sinkron dilaporkan', () => {
  const problems = check({ changedFiles: [], tauriConf: '{"version":"0.1.1"}' });
  assert.equal(problems.length, 1);
  assert.match(problems[0], /Versi desktop tidak sinkron/);
});

test('manifest JSON rusak dilaporkan, bukan dilempar sebagai exception', () => {
  const problems = check({ packageJson: '{not json' });
  assert.equal(problems.length, 1);
  assert.match(problems[0], /tidak terbaca/);
});

test('normalizeChangedFiles menyeragamkan separator dan membuang baris kosong', () => {
  assert.deepEqual(normalizeChangedFiles('app\\build.gradle\r\n\r\n./CHANGELOG.md\n'), [
    'app/build.gradle',
    'CHANGELOG.md',
  ]);
});

test('isSourceFile mengenali direktori, file tunggal, dan dokumentasi', () => {
  assert.equal(isSourceFile('desktop/src-tauri/src/main.rs'), true);
  assert.equal(isSourceFile('gradle.properties'), true);
  assert.equal(isSourceFile('.github/workflows/desktop.yml'), true);
  assert.equal(isSourceFile('docs/VERIFICATION.md'), false);
  assert.equal(isSourceFile('README.md'), false);
});

test('parser versi membaca changelog, gradle, dan manifest', () => {
  assert.equal(topChangelogVersion(changelog), '0.8.5');
  assert.equal(topChangelogVersion('tanpa heading'), null);
  assert.equal(androidVersionName(gradle), '0.8.5');
  assert.equal(androidVersionName('tanpa versionName'), null);
  assert.equal(manifestVersion(packageJson), '0.1.0');
  assert.equal(manifestVersion('{'), null);
});
