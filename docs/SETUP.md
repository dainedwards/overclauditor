# Overclauditor setup (for a person or a Claude agent)

Overclauditor is a fork of Clauditor (https://github.com/bdkent/clauditor): a JetBrains IDE
plugin that runs Claude Code sessions as editor tabs. This file sets it up on a new machine.
Everything machine-specific lives in one optional file, `~/.overclauditor/config.json`.

## 1. Requirements

- A JetBrains IDE, build 243 (2024.3) or newer, with the bundled Terminal plugin.
- Claude Code CLI (`claude`) on PATH, signed in. If it isn't on the IDE's PATH, set its full
  path in Settings > Tools > Overclauditor > claude binary.
- JDK 21 to build. macOS: `/usr/libexec/java_home -v 21` must print a path, or set `JAVA_HOME`.

## 2. Build and install

    git clone <this repo> && cd overclauditor
    tools/publish-local.sh        # bumps -oc.N, builds, writes ~/.overclauditor/repo/

Then, once, in the IDE: Settings > Plugins > gear > Manage Plugin Repositories > `+` >
`file://<HOME>/.overclauditor/repo/updatePlugins.xml` (the script prints the folder). Install
Overclauditor from the Marketplace tab (it lists that repository), restart. From then on each
`tools/publish-local.sh` run is picked up as a plugin update. Uninstall or disable the original
Clauditor first: both register the same tool windows.

An agent can do every step except the IDE clicks; ask the user for those.

## 3. Configure: `~/.overclauditor/config.json`

Optional. Without it you get: a `plan` button, the built-in summary, and the user's status line
shown as usual. Read live; edits apply to tabs opened afterwards (buttons, status line) or
within seconds (summary).

```json
{
  "buttons": [
    { "label": "plan", "send": "/plan" },
    { "label": "review", "send": "/review" },
    { "icon": "Actions.Execute", "tooltip": "Run my thing", "keys": "ctrl+x ctrl+r" }
  ],
  "summary": { "source": "builtin" },
  "hideTerminalStatusLine": false
}
```

- **`buttons`** — the session toolbar's command buttons, in order.
  - `label` makes a text button (left of the fork icon, all one width). `icon` makes an icon
    button (after Messages): any `AllIcons` name as `Group.Name`, e.g. `Actions.Copy`,
    `Actions.Execute`, `General.Settings`.
  - `send` types text into the session; a value starting with `/` is a slash command and gets
    Enter appended. `keys` sends key chords instead: `ctrl+<letter>`, `enter`, `esc`, `tab`,
    space-separated for a sequence. Bind a chord to a command in
    `~/.claude/keybindings.json` (`"ctrl+x ctrl+r": "command:<name>"`) to give a button a
    plugin command without typing.
  - Custom slash commands must exist for the user (`~/.claude/commands/*.md` or a plugin).
    Ask the user which of theirs they want as buttons; don't invent any.
- **`summary`** — the two-line "what this session is about – what to test" text in the
  toolbar, also used to retitle tabs when work starts or the session changes worktree.
  - `"builtin"` (default): the plugin runs `claude -p --model haiku` in the background when
    the session's recent prompts change. Costs one small Haiku call per new prompt.
  - `"file"` with `"file": "~/path/{sessionId}.summary"`: read a summary something else
    writes (line 1 the gist, line 2 what to test), e.g. the user's own status-line script.
    Use this instead of builtin when such a script exists, so it isn't generated twice.
  - `"off"`: no summary; tabs keep their first title except for /rename or a new CLI title.
- **`hideTerminalStatusLine`** (default `false`) — `true` blanks the user's own status line in
  plugin sessions only; their terminals elsewhere are untouched, and the command still runs
  for any side effects (caches it writes, files other tools read). The toolbar already shows
  model, effort, context, cache and usage, so a status line showing the same is redundant
  there, and hiding it gives the prompt back a row or two.
  **Agent: ask only if the user has a status line.** Check `statusLine` in
  `~/.claude/settings.json`, then the project's `.claude/settings.json` and
  `.claude/settings.local.json`. None set → don't mention it; leave the key out. One set →
  tell the user what it shows, that the plugin's toolbar covers the same, and ask whether to
  hide it inside the plugin. If their status line writes a session summary file, suggest
  `"summary": {"source": "file", ...}` pointing at it at the same time.

## 4. Check it works

Open the Sessions tool window: the list fills (the first scan of a large history runs in the
background). Open a session: the toolbar shows model and effort dropdowns, branch, summary,
your buttons; the bottom bar shows usage, cache and context. Usage bars need the CLI's OAuth
login (macOS keychain item "Claude Code-credentials", or `~/.claude/.credentials.json`).

## Where things live

| Path | What |
|---|---|
| `~/.overclauditor/config.json` | this config |
| `~/.overclauditor/repo/` | the local plugin repository the IDE updates from |
| `~/.overclauditor/session-index/` | saved session-list scan positions (safe to delete) |
| `~/.overclauditor/titles.json` | pinned tab titles |
| `~/.overclauditor/summary/` | built-in summaries |
