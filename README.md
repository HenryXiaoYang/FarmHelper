<p align="center">
  <img src="images/logo.png" alt="FarmHelper logo" width="80" height="80">
</p>

# FarmHelper V3

FarmHelper V3 continues development of the original [FarmHelper V2 by JellyLabScripts](https://github.com/JellyLabScripts/FarmHelper), bringing it to Fabric for **Minecraft Java 26.1.2**.

The farming, auxiliary automation, failsafe, HUD, pathfinding, and remote-control modules are included. This build passes compilation, automated local UI checks, and isolated-world compatibility checks; live Hypixel feature parity has **not** been verified. See [porting and verification notes](PORTING.md).

## Installation

Install these into a Minecraft **26.1.2** Fabric instance:

- Fabric Loader **0.19.5 or later**.
- [Fabric API](https://modrinth.com/mod/fabric-api) **0.155.3+26.1.2** (or a compatible newer 26.1.2 build).
- [Baritone API for Fabric **1.18.0**](https://github.com/cabaletta/baritone/releases/download/v1.18.0/baritone-api-fabric-1.18.0.jar). Use the API jar, not the standalone variant.
- `FarmHelperV3-3.0.0.jar` from `build/libs`.
- [Mod Menu **18.0.1**](https://modrinth.com/mod/modmenu) to open **Mods → FarmHelper V3 → Configure**.

Mod Menu is optional. `/fh`, `/farmhelper`, and the default **F** key also open settings in a world. The default macro toggle is **grave/backtick**. OneConfig and the old Forge JDA dependency are no longer required; integration libraries are bundled.

## Settings and migration

Settings live in `.minecraft/config/farmhelperv3/config.json`. The settings screen uses a category sidebar and a scrolling detail panel, with inline descriptions, search across categories, dependent controls, HUD pages, colors, and keyboard/mouse bindings. Settings are organized into 13 main categories, with startup controls before detailed options. Done saves; Cancel or Escape asks whether to save, discard, or keep editing when there are unsaved changes. Unchanged screens close directly. The same protection covers nested pages and multiline drafts. Backspace/Delete clears a binding.

To position HUDs visually, open **HUD & Overlays → Edit HUD Layout**, or run `/fh hud` in a world. Drag Status, Profit, Usage, and Debug previews to place them; use the mouse wheel to scale and arrow keys to nudge. Hold Shift for free dragging or 10-pixel keyboard steps. Auto arrange separates overlapping HUDs. Done saves (or applies back to settings); leaving with changes offers save/discard confirmation.

On first V3 startup, the mod looks for V2 settings in the default OneConfig profile, the legacy OneConfig config directory, and `config/farmhelper/config.json`. It copies a backup to `config/farmhelperv3/v2-config.backup.json`, converts keycodes/colors/HUD positions, and preserves the originals. Existing V3 settings take precedence.

Rewarp points and plot data move to `config/farmhelperv3`; statistics and custom movement recordings are copied to its `data` directory. Custom recordings belong in `config/farmhelperv3/data/movrec`. A custom `farmhelper_sound.wav` still belongs in the game directory.

## Building

```sh
./gradlew build
```

Output: `build/libs/FarmHelperV3-3.0.0.jar`. The build runs the plain-Java regression checks; `./gradlew portChecks` runs them separately. Use `./gradlew runClient -PsmokeTest` for an isolated development instance under `build/smoke-run`, with remote controls/analytics disabled, mixin auditing, and automatic settings/menu regression checks. With an isolated world named `New World` already present in that instance, add `-PcheckWorld` to exercise inventory packets, flight, crop states, block prediction, and rotation thresholds, then exit automatically. Test code is excluded from the release jar. Add `-PwithoutModMenu` to check the optional integration.

CI builds and uploads the mod artifact.

## Attribution and license

FarmHelper V3 is developed and maintained by **HenryXiaoYang**. It adapts the original FarmHelper V2 by JellyLabScripts and its contributors for Fabric 26.1.2, replacing the legacy platform integration and settings UI.

The project remains licensed under [CC BY-NC-SA 4.0](LICENSE). Bundled third-party libraries retain their own licenses.
