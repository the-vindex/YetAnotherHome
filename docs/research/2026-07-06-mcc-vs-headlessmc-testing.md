# MCC vs HeadlessMC for automated testing of CoreCommands (Forge 1.20.1 server-side mod)

Date: 2026-07-06
Use case: bot client(s) to connect to a local offline-mode Forge 1.20.1 dedicated server
(localhost:25565), send chat commands (/sethome, /tpa, ...), read chat/system replies for
assertions, two simultaneous connections for /tpa, fully scriptable from bash on headless
WSL2 Ubuntu 24.04.

## Verdict (short)

**Use Minecraft Console Client (MCC).** It is a protocol-level client (no game install, no
JVM game process, ~84 MB self-contained binary, seconds to connect), supports 1.20.1 and
offline mode natively, has built-in chat logging to file and text scripts, and two
instances are just two processes. HeadlessMC launches the *real* Minecraft client — per
instance that means a full client + assets download (~500 MB+), a Forge client install, a
multi-GB JVM, ~1 min startup, and an extra in-game mod (hmc-specifics) just to send chat.
That is the right tool for testing *client-side* mods; it is heavy overkill for asserting
server-side chat command output.

---

## 1. Minecraft Console Client (MCC)

Repo: https://github.com/MCCTeam/Minecraft-Console-Client
Docs: https://mccteam.github.io/guide/

### Protocol / version support
- Guide states MCC supports Minecraft **1.4.6 through 26.1** (and the latest release adds
  26.2 / protocol 776), so 1.20.1 (protocol 763) is well inside the supported range.
  Sources: https://mccteam.github.io/guide/ ; release notes for tag 20260704-478.
- MCC is a standalone protocol implementation in C# — it does NOT run the Minecraft game.

### Offline-mode auth
- Documented: "For offline mode, substitute `-` as the password argument", i.e.
  `MinecraftClient <username> - <server>`. In `MinecraftClient.ini`: `Login = "name"`,
  `Password = "-"`. Source: https://mccteam.github.io/guide/usage.html and
  https://mccteam.github.io/guide/configuration.html

### Non-interactive automation
- CLI forms (source: https://mccteam.github.io/guide/usage.html):
  - `MinecraftClient.exe <login> <password> <server>`
  - `MinecraftClient.exe <login> <password> <server> "/mycommand"` — sends a single
    command then disconnects automatically ("single commands auto-close after execution").
  - `MinecraftClient.exe <settings.ini> [--section.setting=value ...]` — any ini setting
    overridable via `--section.setting=value` dot notation.
- `Main.Advanced.ExitOnFailure = true` — documented as "disable pauses on error, for
  using MCC in non-interactive scripts".
  Source: https://mccteam.github.io/guide/configuration.html
- Text scripts (source: https://mccteam.github.io/guide/creating-text-script.html and
  web search results incl. https://github.com/MCCTeam/Minecraft-Console-Client/issues/735):
  plain .txt file, one internal command per line, `#` comments. Commands include `send`,
  `wait`, `connect`, `exit`. Sample from docs:
  ```
  send Hello World! I'm a bot scripted using Minecraft Console Client.
  wait 60
  send Now quitting. Bye :)
  exit
  ```
- `wait N` unit: **ticks at MCC's ClientTicksPerSecond (10/s)** — confirmed in source
  `MinecraftClient/ChatBots/Script.cs` (`int ticks = Settings.ClientTicksPerSecond;`,
  supports `wait 10-20` random ranges). So `wait 10` = 1 s.
  Source: https://raw.githubusercontent.com/MCCTeam/Minecraft-Console-Client/master/MinecraftClient/ChatBots/Script.cs
- ScriptScheduler ChatBot: triggers internal commands/scripts on **first login**, on
  every login, on time interval, etc. — the documented way to "send several commands or
  stay connected". Sources: https://mccteam.github.io/guide/usage.html ,
  https://mccteam.github.io/guide/chat-bots.html
- Reading chat programmatically:
  - Everything the bot receives is printed to stdout (redirectable to a file).
  - **ChatLog ChatBot** writes chat to a file: settings `Log_File`
    (default `chatlog-%username%-%serverip%.txt`), `Filter` = `all` / `messages` /
    `chat` / `private` / `internal`, `Add_DateTime`.
    Source: https://mccteam.github.io/guide/chat-bots.html
  - AutoRespond / Alerts bots can pattern-match chat and trigger actions (not needed for
    simple assertions but available). Source: same page.

### Install (headless Ubuntu x64)
- Latest release (checked 2026-07-06 via GitHub API): tag **20260704-478**
  (2026-07-04). Linux x64 asset (84 MB, single file):
  https://github.com/MCCTeam/Minecraft-Console-Client/releases/download/20260704-478/MinecraftClient-20260704-478-linux-x64
  Other assets: linux-arm, linux-arm64, osx-x64/arm64, win-x86/x64/arm64.
- Or installer script: `curl -fsSL https://mccteam.github.io/install.sh | sh`
  (detects arch, downloads right binary). Source: repo README / installation guide.
- .NET: the 84 MB single-file Linux binary is a self-contained .NET publish (no separate
  runtime install needed to *run* it). The installation page's ".NET 10 SDK is required"
  line applies to building from source. Source:
  https://mccteam.github.io/guide/installation.html + asset sizes (runtime bundled).

### Two simultaneous instances
- Nothing special: run two MCC processes with different usernames (and, to keep chat
  logs separate, different working dirs or Log_File names — the ChatLog default already
  embeds `%username%`). No account limits in offline mode.

### Known gotchas
- **TTY / ConsoleInteractive**: MCC's console layer wants a terminal. The official Docker
  docs say the container must run with `-it` and warn "Because of a ConsoleInteractive
  issue, avoid headless startup" (then detach with CTRL+P, CTRL+Q).
  Source: https://mccteam.github.io/guide/installation.html
  Practical workaround on plain Linux: allocate a pty with util-linux `script`, e.g.
  `script -qefc "./MinecraftClient ..." /dev/null > bot.log`, or run inside tmux.
  Set `ExitOnFailure = true` so errors don't block waiting for a keypress.
- 1.20.1 chat signing: in offline mode there are no Mojang keys; on the server side set
  `enforce-secure-profile=false` in server.properties to avoid signed-chat enforcement
  (with online-mode=false this is the standard combination). (Operational note, not from
  MCC docs.)
- MCC "may lag behind brand-new Minecraft releases" — irrelevant for 1.20.1.
  Source: https://mccteam.github.io/guide/

## 2. HeadlessMC

Org/repo: https://github.com/3arthqu4ke/headlessmc (org page: HeadlessHQ)
Docs: https://headlesshq.github.io/headlessmc/

### What it is
- "A command line launcher for Minecraft Java Edition" — it downloads and launches the
  **actual Minecraft client** (vanilla/Fabric/Forge/NeoForge; can also manage servers:
  Paper/Purpur etc.) without a display, by patching LWJGL calls into no-ops/stubs.
  Sources: https://github.com/3arthqu4ke/headlessmc ,
  https://headlesshq.github.io/headlessmc/
- Java 8+; distributed as `headlessmc-launcher.jar` (or a `-wrapper.jar` enabling
  plugins/in-memory launch) plus native Linux/Windows/macOS executables that bring their
  own Java. Source: https://headlesshq.github.io/headlessmc/getting-started/

### Version / modloader support
- Launch syntax like `launch fabric:1.21.4 -lwjgl`, `download 1.12.2`, `forge 1.12.2` —
  Forge 1.20.1 is within supported range; the companion mod hmc-specifics covers
  1.7.10 → 26.1 across Forge/Fabric/NeoForge.
  Sources: https://headlesshq.github.io/headlessmc/launch/ ,
  https://github.com/3arthqu4ke/hmc-specifics

### Offline mode
- Docs are explicit: "HeadlessMc will not allow you to play without having bought
  Minecraft! Accounts will always be validated." Offline accounts are "only … used to run
  the game headlessly in CI/CD pipelines" — i.e. allowed, but a constrained special case,
  and normal `login` flow is Microsoft device auth.
  Source: https://headlesshq.github.io/headlessmc/

### Interacting with the game / reading chat
- HeadlessMC itself is a launcher with its own command REPL (`launch`, `download`,
  `server`, `--command <cmd>` at startup). To control the *running game* (click GUI,
  send chat) you must drop the **hmc-specifics** jar for your exact MC version into the
  mods folder; it adds commands: `msg`/`.` (send chat message), `/` (send chat command),
  `gui`, `click`, `text`, `connect`/`disconnect`, `render`.
  Source: https://github.com/3arthqu4ke/hmc-specifics
- Chat output: the client's own log (stdout / logs/latest.log `[CHAT]` lines) is what
  you'd parse; hmc-specifics docs do not describe a structured chat-read API.
- Their CI story (mc-runtime-test, JSON test specs) targets *does-the-modded-client-boot*
  style testing, not protocol-level bot assertions.
  Source: https://github.com/3arthqu4ke/headlessmc README (testing/CI section).

### Cost per instance (our case)
- Full Minecraft 1.20.1 client + assets download (hundreds of MB, roughly ~500 MB with
  assets) + Forge client install per instance directory; JVM game process (default-ish
  2 GB heap; `-inmemory` and `hmc.assets.dummy` exist to reduce footprint); ~1 minute
  startup on typical hardware. Two simultaneous bots = two full game clients.
  Sources: https://github.com/3arthqu4ke/headlessmc (README: lwjgl patching, in-memory,
  dummy assets), general Minecraft client sizing (operational estimate).
- Also requires keeping client-side Forge + hmc-specifics version-matched to 1.20.1.

## 3. Comparison for this use case

| Criterion | MCC | HeadlessMC |
|---|---|---|
| 1.20.1 protocol | Yes (1.4.6–26.x) | Yes (launches real 1.20.1 client) |
| Offline auth | First-class (`password -`) | Allowed only as CI special case; validates accounts otherwise |
| Footprint per bot | 1 process, ~84 MB binary, low RAM, connects in seconds | Full client download + Forge install + multi-GB JVM, ~1 min startup |
| Send chat/commands | CLI arg, text scripts, ScriptScheduler, stdin | Needs hmc-specifics mod (`msg`, `/`) |
| Read chat for asserts | stdout + ChatLog bot to file with filters | Parse client latest.log `[CHAT]` lines |
| Two simultaneous bots | Trivial (2 processes) | 2 full game instances (heavy) |
| Headless gotcha | Wants a TTY → wrap in `script`/tmux; `ExitOnFailure=true` | Designed headless (lwjgl stubs) but heavyweight |
| Best for | Protocol-level server testing (our case) | Testing client-side mods / real-client CI |

## 4. Recommended MCC setup (tested syntax from docs)

Download:
```bash
mkdir -p ~/mcc && cd ~/mcc
curl -fLo MinecraftClient \
  https://github.com/MCCTeam/Minecraft-Console-Client/releases/download/20260704-478/MinecraftClient-20260704-478-linux-x64
chmod +x MinecraftClient
```

Per-bot ini (e.g. `bot1.ini`):
```toml
[Main.General]
Login = "TestBot1"
Password = "-"
Server = { Host = "127.0.0.1", Port = 25565 }

[Main.Advanced]
ExitOnFailure = true
SessionCache = "none"

[ChatBot.ChatLog]
Enabled = true
Filter = "all"
Log_File = "chatlog-bot1.txt"

[ChatBot.ScriptScheduler]
Enabled = true
[[ChatBot.ScriptScheduler.TaskList]]
Trigger_On_First_Login = true
Action = "script bot1-script.txt"
```

`bot1-script.txt` (wait unit = 1/10 s):
```
wait 20
send /sethome base
wait 10
send /listhomes
wait 10
send /home base
wait 20
exit
```

Run (pty wrapper to dodge the ConsoleInteractive TTY issue):
```bash
script -qefc "./MinecraftClient bot1.ini" /dev/null > bot1-console.log 2>&1
```

Two bots for /tpa: duplicate ini/script with `TestBot2`, run both `script -qefc ...`
invocations in parallel (backgrounded), then grep the two chatlog files / console logs
for expected messages.

Server side prerequisites: `online-mode=false`, recommend `enforce-secure-profile=false`.
