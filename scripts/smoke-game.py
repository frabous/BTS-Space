"""Drive the real client/server with a development-only Forge test mod; capture frames from Minecraft's framebuffer."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import time

spec = importlib.util.spec_from_file_location("server_smoke", Path(__file__).with_name("smoke-server.py"))
server_smoke = importlib.util.module_from_spec(spec)
spec.loader.exec_module(server_smoke)


def launch(root, side, java_home, log, natural_end=False):
    data = json.loads((root / f"build/dev-{side}-launch.json").read_text())
    args = [str(Path(java_home) / "bin/java.exe"), "-Xmx2G",
            "-Dstellar.smoke.output=" + str(root / "build/smoke")] + data["jvmArgs"]
    if side == "client" and natural_end:
        args += ["-Dstellar.smoke.naturalEnd=true"]
    args += ["-classpath", data["classpath"], data["main"]] + data["args"]
    if side == "client":
        args += ["--username", "StellarSmoke", "--server", "127.0.0.1", "--port", "25591"]
    return subprocess.Popen(args, cwd=data["directory"], env=dict(os.environ, **data["environment"]),
                            stdin=subprocess.PIPE if side == "server" else subprocess.DEVNULL,
                            stdout=log, stderr=subprocess.STDOUT)


def wait_for(path, text, process, timeout=120):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if text in path.read_text(encoding="utf-8", errors="replace"):
            return
        if process.poll() is not None:
            raise RuntimeError(f"Process exited before {text}; see {path}")
        time.sleep(1)
    raise RuntimeError(f"Timed out waiting for {text}; see {path}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--java-home", required=True)
    parser.add_argument("--natural-end", action="store_true", help="Capture one full film and verify its natural ending instead of local skip")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    output = root / "build/smoke"
    output.mkdir(exist_ok=True)
    server = client = None
    with (output / "server.log").open("w", encoding="utf-8") as server_log, (output / "client.log").open("w", encoding="utf-8") as client_log:
        try:
            server = launch(root, "server", args.java_home, server_log)
            wait_for(output / "server.log", "Done (", server)
            client = launch(root, "client", args.java_home, client_log, natural_end=args.natural_end)
            wait_for(output / "server.log", "StellarSmoke joined the game", client, 150)
            for command in ["gamemode 1 StellarSmoke", "clear StellarSmoke", "give StellarSmoke stellar:stellar_remote",
                            "tp StellarSmoke 256 80 256 0 90", "gamerule doDaylightCycle false", "time set day"]:
                # Legacy dev RCON bypasses the server queue; setup can race the entity tracker.
                server.stdin.write((command + "\n").encode("utf-8"))
            server.stdin.write(b"list\n"); server.stdin.flush()
            wait_for(output / "server.log", "players online:", server)
            deadline = time.monotonic() + 180
            observed = set()
            casts = finished = 0
            natural_ends = skips = 0
            passed = False
            while time.monotonic() < deadline:
                content = (output / "client.log").read_text(encoding="utf-8", errors="replace")
                for line in content.splitlines():
                    if "STELLAR_SMOKE_" not in line or line in observed:
                        continue
                    observed.add(line)
                    print(line, flush=True)
                    if "STELLAR_SMOKE_CAST" in line or "STELLAR_SMOKE_SKIP" in line:
                        if "STELLAR_SMOKE_SKIP" in line:
                            skips += 1
                            if args.natural_end:
                                raise RuntimeError("Natural-ending smoke unexpectedly skipped the film")
                        time.sleep(0.5)
                        status = server_smoke.rcon("stellar status")
                        print(status, flush=True)
                        if "1 launches" not in status or "Terrain unchanged" not in status:
                            raise RuntimeError("Cast or local skip did not preserve the expected server state")
                        if "STELLAR_SMOKE_CAST" in line:
                            casts += 1
                    if "STELLAR_SMOKE_FINISHED" in line:
                        finished += 1
                        if "0 launches" not in server_smoke.rcon("stellar status"):
                            raise RuntimeError("Server launch did not clean up after presentation")
                    if "STELLAR_SMOKE_NATURAL_END" in line:
                        natural_ends += 1
                    if "STELLAR_SMOKE_PASS" in line:
                        passed = True
                if passed:
                    break
                if client.poll() is not None:
                    raise RuntimeError("Client exited before all smoke checks passed")
                time.sleep(0.5)
            if not passed or casts != 1 or finished != 1:
                raise RuntimeError("Incomplete Railgun client/server smoke test")
            if args.natural_end and natural_ends != 1:
                raise RuntimeError("Natural film ending was not verified")
            if not args.natural_end and skips != 1:
                raise RuntimeError("Local film skip was not verified")
            client.wait(timeout=30)
            server_smoke.rcon("stop")
            server.wait(timeout=30)
            if client.returncode or server.returncode:
                raise RuntimeError("Test JVMs did not stop cleanly")
            ending = "natural ending, restored HUD/camera and stopped soundtrack" if args.natural_end else "local skip"
            print(f"PASS: real client/server handshake, Railgun strike, synchronized selection, film, menu, {ending} and cleanup.")
        finally:
            for process in [client, server]:
                if process is not None and process.poll() is None:
                    subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"], check=False)


if __name__ == "__main__":
    main()
