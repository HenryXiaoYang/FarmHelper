# FarmHelper V3 port and verification

## Platform changes

- Java 25, Gradle 9.5.0, unobfuscated Fabric Loom 1.17.21, Fabric Loader 0.19.5, and Fabric API 0.155.3+26.1.2.
- Mod identity, packages, assets, metadata, and artifact use `farmhelperv3` / FarmHelper V3. Forge, LaunchWrapper, core transformers, legacy remapping, and OneConfig are removed.
- Fabric lifecycle/message/render events and targeted mixins feed the existing feature state machines. Internal event dispatch preserves priority and cancellation, and avoids duplicate registrations.
- Native settings and HUD rendering replace OneConfig. Mod Menu opens the same settings screen as `/fh` and F. V2 settings are copied and converted; originals remain intact.
- Modern item components, container input, sign editing, packet payloads, relative position corrections, GLFW input/window controls, and client-thread game actions replace their 1.8.9 equivalents.
- Walking uses Baritone's public goal API. Flying retains the six-neighbor search and existing movement/follow/smoothing logic, with a cancellable client-thread search budget of 2 ms per tick, a 10-second timeout, and a 65,536-node ceiling. It never uses `Thread.stop()` or reads the live world on a worker thread.
- Screenshot capture is asynchronous. Discord dependencies are bundled, proxy connections use Netty HTTP/SOCKS5 handlers, and network/file work uses background tasks.
- Brightness uses the maximum supported vanilla gamma setting instead of the out-of-range 1.8.9 gamma value. The native settings/HUD appearance differs from OneConfig.
- The updater retains the upstream release feed and filters for V3 jars. It validates mod identity, declared version, and exact Minecraft target before installing. The current feed may contain no compatible V3 releases.

## Feature coverage

No legacy source set is excluded from the build to obtain a passing compile. Existing feature logic is migrated in place; obsolete platform hooks are replaced rather than packaged alongside Forge.

| Area | Included behavior | Verification boundary |
| --- | --- | --- |
| Farming | All macro variants, rewarps, crop selection, tools, rotation, input, crop hitboxes, fast break | Compiled; live farming cycles unverified |
| Auxiliary automation | Visitors, pests, pest exchange, cookies, potions, repellent, bazaar/selling, composter, wardrobe/equipment, pet swapping, plot cleaning, sprayer, Rancher speed | Compiled; live server menus and item parsing unverified |
| Failsafes | Existing detection/reaction modules, recorded reactions, restart, reconnect, scheduler, notifications, audio, clips | Compiled; actual server interventions/recovery unverified |
| Pathfinding | Baritone walking and FarmHelper flying/following/teleport movement | Compiled; real Garden navigation and stopping behavior need validation |
| Display/settings | Status, profit, debug, usage stats, settings, HUD controls, freelook, mouse ungrab, PiP, performance mode | Local settings/status HUD verified; remaining display and OS behaviors need validation |
| Integrations | Discord/WebSocket commands, screenshots, proxy, analytics, updater | Compiled and packaged; live service behavior unverified |

## Checks performed

- `./gradlew build` and `./gradlew portChecks`: event priority, cancellation, duplicate registration, unregistration; legacy keyboard/mouse codes; color/alpha/chroma conversion; HUD anchor conversion; non-mutating migration; malformed-color rejection; incompatible update rejection.
- Minecraft 26.1.2 development client launched on macOS arm64 / JDK 25. Mixin audit force-loads remaining targets without mod injection errors.
- Opened Mod Menu, selected FarmHelper V3, and opened Configure.
- Created and entered an isolated local world; rendered the status HUD.
- Launched without Mod Menu, opened settings using both F and `/fh`, searched settings, changed a HUD preference, and verified the saved JSON value.
- Confirmed F opens settings without swapping the held item into the offhand.
- Saved and exited the local world successfully.
- Inspected the packaged jar for V3 identity, metadata, nested runtime dependencies, and all 51 bundled movement recordings.

## Still requiring live validation

A successful local client launch does not establish Hypixel feature parity. No authenticated Hypixel session, real farming route, economic transaction, Discord bot connection, authenticated proxy, analytics exchange, or released V3 update was exercised. Windows/Linux-specific window, notification, and audio behavior was not tested. Treat this as a port build pending those checks.
