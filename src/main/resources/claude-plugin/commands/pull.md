---
description: Pull main (or a named tree) into the current worktree, without merging the worktree back
argument-hint: [source-tree]
---

Pull: bring another tree's branch INTO the current worktree. Nothing goes the other way; the
source tree is only read.

Source: "$ARGUMENTS". Empty means the **main checkout's** branch. A name means a worktree under
`<main repo>/.claude/worktrees/<name>`, or the main checkout for `main`.

1. **Must start inside a worktree.** If in the main checkout, stop and say there is nothing
   to pull into. Commit any uncommitted work first. Stop if the source is the current branch.
2. **Merge it in, here:** `git merge --no-edit <source-branch>`, using the LOCAL branch (never
   fetch or pull origin into the shared main checkout). Already up to date: say so and stop.
   Resolve conflicts keeping both sides' features working.
3. **Build** with the project's usual build and fix anything the merge broke.
4. **Report:** what came in, how many commits, conflicts resolved.

Never touch the source tree, never push, never merge back: that is `/overclauditor:merge` or
`/overclauditor:push`.
