# Sign the Hack

Sign the Hack is a server-side Paper anti-cheat utility plugin that probes client translation/keybind resolution using temporary sign interactions (using MC-265322) to detect certain mod signatures.

## How it works

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

1. Download latest JAR from releases and place in `plugins/`
2. Start server once to generate config files
3. Edit:
   - `plugins/SignTheHack/config.yml`
   - `plugins/SignTheHack/checks.yml`
   - `plugins/SignTheHack/messages/<locale>.yml`
4. Reload with `/signthehack reload`

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
