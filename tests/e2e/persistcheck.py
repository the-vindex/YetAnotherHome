#!/usr/bin/env python3
"""After server restart: TestBot1's home 'base' must still exist (SavedData persistence)."""
import os, subprocess, sys, threading, time

MCC_DIR = os.environ.get("MCC_DIR", os.path.dirname(os.path.abspath(__file__)))
buf, lock = [""], threading.Lock()
proc = subprocess.Popen(["./MinecraftClient", "bot1.ini"], cwd=MCC_DIR, text=True,
                        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)

def reader():
    for line in proc.stdout:
        with lock:
            buf[0] += line

threading.Thread(target=reader, daemon=True).start()

def expect(pattern, timeout=30):
    deadline = time.time() + timeout
    while time.time() < deadline:
        with lock:
            if pattern in buf[0]:
                return True
        time.sleep(0.2)
    return False

def send(line):
    with lock:
        buf[0] = ""
    proc.stdin.write(line + "\n")
    proc.stdin.flush()

ok = True
def check(desc, result):
    global ok
    ok &= result
    print(("PASS  " if result else "FAIL  ") + desc, flush=True)

check("bot1 rejoined after restart", expect("Server was successfully joined", 60))
time.sleep(3)
send("/send /listhomes")
check("home 'base' survived server restart", expect("Your homes: base", 15))
time.sleep(1.5)
send("/send /home base")
check("teleport to persisted home works", expect("Teleported to home: base", 15))
send("/quit")
try:
    proc.wait(timeout=10)
except subprocess.TimeoutExpired:
    proc.kill()
sys.exit(0 if ok else 1)
