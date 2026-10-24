"""Check retained public JVM descriptors against previously published AARs.

Internal packages, Kotlin coroutine/lambda classes and anonymous implementations
are excluded. New public descriptors are allowed; behavioral policy changes
require separate characterization tests.
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
parser.add_argument("--output-dir", type=Path, required=True)
args = parser.parse_args()
args.output_dir.mkdir(parents=True, exist_ok=True)
javap = Path(os.environ["JAVA_HOME"]) / "bin" / ("javap.exe" if os.name == "nt" else "javap")
records = []
for module in ("gold", "elief"):
    baseline = list(args.baseline_dir.glob(f"{module}-*.aar"))
    assert len(baseline) == 1, baseline
    current = Path(module) / "build/outputs/aar" / f"{module}-release.aar"
    jars = {}
    for label, aar in (("before", baseline[0]), ("after", current)):
        target = args.output_dir / f"{module}-{label}.jar"
        with zipfile.ZipFile(aar) as archive:
            target.write_bytes(archive.read("classes.jar"))
        jars[label] = target
    with zipfile.ZipFile(jars["before"]) as archive:
        targets = sorted(entry[:-6].replace("/", ".") for entry in archive.namelist()
                         if entry.startswith("jp/linkserver/goldy/") and entry.endswith(".class")
                         and "/internal/" not in entry and not re.search(r"\$\d", entry))

    def descriptors(label):
        run = subprocess.run([str(javap), "-classpath", str(jars[label]), "-public", "-s", *targets],
                             capture_output=True, text=True, check=True)
        (args.output_dir / f"{module}-{label}.txt").write_text(run.stdout, encoding="utf-8")
        values = set()
        current_type = previous = ""
        for line in run.stdout.splitlines():
            header = re.search(r"^public .*?\b(?:class|interface) ([^ <{]+)", line)
            if header:
                current_type = header.group(1)
            elif re.match(r"^(?:final |abstract )?(?:class|interface) ", line):
                current_type = ""
            if current_type and line.strip().startswith("descriptor:"):
                values.add((current_type, previous, line.strip()))
            if line.strip():
                previous = line.strip()
        return values

    old, new = descriptors("before"), descriptors("after")
    missing = sorted(old - new)
    records.append(dict(module=module, baseline=baseline[0].name, publicTypes=len(targets),
                        originalDescriptors=len(old), missingDescriptors=missing, retained=not missing))
    assert old and not missing, records[-1]
result = dict(passed=True, modules=records)
(args.output_dir / "result.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
print(json.dumps(result, indent=2))
