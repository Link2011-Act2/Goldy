"""Verify a naming-only refactor against an existing published Gold AAR.

Compile a client against the old JVM facade, then run that same bytecode with
only the new Gold JAR. No producer source or old Gold JAR is used at new runtime.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument("--baseline-dir", type=Path, required=True)
parser.add_argument("--baseline-commit", required=True)
parser.add_argument("--output-dir", type=Path, required=True)
parser.add_argument("--stdlib", type=Path, required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent
output = args.output_dir.resolve()
output.mkdir(parents=True, exist_ok=True)
bin_dir = Path(os.environ["JAVA_HOME"]) / "bin"


def run(command):
    return subprocess.run([str(item) for item in command], cwd=root, check=True,
                          capture_output=True, text=True, encoding="utf-8").stdout


old_path = "gold/src/main/java/jp/linkserver/goldy/internal/ShutUpVersions.kt"
original = run(["git", "show", args.baseline_commit + ":" + old_path])
canonical = (root / old_path.replace("ShutUpVersions.kt", "ReleaseVersions.kt")).read_text(encoding="utf-8")
assert original.replace("compareShutUpVersions(", "compareReleaseVersions(") == canonical, "Comparator source changed beyond its name"

baseline = list(args.baseline_dir.glob("gold-*.aar"))
assert len(baseline) == 1, baseline
jars = {}
for label, aar in (("before", baseline[0]), ("after", root / "gold/build/outputs/aar/gold-release.aar")):
    target = output / ("gold-" + label + ".jar")
    with zipfile.ZipFile(aar) as archive:
        target.write_bytes(archive.read("classes.jar"))
    jars[label] = target

facade = "jp.linkserver.goldy.internal.ShutUpVersionsKt"
method_sets = {}
for label, jar in jars.items():
    symbols = run([bin_dir / "javap.exe", "-classpath", jar, "-public", "-s", facade])
    (output / ("internal-" + label + ".txt")).write_text(symbols, encoding="utf-8")
    method_sets[label] = set(re.findall(r"(public static final .*?;\n\s+descriptor: [^\n]+)", symbols))
assert len(method_sets["before"]) == 2, method_sets["before"]
assert method_sets["before"] <= method_sets["after"], "Old internal JVM methods missing"

corpus = json.loads((root / "gold/src/test/resources/four-app-versions.json").read_text(encoding="utf-8"))
versions = set(value for values in corpus["versions"].values() for value in values)
versions.update(["", "nightly", "1..0", "1.0-", "1.0/Release", "1.0-Preview", "1.0-Internal",
                 "1.0-IntDev", "1.0-IntDev2", "1.0-IntDev_rev0", "1.0-IntDev_rev2", "1.0-IntDev_rev10",
                 "1.0-Beta2", "1.0-Beta10", "1.0-Beta2_rev3", "1.0-Beta2_rev10", "1.0-Beta-rev3",
                 "1.0-Beta.rev3", "1.0-Beta_rev3", "1.0-RC1", "1.0-Stable", "1.0-Release", "v1.0",
                 "1.0+build.2", "9223372036854775808.0", "1.0-IntDev_rev9223372036854775808",
                 "1.0-IntDev_rev2147483648", " V1.0-BETA02 "])
versions = sorted(versions)
inputs = output / "versions.txt"
inputs.write_text("\n".join(versions) + "\n", encoding="utf-8")
client = output / "LegacyVersionClient.java"
client.write_text('''import java.nio.file.*;
import java.util.*;
import jp.linkserver.goldy.internal.ShutUpVersionsKt;
public final class LegacyVersionClient {
    public static void main(String[] args) throws Exception {
        List<String> versions = Files.readAllLines(Paths.get(args[0]));
        for (String left : versions) {
            System.out.println("channel=" + ShutUpVersionsKt.commonReleaseChannel(left).name());
            for (String right : versions) {
                System.out.println("compare=" + ShutUpVersionsKt.compareShutUpVersions(left, right));
            }
        }
    }
}
''', encoding="utf-8")
classes = output / "client-classes"
classes.mkdir(exist_ok=True)
run([bin_dir / "javac.exe", "-encoding", "UTF-8", "--release", "11", "-classpath",
     os.pathsep.join([str(jars["before"]), str(args.stdlib)]), "-d", classes, client])
outputs = {}
for label, jar in jars.items():
    outputs[label] = run([bin_dir / "java.exe", "-classpath",
                          os.pathsep.join([str(classes), str(jar), str(args.stdlib)]),
                          "LegacyVersionClient", inputs])
    (output / ("client-" + label + ".txt")).write_text(outputs[label], encoding="utf-8")
assert outputs["before"] == outputs["after"], "Previously compiled client behavior changed"
assert len(outputs["after"].splitlines()) == len(versions) * (len(versions) + 1)
result = dict(passed=True, baselineCommit=args.baseline_commit, algorithmSourceUnchanged=True,
              legacyFacade=facade, retainedInternalMethods=len(method_sets["before"]),
              clientCompiledAgainst="baseline AAR", newRuntimeUsesBaselineGold=False,
              corpusVersions=len(versions), exactPairComparisons=len(versions) ** 2,
              exactChannelComparisons=len(versions))
(output / "result.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
print(json.dumps(result, indent=2))
