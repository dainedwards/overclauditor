#!/bin/bash
# Serialize the target-touching step of /merge across parallel Claude sessions.
#
#   git-merge-lock.sh acquire <main-repo-root>   -> exits 0 when held, 1 on timeout
#   git-merge-lock.sh release <main-repo-root>
#
# mkdir is the atomic primitive (macOS has no flock binary). A lock older than
# STALE_SECS whose owner pid is gone is broken automatically.

set -uo pipefail

ACTION=${1:?usage: git-merge-lock.sh acquire|release <repo-root>}
ROOT=${2:?usage: git-merge-lock.sh acquire|release <repo-root>}
LOCK="$(git -C "$ROOT" rev-parse --path-format=absolute --git-common-dir)/claude-merge.lock"
STALE_SECS=900
TIMEOUT_SECS=300

case "$ACTION" in
  acquire)
    waited=0
    while ! mkdir "$LOCK" 2>/dev/null; do
      owner=$(cat "$LOCK/pid" 2>/dev/null || echo "")
      age=$(( $(date +%s) - $(stat -f %m "$LOCK" 2>/dev/null || stat -c %Y "$LOCK" 2>/dev/null || date +%s) ))
      if [ -n "$owner" ] && ! kill -0 "$owner" 2>/dev/null && [ "$age" -gt 60 ]; then
        echo "breaking lock from dead pid $owner (${age}s old)" >&2
        rm -rf "$LOCK"; continue
      fi
      if [ "$age" -gt "$STALE_SECS" ]; then
        echo "breaking stale lock (${age}s old, pid ${owner:-unknown})" >&2
        rm -rf "$LOCK"; continue
      fi
      if [ "$waited" -ge "$TIMEOUT_SECS" ]; then
        echo "timed out after ${waited}s waiting for $LOCK (held by pid ${owner:-unknown})" >&2
        exit 1
      fi
      [ "$waited" = 0 ] && echo "waiting for merge lock (held by pid ${owner:-unknown})..." >&2
      sleep 3; waited=$((waited + 3))
    done
    echo $$ >"$LOCK/pid"
    echo "acquired $LOCK"
    ;;
  release)
    rm -rf "$LOCK" && echo "released $LOCK"
    ;;
  *)
    echo "unknown action: $ACTION" >&2; exit 2 ;;
esac
