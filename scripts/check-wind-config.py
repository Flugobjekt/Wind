#!/usr/bin/env python3
"""Run the config regression check with the existing Gradle dependency cache."""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
cache = Path.home() / ".gradle/caches/modules-2/files-2.1/com.electronwill.night-config"
jars = [next((cache / name / "3.8.4").glob("*/*.jar")) for name in ("core", "toml")]
java = Path(os.environ.get("JAVA_HOME", root / ".jdk")) / "bin"
with tempfile.TemporaryDirectory(prefix="wind-config-classes-") as classes:
    cp = os.pathsep.join(map(str, jars))
    subprocess.run([str(java / "javac"), "-cp", cp, "-d", classes,
                    str(root / "wind-server/src/main/java/fun/bm/wind/config/WindGlobalConfig.java"),
                    str(root / "scripts/WindGlobalConfigCheck.java")], check=True)
    subprocess.run([str(java / "java"), "-ea", "-cp", classes + os.pathsep + cp,
                    "WindGlobalConfigCheck"], check=True)
