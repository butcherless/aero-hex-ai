#!/usr/bin/env python3
"""Reports the latest available version for each SBT plugin in project/plugins.sbt.

Queries the repo1.maven.org directory listing directly rather than Maven Central's
search API (which doesn't index the SBT-plugin artifact suffixes reliably). This
project is SBT 1.x (CLAUDE.md pins the 1.x line), so the plain `""` suffix is what
resolves; `_sbt2_3` is tried first only as a forward-compat probe for the eventual
SBT 2.x migration, and 404s harmlessly until then.
"""
import re
import subprocess
import sys
from pathlib import Path
from typing import Optional, Tuple

# Anchored to the start of the line so commented-out `//addSbtPlugin(...)` lines aren't reported.
PLUGIN_RE = re.compile(r'^\s*addSbtPlugin\("([^"]+)"\s*%\s*"([^"]+)"\s*%\s*"([^"]+)"\)', re.MULTILINE)
PRERELEASE_RE = re.compile(r"(?i)(rc|m\d|alpha|beta|snapshot)")

# Plugins allowed to be reported on a pre-release version when no stable release exists yet.
# Mirrors the ZIO Prelude RC exception in CLAUDE.md's versioning policy for regular dependencies.
# Empty today — every pinned plugin has a stable release. Add an artifact id here only with a
# comment explaining why (e.g. "no stable build for this SBT major version yet").
STABLE_ONLY_EXCEPTIONS: set = set()


def version_key(v: str) -> Tuple[int, ...]:
    return tuple(int(n) for n in re.findall(r"\d+", v))


def latest_version(group: str, artifact: str) -> Tuple[Optional[str], bool]:
    """Returns (latest_version, is_prerelease_only). latest_version is None if nothing was found."""
    group_path = group.replace(".", "/")
    for suffix in ("_sbt2_3", ""):
        url = f"https://repo1.maven.org/maven2/{group_path}/{artifact}{suffix}/"
        result = subprocess.run(["curl", "-s", url], capture_output=True, text=True, timeout=20)
        versions = re.findall(r'href="(\d[^"/]*)/?"', result.stdout)
        if not versions:
            continue
        stable = [v for v in versions if not PRERELEASE_RE.search(v)]
        if stable:
            return max(stable, key=version_key), False
        if artifact in STABLE_ONLY_EXCEPTIONS:
            return max(versions, key=version_key), True
        return None, True
    return None, False


def main() -> None:
    plugins_file = Path(sys.argv[1] if len(sys.argv) > 1 else "project/plugins.sbt")
    if not plugins_file.exists():
        print(f"No plugins.sbt found at {plugins_file}", file=sys.stderr)
        sys.exit(1)

    matches = PLUGIN_RE.findall(plugins_file.read_text())
    if not matches:
        print("No addSbtPlugin declarations found.", file=sys.stderr)
        sys.exit(1)

    print(f"{'PLUGIN':<25} {'CURRENT':<15} {'LATEST':<15}")
    for group, artifact, current in matches:
        latest, prerelease_only = latest_version(group, artifact)
        if latest is None:
            print(f"{artifact:<25} {current:<15} {'no stable release':<15}  <-- needs policy review")
        else:
            marker = ""
            if prerelease_only:
                marker = "  <-- pre-release exception"
            elif latest != current:
                marker = "  <-- update available"
            print(f"{artifact:<25} {current:<15} {latest:<15}{marker}")


if __name__ == "__main__":
    main()
