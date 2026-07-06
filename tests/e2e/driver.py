#!/usr/bin/env python3
"""Expect-style driver for two MCC bots testing CoreCommands homes + TPA."""
import os, subprocess, sys, threading, time

MCC_DIR = os.environ.get("MCC_DIR", os.path.dirname(os.path.abspath(__file__)))

class Bot:
    def __init__(self, name, ini):
        self.name = name
        self.lock = threading.Lock()
        self.buf = ""
        self.log = open(os.path.join(MCC_DIR, f"{name}-driver.log"), "w")
        self.proc = subprocess.Popen(
            ["./MinecraftClient", ini], cwd=MCC_DIR, text=True,
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        threading.Thread(target=self._reader, daemon=True).start()

    def _reader(self):
        for line in self.proc.stdout:
            with self.lock:
                self.buf += line
            self.log.write(line)
            self.log.flush()

    def expect(self, pattern, timeout=30):
        deadline = time.time() + timeout
        while time.time() < deadline:
            with self.lock:
                if pattern in self.buf:
                    return True
            time.sleep(0.2)
        return False

    def clear(self):
        with self.lock:
            self.buf = ""

    def send(self, line):
        self.clear()  # only match output arriving after this send
        self.proc.stdin.write(line + "\n")
        self.proc.stdin.flush()

    def close(self):
        try:
            self.send("/quit")
            self.proc.wait(timeout=10)
        except Exception:
            self.proc.kill()
        self.log.close()

results = []
def check(desc, ok):
    results.append((desc, ok))
    print(("PASS  " if ok else "FAIL  ") + desc, flush=True)

def cmd_expect(bot, command, expected, timeout=15, where=None):
    target = where or bot
    if target is not bot:
        target.clear()
    bot.send("/send " + command)
    ok = target.expect(expected, timeout)
    check(f"{bot.name}: {command!r} -> {target.name} sees {expected!r}", ok)
    return ok

bot2 = Bot("bot2", "bot2.ini")
check("bot2 joined server", bot2.expect("Server was successfully joined", 60))
bot1 = Bot("bot1", "bot1.ini")
check("bot1 joined server", bot1.expect("Server was successfully joined", 60))
time.sleep(3)  # let spawn settle

# --- homes ---
cmd_expect(bot1, "/sethome base", "Home 'base' set.")
time.sleep(1.5)
cmd_expect(bot1, "/sethome mine", "Home 'mine' set.")
time.sleep(1.5)
cmd_expect(bot1, "/listhomes", "Your homes:")
time.sleep(1.5)
cmd_expect(bot1, "/home base", "Teleported to home: base")
time.sleep(1.5)
cmd_expect(bot1, "/delhome mine", "Home mine deleted.")
time.sleep(1.5)
cmd_expect(bot1, "/listhomes", "Your homes: base")
time.sleep(1.5)
cmd_expect(bot1, "/delhome nope", "Home nope not found.")
time.sleep(1.5)

# --- no-argument default name: /sethome == /sethome home ---
cmd_expect(bot1, "/sethome", "Home 'home' set.")
time.sleep(1.5)
cmd_expect(bot1, "/home", "Teleported to home: home")
time.sleep(1.5)
cmd_expect(bot1, "/delhome", "Home home deleted.")
time.sleep(1.5)

# --- home cap: ops bypass limit when opUnlimitedHomes=true; regular players do not ---
# bot1 is an op, bot2 is not. maxHomes=5, opUnlimitedHomes=true.
# bot2 fills up to the limit
for i in range(2, 7):
    cmd_expect(bot2, f"/sethome h{i}", f"Home 'h{i}' set.")
    time.sleep(1.5)
cmd_expect(bot2, "/sethome toomany", "You have reached the maximum of 5 homes.")
time.sleep(1.5)

# bot1 (op) bypasses the limit
for i in range(2, 4):
    cmd_expect(bot1, f"/sethome h{i}", f"Home 'h{i}' set.")
    time.sleep(1.5)
cmd_expect(bot1, "/sethome tooopmany", "Home 'tooopmany' set.")  # op bypasses limit
time.sleep(1.5)

# clean up
for i in range(2, 7):
    cmd_expect(bot2, f"/delhome h{i}", f"Home h{i} deleted.")
    time.sleep(1.5)
for i in range(2, 4):
    cmd_expect(bot1, f"/delhome h{i}", f"Home h{i} deleted.")
    time.sleep(1.5)
cmd_expect(bot1, "/delhome tooopmany", "Home tooopmany deleted.")
time.sleep(1.5)

# --- TPA ---
bot2.clear()
cmd_expect(bot1, "/tpa TestBot2", "Teleport request sent to TestBot2")
check("bot2 received TPA request", bot2.expect("TestBot1 wants to teleport to you", 15))
time.sleep(1.5)
bot1.clear()
cmd_expect(bot2, "/tpaccept", "You accepted the teleport request.")
check("bot1 sees acceptance", bot1.expect("Teleport request accepted.", 15))
time.sleep(1.5)
cmd_expect(bot2, "/tpaccept", "No pending teleport requests.")
time.sleep(1.5)
cmd_expect(bot1, "/tpcancel", "No pending teleport request to cancel.")
time.sleep(1.5)

# --- /tpahere: target teleports to requester on accept ---
bot2.clear()
cmd_expect(bot1, "/tpahere TestBot2", "Teleport request sent to TestBot2")
check("bot2 received tpahere request", bot2.expect("TestBot1 wants you to teleport to them", 15))
time.sleep(1.5)
bot1.clear()
cmd_expect(bot2, "/tpaccept", "You accepted the teleport request.")
check("bot1 sees tpahere acceptance", bot1.expect("Teleport request accepted.", 15))
time.sleep(1.5)

# --- request expiry sweep notifies both parties (tpaExpirySeconds=8 in test config) ---
bot2.clear()
cmd_expect(bot1, "/tpa TestBot2", "Teleport request sent to TestBot2")
check("requester notified of expiry", bot1.expect("Your teleport request to TestBot2 has expired.", 20))
check("target notified of expiry", bot2.expect("The teleport request from TestBot1 has expired.", 5))
time.sleep(1.5)
cmd_expect(bot2, "/tpaccept", "No pending teleport requests.")
time.sleep(1.5)

# --- /tphere (bot1 is op) ---
bot2.clear()
cmd_expect(bot1, "/tphere TestBot2", "Teleported TestBot2 to your location.")
check("bot2 was summoned", bot2.expect("You have been teleported to TestBot1", 15))
time.sleep(1.5)

# --- /back toggles with the last teleport origin (teleportWarmupSeconds=3) ---
cmd_expect(bot1, "/home base", "Teleporting in 3s")
check("warmup completes", bot1.expect("Teleported to home: base", 15))
time.sleep(1.5)
cmd_expect(bot1, "/back", "Teleported back to your last location.", 15)
time.sleep(1.5)
cmd_expect(bot1, "/back", "Teleported back to your last location.", 15)
time.sleep(1.5)

# --- death: /back goes to the corpse even after /home for equipment ---
cmd_expect(bot1, "/kill TestBot1", "Killed TestBot1")
time.sleep(3)  # auto-respawn
cmd_expect(bot1, "/home base", "Teleported to home: base", 15)
time.sleep(1.5)
cmd_expect(bot1, "/back", "Teleported back to your death location.", 15)
time.sleep(1.5)
cmd_expect(bot1, "/back", "Teleported back to your last location.", 15)
time.sleep(1.5)

# --- hurt lockout (hurtLockoutSeconds=5): teleports denied after damage ---
cmd_expect(bot1, "/gamemode survival", "Set own game mode to Survival Mode")
time.sleep(1.5)
cmd_expect(bot1, "/damage TestBot1 1", "Applied 1.0 damage to TestBot1")
cmd_expect(bot1, "/home base", "because you took damage.")
time.sleep(6)  # wait out the lockout
cmd_expect(bot1, "/home base", "Teleported to home: base", 15)
time.sleep(1.5)

# --- damage during warmup cancels the pending teleport ---
bot1.send("/send /home base")
check("warmup started", bot1.expect("Teleporting in 3s", 10))
bot1.send("/send /damage TestBot1 1")
check("teleport cancelled by damage", bot1.expect("Teleport cancelled because you took damage.", 10))
time.sleep(6)  # let the lockout decay
cmd_expect(bot1, "/gamemode creative", "Set own game mode to Creative Mode")
time.sleep(1.5)

# --- death priority expires (deathBackExpirySeconds=30) ---
cmd_expect(bot1, "/kill TestBot1", "Killed TestBot1")
time.sleep(32)
cmd_expect(bot1, "/back", "Teleported back to your last location.", 15)

bot1.close()
bot2.close()

failed = [d for d, ok in results if not ok]
print(f"\n{len(results) - len(failed)}/{len(results)} checks passed", flush=True)
sys.exit(1 if failed else 0)
