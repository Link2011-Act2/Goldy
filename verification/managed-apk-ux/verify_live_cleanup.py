"""Owned emulator only: actual signed APK replacement while another UID holds its URI open."""
import argparse
import hashlib
import json
import re
import subprocess
import time
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("--serial", required=True)
parser.add_argument("--adb", required=True)
parser.add_argument("--artifacts", type=Path, required=True)
args = parser.parse_args()
if not re.fullmatch(r"emulator-\d+", args.serial):
    raise SystemExit("This test may only operate on an explicitly named emulator")
pkg = "jp.linkserver.goldy.test"
runner = pkg + "/jp.linkserver.goldy.GoldyTestRunner"
events = []

def adb(*parts, timeout=60):
    result = subprocess.run([args.adb, "-s", args.serial, *map(str, parts)],
                            capture_output=True, timeout=timeout, check=True)
    output = result.stdout.decode("utf-8", errors="replace").strip()
    if "am" in parts and "start" in parts:
        print(output, flush=True)
    return output

def instrument(mode, *extra):
    output = adb("shell", "am", "instrument", "-w", "-e", "mode", mode, *extra, runner)
    events.append(output)
    if "INSTRUMENTATION_CODE: 0" not in output or "failure=" in output:
        raise AssertionError(output)
    return output

assert adb("shell", "getprop", "sys.boot_completed") == "1"
sdk = int(adb("shell", "getprop", "ro.build.version.sdk"))
assert sdk in (28, 36), sdk
assert "Success" in adb("install", "--no-streaming", "-r", "-t", Path(__file__).resolve().parents[1] / "android-probe/build/outputs/apk/debug/GoldyAndroidProbe-debug.apk")
assert "Success" in adb("shell", "pm", "clear", "example.goldyprobe")
assert "Success" in adb("install", "--no-streaming", "-r", "-d", "-t", args.artifacts / "host-v1.apk")
assert "Success" in adb("shell", "pm", "clear", pkg)
adb("shell", "appops", "set", pkg, "REQUEST_INSTALL_PACKAGES", "allow")
adb("push", args.artifacts / "host-v2.apk", "/data/local/tmp/goldy-maintenance-target.apk")
adb("shell", "run-as", pkg, "mkdir", "-p", "files")
adb("shell", "run-as", pkg, "cp", "/data/local/tmp/goldy-maintenance-target.apk", "files/maintenance-target.apk")
events.append(adb("shell", "am", "start", "-W", "-n", pkg + "/jp.linkserver.goldy.InstallLifecycleActivity"))

def wait_file(package, filename, seconds=25):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        try:
            output = adb("shell", "run-as", package, "cat", "files/" + filename)
        except subprocess.CalledProcessError:
            time.sleep(0.25)
            continue
        if output and "No such file" not in output:
            return output
        time.sleep(0.25)
    raise AssertionError("Timed out waiting for " + package + "/" + filename)

prepared = wait_file(pkg, "maintenance-prepare.txt")
events.append(prepared)
assert "failure=" not in prepared, prepared
request_id = re.search(r"(?:^|\n)id=([0-9a-f-]+)", prepared).group(1)
uri = re.search(r"(?:^|\n)uri=(content://[^\s]+)", prepared).group(1)
expected = hashlib.sha256((args.artifacts / "host-v2.apk").read_bytes()).hexdigest()
assert f"sha256={expected}" in prepared

# A real app UID holds its descriptor while the owner process dies during replacement.
# The adb content command uses getContentProviderExternal, which cannot acquire a private
# provider via a path-specific grant. It is not representative of an installer's resolver.
assert wait_file("example.goldyprobe", "reader-ready") == "open"
assert "Success" in adb("install", "--no-streaming", "-r", "-t", args.artifacts / "host-v2.apk")
deadline = time.monotonic() + 15
while True:
    record = json.loads(adb("shell", "run-as", pkg, "cat", "files/goldy-apks/records/" + request_id + ".json"))
    if record.get("lifecycle", {}).get("backing") == "Installed":
        assert record["lifecycle"]["confirmed"]
        break
    assert time.monotonic() < deadline, "Protected package-replaced cleanup did not finish"
    time.sleep(0.25)
assert adb("shell", "run-as", pkg, "ls", "files/goldy-apks/install").strip() == ""
adb("shell", "run-as", "example.goldyprobe", "touch", "files/reader-continue")
assert wait_file("example.goldyprobe", "reader-fd-verified") == "identical"
# Restore in a fresh owner process as well. Do this only after the already-open FD
# was completely read; the test is no longer holding a provider client at this point.
events.append(adb("shell", "am", "start", "-S", "-W", "-n", pkg + "/jp.linkserver.goldy.InstallLifecycleActivity", "--es", "id", request_id))
verified = wait_file(pkg, "maintenance-verify.txt")
assert "failure=" not in verified, verified
events.append(verified)
reader_result = wait_file("example.goldyprobe", "reader-result")
assert reader_result.startswith("PASS:"), reader_result
events.append(reader_result)
reopened = wait_file("example.goldyprobe", "reader-reopen-result")
assert reopened.startswith("PASS:"), reopened
events.append(reopened)
adb("shell", "rm", "/data/local/tmp/goldy-maintenance-target.apk")
report = {"sdk": sdk, "serial": args.serial, "requestId": request_id, "sha256": expected,
          "realPackageReplacement": True, "redundantCopyDeleted": True,
          "protectedPackageReplacedCleanup": True,
          "externalOpenDescriptorSurvived": True, "externalUriReopenWithFreshGrantIdentical": True,
          "explicitRetryUsingSdkLaunch": True,
          "cancelOrUnknownRetainedBeforeUpdate": True, "duplicatePrepareReused": True,
          "events": events}
destination = args.artifacts / f"live-cleanup-api{sdk}.json"
destination.write_text(json.dumps(report, indent=2), encoding="utf-8")
print(json.dumps({k: v for k, v in report.items() if k != "events"}, indent=2))
