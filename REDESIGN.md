# Termux redesign

New screens (package `com.termux.app.ui`, layouts `res/layout/activity_*.xml`):

| Screen | Class | Notes |
|---|---|---|
| Home (launcher) | `DashboardActivity` | status card, quick actions, recent sessions, bottom nav |
| Terminal | `TermuxActivity` (restyled) | title + drawer, session chips, focus mode, command pill, key row |
| Command Templates | `CommandTemplatesActivity` | category chips, `<placeholder>` prompts, custom templates |
| Package Manager | `PackageManagerActivity` | reads `dpkg/status` + apt lists; Search / Installed / Updates; Update All |
| File Manager | `FileManagerActivity` | virtual `/` root, breadcrumbs, New / Paste / Select / More |
| Settings | `SettingsActivity` | Theme, Blur, Cursor Style, Scrollback, Version, About, Notifications; "All Termux settings" opens the classic tree |

Actions (install, update, run template, ...) are typed into the current terminal session through
`TerminalCommands.run()`, which `TermuxActivity` consumes in `onNewIntent` / `runPendingActions()`.

Theme tokens: `res/values/colors.xml` (light) and `res/values-night/colors.xml` (dark), `tx_*`.
The terminal screen always uses the dark `tt_*` tokens.

## GitHub Sync (Home > Sync)
Pick a zip/folder, pick or create a repo, push with real `git` inside the Termux environment.
Token is stored AES-GCM encrypted via Android Keystore (API 23+); sent to git via env (GIT_CONFIG_*), never written to .git/config or logged.
Needs a classic PAT with `repo` scope. `git` is auto-installed with `pkg install -y git` if missing.

## v4
- Terminal key row: new CMD key (7th slot of row 1) opens Command Templates.
- Home: Command Templates chips, Saved Sessions, System card (storage/RAM/packages), GitHub Sync card.
- Save session: terminal ⋮ menu > Save session, or Home > Sessions Manage (long-press) / Saved Sessions > Save +.
  Stored in ~/.cremux/sessions (output .txt + .json with folder). Restore opens a new session in the saved folder.

## Branding
Crémux "C" + prompt icon (black/white), monochrome accent (white on dark, black on light), grouped Home lists.
Package name, env vars and commands (termux-*, pkg, TERMUX_*) unchanged.
