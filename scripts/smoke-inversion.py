"""Real client/server SS-05 Gravity Inversion test, isolated drivers and framebuffer review artifacts."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import re
import socket
import subprocess
import time

spec = importlib.util.spec_from_file_location("inversion_runner", Path(__file__).with_name("smoke-moon.py"))
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)
PREFIX = "STELLAR_INVERSION_SMOKE_"
FRAMES = [10, 40, 70, 84, 120, 160, 196, 205, 225, 240, 250, 270, 290, 302, 306, 310,
          316, 317, 318, 319, 321, 324, 330, 340, 356, 375]
SKIP_FRAMES = 4
OBSERVER_FRAMES = [120, 170, 210, 228, 252]
PULSE, COLLAPSE, PULSES = 7.5, 120.0, 22
# Every cue of GravityInversionSoundtrack must actually start during the natural film.
SOUNDS = {"blackhole_space_bed", "tether_strain_loop", "blackhole_reveal", "electric_snap", "moon_attract_gravity_charge",
          "moon_attract_moon_gravitational_pull", "asteroid_sonic_boom", "blackhole_tidal", "blackhole_horizon", "tether_snap",
          "moon_attract_pre_impact_tension", "tunnel", "kinetic_hammer_fall", "descent", "impact_hit", "kinetic_hammer_impact",
          "moon_attract_moon_impact", "cannon_shot", "moon_attract_shockwave_expansion", "moon_attract_debris_rain",
          "rumble_tail", "moon_attract_aftermath_ambience"}
COW = 'summon cow 260.5 4 252.5 {CustomName:"SS05Probe",Health:1000f,Attributes:[{Name:"generic.maxHealth",Base:1000d}]}'
ITEM = 'summon item 250.5 4.2 256.5 {CustomName:"SS05Item",Item:{id:"minecraft:stone",Count:1b},PickupDelay:32767s,Age:-32768s}'


def contact_sheet(output):
    from PIL import Image, ImageDraw
    def order(file):
        if file.name.startswith("inversion-skill"):
            return -1, 0
        sequence = int(re.search(r"sequence-(\d+)", file.name)[1])
        frame = re.search(r"-(\d+)ticks", file.name)
        age = int(frame[1]) if frame else -1 if "baseline" in file.name else 90 if "post-skip" in file.name else 1000
        return sequence, age
    files = sorted((output / "screenshots").glob("*.png"), key=order)
    if not files:
        return []
    sheet = Image.new("RGB", (960, ((len(files) + 2) // 3) * 205), "#171923")
    draw = ImageDraw.Draw(sheet)
    for index, file in enumerate(files):
        with Image.open(file) as image:
            image.load()
            if image.size != (1280, 720):
                raise RuntimeError(f"Unexpected framebuffer size for {file}: {image.size}")
            image.thumbnail((320, 180))
            x, y = index % 3 * 320, index // 3 * 205
            sheet.paste(image.convert("RGB"), (x, y))
            draw.text((x + 4, y + 182), file.name, fill="white")
    sheet.save(output / "inversion-contact-sheet.jpg")
    return [str(file.relative_to(output)) for file in files]


def parse_probe(response):
    line = next(line for line in response.splitlines() if "STELLAR_INVERSION_PROBE alive=" in line)
    probe = {"line": line.split("STELLAR_INVERSION_PROBE", 1)[1].strip(), "hurts": {}}
    for key, value in re.findall(r"(\w+)=(\S+)", probe["line"]):
        probe[key] = value
    for kind, amount, count in re.findall(r"hurt\[([\w.]+)@([\d.]+)\]=(\d+)", probe["line"]):
        probe["hurts"][(kind, float(amount))] = int(count)
    return probe


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--java-home", required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    java_home = Path(args.java_home).resolve()
    if not (java_home / "bin/java.exe").is_file():
        raise RuntimeError("Provide the Java 8 JDK used for the development build")
    server_dir = root / "run-server"
    world = server_dir / "stellar-smoke-world"
    if not server_dir.is_dir() or server_dir.is_symlink() or not world.is_dir() or world.is_symlink():
        raise RuntimeError("Expected the existing disposable run-server/stellar-smoke-world")
    properties = {}
    for line in (server_dir / "server.properties").read_text(encoding="utf-8").splitlines():
        if line and not line.startswith(("#", "!")) and "=" in line:
            key, value = line.split("=", 1)
            properties[key.strip()] = value.strip()
    expected = {"level-name": "stellar-smoke-world", "server-ip": "127.0.0.1", "server-port": "25591",
                "online-mode": "false", "enable-rcon": "true", "rcon.port": "25592", "rcon.password": "stellar-local-smoke"}
    if any(properties.get(key) != value for key, value in expected.items()):
        raise RuntimeError("Server does not match the disposable localhost smoke configuration")
    for port in (25591, 25592):
        with socket.socket() as probe:
            probe.bind(("127.0.0.1", port))
    manifests = {side: json.loads((root / f"build/dev-{side}-launch.json").read_text(encoding="utf-8"))
                 for side in ("server", "client")}
    if Path(manifests["server"]["directory"]).resolve() != server_dir.resolve():
        raise RuntimeError("Exported server does not target the disposable world")
    base = runner.directory(root / "build/smoke-inversion", exist_ok=True)
    output = runner.directory(base / (time.strftime("%Y%m%d-%H%M%S") + f"-{os.getpid()}"))
    runtime = runner.directory(output / "client-runtime")
    (runtime / "options.txt").write_text(
        "version:1343\nrenderDistance:8\nmaxFps:60\nenableVsync:false\nfboEnable:true\n"
        "fullscreen:false\nguiScale:2\nparticles:0\nrenderClouds:false\npauseOnLostFocus:false\n"
        "autoJump:false\nnarrator:0\ntutorialStep:none\n", encoding="utf-8")
    report = {"passed": False, "output": str(output), "world": str(world), "events": [], "frames": [], "observer_frames": [],
              "commands": [], "probes": {}, "capture_assertions": "Presence, size and timing only. Visual quality requires image review."}
    server_path, client_path = output / "inversion-server.log", output / "inversion-client.log"
    server = client = None

    def console(command):
        offset = server_path.stat().st_size
        server.stdin.write((command + "\nlist\n").encode("utf-8"))
        server.stdin.flush()
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            with server_path.open("rb") as stream:
                stream.seek(offset)
                response = stream.read().decode("utf-8", errors="replace")
            if "players online:" in response:
                report["commands"].append({"command": command, "response": response})
                return response
            if server.poll() is not None:
                raise RuntimeError("Server exited during " + command)
            time.sleep(0.05)
        raise RuntimeError("Server console barrier timeout: " + command)

    def status(count):
        response = console("stellar status")
        if f"{count} launches" not in response or "Gravity Inversion" not in response or "Terrain unchanged" not in response:
            raise RuntimeError("Unexpected server launch status: " + response)

    def terrain():
        if "Successfully found" not in console("testforblock 256 3 256 minecraft:grass"):
            raise RuntimeError("Disposable target block is not intact grass")

    def prepare(sequence):
        for command in ("stellar cancel", "gamemode 1 StellarSmoke", "clear StellarSmoke",
                        "stellar_inversion_probe home", "give StellarSmoke stellar:stellar_remote",
                        "gamerule doDaylightCycle false", "gamerule doMobSpawning false", "time set day", "weather clear",
                        "kill @e[type=cow]", "kill @e[type=item]", COW, ITEM):
            console(command)
        if "STELLAR_INVERSION_PROBE reset" not in console("stellar_inversion_probe reset"):
            raise RuntimeError("Live probe did not reset")
        terrain()
        (output / f"ready-{sequence}").write_text("ready\n", encoding="utf-8")

    def verify_probe(sequence):
        probe = parse_probe(console("stellar_inversion_probe report"))
        report["probes"][sequence] = probe["line"]
        print("PROBE", sequence, probe["line"], flush=True)
        hurts = probe["hurts"]
        shear, collapse = hurts.get(("stellar.inversion", PULSE), 0), hurts.get(("stellar.collapse", COLLAPSE), 0)
        foreign = {key: count for key, count in hurts.items() if key not in {("stellar.inversion", PULSE), ("stellar.collapse", COLLAPSE)}}
        if probe.get("alive") != "true" or foreign:
            raise RuntimeError(f"Probe cow died or took foreign damage (fall?): {probe['line']}")
        if float(probe["cowLift"]) < 5 or (sequence == 0 and float(probe["itemLift"]) < 3):
            raise RuntimeError("Probe bodies did not lift off: " + probe["line"])
        if sequence == 0 and not (shear == PULSES and collapse == 1 and abs(float(probe["health"]) - (1000 - PULSES * PULSE - COLLAPSE)) < 0.01):
            raise RuntimeError("Natural run did not deal 15/s + 120: " + probe["line"])
        if sequence == 1 and not (collapse == 0 and 15 <= shear <= 21):
            raise RuntimeError("Canceled run did not stop the field cleanly: " + probe["line"])

    print(f"ARTIFACTS {output}", flush=True)
    with server_path.open("w", encoding="utf-8") as server_log, client_path.open("w", encoding="utf-8") as client_log:
        try:
            damage_base = runner.directory(output / "damage-probe")
            damage_manifest = dict(manifests["client"])
            report["damage_driver_classes_sha256"] = runner.isolate_driver(damage_manifest, damage_base, "GravityInversionDamageSmokeMod")
            damage_driver = damage_manifest["classpath"].split(os.pathsep)[0]
            manifests["server"]["classpath"] = os.pathsep.join([damage_driver, manifests["server"]["classpath"]])
            manifests["server"]["jvmArgs"] = manifests["server"]["jvmArgs"] + ["-Dstellar.smoke.inversion.damage=true"]
            report["driver_classes_sha256"] = runner.isolate_driver(manifests["client"], output, "GravityInversionClientSmokeMod")
            server = runner.launch(manifests["server"], "server", java_home, output, server_dir, server_log)
            runner.wait_for(server_path, "Done (", [server])
            runner.wait_for(server_path, "STELLAR_INVERSION_DAMAGE_PASS", [server])
            report["damage_probe"] = next(line for line in server_path.read_text(encoding="utf-8", errors="replace").splitlines()
                                          if "STELLAR_INVERSION_DAMAGE_PASS" in line)
            print(report["damage_probe"], flush=True)
            client = runner.launch(manifests["client"], "client", java_home, output, runtime, client_log, "inversion")
            runner.wait_for(client_path, PREFIX + "READY", [server, client])
            runner.wait_for(server_path, "StellarSmoke joined the game", [server, client])
            prepare(0)
            observed, casts, finished, skipped, canceled, audio = set(), set(), set(), set(), set(), set()
            menu = key = natural = passed = False
            cast_at = time.monotonic()
            deadline = time.monotonic() + 240
            while time.monotonic() < deadline:
                for line in client_path.read_text(encoding="utf-8", errors="replace").splitlines():
                    if PREFIX not in line or line in observed:
                        continue
                    observed.add(line)
                    report["events"].append(line)
                    print(line, flush=True)
                    event = line.split(PREFIX, 1)[1]
                    if event.startswith("FAIL"):
                        raise RuntimeError(line)
                    match = re.search(r"sequence=(\d+)", event)
                    sequence = int(match[1]) if match else None
                    if event.startswith("MENU_SELECTED"):
                        menu = "wireId=9 row=4" in event
                    elif event.startswith("SELECT") and sequence == 0:
                        key = "wireId=9 key=G" in event
                    elif event.startswith("CAST"):
                        casts.add(sequence)
                        cast_at = time.monotonic()
                    elif event.startswith("LAUNCH"):
                        status(1)
                    elif event.startswith("AUDIO_SOURCE") and sequence == 0:
                        audio.add(re.search(r"name=stellar:cinematic\.(\S+)", event)[1])
                    elif event.startswith("FRAME"):
                        report["frames"].append({"sequence": sequence, "file": re.search(r"file=(\S+)", event)[1],
                            "requested_ticks": int(re.search(r"requestedTicks=(\d+)", event)[1]),
                            "actual_ticks": float(re.search(r"actualTicks=([\d.]+)", event)[1])})
                    elif event.startswith("OBSERVER_FRAME"):
                        report["observer_frames"].append({"file": re.search(r"file=(\S+)", event)[1],
                            "requested_ticks": int(re.search(r"requestedTicks=(\d+)", event)[1]),
                            "actual_ticks": float(re.search(r"actualTicks=([\d.]+)", event)[1])})
                    elif event.startswith("SKIP"):
                        status(1)
                        skipped.add(sequence)
                    elif event.startswith("CANCEL_REQUEST"):
                        if sequence not in skipped:
                            raise RuntimeError("Cancellation preceded local skip verification")
                        status(1)
                        console("stellar cancel")
                        canceled.add(sequence)
                    elif event.startswith("NATURAL_END"):
                        natural = sequence == 0
                    elif event.startswith("FINISHED"):
                        status(0)
                        terrain()
                        verify_probe(sequence)
                        finished.add(sequence)
                        if sequence == 0:
                            # SS-05 (19 s) ends before the 30 s server recharge; the second cast must not be refused.
                            time.sleep(max(0.0, cast_at + 36 - time.monotonic()))
                            prepare(1)
                    elif event == "PASS":
                        passed = True
                if passed:
                    break
                if client.poll() is not None or server.poll() is not None:
                    raise RuntimeError("Test JVM exited before acceptance passed")
                time.sleep(0.1)
            frames = [(f["sequence"], f["requested_ticks"]) for f in report["frames"]]
            if not (passed and casts == finished == {0, 1} and skipped == canceled == {1} and menu and key and natural
                    and frames == [(0, tick) for tick in FRAMES] + [(1, tick) for tick in FRAMES[:SKIP_FRAMES]]
                    and [frame["requested_ticks"] for frame in report["observer_frames"]] == OBSERVER_FRAMES):
                raise RuntimeError("Incomplete Gravity Inversion natural-end/skip acceptance evidence")
            if SOUNDS - audio:
                raise RuntimeError("SS-05 film sounds never started: " + ", ".join(sorted(SOUNDS - audio)))
            client.wait(timeout=30)
            server.stdin.write(b"stop\n")
            server.stdin.flush()
            server.wait(timeout=30)
            if client.returncode != 0 or server.returncode != 0:
                raise RuntimeError("JVMs did not stop cleanly")
            report["captures"] = contact_sheet(output)
            names = {Path(file).name for file in report["captures"]}
            expected_files = {"inversion-skill-menu.png", "sequence-1-post-skip.png"}
            expected_files.update(f"sequence-{seq}-{kind}.png" for seq in (0, 1) for kind in ("baseline", "restored"))
            expected_files.update(frame["file"] for frame in report["frames"])
            expected_files.update(frame["file"] for frame in report["observer_frames"])
            if names != expected_files:
                raise RuntimeError("Missing framebuffer captures")
            logs = client_path.read_text(encoding="utf-8", errors="replace") + server_path.read_text(encoding="utf-8", errors="replace")
            failures = re.findall(r"[^\n]*(?:OpenGL Error|GL ERROR|SMOKE_FAIL|moved wrongly|moved too quickly|kicked for floating|unknown soundEvent|CodecJOrbis)[^\n]*",
                                  logs, flags=re.IGNORECASE)
            if failures:
                raise RuntimeError("Client/server rendering or movement failures: " + "\n".join(failures))
            report["passed"] = True
            print("PASS: fifth-row/G selection, 19s natural film with every SS-05 cue, real lift and 15/s + 120 on a live cow, "
                  "no fall damage, skip keeps the server launch, witness shake, cancel, camera/audio cleanup, terrain intact.", flush=True)
        except Exception as exception:
            report["error"] = str(exception)
            raise
        finally:
            for process in (client, server):
                if process is not None and process.poll() is None:
                    subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"], check=False)
            if "captures" not in report:
                try:
                    report["captures"] = contact_sheet(output)
                except Exception as exception:
                    report["capture_error"] = str(exception)
            (output / "inversion-report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
