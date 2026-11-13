---
description: Merge the current worktree's branch into main (or a named tree), then go back into the worktree to keep working
argument-hint: [target-tree]
---

Push: run `/overclauditor:merge`, then return to the worktree so work continues on the same
branch.

Target: "$ARGUMENTS", meaning the same as for `/overclauditor:merge` (empty = the main
checkout).

1. **Must start inside a worktree.** If not, say to use `/overclauditor:merge` instead. Note
   the SOURCE directory (`pwd`) and branch first.
2. **Follow `${CLAUDE_PLUGIN_ROOT}/commands/merge.md`** steps 1 to 6 with this target. Read it
   now.
3. **Re-enter the worktree** afterwards, whether or not the push landed:
   `EnterWorktree(path: <source-dir>)`. Confirm the directory and branch. The branch already
   has the target's commits (merge step 2), so nothing more to merge.
4. **Report** as the merge does, plus one line saying the session is back in `<source-dir>`.

Never remove the worktree or branch.
