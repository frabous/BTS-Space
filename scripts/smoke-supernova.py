"""Real client/server Supernova test, isolated driver and framebuffer review artifacts."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import re
import socket
import subprocess
import time

spec = importlib.util.spec_from_file_location("supernova_runner", Path(__file__).with_name("smoke-moon.py"))
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)
PREFIX = "STELLAR_SUPERNOVA_SMOKE_"
FRAMES = [24, 62, 92, 140, 180, 220, 280, 304, 316, 319, 320, 321, 324, 328, 338, 350, 365, 385, 400, 403, 414, 435, 472, 480, 486, 500, 540, 585, 620]
OBSERVER_FRAMES = [300, 316, 338, 350, 403, 500, 540, 562, 590]
OBSERVER_MODES = [0, 1, 2, 1, 0, 0, 1, 2, 0]


def contact_sheet(output):
    from PIL import Image, ImageDraw
    files = sorted((output / "screenshots").glob("*.png"))
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
    sheet.save(output / "supernova-contact-sheet.jpg")
    return [str(file.relative_to(output)) for file in files]


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
    base = runner.directory(root / "build/smoke-supernova", exist_ok=True)
    output = runner.directory(base / (time.strftime("%Y%m%d-%H%M%S") + f"-{os.getpid()}"))
    runtime = runner.directory(output / "client-runtime")
    (runtime / "options.txt").write_text(
        "version:1343\nrenderDistance:8\nmaxFps:60\nenableVsync:false\nfboEnable:true\n"
        "fullscreen:false\nguiScale:2\nparticles:0\nrenderClouds:false\npauseOnLostFocus:false\n"
        "autoJump:false\nnarrator:0\ntutorialStep:none\n", encoding="utf-8")
    report = {"passed": False, "output": str(output), "world": str(world), "events": [], "frames": [], "observer_frames": [], "commands": [],
              "capture_assertions": "Presence, size and timing only. Visual quality requires image review."}
    server_path, client_path = output / "supernova-server.log", output / "supernova-client.log"
    server = client = None
    roof = False

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
        if f"{count} launches" not in response or "Supernova" not in response or "Terrain unchanged" not in response:
            raise RuntimeError("Unexpected server launch status: " + response)

    def terrain():
        if "Successfully found" not in console("testforblock 256 3 256 minecraft:grass"):
            raise RuntimeError("Disposable target block is not intact grass")

    def prepare(sequence):
        for command in ("stellar cancel", "gamemode 1 StellarSmoke", "clear StellarSmoke",
                        "stellar_smoke_relocate origin", "give StellarSmoke stellar:stellar_remote",
                        "gamerule doDaylightCycle false", "gamerule doMobSpawning false", "time set day", "weather clear"):
            console(command)
        terrain()
        (output / f"ready-{sequence}").write_text("ready\n", encoding="utf-8")

    print(f"ARTIFACTS {output}", flush=True)
    with server_path.open("w", encoding="utf-8") as server_log, client_path.open("w", encoding="utf-8") as client_log:
        try:
            damage_base = runner.directory(output / "damage-probe")
            damage_manifest = dict(manifests["client"])
            report["damage_driver_classes_sha256"] = runner.isolate_driver(damage_manifest, damage_base, "SupernovaDamageSmokeMod")
            damage_driver = damage_manifest["classpath"].split(os.pathsep)[0]
            manifests["server"]["classpath"] = os.pathsep.join([damage_driver, manifests["server"]["classpath"]])
            manifests["server"]["jvmArgs"] = manifests["server"]["jvmArgs"] + ["-Dstellar.smoke.supernova.damage=true"]
            report["driver_classes_sha256"] = runner.isolate_driver(manifests["client"], output, "SupernovaClientSmokeMod")
            server = runner.launch(manifests["server"], "server", java_home, output, server_dir, server_log)
            runner.wait_for(server_path, "Done (", [server])
            runner.wait_for(server_path, "STELLAR_SUPERNOVA_DAMAGE_PASS", [server])
            report["damage_probe"] = next(line for line in server_path.read_text(encoding="utf-8", errors="replace").splitlines()
                                          if "STELLAR_SUPERNOVA_DAMAGE_PASS" in line)
            print(report["damage_probe"], flush=True)
            client = runner.launch(manifests["client"], "client", java_home, output, runtime, client_log, "supernova")
            runner.wait_for(client_path, PREFIX + "READY", [server, client])
            runner.wait_for(server_path, "StellarSmoke joined the game", [server, client])
            prepare(0)
            observed, casts, finished, skipped, canceled = set(), set(), set(), set(), set()
            menu = key = natural = passed = False
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
                        menu = "wireId=6 row=3" in event
                    elif event.startswith("SELECT") and sequence == 0:
                        key = "wireId=6 key=N" in event
                    elif event.startswith("CAST"):
                        casts.add(sequence)
                    elif event.startswith("LAUNCH"):
                        status(1)
                    elif event.startswith("FRAME"):
                        report["frames"].append({"sequence": sequence, "file": re.search(r"file=(\S+)", event)[1],
                            "requested_ticks": int(re.search(r"requestedTicks=(\d+)", event)[1]),
                            "actual_ticks": float(re.search(r"actualTicks=([\d.]+)", event)[1])})
                    elif event.startswith("OBSERVER_FRAME"):
                        report["observer_frames"].append({"file": re.search(r"file=(\S+)", event)[1],
                            "requested_ticks": int(re.search(r"requestedTicks=(\d+)", event)[1]),
                            "actual_ticks": float(re.search(r"actualTicks=([\d.]+)", event)[1]),
                            "distance": float(re.search(r"distance=([\d.]+)", event)[1]),
                            "perspective": int(re.search(r"perspective=(\d+)", event)[1]),
                            "shelter": float(re.search(r"shelter=([\d.Ee+-]+)", event)[1]),
                            "open_volume": float(re.search(r"openVolume=([\d.Ee+-]+)", event)[1]),
                            "muffled_volume": float(re.search(r"muffledVolume=([\d.Ee+-]+)", event)[1])})
                    elif event.startswith("SHELTER_REQUEST"):
                        # One explicitly owned temporary roof in the disposable test world, not gameplay terrain edits.
                        roof = "state=closed" in event
                        console("fill 4092 8 4092 4100 8 4100 minecraft:" + ("stone" if roof else "air"))
                    elif event.startswith("SKIP"):
                        status(1)
                        skipped.add(sequence)
                        # A real distant observer, with no cinema and the stellar origin beyond its far plane.
                        console("stellar_smoke_relocate observer")
                    elif event.startswith("CANCEL_REQUEST"):
                        if sequence not in skipped:
                            raise RuntimeError("Cancellation preceded local skip verification")
                        status(1)
                        console("stellar_smoke_relocate origin")
                        console("stellar cancel")
                        canceled.add(sequence)
                    elif event.startswith("NATURAL_END"):
                        natural = sequence == 0
                    elif event.startswith("FINISHED"):
                        status(0)
                        terrain()
                        finished.add(sequence)
                        if sequence == 0:
                            prepare(1)
                    elif event == "PASS":
                        passed = True
                if passed:
                    break
                if client.poll() is not None or server.poll() is not None:
                    raise RuntimeError("Test JVM exited before acceptance passed")
                time.sleep(0.1)
            frames = [(f["sequence"], f["requested_ticks"]) for f in report["frames"]]
            if not any(f["sequence"] == 0 and 320 <= f["actual_ticks"] < 322.6 for f in report["frames"]):
                raise RuntimeError("No framebuffer captured the brief Supernova impact drawing")
            if not (passed and casts == finished == {0, 1} and skipped == canceled == {1} and menu and key and natural
                    and frames == [(0, tick) for tick in FRAMES] + [(1, tick) for tick in FRAMES[:7]]
                    and [frame["requested_ticks"] for frame in report["observer_frames"]] == OBSERVER_FRAMES
                    and [frame["perspective"] for frame in report["observer_frames"]] == OBSERVER_MODES
                    and all(frame["distance"] > 4000 for frame in report["observer_frames"])):
                raise RuntimeError("Incomplete Supernova natural-end/skip acceptance evidence")
            client.wait(timeout=30)
            server.stdin.write(b"stop\n")
            server.stdin.flush()
            server.wait(timeout=30)
            if client.returncode != 0 or server.returncode != 0:
                raise RuntimeError("JVMs did not stop cleanly")
            report["captures"] = contact_sheet(output)
            names = {Path(file).name for file in report["captures"]}
            expected_files = {"supernova-skill-menu.png", "sequence-1-post-skip.png"}
            expected_files.update(f"sequence-{seq}-{kind}.png" for seq in (0, 1) for kind in ("baseline", "restored"))
            expected_files.update(frame["file"] for frame in report["frames"])
            expected_files.update(frame["file"] for frame in report["observer_frames"])
            if names != expected_files:
                raise RuntimeError("Missing framebuffer captures")
            logs = client_path.read_text(encoding="utf-8", errors="replace") + server_path.read_text(encoding="utf-8", errors="replace")
            if re.search(r"OpenGL Error|GL ERROR|SMOKE_FAIL|moved wrongly|moved too quickly|kicked for floating", logs, re.I):
                raise RuntimeError("Client/server reported rendering or movement errors")
            report["passed"] = True
            print("PASS: global damage, full film, distant star/F5 player models, sheltered witness SFX, skip/cancel, GL/camera/audio cleanup, terrain intact.", flush=True)
        except Exception as exception:
            report["error"] = str(exception)
            raise
        finally:
            if roof and server is not None and server.poll() is None:
                try:
                    console("fill 4092 8 4092 4100 8 4100 minecraft:air")
                except (OSError, RuntimeError):
                    report["roof_cleanup"] = "Server unavailable; only the disposable roof fixture may remain"
            for process in (client, server):
                if process is not None and process.poll() is None:
                    subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"], check=False)
            if "captures" not in report:
                report["captures"] = contact_sheet(output)
            (output / "supernova-report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
