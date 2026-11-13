#!/bin/bash
# Once a day: fetch Clauditor (remote `upstream`) and list commits we haven't taken.
# Silent when already checked today or when there's nothing new. --force skips the daily gate.
set -euo pipefail
cd "$(dirname "$0")/.."
stamp="$HOME/.overclauditor/upstream-checked"
today=$(date +%F)
if [ "${1:-}" != "--force" ] && [ "$(cat "$stamp" 2>/dev/null)" = "$today" ]; then exit 0; fi
git fetch -q upstream
mkdir -p "$(dirname "$stamp")" && echo "$today" > "$stamp"
new=$(git log --oneline --no-merges main..upstream/main)
[ -z "$new" ] && exit 0
echo "Clauditor has $(echo "$new" | wc -l | tr -d ' ') new commit(s) since our last merge:"
echo "$new"
echo "-> follow docs/upstream-updates.md"
