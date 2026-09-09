#!/usr/bin/env python3
"""Bounded packaged-server pre-EULA startup plus actual config/Kaiiju integration."""
from pathlib import Path
import os
import subprocess
import tempfile
import tomllib

root = Path(__file__).resolve().parents[1]
java = Path(os.environ.get("JAVA_HOME", root / ".jdk")) / "bin"
jar = root / "wind-server/build/libs/wind-paperclip-26.2.local-SNAPSHOT.jar"
run = root / "run/config-regression"
run.mkdir(parents=True, exist_ok=True)
# Refuse to use an accepted EULA; this check must stop at the normal EULA gate.
if (run / "eula.txt").exists():
    assert "eula=true" not in (run / "eula.txt").read_text()
subprocess.run([str(java / "java"), "-Xms256m", "-Xmx1g", "-jar", str(jar), "--nogui"],
               cwd=run, timeout=90, check=True)
assert "You need to agree to the EULA" in (run / "logs/latest.log").read_text()
data = tomllib.loads((run / "Wind/wind_global_config.toml").read_text())
assert all(data[name] for name in ("luminol", "lophine", "lophine_carpet", "wind"))
cp = os.pathsep.join(map(str, [*run.glob("versions/*/*.jar"), *run.glob("libraries/**/*.jar")]))
with tempfile.TemporaryDirectory(dir=root / "run", prefix="config-integration-") as temporary:
    work = Path(temporary)
    subprocess.run([str(java / "javac"), "-cp", cp, "-d", str(work),
                    str(root / "scripts/WindConfigIntegrationCheck.java")], check=True, timeout=30)
    for mode in ("fresh", "migration"):
        directory = work / mode
        directory.mkdir()
        subprocess.run([str(java / "java"), "-ea", "-cp", str(work) + os.pathsep + cp,
                        "WindConfigIntegrationCheck", *([] if mode == "fresh" else [mode])],
                       cwd=directory, check=True, timeout=90)
print("Packaged pre-EULA startup and integration checks passed")
