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

Optional. Without it you get: `plan` `merge` `push` `pull` buttons (the last three are the bundled
`/overclauditor:*` commands), the built-in summary, and the user's status line shown as usual. Read live; edits apply to tabs opened afterwards (buttons, status line) or
within seconds (summary).

```json
{
  "buttons": [
    { "label": "plan", "send": "/plan" },
    { "label": "review", "send": "/review" },
    { "icon": "Actions.Execute", "tooltip": "Run my thing", "keys": "ctrl+x ctrl+r" }
  ],
  "summary": { "source": "builtin" },
  "hideTerminalStatusLine": false,
  "bundledCommands": true
}
```

- **`buttons`** — the session toolbar's command buttons, in order.
  - `label` makes a text button (left of the fork icon, all one width). `icon` makes an icon
    button (after Messages): any `AllIcons` name as `Group.Name`, e.g. `Actions.Copy`,
    `Actions.Execute`, `General.Settings`. `"badge": "*"` adds a short mark to the icon's
    corner, to tell two similar icons apart (e.g. run vs run-this-worktree).
  - `send` types text into the session; a value starting with `/` is a slash command and gets
    Enter appended. `keys` sends key chords instead: `ctrl+<letter>`, `enter`, `esc`, `tab`,
    space-separated for a sequence. Bind a chord to a command in
    `~/.claude/keybindings.json` (`"ctrl+x ctrl+r": "command:<name>"`) to give a button a
    plugin command without typing.
  - Custom slash commands must exist for the user (`~/.claude/commands/*.md` or a plugin).
    Ask the user which of theirs they want as buttons; don't invent any. A `buttons` list
    replaces the defaults, so include `/overclauditor:merge` etc. if they still want those.
  - **Agent: if the user already has their own `/merge`, `/push` or `/pull`**
    (`~/.claude/commands/`), ask whether the buttons should send theirs or the bundled
    `/overclauditor:*` ones.
- **`defaultModel`** / **`defaultEffort`** (e.g. `"opus"`, `"medium"`) — what every NEW session
  starts with (`claude --model --effort`, session-only). The toolbar's dropdowns send `/model` and
  `/effort`, which Claude Code also saves as the default for new sessions; set these so one tab's
  choice doesn't decide the next session's. Resumed sessions and forks keep their own.
  **Agent: ask the user** which model and effort they want new sessions to start on.
- **`bundledCommands`** (default `true`) — load the bundled `/overclauditor:merge`, `:push`,
  `:pull` into plugin sessions (`claude --plugin-dir <IDE system folder>/overclauditor/claude-plugin`).
  Nothing is installed into `~/.claude`. `false` leaves them out.
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

## Where things live, and what uninstalling leaves

The plugin installs nothing outside the IDE and never edits the user's Claude settings: it
passes its status-line capture and hooks to the sessions it starts with `claude --settings
<temp file>`, deleted when the session closes.

| Path | What | On uninstall |
|---|---|---|
| `~/.overclauditor/config.json` | this config, written by the user | kept (theirs to delete) |
| IDE system folder `/overclauditor/` | session-list index, pinned titles, built-in summaries, the bundled commands | deleted |
| temp folder `clauditor-*` | per-session status capture | deleted when sessions close; leftovers swept at startup and on uninstall |

Double-clicking the empty editor tab bar starts a new session; the IDE's own double-click there
(hide/restore all tool windows, registry `editor.maximize.on.double.click`) is switched off while
the plugin is installed and switched back on when it is removed.

Built-in summaries run `claude -p --no-session-persistence` with auto-memory off, so they add
nothing under `~/.claude/projects`. Developer-only (`tools/publish-local.sh`):
`~/.overclauditor/repo/`, the local plugin repository.
