<p align="center">
  <img src="art/cremux-logo.png" width="120" alt="Crémux logo">
</p>

<h1 align="center">Crémux</h1>
<p align="center">Linux on Android, redesigned. A modified build of <a href="https://github.com/termux/termux-app">Termux</a> with a new interface.</p>

## Features
- **Home dashboard**: environment status, quick actions, recent and saved sessions, storage and memory.
- **Terminal**: session chips, command pill, restyled extra keys with a **CMD** key for templates.
- **Command Templates**: ready-made and custom commands with `<placeholder>` prompts.
- **Package Manager**: search, install, remove and update packages.
- **File Manager**: browse Termux home and shared storage.
- **GitHub Sync**: pick a zip or folder, choose or create a repo, push with real `git`. Token stored encrypted in the Android Keystore.
- **Save session**: keep a session's output and folder, restore it later.
- **Themes**: system, light, dark and AMOLED black.

## Build
Push to `main` and GitHub Actions builds the APKs (Actions tab → latest run → Artifacts: `Cremux-arm64-v8a` for most phones, `Cremux-armeabi-v7a` for older 32-bit ones).

## Notes
- Application id stays `com.termux`, so Crémux cannot be installed next to the official Termux, and Termux commands and plugins keep working.
- Crémux is not affiliated with or endorsed by the Termux project.
- The original Termux README is kept in [docs/UPSTREAM_README.md](docs/UPSTREAM_README.md).

## License
Based on Termux, licensed under GPLv3 (see [LICENSE.md](LICENSE.md)). Modifications are released under the same license.
