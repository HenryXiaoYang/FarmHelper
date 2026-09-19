# FarmHelper V3

Fabric port of [JellyLabScripts/FarmHelper V2](https://github.com/JellyLabScripts/FarmHelper), targeting **Minecraft Java 26.1.2** and **Java 25**. Mod ID: `farmhelperv3`; version: `3.0.0`.

The farming, auxiliary automation, failsafe, HUD, pathfinding, and remote-control modules are included. This build passes compilation and local startup/UI checks; live Hypixel feature parity has **not** been verified. See [porting and verification notes](PORTING.md).

## Installation

Install these into a Minecraft **26.1.2** Fabric instance using Java 25:

- Fabric Loader **0.19.5 or later**.
- [Fabric API](https://modrinth.com/mod/fabric-api) **0.155.3+26.1.2** (or a compatible newer 26.1.2 build).
- [Baritone API for Fabric **1.18.0**](https://github.com/cabaletta/baritone/releases/download/v1.18.0/baritone-api-fabric-1.18.0.jar). Use the API jar, not the standalone variant.
- `FarmHelperV3-3.0.0.jar` from `build/libs`.
- [Mod Menu **18.0.1**](https://modrinth.com/mod/modmenu) to open **Mods → FarmHelper V3 → Configure**.

Mod Menu is optional. `/fh`, `/farmhelper`, and the default **F** key also open settings in a world. The default macro toggle is **grave/backtick**. OneConfig and the old Forge JDA dependency are no longer required; Discord/WebSocket libraries are bundled.

## Settings and migration

Settings live in `.minecraft/config/farmhelperv3/config.json`. The settings screen supports search, dependent controls, HUD pages, colors, and keyboard/mouse bindings. Backspace/Delete clears a binding.

On first V3 startup, the mod looks for V2 settings in the default OneConfig profile, the legacy OneConfig config directory, and `config/farmhelper/config.json`. It copies a backup to `config/farmhelperv3/v2-config.backup.json`, converts keycodes/colors/HUD positions, and preserves the originals. Existing V3 settings take precedence.

Rewarp points and plot data move to `config/farmhelperv3`; statistics and custom movement recordings are copied to its `data` directory. Custom recordings belong in `config/farmhelperv3/data/movrec`. A custom `farmhelper_sound.wav` still belongs in the game directory.

## Building

```sh
./gradlew build
```

Output: `build/libs/FarmHelperV3-3.0.0.jar`. The build runs the plain-Java regression checks; `./gradlew portChecks` runs them separately. Use `./gradlew runClient -PsmokeTest` for an isolated development instance under `build/smoke-run`, with remote controls/analytics disabled and mixin auditing enabled. Add `-PwithoutModMenu` to check the optional integration.

CI builds and uploads an artifact without publishing releases or sending Discord messages.

## Attribution and license

Original FarmHelper V2 by JellyLabScripts and its contributors. V3 adapts the code and resources for Fabric 26.1.2 and replaces the legacy platform integration and settings UI.

The project remains licensed under [CC BY-NC-SA 4.0](LICENSE). Bundled third-party libraries retain their own licenses.
