#!/usr/bin/env bash
# Run after extracting the TV Browser starter into a local folder.
# This script never makes a public repository and never overwrites an existing repo.
set -euo pipefail
cd "$(dirname "$0")/.."
command -v gh >/dev/null || { echo 'Install GitHub CLI (gh) first.' >&2; exit 1; }
command -v git >/dev/null || { echo 'Install Git first.' >&2; exit 1; }
gh auth status >/dev/null
if gh repo view RudyCity/tv-browser >/dev/null 2>&1; then
  echo 'RudyCity/tv-browser already exists or is accessible. Refusing to overwrite.' >&2
  exit 1
fi
if [ -e .git ]; then
  echo 'Found an existing .git directory; refusing to change the repository setup.' >&2
  exit 1
fi
git init -b main
git add .
git commit -m 'feat: scaffold dual-engine TV browser with safe WebView fallback'
gh repo create RudyCity/tv-browser --private --source . --remote origin --push
visibility=$(gh repo view RudyCity/tv-browser --json visibility --jq .visibility)
[ "$visibility" = 'PRIVATE' ] || { echo 'ERROR: visibility is not private!' >&2; exit 1; }
echo 'Verified GitHub repository visibility: PRIVATE'
printf 'Private repository created: https://github.com/RudyCity/tv-browser\n'
