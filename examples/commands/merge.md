---
description: Merge the current worktree's branch into main (or into a named worktree)
argument-hint: [target-tree]
---

Merge the current worktree's branch into another tree.

Target: "$ARGUMENTS". Empty means the **main checkout** of this repository. A name means a
worktree under `<main repo>/.claude/worktrees/<name>`, or the main checkout for `main`.

**The target is shared with other Claude sessions.** Do the slow, failure-prone work
(conflicts, build fixes) in YOUR worktree, and touch the target as briefly as possible, under
a lock. Never leave the target conflicted or broken.

1. **Commit** any uncommitted work in the current worktree. Note the SOURCE branch. Find the
   main repo root with `git worktree list` (first entry), then the TARGET directory and
   branch. Stop if source and target are the same.

2. **Integrate in your own tree first:** `git -C <source-dir> merge --no-edit <target-branch>`.
   Resolve conflicts here, keeping both sides' features working; explain non-obvious choices.

3. **Build and test in your tree** with the project's usual build. Fix anything the merge
   broke here, before going near the target.

4. **Leave the worktree:** `ExitWorktree(action: "keep")`. A worktree session cannot run git
   in the shared checkout. Don't route around this with `update-ref` or self-pushes: that
   moves the branch pointer while the target's files stay behind.

5. **Lock, then merge the target:**

       bash ~/.claude/git-merge-lock.sh acquire <main-repo-root>

   On timeout, report that another session holds the lock and stop. With the lock:
   a. If `git -C <target-dir> rev-parse HEAD` moved since step 2, release and go back to 2.
   b. `git -C <target-dir> merge --no-ff --no-edit <source-branch>` (guaranteed clean after
      step 2; if not, `merge --abort`, release, start over).
   c. `git -C <target-dir> push origin <target-branch>`. A failed push does NOT undo the
      merge: report it and move on.
   d. Always release: `bash ~/.claude/git-merge-lock.sh release <main-repo-root>`.

6. **Build the target** so what the user runs next is the merged code.

7. **Report:** what merged into what, conflicts resolved, and whether the push landed.
   Never delete the worktree or its branch; the user cleans those up.
