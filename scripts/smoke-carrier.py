"""SS-03B connected acceptance test against the existing disposable localhost smoke world."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import re
import socket
import subprocess
import time

spec = importlib.util.spec_from_file_location("carrier_harness", Path(__file__).with_name("smoke-moon.py"))
harness = importlib.util.module_from_spec(spec)
spec.loader.exec_module(harness)
PREFIX = "STELLAR_CARRIER_SMOKE_"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--java-home", required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    java = Path(args.java_home).resolve()
    server_dir = root / "run-server"
    properties = dict(line.split("=", 1) for line in (server_dir / "server.properties").read_text().splitlines()
                      if line and not line.startswith(("#", "!")) and "=" in line)
    expected = {"level-name": "stellar-smoke-world", "server-ip": "127.0.0.1", "server-port": "25591",
                "online-mode": "false", "enable-rcon": "true", "rcon.port": "25592", "rcon.password": "stellar-local-smoke"}
    if any(properties.get(k) != v for k, v in expected.items()) or not (server_dir / "stellar-smoke-world").is_dir():
        raise RuntimeError("Not the disposable localhost smoke server")
    if server_dir.is_symlink() or (server_dir / "stellar-smoke-world").is_symlink():
        raise RuntimeError("Unsafe disposable world path")
    for port in (25591, 25592):
        with socket.socket() as probe:
            probe.bind(("127.0.0.1", port))
    manifests = {side: json.loads((root / f"build/dev-{side}-launch.json").read_text()) for side in ("server", "client")}
    if Path(manifests["server"]["directory"]).resolve() != server_dir.resolve():
        raise RuntimeError("Wrong server launch directory")
    output = harness.directory(harness.directory(root / "build/smoke-carrier", exist_ok=True)
                               / (time.strftime("%Y%m%d-%H%M%S") + f"-{os.getpid()}"))
    runtime = harness.directory(output / "client-runtime")
    (runtime / "options.txt").write_text("version:1343\nrenderDistance:8\nmaxFps:60\nenableVsync:false\n"
        "fboEnable:true\nfullscreen:false\nguiScale:2\nparticles:0\nrenderClouds:false\npauseOnLostFocus:false\n"
        "autoJump:false\nnarrator:0\ntutorialStep:none\n")
    report = {"passed": False, "events": [], "commands": [], "output": str(output), "shots": {}}
    server = client = None
    server_path, client_path = output / "carrier-server.log", output / "carrier-client.log"

    def console(command):
        offset = server_path.stat().st_size
        server.stdin.write((command + "\nlist\n").encode()); server.stdin.flush()
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            with server_path.open("rb") as stream:
                stream.seek(offset); response = stream.read().decode(errors="replace")
            if "players online:" in response:
                report["commands"].append({"command": command, "response": response}); return response
            if server.poll() is not None:
                raise RuntimeError("Server exited")
            time.sleep(0.05)
        raise RuntimeError("Server console timeout: " + command)

    def prepare(sequence):
        for command in ("stellar cancel", "gamemode 0 StellarSmoke", "clear StellarSmoke",
                        "tp StellarSmoke 264.5 4 256.5 0 90", "give StellarSmoke stellar:stellar_remote",
                        "gamerule doMobSpawning false", "gamerule doDaylightCycle false", "time set day", "weather clear",
                        "kill @e[tag=stellar-carrier-smoke]",
                        'summon minecraft:husk 256.5 4 256.5 {Tags:["stellar-carrier-smoke"],NoAI:1b,Silent:1b,PersistenceRequired:1b,Health:1000.0f,Attributes:[{Name:"generic.maxHealth",Base:1000.0},{Name:"generic.knockbackResistance",Base:1.0}]}'):
            console(command)
        (output / f"ready-{sequence}").write_text("ready\n")

    print("ARTIFACTS " + str(output), flush=True)
    with server_path.open("w") as server_log, client_path.open("w") as client_log:
        try:
            report["driver_sha256"] = harness.isolate_driver(manifests["client"], output, "CarrierGunnerySmokeMod")
            server = harness.launch(manifests["server"], "server", java, output, server_dir, server_log, feature="carrier")
            harness.wait_for(server_path, "Done (", [server])
            client = harness.launch(manifests["client"], "client", java, output, runtime, client_log, feature="carrier")
            harness.wait_for(client_path, PREFIX + "READY", [server, client])
            harness.wait_for(server_path, "StellarSmoke joined the game", [server, client])
            prepare(0)
            observed, finished = set(), set()
            passed = False
            deadline = time.monotonic() + 180
            while time.monotonic() < deadline:
                for line in client_path.read_text(errors="replace").splitlines():
                    if PREFIX not in line or line in observed:
                        continue
                    observed.add(line); report["events"].append(line); print(line, flush=True)
                    event = line.split(PREFIX, 1)[1]
                    if event.startswith("FAIL"):
                        raise RuntimeError(line)
                    if event.startswith("SHOT "):
                        seq = re.search(r"sequence=(\d+)", event)[1]
                        tick = int(re.search(r"tick=(\d+)", event)[1])
                        ticks = report["shots"].setdefault(seq, [])
                        if tick not in ticks:
                            if ticks and tick - ticks[-1] < 8:
                                raise RuntimeError("Server accepted shot spam faster than 8 ticks")
                            ticks.append(tick)
                    if event.startswith("CANCEL_REQUEST"):
                        console("stellar cancel")
                    if event.startswith("FINISHED "):
                        seq = int(re.search(r"sequence=(\d+)", event)[1]); finished.add(seq)
                        for x in (256, 264):
                            if "Successfully found" not in console(f"testforblock {x} 3 256 minecraft:grass"):
                                raise RuntimeError("Cannons changed terrain")
                        health = console("entitydata @e[tag=stellar-carrier-smoke,c=1] {}")
                        match = re.search(r"Health:([\d.]+)f", health)
                        if not match or not 0 < float(match[1]) < 1000:
                            raise RuntimeError("No authoritative cannon damage to live target: " + health)
                        if seq < 2:
                            prepare(seq + 1)
                    if event.startswith("PASS"):
                        passed = True
                if passed:
                    break
                if client.poll() is not None or server.poll() is not None:
                    raise RuntimeError("Test JVM exited before PASS")
                time.sleep(0.1)
            if not passed or finished != {0, 1, 2} or any(len(report["shots"].get(str(i), [])) < 3 for i in range(3)):
                raise RuntimeError("Incomplete connected gunnery test")
            client.wait(timeout=30)
            harness.server_smoke.rcon("stop"); server.wait(timeout=30)
            combined = client_path.read_text(errors="replace") + server_path.read_text(errors="replace")
            errors = re.findall(r"[^\n]*(?:OpenGL Error|GL ERROR|moved wrongly|moved too quickly|kicked for floating|SMOKE_FAIL)[^\n]*", combined, re.I)
            if errors or client.returncode != 0 or server.returncode != 0:
                raise RuntimeError("Runtime failures: " + "\n".join(errors))
            report["passed"] = True
            print("PASS: payload selection, actual survival boarding, held fire/zoom, server damage/cadence, 30s return, J exit, cancel, terrain and flight/camera cleanup.")
        except Exception as error:
            report["error"] = str(error); raise
        finally:
            for process in (client, server):
                if process is not None and process.poll() is None:
                    subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"], check=False)
            (output / "carrier-report.json").write_text(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
