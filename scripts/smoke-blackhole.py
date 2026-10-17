"""Real connected Forge Blackhole acceptance test, in disposable development directories."""
import argparse
import importlib.util
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time

spec = importlib.util.spec_from_file_location("server_smoke", Path(__file__).with_name("smoke-server.py"))
server_smoke = importlib.util.module_from_spec(spec)
spec.loader.exec_module(server_smoke)


def launch(root, side, java_home, output, directory, log):
    data = json.loads((root / f"build/dev-{side}-launch.json").read_text())
    if side == "client" and not any(
            (Path(entry) / "fr/frabous/stellar/smoke/BlackholeClientSmokeMod.class").is_file()
            for entry in data["classpath"].split(os.pathsep)):
        raise RuntimeError("Export manifests with exportDevLaunches -Psmoke first")
    command = [str(Path(java_home) / "bin/java.exe"), "-Xmx3G"] + data["jvmArgs"]
    if side == "client":
        command += ["-Dstellar.smoke.blackhole=true", f"-Dstellar.smoke.output={output}"]
    command += ["-classpath", data["classpath"], data["main"]] + data["args"]
    if side == "client":
        command += ["--username", "StellarSmoke", "--server", "127.0.0.1", "--port", "25591",
                    "--width", "1280", "--height", "720", "--gameDir", str(directory)]
    return subprocess.Popen(command, cwd=directory, env=dict(os.environ, **data["environment"]),
                            stdin=subprocess.PIPE if side == "server" else subprocess.DEVNULL,
                            stdout=log, stderr=subprocess.STDOUT)


def wait_for(path, text, process, timeout=180):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if text in path.read_text(encoding="utf-8", errors="replace"):
            return
        if process.poll() is not None:
            raise RuntimeError(f"Process exited before {text}; see {path}")
        time.sleep(0.5)
    raise RuntimeError(f"Timed out waiting for {text}; see {path}")


def contact_sheet(output):
    from PIL import Image, ImageDraw
    def order(file):
        sequence = int(file.name.split("-")[1])
        age = -1 if "baseline" in file.name else 1000 if "restored" in file.name else int(file.stem.split("-")[2].replace("ticks", ""))
        return sequence, age
    files = sorted((output / "screenshots").glob("*.png"), key=order)
    if not files:
        return []
    sheet = Image.new("RGB", (960, ((len(files) + 2) // 3) * 205), "#171923")
    draw = ImageDraw.Draw(sheet)
    for index, file in enumerate(files):
        with Image.open(file) as image:
            image.thumbnail((320, 180))
            x, y = index % 3 * 320, index // 3 * 205
            sheet.paste(image, (x, y))
            draw.text((x + 4, y + 182), file.name, fill="white")
    sheet.save(output / "contact-sheet.jpg")
    return [str(file.relative_to(output)) for file in files]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--java-home", required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    run_id = time.strftime("%Y%m%d-%H%M%S")
    output = root / "build/smoke-blackhole" / run_id
    output.mkdir(parents=True)
    server_dir = root / "run-server" / ("blackhole-" + run_id)
    client_dir = output / "client-runtime"
    server_dir.mkdir(); client_dir.mkdir()
    (server_dir / "eula.txt").write_text("eula=true\n")
    # Mobs stay lethal; the survival test player is spared so the harness can observe the whole field.
    (server_dir / "config").mkdir()
    (server_dir / "config" / "stellar.cfg").write_text(
        "gameplay {\n    B:blackholeLethal=true\n    B:blackholeLethalPlayers=false\n}\n")
    (server_dir / "server.properties").write_text(
        "server-ip=127.0.0.1\nserver-port=25591\nonline-mode=false\n"
        "enable-rcon=true\nrcon.port=25592\nrcon.password=stellar-local-smoke\n"
        "level-name=blackhole-flat\nlevel-type=FLAT\nlevel-seed=7047291\n"
        "generate-structures=false\nview-distance=6\nspawn-protection=0\n"
        "gamemode=0\ndifficulty=0\nallow-flight=false\nmax-players=2\n")
    (client_dir / "options.txt").write_text(
        "version:1343\nrenderDistance:8\nmaxFps:60\nenableVsync:false\nfboEnable:true\n"
        "fullscreen:false\nguiScale:2\nparticles:0\nrenderClouds:false\n"
        "pauseOnLostFocus:false\nautoJump:false\nnarrator:0\ntutorialStep:none\n")
    report = {"passed": False, "output": str(output), "server_directory": str(server_dir),
              "server_altitudes": [], "events": [], "rcon": []}
    report["compiled_classes_sha256"] = {
        name: hashlib.sha256((root / "build/sourcesSets/main/fr/frabous/stellar" / name).read_bytes()).hexdigest()
        for name in ["client/BlackholeEffects.class", "client/BlackholeVisualMath.class",
                     "client/BlackholeSoundtrack.class", "client/CinematicSoundtrack.class",
                     "client/TidalShader.class", "client/SpacetimeLens.class", "api/BlackholeTides.class",
                     "common/BlackholeField.class", "common/LaunchController.class"]}
    server = client = None
    chat_offsets = {}

    def rcon(command):
        # Legacy dev RCON executes on its listener thread; even tp/entitydata can race world ticks.
        if command != "stop":
            log_path = output / "server.log"
            offset = log_path.stat().st_size
            server.stdin.write((command + "\nlist\n").encode("utf-8")); server.stdin.flush()
            deadline = time.monotonic() + 10
            while time.monotonic() < deadline:
                with log_path.open("rb") as stream:
                    stream.seek(offset)
                    response = stream.read().decode("utf-8", errors="replace")
                if "players online:" in response:
                    report["rcon"].append({"command": command, "transport": "server-console", "response": response})
                    return response
                if server.poll() is not None:
                    raise RuntimeError("Server exited while running console command " + command)
                time.sleep(0.02)
            raise RuntimeError("Console queue did not acknowledge " + command)
        response = server_smoke.rcon(command)
        report["rcon"].append({"command": command, "response": response})
        return response

    def prepare(sequence):
        for command in ["stellar cancel", "kill @e[type=!player]", "gamemode 0 StellarSmoke", "clear StellarSmoke",
                        "tp StellarSmoke 264.5 4 256.5 0 90", "give StellarSmoke stellar:stellar_remote",
                        "gamerule doDaylightCycle false", "gamerule doMobSpawning false", "time set day"]:
            rcon(command)
        for x, z, entity in [(264, 256, "cow"), (265, 256, "pig"), (264, 257, "sheep"),
                             (265, 257, "cow"), (266, 256, "pig"), (266, 257, "sheep")]:
            rcon(f'summon {entity} {x} 4 {z} {{NoAI:1b,PersistenceRequired:1b,Tags:["bhmob"]}}')
        # 1.12 only reports pet deaths: the owner receives the death message as a logged client chat line.
        for z in (256, 257):
            rcon(f'summon wolf 263 4 {z} {{NoAI:1b,PersistenceRequired:1b,CustomName:"BhVictim",Owner:"StellarSmoke",Tags:["bhmob"]}}')
        chat_offsets[sequence] = (output / "client.log").stat().st_size
        time.sleep(1)
        (output / f"ready-{sequence}").write_text("ready\n")

    def server_position(sequence, age):
        # A short-lived marker copies the real MP position via execute. entitydata returns its NBT.
        rcon('execute StellarSmoke ~ ~ ~ summon armor_stand ~ ~ ~ {NoGravity:1b,Marker:1b,Invisible:1b,Tags:["bhprobe"]}')
        response = rcon('entitydata @e[tag=bhprobe,c=1] {}')
        rcon('kill @e[tag=bhprobe]')
        match = re.search(r"Pos:\[([^\]]+)\]", response)
        if not match:
            raise RuntimeError("Cannot read authoritative server position: " + response)
        position = [float(value.strip().rstrip("dD")) for value in match.group(1).split(",")]
        report["server_altitudes"].append({"sequence": sequence, "client_age": age, "position": position})
        print(f"SERVER_POSITION sequence={sequence} age={age} position={position}", flush=True)
        if position[1] < 300:
            raise RuntimeError("Server player did not sustain Y >= 300")
        response = rcon('testfor StellarSmoke {playerGameType:0,abilities:{mayfly:1b,flying:0b}}')
        if "Found StellarSmoke" not in response:
            raise RuntimeError("Server survival capture capabilities incorrect: " + response)

    print(f"ARTIFACTS {output}", flush=True)
    with (output / "server.log").open("w", encoding="utf-8") as server_log, (output / "client.log").open("w", encoding="utf-8") as client_log:
        try:
            server = launch(root, "server", args.java_home, output, server_dir, server_log)
            wait_for(output / "server.log", "Done (", server)
            client = launch(root, "client", args.java_home, output, client_dir, client_log)
            wait_for(output / "server.log", "StellarSmoke joined the game", client)
            prepare(0)
            deadline = time.monotonic() + 300
            observed = set(); sampled = set(); finished = set(); passed = False
            while time.monotonic() < deadline:
                content = (output / "client.log").read_text(encoding="utf-8", errors="replace")
                for line in content.splitlines():
                    if "STELLAR_BLACKHOLE_SMOKE_" not in line or line in observed:
                        continue
                    observed.add(line); report["events"].append(line)
                    print(line, flush=True)
                    if "SMOKE_FAIL" in line:
                        raise RuntimeError(line)
                    if "SMOKE_SAMPLE" in line:
                        sequence = int(re.search(r"sequence=(\d+)", line)[1])
                        age = float(re.search(r"age=([\d.]+)", line)[1])
                        bucket = int(age // 100)
                        if bucket in (3, 4, 5, 6) and (sequence, bucket) not in sampled:
                            server_position(sequence, age); sampled.add((sequence, bucket))
                    if "SMOKE_CANCEL_REQUEST" in line:
                        if "1 launches" not in rcon("stellar status"):
                            raise RuntimeError("J unexpectedly canceled the server field")
                        rcon("stellar cancel")
                    if "SMOKE_FINISHED" in line:
                        sequence = int(re.search(r"sequence=(\d+)", line)[1]); finished.add(sequence)
                        if "0 launches" not in rcon("stellar status"):
                            raise RuntimeError("Server launch leaked after completion")
                        response = rcon('testfor StellarSmoke {playerGameType:0,abilities:{mayfly:0b,flying:0b},OnGround:1b}')
                        if "Found StellarSmoke" not in response:
                            raise RuntimeError("Server release failed: " + response)
                        if sequence == 1:
                            # Mobs held until the natural release land back at their origin: none may survive.
                            # A chunk unloaded behind a lone player drops its mobs where they orbit instead.
                            held = rcon("testfor @e[tag=bhmob,x=254,y=0,z=246,dx=22,dy=24,dz=22]")
                            if "Found" in held:
                                raise RuntimeError("Mobs survived the lethal horizon: " + held)
                            escaped = re.findall(r"Teleported (\w+) to ([-\d.]+), ([-\d.]+), ([-\d.]+)",
                                                 rcon("tp @e[tag=bhmob] ~ ~ ~"))
                            report["chunk_unload_escapes"] = escaped
                            print(f"CHUNK_UNLOAD_ESCAPES {escaped}", flush=True)
                            with (output / "client.log").open("rb") as stream:
                                stream.seek(chat_offsets[1])
                                chat = stream.read().decode("utf-8", errors="replace")
                            # Translated text or raw key: either proves the tidal damage source killed it.
                            if not re.search(r"\[CHAT\].*BhVictim.*spaghettif", chat):
                                raise RuntimeError("Missing spaghettification death message in the client chat")
                        if sequence == 0:
                            prepare(1)
                    if "SMOKE_PASS" in line:
                        passed = True
                if passed:
                    break
                if client.poll() is not None or server.poll() is not None:
                    raise RuntimeError("Test JVM exited before PASS")
                time.sleep(0.2)
            if not passed or finished != {0, 1} or len(sampled) < 6:
                raise RuntimeError("Incomplete connected Blackhole acceptance test")
            client.wait(timeout=30)
            rcon("stop"); server.wait(timeout=30)
            if client.returncode != 0 or server.returncode != 0:
                raise RuntimeError("Test JVMs did not stop cleanly")
            if len(list((output / "screenshots").glob("*.png"))) != 36:
                raise RuntimeError("Missing Minecraft framebuffer captures (expected 36)")
            combined = content + (output / "server.log").read_text(encoding="utf-8", errors="replace")
            failures = re.findall(r"[^\n]*(?:moved too quickly|moved wrongly|flying is not enabled|kicked for floating|OpenGL Error|GL ERROR|SMOKE_FAIL)[^\n]*",
                                  combined, flags=re.IGNORECASE)
            if failures:
                raise RuntimeError("Movement/GL failures: " + "\n".join(failures))
            report["passed"] = True
            print("PASS: connected survival ascent, J opt-out, lensing, spaghettified and shredded mobs, cancel and natural 800-tick release.", flush=True)
        except Exception as exception:
            report["error"] = str(exception)
            raise
        finally:
            for process in [client, server]:
                if process is not None and process.poll() is None:
                    subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"], check=False)
            report["captures"] = contact_sheet(output)
            (output / "report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
