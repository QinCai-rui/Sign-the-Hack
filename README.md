# Sign the Hack

Sign the Hack is a Paper anti-cheat utility plugin that probes client translation/keybind resolution using temporary sign interactions (MC-265322-style probing) to detect suspicious mod signatures.

## Why sign probing works

The plugin places temporary signs with translation/keybind payload keys and opens the sign editor to force client-side resolution. Different clients can resolve or expose keys differently:

- vanilla-safe resolution patterns -> usually `NOT_DETECTED`
- mod-specific translation/keybind signatures -> `DETECTED`
- blocked/malformed/missing responses before timeout -> `PROTECTED`

`PROTECTED` is intentionally **not** treated as clean.

## Requirements

- Java 21
- Paper server (1.21 API target)
- Optional: PlaceholderAPI, GrimAC, Vulcan, Spartan

## Installation

1. Build: `mvn test` (or your CI build)
2. Put resulting jar into server `plugins/`
3. Start server once to generate config files
4. Edit:
   - `plugins/Sign the Hack/config.yml`
   - `plugins/Sign the Hack/checks.yml`
   - `plugins/Sign the Hack/messages/<locale>.yml`
5. Reload with `/signthehack reload`

## Commands

- `/signthehack <player>`
- `/signthehack <player> mod1,mod2,...`
- `/signthehack reload`
- `/signthehack alerts`
- `/signthehack diagnose`
- `/signthehack trigger <player> <grim|vulcan|spartan>` (manual trigger helper)

Aliases: `/sth`, `/hacksign`

## Permissions

- `signthehack.check`
- `signthehack.reload`
- `signthehack.alerts`
- `signthehack.*`

## Config files

### `config.yml`
Contains:
- probe batching/timing (`max-checks-per-sign`, sign delay, timeout)
- check sets (`manual-default`, `join`, `anticheat`)
- auto join scans + first join mode
- per-reason cooldowns
- bedrock prefix skip rules
- action hooks (`on-detected`, `on-protected`, `on-clean`)
- webhook settings (`&name&`, `&checker&`, `&reason&`, `&hacks&`, `&results&`)
- SQLite file location

### `checks.yml`
Fully data-driven checks with:
- `display-name`
- `key`
- `mode` (`METEOR`, `TRANSLATE`, `KEYBIND`)
- `signatures`

Defaults include Meteor, LiquidBounce, Freecam, Wurst, XRay, ChestESP, KillAura, AutoFish, Lumina, AutoSwitch, BleachHack, Aristois, Coffee, World Downloader, AutoClicker, AntiAFK, plus extra autoclicker signatures.

### `messages/*.yml`
Localized MiniMessage text bundles.
Shipped locales:
- `en`, `it`, `de`, `es`, `fr`, `pt`, `ru`

## Architecture

Main package: `xyz.qincai.signthehack`

- `SignTheHackPlugin` - bootstrap and wiring
- `ScanService` - concurrency-safe scan state machine, batching, timeout, cleanup
- `DetectionEvaluator` - deterministic status evaluation
- `CooldownService` - per-player per-reason cooldowns
- `AlertService` - subscriber broadcasting
- `AnticheatIntegrationService` - optional Grim/Vulcan/Spartan hooks
- `ActionService` - command hook execution and reason rendering with exact triggering checks
- `PersistenceService` - async SQLite writes
- `SqlMigrations` - migration runner + WAL pragmas
- `WebhookService` - async webhook queue with retry/backoff

## Persistence

SQLite stores:
- scans
- per-check results
- punishment/action audit records (actions + rendered reason)

Migration files: `src/main/resources/db/migrations/`

## Security/operational notes

- No blocking DB/webhook I/O on main thread
- Response timeout always classifies as `PROTECTED`
- Action reason includes exact trigger check triplets: `id|display|status`
- Placeholder replacement strips risky quote characters in webhook payload fields
- Debug logging includes scan correlation IDs

## Testing and CI

Tests include:
- detection evaluator behavior
- cooldown behavior
- command parser behavior
- migration table creation

CI workflow: `.github/workflows/ci.yml`

## Assumptions

- Anti-cheat plugin event class names can vary by version; integration uses optional runtime binding and degrades gracefully.
- Sign-editor probe behavior depends on server/client protocol support and plugin ordering.
