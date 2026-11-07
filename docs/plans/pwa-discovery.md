# Installed web apps in Home and the app drawer

## Goal

Show installed Progressive Web Apps in the app drawer and automatically place newly discovered installed web apps on Home, without duplicates or changes to existing icon positions. Include both Android-packaged web apps and browser-provided web shortcuts accepted by this launcher.

## Source baseline

Verified against `83ac857d36636ea13bbb605f0cf2bab28ff8a3ac` on `main`.

- `src/main/kotlin/io/github/gastoncampana/launcher/Launcher.kt:337`: `loadApps` lists only `LauncherActivityInfo` entries. Browser shortcuts are not drawer entries.
- `src/main/kotlin/io/github/gastoncampana/launcher/Launcher.kt:202`: package callbacks reload apps, but no shortcut callback synchronizes them.
- `src/main/kotlin/io/github/gastoncampana/launcher/Launcher.kt:142`: `addShortcut` pins an accepted shortcut and places it on Home only. A full desktop rejects it.
- `src/main/kotlin/io/github/gastoncampana/launcher/Launcher.kt:944`: `PinActivity` validates the system request and asks for confirmation.
- `src/main/kotlin/io/github/gastoncampana/launcher/Model.kt:154`: `placed` finds room without moving existing items and can append a desktop page.
- `src/main/kotlin/io/github/gastoncampana/launcher/Ui.kt:1030`: the drawer consumes Android app entries, including their app-specific menus and drag targets.

## Approach

1. Characterize existing discovery and placement behavior with unit tests. Introduce pure discovery/identity and reconciliation rules before platform integration.
2. Recognize installed Android web app packages from verified platform metadata, not display labels alone. Keep ordinary Android apps unchanged. Query pinned web shortcuts accessible to this launcher while it holds the Home role. Validate the web identity against the platform contract and distinguish them from ordinary chat/action shortcuts.
3. Build drawer entries with stable identities and correct `Target` values. Web shortcuts open their own identity, never the browser's main activity. Keep alphabetical sorting, search, dragging, tabs and visibility consistent without app-only actions on shortcut entries.
4. Reconcile initial discovery, package changes, accepted pin requests, shortcut callbacks and return to Home. Keep transient query failures separate from a verified empty result. Persist discovered identities so removing an icon intentionally does not cause it to reappear on every refresh. Preserve existing layout and backup compatibility.
5. Use shared placement logic for newly discovered web apps and accepted shortcuts. Deduplicate existing desktop/dock entries, preserve positions, use free space and append a page only when required. Existing web app entries missing from Home are added once on initial discovery.
6. Retain explicit confirmation for externally supplied pin requests. Do not enable network permission or expand shortcut access beyond Android's Home-role permissions.
7. Update the feature guide in both locales and relevant README/architecture documentation.

## Decisions and boundaries

| Topic | Decision |
| --- | --- |
| Packaged PWA and web shortcuts | Both supported when Android exposes their identities to this launcher. |
| Other launchers' private shortcuts | Do not claim recovery. The user may need to add them again from the browser. |
| Pin confirmation | Preserve system-request validation and explicit confirmation. |
| Ordinary app installs | Do not automatically clutter Home with unrelated native apps. |
| Duplicate identity | Use platform identity, not title, URL guesswork or browser package alone. |
| Intentional removal | Do not reinsert an icon on subsequent reloads. |
| Network | No new network access or permission. |
| Distribution | Local changes and verification only. No push, release, signing-key transfer or phone installation. |

## Expected files

- `src/main/kotlin/io/github/gastoncampana/launcher/Model.kt`
- `src/main/kotlin/io/github/gastoncampana/launcher/WebApps.kt`
- `src/main/kotlin/io/github/gastoncampana/launcher/ShortcutIcons.kt`
- `src/main/kotlin/io/github/gastoncampana/launcher/Launcher.kt`
- `src/main/kotlin/io/github/gastoncampana/launcher/Ui.kt`
- `src/test/kotlin/io/github/gastoncampana/launcher/ModelTest.kt`
- `src/test/kotlin/io/github/gastoncampana/launcher/WebAppsTest.kt`
- `src/test/kotlin/io/github/gastoncampana/launcher/ShortcutIconsTest.kt`
- `src/main/res/values/strings.xml`
- `src/main/res/values-es/strings.xml`
- `README.md`
- `CLAUDE.md`

## Verification

- Inspect Android SDK contracts and representative browser source before choosing web app detection fields.
- Run the existing `./gradlew testDebugUnitTest` baseline with JDK 21 and SDK 37.
- Observe failing regression tests before production changes for discovery, identity, deduplication, multiple web apps from one browser, ordinary shortcut exclusion, initial reconciliation, deliberate removal, full-page placement and existing layout round trips.
- Verify app-only drawer behavior is unchanged and shortcut taps/dragging use the actual shortcut target.
- Run the full unit suite and Android lint; report actual counts and classify any environment/build failures separately.
- Inspect merged manifest for absence of `android.permission.INTERNET`.
- Native-device checks: packaged PWA install, accepted browser shortcut, existing installs, relaunch, full Home page, search, drag, removal and unavailable shortcut. Check both themes. If an Android device/emulator is unavailable, mark these checks blocked; a browser rendering is not an Android launcher test.
- Before publication or merging, require an independent second-engine review of committed changes in an isolated detached worktree.

## Risks and mitigations

### Classification state machine

Native activity identity is the component plus user serial. Optional web classification is keyed by package plus
user serial and has three states: absent, verified web app, verified native app. A successful metadata query moves
absent to a cached boolean. A failed metadata query stays absent and leaves the native entry visible; it must not
abort the full activity list. A package callback invalidates the cached classification for that package. Full
activity-list or shortcut-query failures retain their respective last verified lists. `reloadLock` serializes
reloads; layout reconciliation and pin acceptance publish on the main thread.

### Other risks

- Browser and Android installation modes differ. Detect from documented platform/browser evidence and do not promise inaccessible historical shortcuts.
- Shortcut identity can collide with app aliases. Use a separate identity namespace, retaining existing native app ids and package matching.
- A refresh can race pin acceptance or fail while a profile is locked. Serialize reconciliation, preserve the last verified state on read failure and keep acceptance idempotent.
- Automatic placement can undo the user's layout choices. Persist discovery separately from icon presence and test intentional removal.
- Native compilation tooling may not support this server's ARM64 architecture. Verify the actual toolchain before claiming test/build success; do not substitute mocked platform output.

## Definition of done

Both supported web-app representations appear in the drawer and are placed on Home once, with correct icons/labels/launch targets. Layout positions, removal intent, confirmation and the no-network invariant remain intact. Full local test/lint evidence and unverified native surfaces are reported explicitly. Orca lists the cloned repository after registration.

## Result

Implemented in `de6facf`, `010b80f` and `bd9e866` without changing existing native app ids, the persisted layout schema,
Android permissions or pin confirmation. Native activity discovery remains separate from optional metadata
classification and browser shortcut discovery. Web shortcut drawer items, keyboard actions and drag operations
retain the actual shortcut target. Existing layout positions and deliberate Home removal are preserved.

Validation of `bd9e866`: `testDebugUnitTest lintDebug assembleDebug` completed successfully. JUnit reports contain
72 tests, zero failures, zero errors and zero skipped tests. Lint reports zero errors and ten warnings. The debug
APK was assembled, its permission list was inspected with ARM64 aapt2, and it does not request INTERNET. English
and Spanish translatable resource keys match. Regression evidence includes failed tests before implementing
identity/placement, dock-target acceptance, checked query failures, per-package metadata isolation and atomic PNG
replacement.

Independent Claude Opus review found five concrete defects: uncaught checked metadata errors, native-only dock
drops, truncating PNG writes, a native-only tab counter and a misleading duplicate-pin message. The fixes include
regression tests for platform-query failure retention, metadata cache transitions, native/shortcut dock placement
and atomic icon replacement. The final read-only review of `bd9e866` closed all five findings and found no new
demonstrable code defects. It explicitly did not approve release without native device checks.

Unverified surfaces: real browser-to-launcher pin requests, installed WebAPK discovery, work-profile behavior,
actual tap/drag interactions, persistence after Android process death and both native UI themes. No native device
or emulator was exercised; a desktop browser is not a substitute for this Kotlin/Compose Android app. No release
APK was signed with the distribution key, no phone was modified and no changes were pushed or published. Orca
registration is incomplete because its headless runtime cannot start with the container's sandbox constraints;
the sandbox-preserving path leaves it stopped rather than disabling isolation.
