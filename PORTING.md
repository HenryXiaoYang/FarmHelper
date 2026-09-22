# FarmHelper V3 port and verification

## Platform changes

- Java 25, Gradle 9.5.0, unobfuscated Fabric Loom 1.17.21, Fabric Loader 0.19.5, and Fabric API 0.155.3+26.1.2.
- Mod identity, packages, assets, metadata, and artifact use `farmhelperv3` / FarmHelper V3. Forge, LaunchWrapper, core transformers, legacy remapping, and OneConfig are removed.
- Fabric lifecycle/message/render events and targeted mixins feed the existing feature state machines. Internal event dispatch preserves priority and cancellation, and avoids duplicate registrations.
- Startup opens the Minecraft main menu directly, with no FarmHelper welcome or typed confirmation. Authentication retries and ban-report authentication run in the background to keep menu input responsive.
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

- `./gradlew build` and `./gradlew portChecks`: all 104 string-based boolean setting dependencies resolve to public boolean fields (including the renamed Anti Stuck switch); stalled background I/O does not block the scheduler; event priority, cancellation, duplicate registration, unregistration; legacy keyboard/mouse codes; color/alpha/chroma conversion; HUD anchor conversion; non-mutating migration; malformed-color rejection; incompatible update rejection.
- Minecraft 26.1.2 development client launched on macOS arm64 / JDK 25. Mixin audit force-loads remaining targets without mod injection errors.
- Verified direct startup to the main menu without a FarmHelper welcome screen; clicked Options and Mods successfully.
- Opened Mod Menu, selected FarmHelper V3, and opened Configure.
- Created and entered an isolated local world; rendered the status HUD.
- Launched without Mod Menu, opened settings using both F and `/fh`, searched settings, changed a HUD preference, and verified the saved JSON value.
- Confirmed F opens settings without swapping the held item into the offhand.
- Saved and exited the local world successfully.
- Inspected the packaged jar for V3 identity, metadata, nested runtime dependencies, and all 51 bundled movement recordings.

## Still requiring live validation

A successful local client launch does not establish Hypixel feature parity. No authenticated Hypixel session, real farming route, economic transaction, Discord bot connection, authenticated proxy, analytics exchange, or released V3 update was exercised. Windows/Linux-specific window, notification, and audio behavior was not tested. Treat this as a port build pending those checks.

## Compatibility repair (2026-09-19)

The earlier compile-only review missed behavioral differences. This repair was checked against the generated **26.1.2 client sources**, including `PacketUtils`, `PacketProcessor`, `ClientPacketListener`, `InventoryMenu`, `MultiPlayerGameMode`, `LivingEntity`, and `Player`. Toolchain requirements were cross-checked with [Fabric's official 26.1 migration notes](https://www.fabricmc.net/2026/03/14/261.html).

| Area | Repair | Evidence |
| --- | --- | --- |
| Initialization / input | Thin bootstrap checks the Baritone API before loading the heavy entrypoint. Input, render, proxy and event hooks stay inactive after failed initialization; Mod Menu displays the initialization error. GUI key events still work without a player. | Native MouseHandler clicks on Options, multiplayer Back and disconnected Back with `ready` true and false; disconnected-screen key press/release check. This simulates inactive hooks, not Lunar's entire loader. |
| Settings | Remove welcome/confirmation; fix Anti Stuck field dependency and an uninitialized text default. | All 104 string dependencies checked; 590 settings evaluated; 662 Next clicks, including extra clicks at the last page, through native screens. |
| Packet order | Dispatch on return from the client-thread packet handoff, immediately before vanilla applies changes. Bundle children are dispatched individually. Disconnect has its own handoff because vanilla does not use PacketUtils for it. | Synthetic single and bundled packets: exactly one callback per child and the old inventory still visible. Separate network-thread scheduling check. |
| Inventories / items | Map menu slots through the actual Slot container/index; support direct inventory, slot and full-content packets. Ignore chest-only and stale-menu slots; mark full snapshots to avoid false income. Include the ninth hotbar slot; query boots by equipment slot. Use undecorated hover names. | Direct slot 8, inventory-menu slot 44, chest slot 62, chest-only slots, stale menu IDs and full 36-slot snapshots. |
| Counters / text | Read modern custom_data; preserve same-name tool identity using UUID, with ID/name fallback; support mined_crops counters. Preserve styled visitor colors and native tab order/listing, handle removals/team changes, clear stale tab data, and parse NPC chat without requiring embedded section codes. | Styled-component regression; same-name UUID distinction; custom_data counter fallback. Actual Hypixel text/menu samples remain unverified. |
| Movement | Replace the general 1.8.9 simulator with a flying-only stopping predictor using current collision shapes, combined horizontal cutoff, vertical cutoff, drag and flight permission. Use ground contact instead of exact legacy gravity velocity. Bound path search to world height and floor negative block coordinates. | Six native Player.travel comparisons: horizontal, vertical/diagonal and low diagonal speed, each with/without a solid obstacle. Negative-coordinate bounds check. |
| Breaking / crop shapes | Plot cleaning uses native destruction. Pingless cactus changes destroy progress inside vanilla's prediction scope; no AIR mutation before prediction. Emit successful destruction once. Disabled hitbox options preserve vanilla shapes; crop subclasses use their own age property. | Cactus START packet has a positive prediction sequence, block becomes AIR, exactly one click/destruction event. Iterate all registered CropBlock states, including beetroot. |
| Failsafe numbers / state | Wrap yaw difference; use separate yaw and pitch thresholds; use long timestamps and reset lag samples on join; convert seconds to ticks correctly. Preserve the documented V2 knockback configuration unit. Reset stale Baritone calculation status for new requests. | Rotation wraparound and independent threshold checks; source review for time and knockback units. |

Commands used:

```sh
./gradlew build
./gradlew runClient -PsmokeTest -PcheckWorld
./gradlew runClient -PsmokeTest -PcheckWorld -PwithoutModMenu
```

`ClientChecks` is a test-only Fabric entrypoint. It uses the isolated `build/smoke-run/saves/New World`, restores its temporary inventory/block/player changes, and shuts down normally after the world checks. It is not included in the release jar. The development world must exist before using `-PcheckWorld`.

These checks repair and verify the shared compatibility paths; they do not establish every live Hypixel state-machine branch. The stopping predictor handles no-input ability flight and returns control on landing; it is not a general ground/fluid simulator, and cobwebs, unusual block effects, mounts and live server corrections are outside the comparisons above. Original movement recordings still replay inputs under the current game's physics, so their exact V2 spatial trajectories are not promised. Live Garden routes, server menus, economic transactions and external integrations still need authenticated validation as listed above.

## Settings layout (2026-09-19)

- Replace Previous/Next pagination with a category sidebar and independent native scrolling lists. The right pane groups settings by subsection, shows inline descriptions and compact values, and uses a dark translucent background. Search spans the current page's categories.
- Preserve Mod Menu, F and `/fh` entrypoints, dependencies, key binding capture, numeric validation, nested HUD/color pages and masked credentials. Full values remain available in tooltips and narration.
- Done saves the root screen; nested Done returns to its parent. Cancel/Escape restores the edited settings, including key bindings and nested changes. Invalid drafts survive resizing and prevent saving until corrected; Cancel still exits.
- Client regression traverses 590 settings and 45 category groups (including nested pages), exercises scrolling and empty search results, and checks cancellation, a 320×240 resize with invalid input, keybinding rollback and Done persistence. Viewed the actual game window to check layout, clipping and contrast.
- `./gradlew runClient -PsmokeTest -PsettingsPreview` leaves the new screen open after the UI checks for visual inspection.

## Farming tool detection repair (version remains 3.0.0)

- Read Hypixel data from the modern `minecraft:custom_data` root, falling back to the legacy `ExtraAttributes` wrapper. ID, UUID, inventory searches and cultivating counters share this reader. Verified the root schema against [Skyblocker's ItemUtils](https://github.com/SkyblockerMod/Skyblocker/blob/master/src/main/java/de/hysky/skyblocker/utils/ItemUtils.java).
- Match crop-specific tool families by internal ID, including all Mk. I–III tiers, Eclipse (Sunflower and Moonflower), Wild Rose and the existing crop tools. Display-name changes such as Hoe → Sickle/Cutter do not affect detection. Cactus prediction also uses the ID. Prefer a crop-specific tool over a general gardening tool, retain the held tool on equal matches, and inspect all nine hotbar slots.
- General farming tools remain fallbacks; tilling hoes, mathematical blueprints, ordinary axes and the consumable Farming Toolkit are not mistaken for harvesting tools. Tools withdrawn from the toolkit are matched by their normal item ID. Tool XP (`levelable_exp`) is never counted as harvested crops.
- Regression fixtures capture 36 current tool definitions from [NEU item repository commit 9dce36f](https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/tree/9dce36fde21a40e7c0e2679412626e315fee28fc/items); exercise each with modern and legacy data, plus hotbar priority, both Eclipse crops, Wild Rose, UUID/counters and negative examples. These are local fixture checks, not an authenticated Hypixel session.

## Continuous harvesting with a released cursor (version remains 3.0.0)

- Reproduced the user's single-crop stop with `autoUngrabMouse` enabled and Fast Break disabled: the first queued click harvested a carrot, but the second held-attack tick did nothing. Vanilla `Minecraft.handleKeybinds` gates `continueAttack` on `MouseHandler.isMouseGrabbed()`.
- Relax only that call site's capture check while the macro is toggled and FarmHelper has intentionally released the cursor. Keep vanilla's held-key, screen, instant-attack and item-use checks. Do not change the low-BPS failsafe or globally pretend the cursor is captured.
- The regression failed before the fix on crop 2 and passed afterward on three consecutive crops. It also verifies that releasing attack or opening settings stops harvesting and that ordinary cursor release keeps vanilla behavior. The check runs through the real `handleKeybinds` input path in the isolated local world.

## Freelook harvesting transition (version remains 3.0.0)

- Reproduced a stop when entering Freelook from FarmHelper's released-cursor state. Vanilla `MouseHandler.grabMouse()` sets `missTime = 10000`; outside macOS it also resets held keys to their physical states.
- `UngrabMouse.regrabMouse()` now preserves the existing cooldown and held automation keys when intentionally recapturing during an active macro with no screen open. It does not globally clear attack cooldowns or change menu capture behavior.
- The client regression failed before the fix at “Continue harvesting after entering Freelook”. After the fix, two enter/rotate/exit cycles harvest continuously, the camera turns without changing player rotation, and release/menu stop checks still pass. The synchronous test restores its temporary window-focus fixture after running.

## World overlays: ESP, tracers, plot/path markers (version remains 3.0.0)

- All RenderUtils world APIs now accept absolute world coordinates. Updated pest boxes, plot bounds, vacuum debug markers and path lines together; entity ESP/text/tracer endpoints use the frame's interpolated entity position.
- Extraction stores an immutable list of native geometry descriptions on Fabric's per-frame `LevelRenderState`. Drawing consumes that snapshot after translucent terrain. Geometry still uses Minecraft's native cuboid builder and near-plane line clipping.
- Dedicated translucent box/line pipelines disable depth testing and writes; world text uses the native see-through font pipeline. FarmHelper no longer submits always-on-top debug gizmos, whose vanilla late-debug pass clears world depth. Translucent boxes sort on upload.
- Tracers start 0.25 blocks ahead of the current camera, yielding an on-screen line in first person and following the camera in third person/Freelook. The old eye-to-target line collapsed under first-person perspective. Labels preserve V2's distance-adjusted text size.
- `RenderChecks` renders temporary markers behind an opaque wall, checks frame coordinates/scale and pipeline depth state, writes three PNGs (`build/porting/overlay-0.png` through `overlay-2.png`), and checks that both overlay colors reached the framebuffer. First person, third person and Freelook were inspected.
- Passed the full local client checks with vanilla rendering, then with the installed Sodium 0.9.1 and Iris 1.11.3 jars (Minecraft 26.1.2, no shader pack selected). The temporary test jars were removed afterward. This does not certify every shader pack or Lunar-specific modification.
- API reference: [Fabric 26.1.2 world rendering](https://github.com/FabricMC/fabric-docs/blob/main/versions/26.1.2/develop/rendering/world.md).

## Pest activation and instructions (version remains 3.0.0)

- Normalize the resource-pack pest glyph U+E07F (observed in the user's live log) to the legacy pest marker before scoreboard filtering. Previously it was removed, so the count parser could treat a populated Garden as having zero pests. Test both glyphs through the real cleaning/counting chain.
- Manual button and hotkey now share one startup path and report blocked conditions (disabled switch, wrong location, failsafe, zero detected pests, or no flight permission) instead of silently doing nothing. The AFK tick uses the same conditions without spamming manual messages.
- Clarify the settings: normal farming checks the configured pest threshold at its feature/rewarp checks; idle AFK mode can start when at least one pest is detected; manual Trigger now / configured hotkey starts a single run. The configured vacuum must be in the hotbar and flight must be available.
- Local checks cover modern/legacy counters and manual start conditions. No live pest-clearing run was started for this test, and the user's automation settings were not changed.

## Tab pest-count source (version remains 3.0.0)

- User screenshots showed `Pests: / Alive: 7 / Plots: 2, 7`, while the mod's P-key startup check reported zero. The prior glyph fix covered only sidebar parsing and missed the Tab `Alive` source.
- Parse `Alive` only within the Pests Tab section. Its explicit value, including zero, takes priority over the optional sidebar counter. Read spaced or compact comma-separated plot lists. Reset both the selected count source and plot state on world changes.
- Regression replaying the screenshot failed before the fix and passed afterward: 7 pests in plots 2 and 7, manual startup allowed, sidebar updates cannot erase the Tab count, unrelated Alive lines are ignored, explicit zero wins over stale sidebar data, and legacy fallback works when the widget is absent. The complete local client check suite passed.

## Settings exit confirmation and organization (version remains 3.0.0)

- Changed settings now prompt on Cancel/Escape: Save and exit, Discard changes, or Keep editing. Unchanged screens close directly. Dialog Escape returns to editing. Invalid drafts disable saving while still allowing correction or discard. Save failures return to the editor without discarding changes. English and Simplified Chinese dialog text is included.
- Nested HUD/color settings and multiline text have the same confirmation behavior; nested Apply returns to the parent without writing the root configuration. Discarding the root restores nested edits too. Multiline drafts survive resizing.
- Consolidated the sidebar into 13 main categories: Farming, Controls, Pests, Visitors, Automation, Scheduler & Contests, Failsafes, HUD & Overlays, Performance, Integrations, Privacy, Advanced, Debug. Moved ESP/tracer colors and markers into HUD & Overlays, rendering/FPS into Performance, privacy switches into Privacy, and auxiliary automation into Automation.
- Explicit per-setting order puts feature switches and manual starts first. Sections stay contiguous; help, detailed options and notification settings follow. Persisted field names, configured values, bindings and version are unchanged.
- Checks cover all 590 entries, 39 groups including nested pages, no missing/duplicate settings, Pest startup ordering, save/discard/keep-editing, clean exit, invalid values, keybinding rollback, nested edits and multiline drafts. Chinese confirmation was rendered and inspected in the isolated client (`build/porting/settings-exit-confirmation.png`).

## Visual HUD layout editor (version remains 3.0.0)

- Added a draggable editor for Status, Profit, Usage, and Debug HUDs, accessible from HUD & Overlays, individual HUD settings, and `/fh hud` / `/farmhelper hud`. It works with no world loaded and shows stable sample previews even for disabled or context-hidden HUDs.
- Supports screen-edge/center snapping, Shift to bypass snapping, one-pixel arrow nudging (Shift: ten pixels), wheel scaling, visibility preference and auto-arrange. The selectors make overlapping HUDs reachable. Existing x/y/scale/anchor fields are reused; dragging preserves anchors and auto-arrange chooses corner anchors.
- Preview and live rendering share the same bounds/placement calculation. The normal FarmHelper HUD pass is suppressed while editing to avoid duplicates, and empty HUD content no longer draws a stray background rectangle.
- Save/discard confirmation reuses the settings dialog. Nested Apply participates in the parent's save/discard transaction; direct Save writes the layout atomically through NativeConfig.
- Local checks cover all nine anchors, disabled previews, dragging offsets, edge clamping/snapping, keyboard movement, scale limits, auto-arrange, the settings entrypoint, saved JSON values, direct discard and parent rollback. An isolated-world preview is captured with `./gradlew runClient -PsmokeTest -PcheckWorld -PsettingsPreview=hud` to `build/porting/hud-editor-preview.png`.

## Opt-in Bazaar sell orders (3.0.2)

- Added Use Bazaar Sell Orders under Automation / Auto Sell, default off and available with the BZ market. Farming and custom-filter products are grouped by SkyBlock ID; tools, equipment, and unique items are excluded. Current flower crops are recognized without changing the existing NPC crop list. Eligible enchanted farming-sack contents are withdrawn and listed in batches.
- Uses the native Same as Best Offer choice and checks the final product, exact quantity, and unit price before submitting. Public `/v2/skyblock/bazaar` data supplies product membership and best-offer comparisons; requests are asynchronous, bounded, and require no credentials. Unavailable/stale data aborts safely. No order failure falls back to an instant sale or an NPC crop sale.
- Confirmed Garden spawn returns trigger one management pass before resuming farming; initial startup and ordinary resumes do not. Only unique orders confirmed as created in the current world session are tracked. Manual Bazaar input or world unload drops ownership. Completed orders are claimed; undercut orders are cancelled and only the observed returned quantity is relisted. Repricing has no price-drop floor. Failures retain items, release Auto Sell's pause, and back off automatic attempts for five minutes; manual activation bypasses the backoff.
- Order-menu parsing follows the published fields in [SkyHanni's BazaarOrderApi](https://github.com/hannibal002/SkyHanni/blob/0913ae5c96fc52d03aa41ad1a8369c8643e6dc63/src/main/java/at/hannibal2/skyhanni/features/inventory/bazaar/BazaarOrderApi.kt). Rounded fill counts are never used as exact cancellation quantities. Missing fields, duplicate identities, unexpected menus, and unsupported pagination are not acted on.
- Plain-Java and isolated-client fixtures cover exact confirmation checks, duplicate ownership, partial cancellation/claim reconciliation, delayed responses without repeat confirmations, tool/equipment exclusions, sack batching, spawn-return consumption, default instant/NPC routing, and failure cooldown. The isolated test server supplies no-op `/bz` and `/managebazaarorders` commands; these are simulated server menus, not verification of live Hypixel transactions. Current live menus and a full transaction still need validation before asserting live-server compatibility.

### Bazaar-Utils source cross-check

Compared against [mkram17/Bazaar-Utils at 4ed4679](https://github.com/mkram17/Bazaar-Utils/tree/4ed4679885f81227d4f8ba5fe6ae2201aa281952), particularly `BazaarScreenType`, `BazaarSlots`, `TransactionPageLayout`, `SlotRendererProvider`, and `OrderUtil`.

- Corrected the missing **How many are you selling?** stage: select a matching native quantity preset, or **Custom Amount**, then validate the sign's **Enter amount / to sell** prompt. Product-page clicks are handled according to the returned screen; whole-inventory offers may proceed directly to price, while partial relists request a custom quantity with right-click.
- Product detection uses the product display and Create Sell Offer control, supporting category breadcrumbs/truncated titles. Order lists open with `/managebazaarorders` and accept the documented Bazaar Orders title variants.
- Sell cancellation uses the verified sell control (green terracotta, slot 13, item-refund lore); the dialog need not repeat the product name. Buy-order cancel controls are not accepted. The reference's live sell-confirmation matcher uses slot 13 while its preview uses slot 12, so either is accepted only when unique and the transaction details match exactly.
- Fixtures now use the reference's native button positions/types and quantity-screen sequence, including actual sign-update packets, custom partial relists with pre-existing inventory, and rejection of price/buy sign prompts. The public price-side mapping agrees with Bazaar-Utils: sell offers use the instant-buy side of the order book. No dependency on Bazaar-Utils was added; its chat-message suppression cannot bypass the inventory/order reconciliation.
- This validates the implementation against published source and local simulations. It is not a claim that a live Hypixel transaction or a co-loaded Bazaar-Utils client has been exercised.

## Harvesting after interruptions (3.0.2)

- The user's log shows pest cleanup returning to spawn and restoring the farming state. A local replay of the shared pause/resume path reproduced the failure: native mouse capture left `Minecraft.missTime` at 10000, suppressing block attacks even after the state machine held attack again.
- Clear the capture/menu-dismissal suppression when the shared macro base is enabled, covering initial startup and every resume caller. Route explicit macro captures through UngrabMouse so its capture flag stays synchronized, preserve synthetic held input during recapture, and restore intentional cursor release on resume unless Freelook is active. Intentional release also works when the window is already uncaptured/unfocused.
- Regression uses the actual vertical farming state, pause/resume methods and native input handling. It checks released input while paused, restored state, captured/released/unfocused cursor cases, successive crop breaks without a physical click, and redundant resume preserving ordinary attack cooldowns. Existing menu, Freelook and rendering checks remain in the client suite.

## Pumpkin regrowth false positives (3.0.2)

- The user's archived September 20 log reports `minecraft:carved_pumpkin` as a newly placed obstacle. Modern `PumpkinBlock` does not include `CarvedPumpkinBlock`, so the old instanceof check missed this farm-pumpkin representation. Replaying air → carved pumpkin reproduced DirtFailsafe recording it as an obstruction.
- Share `CropUtils.isPumpkin` across crop classification/readiness, initial/mouse-over crop detection, row/obstruction decisions, BPS counting and desync tracking. Recognize precisely ordinary and carved pumpkins, excluding jack-o-lanterns; include carved pumpkins in the existing crop-render whitelist.
- Regression passes for ordinary pumpkins and all four carved-pumpkin facings: regrowth creates no dirt-check candidate, crop selection identifies pumpkin, and harvests increment BPS. Dirt, stone and jack-o-lantern placement still produce obstruction candidates. Build and the full isolated-client suite pass.

## GUI-only harvesting interruption (post-release 3.0.2 fix)

- The remaining report occurred with the tested 3.0.2 classes installed: movement continued, and manually holding attack did not recover harvesting. The previous regression covered explicit macro pause/resume but missed opening a GUI while the macro stayed enabled.
- Minecraft writes `missTime = 10000` on every GUI tick. Closing that GUI can resume held movement/attack without calling the macro's `onEnable`, leaving block breaking suppressed. A replay reproduced this separately from mouse recapture.
- At native input processing, clear only suppression above the ordinary 10-tick missed-swing cooldown when the main farming macro is active, attack is already held, no screen is open, and no auxiliary feature or failsafe owns control. This does not synthesize additional clicks or remove normal attack cooldowns.
- Checks cover GUI close without pause/resume, normal cooldown preservation, inactive/auxiliary/failsafe guards, and multi-tick ordinary/carved pumpkin harvesting. Live Lunar validation still follows installing the new CI artifact and restarting.

## Auto Sell AMOUNT timeout (post-release 3.0.2 fix)

- September 21 screenshots and logs show Auto Sell repeatedly timing out in AMOUNT with crops left in inventory. The implementation assumed every Create Sell Offer click opened the quantity menu. A regression replay with a direct transition to the price screen failed before the fix.
- Accept the quantity menu, a validated sell-amount sign, or the price screen after creating an offer. Whole-inventory listings use left-click; partial relists request custom quantity with right-click. All routes still require exact product/quantity/unit-price confirmation, so a partial relist cannot silently sell the entire inventory.
- Timeout messages now include the actual screen title. Fixtures cover direct-price listings, quantity presets, custom partial relists, direct quantity signs, wrong sign prompts, and unchanged confirmation guards. Live server execution remains to be verified after installation.

## Remove Auto Sell price checks (post-release 3.0.2 change)

- Removed the HTTP client, external Bazaar price request, PRICES wait state, cached market data and price comparisons from Auto Sell. Starting an order sale immediately sends `/managebazaarorders`; eligible products are selected from inventory and verified through the game menus. The native Same as Best Offer control supplies the sale price.
- Removed automatic cancellation/repricing along with its market-price dependency. Spawn-return management only claims completed orders confidently owned by this session. Unfilled/manual orders are left alone.
- Product and quantity checks remain. The confirmation/order UI price is read only as part of an order's identity; it is not compared with a quote or an external price. Unknown custom items cannot fall back to NPC selling merely because API data is absent; only an explicit native Bazaar rejection can mark them unavailable for listing.
- Automatic inventory-triggered sales wait for rewarps to finish before opening menus. Regression checks cover immediate command dispatch without a price request, successful GUI-only listing, retained unfilled orders, completed-order claims, and product-classification guards.

## Native bulk coin claim (post-release 3.0.2 change)

- Replaced per-order claiming with the native bulk coin-claim control. Removed the session-owned-order queue and its spawn-return gate, so claiming also works for manual orders, older orders and available proceeds from partial fills.
- Check the order menu first, then the Bazaar overview if needed. Match a dedicated coin-claim label, or a generic claim-all label with sell-coin context; item/mixed buy-order claim controls are excluded. Send one left-click, allow a short GUI settling delay, then resume. No cancellation, repricing or external price request is involved.
- Claiming is a single native request, not repeated clicks or a guarantee that every server-side credit has completed. Missing/ambiguous controls are reported and left untouched. Fixtures cover order/overview layouts, no session ledger, empty balances, item-claim exclusion and no repeated request while responses arrive.
- The feature exists in Hypixel's [0.20.5 release notes](https://hypixel.net/threads/hypixel-skyblock-0-20-5-artist%E2%80%99s-abode.5738722/). Live button interaction was not exercised: the game window closed before menu inspection could proceed.

## Sale triggers, pre/post claims, and occupied order slots (post-release 3.0.2 fix)

- The September 22 report includes a native maximum-of-21-orders rejection. Logs show earlier successful bulk claims on spawn returns, but later inventory-triggered sale runs posted orders without first claiming and did not handle the limit response until a timeout.
- Every order-selling pass now claims first, waits for a native Bazaar acknowledgement, refreshes the order list, lists inventory, and claims again before completing. NPC/instant-sale successful completion also enters the bulk claim flow; cancellation/failure does not schedule extra trading. Requests are not blindly considered complete after a fixed half-second delay.
- Learn capacity from the server's limit message rather than hardcoding 21. A rejected listing permits one claim/refresh recovery and a retry only when the fresh list has room. Count both own buy and sell orders. If still full, retain items and apply the existing five-minute backoff, without cancelling orders, repricing, or falling back to instant selling.
- Automatic triggers are confirmed spawn return and the existing inventory-full threshold/time. When automatic pest clearing is enabled and pests remain, spawn selling is deferred until a clear return; pest handling retains priority. Shared automatic start requests from visitors/composter/full-inventory recovery are gated on actual inventory fullness. Manual commands/buttons remain explicit overrides. Corrected fullness counting to use the 36 inventory/hotbar slots, excluding equipment.
- Client checks cover pre/post claims, delayed acknowledgements, both trigger settings, pest-before-sale ordering, full inventory independent of spawn, NPC completion, the 21-slot rejection with buy orders, freed-slot recovery, and bounded retries. Local build and the complete isolated-client suite passed.
