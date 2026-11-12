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
