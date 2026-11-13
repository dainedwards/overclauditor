# Overclauditor — notes for Claude sessions

Fork of Clauditor (bdkent/clauditor, remote `upstream`) with the owner's changes; plugin id
`com.overclauditor`. Package names stay `com.clauditor` (internal only).

- **First thing in a session: run `tools/upstream-check.sh`.** It checks once a day and is
  silent otherwise. If it lists commits, follow `docs/upstream-updates.md` before other work.

- **Ship every change with `tools/publish-local.sh`**: bumps `-oc.N`, builds, writes
  `~/.overclauditor/repo/updatePlugins.xml`, commits the bump. Rider polls that file (custom
  plugin repository) and auto-updates; then `git push`.
- JDK 21: `~/Library/Java/JavaVirtualMachines/openjdk-21.0.2` (the script sets JAVA_HOME).
- Push over SSH (`origin` is git@github.com): the `gh` token lacks `workflow` scope.
- Never kill a test `claude` process mid-start: three kills turn Claude Code's fullscreen
  renderer off (`fullscreenAutoDisabled` in `~/.claude.json`). Exit with `/exit`.
- A Kotlin top-level `val` between `@Service` and its class steals the annotation; the
  service then never registers and the Sessions pane comes up empty.
