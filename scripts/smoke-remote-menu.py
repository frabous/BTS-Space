"""Exercise the holographic remote in a real client/server; save framebuffer and sound-engine evidence."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import socket
import subprocess
import time

spec = importlib.util.spec_from_file_location("remote_runner", Path(__file__).with_name("smoke-moon.py"))
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)
PREFIX = "STELLAR_REMOTE_SMOKE_"


def contact_sheet(output):
    from PIL import Image, ImageDraw
    files = sorted((output / "screenshots").glob("*.png"))
    sheet = Image.new("RGB", (1260, ((len(files) + 2) // 3) * 258), "#091522")
    draw = ImageDraw.Draw(sheet)
    for index, file in enumerate(files):
        with Image.open(file) as image:
            if image.size != (1280, 720):
                raise RuntimeError("Unexpected framebuffer size: " + str(file))
            image.thumbnail((420, 236))
            x, y = index % 3 * 420, index // 3 * 258
            sheet.paste(image.convert("RGB"), (x, y))
            draw.text((x + 5, y + 239), file.name, fill="white")
    sheet.save(output / "remote-contact-sheet.jpg")
    return [str(file.relative_to(output)) for file in files]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-home", required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    java_home = Path(args.java_home).resolve()
    if not (java_home / "bin/java.exe").is_file():
        raise RuntimeError("Provide the Java 8 JDK used for the development build")
    server_dir = root / "run-server"
    world = server_dir / "stellar-smoke-world"
    if not world.is_dir() or world.is_symlink() or server_dir.is_symlink():
        raise RuntimeError("Expected the existing disposable run-server/stellar-smoke-world")
    properties = dict(line.split("=", 1) for line in (server_dir / "server.properties").read_text().splitlines()
                      if line and not line.startswith(("#", "!")) and "=" in line)
    expected = {"level-name": "stellar-smoke-world", "server-ip": "127.0.0.1", "server-port": "25591",
                "online-mode": "false", "enable-rcon": "true", "rcon.port": "25592", "rcon.password": "stellar-local-smoke"}
    if any(properties.get(key) != value for key, value in expected.items()):
        raise RuntimeError("Server is not configured for the disposable localhost test")
    for port in (25591, 25592):
        with socket.socket() as probe:
            probe.bind(("127.0.0.1", port))
    manifests = {side: json.loads((root / f"build/dev-{side}-launch.json").read_text()) for side in ("client", "server")}
    if Path(manifests["server"]["directory"]).resolve() != server_dir.resolve():
        raise RuntimeError("Exported server does not target the disposable world")
    base = runner.directory(root / "build/smoke-remote", exist_ok=True)
    output = runner.directory(base / (time.strftime("%Y%m%d-%H%M%S") + f"-{os.getpid()}"))
    runtime = runner.directory(output / "client-runtime")
    (runtime / "options.txt").write_text(
        "version:1343\nrenderDistance:6\nmaxFps:60\nenableVsync:false\nfboEnable:true\n"
        "fullscreen:false\nguiScale:2\nparticles:0\nrenderClouds:false\npauseOnLostFocus:false\n"
        "autoJump:false\nnarrator:0\ntutorialStep:none\nsoundCategory_music:0.0\n", encoding="utf-8")
    report = {"passed": False, "output": str(output), "events": [], "screenshots": [], "commands": [],
              "capture_assertions": "Real framebuffer captures; visual quality requires image review."}
    server_path, client_path = output / "remote-server.log", output / "remote-client.log"
    server = client = None

    def console(command):
        offset = server_path.stat().st_size
        server.stdin.write((command + "\nlist\n").encode("utf-8")); server.stdin.flush()
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
        raise RuntimeError("Server console timeout: " + command)

    print("ARTIFACTS " + str(output), flush=True)
    with server_path.open("w", encoding="utf-8") as server_log, client_path.open("w", encoding="utf-8") as client_log:
        try:
            report["driver_classes_sha256"] = runner.isolate_driver(manifests["client"], output, "RemoteMenuSmokeMod")
            server = runner.launch(manifests["server"], "server", java_home, output, server_dir, server_log)
            runner.wait_for(server_path, "Done (", [server])
            client = runner.launch(manifests["client"], "client", java_home, output, runtime, client_log, "remote")
            runner.wait_for(client_path, PREFIX + "READY", [server, client])
            runner.wait_for(server_path, "StellarSmoke joined the game", [server, client])
            for command in ("gamemode 1 StellarSmoke", "clear StellarSmoke", "give StellarSmoke stellar:stellar_remote",
                            "tp StellarSmoke 264.5 4 256.5 0 0", "gamerule doDaylightCycle false", "time set day", "weather clear"):
                console(command)
            (output / "ready").write_text("ready\n")
            runner.wait_for(client_path, PREFIX + "PASS", [server, client], 90)
            content = client_path.read_text(encoding="utf-8", errors="replace")
            report["events"] = [line for line in content.splitlines() if PREFIX in line]
            for line in report["events"]:
                print(line, flush=True)
            if PREFIX + "FAIL" in content or len([line for line in report["events"] if PREFIX + "SELECT" in line]) != 4:
                raise RuntimeError("Incomplete remote menu interaction checks")
            if "0 launches" not in console("stellar status"):
                raise RuntimeError("Selecting a strike unexpectedly launched it")
            report["screenshots"] = contact_sheet(output)
            if len(report["screenshots"]) != 23:
                raise RuntimeError("Expected opening, hover, briefing, d-pad, GUI-scale, restored-world, "
                                   "first-person press and third-person frames")
            client.wait(timeout=30)
            server.stdin.write(b"stop\n"); server.stdin.flush(); server.wait(timeout=30)
            if client.returncode or server.returncode:
                raise RuntimeError("Test JVMs did not stop cleanly")
            report["passed"] = True
            print("PASS: four server-synchronized selections, perspective picking, briefings, keyboard, GUI scales, five real audio cues, "
                  "first-person thumb presses, third-person views and cleanup.")
        finally:
            for process in (client, server):
                if process is not None and process.poll() is None:
                    subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"], check=False)
            if client_path.exists() and not report["events"]:
                report["events"] = [line for line in client_path.read_text(encoding="utf-8", errors="replace").splitlines() if PREFIX in line]
            (output / "report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
