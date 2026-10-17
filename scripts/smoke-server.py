"""Boot the real dedicated Forge dev server, exercise console commands, then stop cleanly."""
import argparse
import json
import os
from pathlib import Path
import queue
import subprocess
import socket
import struct
import threading
import time


def rcon(command):
    def receive(sock, size):
        data = b""
        while len(data) < size:
            part = sock.recv(size - len(data))
            if not part:
                raise RuntimeError("RCON closed early")
            data += part
        return data

    def packet(sock, request_id, kind, text):
        payload = struct.pack("<ii", request_id, kind) + text.encode() + b"\0\0"
        sock.sendall(struct.pack("<i", len(payload)) + payload)
        size = struct.unpack("<i", receive(sock, 4))[0]
        return receive(sock, size)

    with socket.create_connection(("127.0.0.1", 25592), timeout=5) as sock:
        response = packet(sock, 1, 3, "stellar-local-smoke")
        if struct.unpack("<i", response[:4])[0] == -1:
            raise RuntimeError("RCON authentication failed")
        response = packet(sock, 2, 2, command)
        return response[8:-2].decode("utf-8", errors="replace")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--java-home", required=True)
    parser.add_argument("--gradle-home")
    parser.add_argument("--railgun-check", action="store_true")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    manifest = json.loads((root / "build/dev-server-launch.json").read_text())
    command = [str(Path(args.java_home) / "bin/java.exe"), "-Xmx2G"] + manifest["jvmArgs"]
    command += ["-classpath", manifest["classpath"], manifest["main"]] + manifest["args"]
    env = dict(os.environ, **manifest["environment"])
    process = subprocess.Popen(command, cwd=manifest["directory"], env=env, stdin=subprocess.DEVNULL,
                               stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                               text=True, encoding="utf-8", errors="replace", bufsize=1)
    lines = queue.Queue()

    def reader():
        for line in process.stdout:
            lines.put(line)
        lines.put(None)

    threading.Thread(target=reader, daemon=True).start()
    deadline = time.monotonic() + 240
    ready = False
    stopping = False
    status = False
    output = []
    try:
        while time.monotonic() < deadline:
            try:
                line = lines.get(timeout=1)
            except queue.Empty:
                continue
            if line is None:
                break
            output.append(line)
            print(line, end="", flush=True)
            if 'Done (' in line:
                ready = True
                if args.railgun_check:
                    continue
            if ready and not stopping and (not args.railgun_check or "STELLAR_RAILGUN_SMOKE_PASS" in line):
                time.sleep(2)
                response = rcon("stellar status")
                print(response)
                output.append(response + "\n")
                status = "Beyond The Space:" in response
                response = rcon("stellar cancel")
                print(response)
                output.append(response + "\n")
                if "Beyond The Space launches canceled in this dimension." not in response:
                    raise RuntimeError("Cancel console command failed")
                stopping = True
                rcon("stop")
        if process.poll() is None:
            process.wait(timeout=30)
        report = root / "build" / "smoke-server.log"
        report.write_text("".join(output), encoding="utf-8")
        if not (ready and status and stopping and process.returncode == 0):
            raise SystemExit("Dedicated server smoke check failed; see build/smoke-server.log")
        print("PASS: dedicated server boot, Beyond The Space status, cancel, clean stop.")
    finally:
        if process.poll() is None:
            # Stop only the test process and its child JVMs, never other Java applications.
            subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"], check=False)


if __name__ == "__main__":
    main()
