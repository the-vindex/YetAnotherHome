# End-to-end tests (Minecraft Console Client)

Two-bot integration tests that exercise every CoreCommands command against a
real Forge dedicated server, asserting on the chat responses the server sends
back. Uses [Minecraft Console Client](https://github.com/MCCTeam/Minecraft-Console-Client)
(MCC) as a headless protocol-level client — no game install needed.
See `docs/research/2026-07-06-mcc-vs-headlessmc-testing.md` for why MCC.

## Run (one command)

```bash
tests/e2e/run.sh
```

The script is self-contained and deterministic:

1. Downloads a pinned MCC build into `tests/e2e/.cache/` (once; ~85 MB).
2. Wipes `run/world` so every run starts from a fresh world, and writes the
   required `run/eula.txt` + `run/server.properties` (offline mode).
3. Starts the Forge server via `./gradlew runServer`, waits for readiness.
4. Runs `driver.py` — full suite: homes (incl. no-arg defaults and the
   home cap), TPA flow (incl. /tpahere and expiry notifications), /tphere,
   /back (death priority, toggle, expiry), and the combat hurt-lockout and
   teleport-warmup mechanics. TestBot1 is opped via a pre-seeded ops.json;
   timings come from the test config written to `run/config/`.
5. Stops the server, asserts `world/data/yetanotherhome_homes.dat` was written.
6. Restarts the server and runs `persistcheck.py` — homes survive the
   restart via world SavedData (3 checks).
7. Tears the server down (also on failure, via trap) and exits non-zero if
   any check failed.

Requires: bash, python3, curl, port 25565 free. First run also pays the
usual ForgeGradle setup cost.

## Manual run (advanced)

Start the server yourself (`online-mode=false`,
`enforce-secure-profile=false`, `eula=true`), then:

```bash
export MCC_DIR=/path/to/dir-containing-MinecraftClient   # bot inis are read from here too
cp bot1.ini bot2.ini "$MCC_DIR"
python3 driver.py          # expects a fresh world (no pre-existing homes)
# then restart the server and run:
python3 persistcheck.py
```

Both scripts exit 0 on success and print PASS/FAIL per check.

Notes:
- MCC is driven over plain stdin/stdout pipes (its interactive console
  corrupts input on a bare pty — do not wrap it in `script`/pty).
- MCC rewrites the `.ini` files with a full config dump on exit; that is
  harmless, but re-copy the minimal inis from this directory if you want a
  clean slate.
- Bots log in offline-mode as TestBot1/TestBot2 (password `-`).
