# Sign the Hack

Sign the Hack is a server-side Paper anti-cheat utility plugin that probes client translation/keybind resolution using temporary sign interactions (using [MC-265322](https://bugs.mojang.com/browse/MC/issues/MC-265322)) to detect certain mod signatures.

## How it works

When a player is probed, the plugin spawns temporary signs with custom text that reference specific mod signatures. Legitimate clients will resolve the text and return it to the server, however clients with certain mods will fail to resolve it correctly. 

The plugin can then classify the client's status as DETECTED, PROTECTED, NOT_DETECTED, or SKIPPED for each check.

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
