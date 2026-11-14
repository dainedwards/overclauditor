# Changelog

Overclauditor versions are `<Clauditor base>-oc.<n>`. Everything below is on top of
Clauditor 0.7.30 (https://github.com/bdkent/clauditor).

## 0.7.30-oc

### Added
- Model and effort dropdowns on the toolbar, colour-coded, sized to their options; models show
  their version (learned live from the status line). Effort: low, medium, high, xhigh, max.
- Branch, change count (`✎4 (2)`: changed files, how many by this session) and the session
  summary ("what it's about - what to test") on the same row; the summary wraps smaller
  instead of growing the bar.
- Configurable command buttons (`~/.overclauditor/config.json`): slash commands or key chords,
  text or icon. Defaults: plan, merge, push, pull. Bundled `/overclauditor:merge`, `:push`, `:pull` commands, loaded per session with `--plugin-dir` (nothing installed).
- Bottom bar: 5-hour, weekly and per-model weekly usage, prompt-cache countdown, context; all
  bars ramp green to red.
- Built-in session summary via a background Haiku call, or read from your own file.
- Pinned tab titles that change only on a worktree switch, the first file edit, /rename, or a
  new CLI title. Short tab titles (a few whole words).
- New session from the editor tab bar (right-click or double-click, and the tab menu), and a
  Resume submenu with the last 10 sessions; no name
  prompt, the CLI titles it.
- Icons for fork, Messages, and icon buttons; a new plugin icon.
- `hideTerminalStatusLine`: hide your own status line inside the plugin (off by default).
- Daily upstream check for maintainers (`tools/upstream-check.sh`, `docs/upstream-updates.md`).

- Red tab badge for a session whose latest reply is an API error (logged out, rate limited,
  overloaded): a ring until you look at the tab, a dot after, gone when the session recovers.
  Read from the transcript's own error flag; no model calls.

### Changed
- Session list loads in the background (Clauditor built it on the UI thread: 16-26 s freezes
  on large histories), parses with a streaming reader (about 2x faster), and keeps a saved
  index so each start reads only new lines.
- The blank status-line row under the prompt is collapsed.
- Clauditor's worktree bar (Commit, Update, Merge, Show Changes, Open in...) is behind a
  setting, off by default; when off it is never built and polls nothing.
- The Commit button is gone; caches moved to the IDE system folder and are removed on uninstall.

### Fixed
- The model and effort dropdowns saved the pick as Claude Code's default for every new session
  (and said so), so one tab's "low" became every resumed session's effort. They now drive the
  `/model` and `/effort` pickers' session-only key, reading the screen at each step; settings.json
  is never touched. Resumed sessions and forks start on the model and effort their transcript
  last records (`--model --effort`); new sessions on `defaultModel` / `defaultEffort`.
- A summary that came back as a sentence ("Cannot build the status line: the images did not
  come through...") could become the tab title. Titles must now be title-shaped (48 characters,
  8 words, no colon, code, path or closing full stop); a bad pinned title is repaired on load, and
  neither the built-in summarizer nor the summary bar keeps such an answer.
- Command buttons and the model/effort dropdowns no longer submit a half-typed prompt along
  with the command: a draft in the input box is stashed first (Claude Code's ctrl+s) and comes
  back once the command has run. The `plan` button now actually enters plan mode with a draft
  present.
- Error text (e.g. "Invalid API key · Please run /login") could become a tab title or the summary:
  titles now never take error-like text, and failed summarizer runs are never saved.
- Titles showed the raw first prompt or `<local-command-caveat>`; now the CLI's own title.
- The model dropdown showed Sonnet until the first status update.
- "Session unresponsive" fired while a resume was loading and never cleared once raised.
- Open tabs didn't restore on restart unless the Sessions pane was open.
