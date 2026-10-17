"""Connected Moon acceptance test; Minecraft framebuffer captures are review artifacts, not shape assertions."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import time

spec = importlib.util.spec_from_file_location("moon_server_smoke", Path(__file__).with_name("smoke-server.py"))
server_smoke = importlib.util.module_from_spec(spec)
spec.loader.exec_module(server_smoke)

PREFIX = "STELLAR_MOON_SMOKE_"
PACKAGE = Path("fr/frabous/stellar/smoke")
FRAMES = [10, 20, 30, 40, 48, 56, 66, 78, 92, 104, 116, 126, 136, 150, 164, 174, 186, 198, 212, 228, 246, 280,
          320, 346, 364, 372, 380, 390, 420, 460, 500, 524, 544, 558, 566, 572, 580, 596, 620, 660, 740, 830]
SKIP_FRAMES = 22
# Synthesized SS-03 set (generate-asteroid-puller-sfx.py): each must start during the natural film.
SS03_SOUNDS = {"carrier_warp_open", "carrier_warp_collapse", "carrier_brake", "carrier_bay_open",
               "carrier_clamp_release", "carrier_drive_loop", "carrier_spool", "lance_reentry", "lance_retro",
               "lance_touchdown", "lance_anchor", "lance_unfold", "lance_harpoon", "tether_lock",
               "tether_strain_loop", "tether_snap", "asteroid_sonic_boom"}


def directory(path, exist_ok=False):
    if not path.parent.is_dir() or path.parent.is_symlink() or path.is_symlink():
        raise RuntimeError(f"Missing or unsafe parent directory for {path}")
    path.mkdir(exist_ok=exist_ok)
    return path


def isolate_driver(manifest, output, name="MoonClientSmokeMod"):
    entries = manifest["classpath"].split(os.pathsep)
    sources = [Path(entry) for entry in entries if (Path(entry) / PACKAGE / f"{name}.class").is_file()]
    if len(sources) != 1:
        raise RuntimeError(f"Export compiled {name} classes with exportDevLaunches -Psmoke first")
    driver = directory(output / ("moon-driver" if name == "MoonClientSmokeMod" else "supernova-driver"))
    target = driver
    for part in PACKAGE.parts:
        target = directory(target / part)
    classes = list((sources[0] / PACKAGE).glob(f"{name}*.class"))
    for source in classes:
        shutil.copy2(source, target / source.name)
    # ClientSmokeMod registers by default. Exclude the entire shared smoke output
    # and copy only this gated driver, including any compiler-generated inner classes.
    isolated = []
    for entry in entries:
        if any((Path(entry) / PACKAGE / f"{mod}.class").is_file()
               for mod in (name, "ClientSmokeMod", "BlackholeClientSmokeMod")):
            if (Path(entry) / "fr/frabous/stellar/client/LocalClientBridge.class").is_file():
                raise RuntimeError("Smoke and production classes share an output directory; cannot isolate drivers")
            continue
        isolated.append(entry)
    manifest["classpath"] = os.pathsep.join([str(driver)] + isolated)
    return {source.name: hashlib.sha256(source.read_bytes()).hexdigest() for source in classes}


def launch(manifest, side, java_home, output, runtime, log, feature="moon"):
    command = [str(java_home / "bin/java.exe"), "-Xmx3G"] + manifest["jvmArgs"]
    arguments = manifest["args"][:]
    if side == "client":
        command += [f"-Dstellar.smoke.{feature}=true", "-Dstellar.smoke.blackhole=false", f"-Dstellar.smoke.output={output}"]
        managed = {"--username", "--server", "--port", "--width", "--height", "--gameDir"}
        filtered = []
        index = 0
        while index < len(arguments):
            if arguments[index] in managed:
                index += 2
            else:
                filtered.append(arguments[index]); index += 1
        arguments = filtered + ["--username", "StellarSmoke", "--server", "127.0.0.1", "--port", "25591",
                                "--width", "1280", "--height", "720", "--gameDir", str(runtime)]
    command += ["-classpath", manifest["classpath"], manifest["main"]] + arguments
    return subprocess.Popen(command, cwd=runtime, env=dict(os.environ, **manifest["environment"]),
                            stdin=subprocess.PIPE if side == "server" else subprocess.DEVNULL,
                            stdout=log, stderr=subprocess.STDOUT)


def wait_for(path, text, processes, timeout=180):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if text in path.read_text(encoding="utf-8", errors="replace"):
            return
        if any(process.poll() is not None for process in processes):
            raise RuntimeError(f"Test JVM exited before {text}; see {path}")
        time.sleep(0.2)
    raise RuntimeError(f"Timed out waiting for {text}; see {path}")


def contact_sheet(output):
    from PIL import Image, ImageDraw
    def order(file):
        if file.name.startswith("moon-skill-"):
            return -1, 0 if file.name == "moon-skill-menu.png" else 1
        sequence = int(re.search(r"sequence-(\d+)", file.name)[1])
        frame = re.search(r"moon-(\d+)ticks", file.name)
        age = int(frame[1]) if frame else -1 if "baseline" in file.name else 280 if "post-skip" in file.name else 1000
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
                raise RuntimeError(f"Unexpected framebuffer dimensions for {file}: {image.size}")
            image.thumbnail((320, 180))
            x, y = index % 3 * 320, index // 3 * 205
            sheet.paste(image.convert("RGB"), (x, y))
            draw.text((x + 4, y + 182), file.name, fill="white")
    sheet.save(output / "moon-contact-sheet.jpg")
    return [str(file.relative_to(output)) for file in files]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--java-home", required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    java_home = Path(args.java_home).resolve()
    if not (java_home / "bin/java.exe").is_file():
        raise RuntimeError("--java-home must identify the Java 8 JDK used for the exported development build")
    server_dir = root / "run-server"
    if not server_dir.is_dir() or server_dir.is_symlink() or not (server_dir / "stellar-smoke-world").is_dir():
        raise RuntimeError("Expected the existing disposable run-server/stellar-smoke-world")
    properties = {}
    for line in (server_dir / "server.properties").read_text(encoding="utf-8").splitlines():
        if line and not line.startswith(("#", "!")) and "=" in line:
            key, value = line.split("=", 1); properties[key.strip()] = value.strip()
    expected = {"level-name": "stellar-smoke-world", "server-ip": "127.0.0.1", "server-port": "25591",
                "online-mode": "false", "enable-rcon": "true", "rcon.port": "25592", "rcon.password": "stellar-local-smoke"}
    if any(properties.get(key) != value for key, value in expected.items()):
        raise RuntimeError("Server properties do not match the disposable localhost smoke configuration")
    if (server_dir / "stellar-smoke-world").is_symlink():
        raise RuntimeError("Disposable smoke world must not be a symlink")
    for port in (25591, 25592):
        with socket.socket() as probe:
            probe.bind(("127.0.0.1", port))
    manifests = {side: json.loads((root / f"build/dev-{side}-launch.json").read_text(encoding="utf-8"))
                 for side in ("server", "client")}
    if Path(manifests["server"]["directory"]).resolve() != server_dir.resolve():
        raise RuntimeError("Exported server launch does not target the disposable run-server directory")
    output_root = directory(root / "build/smoke-moon", exist_ok=True)
    output = directory(output_root / (time.strftime("%Y%m%d-%H%M%S") + f"-{os.getpid()}"))
    client_dir = directory(output / "client-runtime")
    (client_dir / "options.txt").write_text(
        "version:1343\nrenderDistance:8\nmaxFps:60\nenableVsync:false\nfboEnable:true\n"
        "fullscreen:false\nguiScale:2\nparticles:0\nrenderClouds:false\n"
        "pauseOnLostFocus:false\nautoJump:false\nnarrator:0\ntutorialStep:none\n", encoding="utf-8")
    report = {"passed": False, "output": str(output), "server_directory": str(server_dir),
              "world": "stellar-smoke-world", "events": [], "commands": [], "frames": [],
              "requested_seconds": [tick / 20 for tick in FRAMES],
              "capture_assertions": "File presence, framebuffer dimensions and recorded timing only; shape fidelity requires review."}
    server = client = None
    server_path, client_path = output / "moon-server.log", output / "moon-client.log"

    def console(command):
        # RCON mutates entities off-thread in this legacy dev runtime. Queue all
        # setup/cancel commands on the real server thread and wait for a list barrier.
        offset = server_path.stat().st_size
        server.stdin.write((command + "\nlist\n").encode("utf-8")); server.stdin.flush()
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            with server_path.open("rb") as stream:
                stream.seek(offset)
                response = stream.read().decode("utf-8", errors="replace")
            if "players online:" in response:
                report["commands"].append({"command": command, "transport": "server-console", "response": response})
                return response
            if server.poll() is not None:
                raise RuntimeError("Server exited while executing " + command)
            time.sleep(0.05)
        raise RuntimeError("Console barrier timed out for " + command)

    def status(expected_count):
        response = console("stellar status")
        if f"{expected_count} launches" not in response or "Asteroid Puller" not in response or "Terrain unchanged" not in response:
            raise RuntimeError("Unexpected authoritative Moon status: " + response)
        return response

    def terrain():
        response = console("testforblock 256 3 256 minecraft:grass")
        if "Successfully found" not in response:
            raise RuntimeError("Disposable target terrain was altered or is not flat grass: " + response)

    def prepare(sequence):
        for command in ("stellar cancel", "gamemode 1 StellarSmoke", "clear StellarSmoke",
                        "tp StellarSmoke 264.5 4 256.5 0 90", "give StellarSmoke stellar:stellar_remote",
                        "gamerule doDaylightCycle false", "gamerule doMobSpawning false", "time set day", "weather clear"):
            console(command)
        terrain()
        (output / f"ready-{sequence}").write_text("ready\n", encoding="utf-8")

    print(f"ARTIFACTS {output}", flush=True)
    with server_path.open("w", encoding="utf-8") as server_log, client_path.open("w", encoding="utf-8") as client_log:
        try:
            report["driver_classes_sha256"] = isolate_driver(manifests["client"], output)
            server = launch(manifests["server"], "server", java_home, output, server_dir, server_log)
            wait_for(server_path, "Done (", [server])
            client = launch(manifests["client"], "client", java_home, output, client_dir, client_log)
            wait_for(client_path, PREFIX + "READY", [server, client])
            wait_for(server_path, "StellarSmoke joined the game", [server, client])
            prepare(0)
            deadline = time.monotonic() + 240
            observed, casts, finished, skipped, canceled, audio = set(), set(), set(), set(), set(), set()
            natural_end = menu_selected = key_selected = passed = False
            while time.monotonic() < deadline:
                content = client_path.read_text(encoding="utf-8", errors="replace")
                for line in content.splitlines():
                    if PREFIX not in line or line in observed:
                        continue
                    observed.add(line); report["events"].append(line)
                    print(line, flush=True)
                    event = line.split(PREFIX, 1)[1]
                    if event.startswith("FAIL"):
                        raise RuntimeError(line)
                    sequence_match = re.search(r"sequence=(\d+)", event)
                    sequence = int(sequence_match[1]) if sequence_match else None
                    if event.startswith("MENU_SELECTED "):
                        menu_selected = "wireId=5 row=2" in event
                    elif event.startswith("SELECT ") and sequence == 0:
                        key_selected = "wireId=5 key=M" in event
                    elif event.startswith("CAST "):
                        casts.add(sequence)
                    elif event.startswith("LAUNCH "):
                        status(1)
                    elif event.startswith("FRAME "):
                        report["frames"].append({"sequence": sequence,
                            "file": re.search(r"file=(\S+)", event)[1],
                            "requested_ticks": float(re.search(r"requestedTicks=([\d.]+)", event)[1]),
                            "actual_ticks": float(re.search(r"actualTicks=([\d.]+)", event)[1])})
                    elif event.startswith("AUDIO_SOURCE ") and sequence == 0:
                        audio.add(re.search(r"name=stellar:cinematic\.(\S+)", event)[1])
                    elif event.startswith("SKIP "):
                        status(1); skipped.add(sequence)
                    elif event.startswith("CANCEL_REQUEST "):
                        if sequence not in skipped:
                            raise RuntimeError("Server cancellation preceded local skip verification")
                        status(1); console("stellar cancel"); canceled.add(sequence)
                    elif event.startswith("NATURAL_END "):
                        natural_end = sequence == 0
                    elif event.startswith("FINISHED "):
                        status(0); terrain(); finished.add(sequence)
                        if sequence == 0:
                            prepare(1)
                    elif event == "PASS":
                        passed = True
                if passed:
                    break
                if client.poll() is not None or server.poll() is not None:
                    raise RuntimeError("Test JVM exited before the connected Moon checks passed")
                time.sleep(0.1)
            expected_frames = [(0, tick) for tick in FRAMES] + [(1, tick) for tick in FRAMES[:SKIP_FRAMES]]
            actual_frames = [(frame["sequence"], frame["requested_ticks"]) for frame in report["frames"]]
            if not (passed and casts == finished == {0, 1} and skipped == canceled == {1}
                    and natural_end and menu_selected and key_selected and actual_frames == expected_frames):
                raise RuntimeError("Incomplete natural-ending/local-skip Moon acceptance test")
            if SS03_SOUNDS - audio:
                raise RuntimeError("SS-03 sounds never started: " + ", ".join(sorted(SS03_SOUNDS - audio)))
            client.wait(timeout=30)
            server_smoke.rcon("stop"); server.wait(timeout=30)
            if client.returncode != 0 or server.returncode != 0:
                raise RuntimeError("Test JVMs did not stop cleanly")
            report["captures"] = contact_sheet(output)
            expected_files = {"moon-skill-menu.png", "moon-skill-details.png", "sequence-1-post-skip.png"}
            expected_files.update(f"sequence-{sequence}-{kind}.png" for sequence in (0, 1) for kind in ("baseline", "restored"))
            expected_files.update(frame["file"] for frame in report["frames"])
            if {Path(file).name for file in report["captures"]} != expected_files:
                raise RuntimeError(f"Missing or unexpected Moon Minecraft framebuffer captures (expected {len(expected_files)})")
            combined = client_path.read_text(encoding="utf-8", errors="replace") + server_path.read_text(encoding="utf-8", errors="replace")
            failures = re.findall(r"[^\n]*(?:OpenGL Error|GL ERROR|SMOKE_FAIL|moved wrongly|moved too quickly|kicked for floating|unknown soundEvent|CodecJOrbis)[^\n]*",
                                  combined, flags=re.IGNORECASE)
            if failures:
                raise RuntimeError("Client/server rendering or movement failures: " + "\n".join(failures))
            report["passed"] = True
            print("PASS: Moon third-row/M selection, 43s natural film with all 17 SS-03 sounds started, local skip retains server launch, cancel, camera/audio cleanup and clean JVM stop.", flush=True)
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
            (output / "moon-report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
