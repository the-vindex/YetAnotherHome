#!/usr/bin/env bash
# Self-contained e2e test run: fresh world, pinned MCC, full suite + restart
# persistence check. Exit 0 = all checks passed.
set -euo pipefail

E2E_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$E2E_DIR/../.." && pwd)"
CACHE_DIR="$E2E_DIR/.cache"
RUN_DIR="$ROOT_DIR/run"
SERVER_LOG="$CACHE_DIR/server.log"
MCC_VERSION="20260704-478"
MCC_URL="https://github.com/MCCTeam/Minecraft-Console-Client/releases/download/$MCC_VERSION/MinecraftClient-$MCC_VERSION-linux-x64"
READY_TIMEOUT=300
export MCC_DIR="$CACHE_DIR"

log() { echo "[e2e] $*"; }
fail() { log "FATAL: $*"; exit 2; }

server_pid() { ss -tlnp 2>/dev/null | grep -oP ':25565\b.*pid=\K[0-9]+' | head -1; }

stop_server() {
    local pid
    pid="$(server_pid)" || true
    [ -n "${pid:-}" ] || return 0
    log "stopping server (pid $pid)"
    kill "$pid" 2>/dev/null || true
    for _ in $(seq 60); do
        kill -0 "$pid" 2>/dev/null || return 0
        sleep 1
    done
    kill -9 "$pid" 2>/dev/null || true
}

start_server() {
    log "starting server"
    : > "$SERVER_LOG"
    (cd "$ROOT_DIR" && ./gradlew runServer --console=plain >> "$SERVER_LOG" 2>&1) &
    GRADLE_PID=$!
    for i in $(seq "$READY_TIMEOUT"); do
        if grep -qE 'Done \([0-9.]+s\)!' "$SERVER_LOG"; then
            log "server ready (${i}s)"
            return 0
        fi
        if ! kill -0 "$GRADLE_PID" 2>/dev/null; then
            tail -30 "$SERVER_LOG" >&2
            fail "server process exited before becoming ready"
        fi
        sleep 1
    done
    tail -30 "$SERVER_LOG" >&2
    fail "server not ready after ${READY_TIMEOUT}s"
}

cleanup() { stop_server; }
trap cleanup EXIT

# --- preconditions ---
[ -z "$(server_pid)" ] || fail "port 25565 already in use; stop that server first"
command -v python3 >/dev/null || fail "python3 required"
mkdir -p "$CACHE_DIR"

# --- MCC (pinned version, cached) ---
if [ ! -x "$CACHE_DIR/MinecraftClient" ]; then
    log "downloading MCC $MCC_VERSION"
    curl -fsSLo "$CACHE_DIR/MinecraftClient" "$MCC_URL"
    chmod +x "$CACHE_DIR/MinecraftClient"
fi
cp "$E2E_DIR/bot1.ini" "$E2E_DIR/bot2.ini" "$CACHE_DIR/"
rm -f "$CACHE_DIR"/chatlog-bot*.txt "$CACHE_DIR"/*-driver.log

# --- fresh, deterministic server state ---
log "resetting server world state in $RUN_DIR"
rm -rf "$RUN_DIR/world" "$RUN_DIR/defaultconfigs"
mkdir -p "$RUN_DIR/config"
# Short timings so expiry/lockout tests don't wait for production defaults.
cat > "$RUN_DIR/config/yetanotherhome.toml" <<'EOF'
maxHomes = 5
opUnlimitedHomes = true
tpaExpirySeconds = 8
deathBackExpirySeconds = 30
hurtLockoutSeconds = 5
teleportWarmupSeconds = 3
EOF
# TestBot1 is op (level 4) so the suite can test /tphere, /kill, /damage.
# UUID is the deterministic offline-mode UUID for the name "TestBot1".
cat > "$RUN_DIR/ops.json" <<'EOF'
[{"uuid": "b572b886-b31e-3bc4-b065-2b35c7e5c522", "name": "TestBot1", "level": 4, "bypassesPlayerLimit": false}]
EOF
printf 'eula=true\n' > "$RUN_DIR/eula.txt"
cat > "$RUN_DIR/server.properties" <<'EOF'
online-mode=false
enforce-secure-profile=false
difficulty=peaceful
level-type=minecraft\:flat
gamemode=creative
spawn-protection=0
view-distance=4
max-tick-time=-1
sync-chunk-writes=false
EOF

# --- phase 1: full command suite ---
start_server
log "running command suite (driver.py)"
python3 "$E2E_DIR/driver.py"
stop_server

# --- phase 2: persistence across restart ---
[ -f "$RUN_DIR/world/data/yetanotherhome_homes.dat" ] \
    || fail "yetanotherhome_homes.dat was not written on server stop"
log "yetanotherhome_homes.dat written on shutdown"
start_server
log "running restart persistence check (persistcheck.py)"
python3 "$E2E_DIR/persistcheck.py"
stop_server

log "ALL E2E TESTS PASSED"
