#!/usr/bin/env python3
"""Reports the latest available tag for each container image a project pins, and
checks that each image is pinned to the same tag everywhere it appears.

Usage: check_container_images.py [ROOT_OR_FILE ...]   (default: the current dir)

Each directory argument is scanned recursively (build output, dependency and IDE
dirs such as target/, node_modules/, .bloop/ and .git/ are skipped) for:

  - `image:` lines in any *.yml / *.yaml: docker-compose.yml, compose.yml,
    docker-compose-postgres.yml in a test resources dir, a workflow's
    `services:` / `container:` image, and so on
  - Testcontainers `DockerImageName.parse("...")` string literals in *.scala.
    These have no manifest, so Dependabot never sees them. In aero-hex-ai,
    infrastructure/integration-tests/.../PostgresContainerSupport.scala pins
    `postgres` this way and must move in lockstep with docker-compose.yml's
    `postgres` service, which the cross-file consistency check enforces.

A file argument is scanned on its own. Image refs built from variables (`${...}`)
are skipped. Untagged or `:latest` images are listed but not looked up; see
check_unversioned_images.py for why they matter.

Like the plugin/actions checks in this skill, this only reports — it never
rewrites a file. It queries the Docker Hub registry API for available tags.
Exit code is 0 when everything was checked (including a project that pins no
images at all), 1 if the same image is pinned to different tags across files,
and 2 (taking precedence) if a Docker Hub lookup failed (network error, bad
response, or no tags returned), so a broken check can't pass as "up to date".
"""
import json
import re
import subprocess
import sys
from pathlib import Path
from typing import Optional

DOCKERIMAGE_RE = re.compile(r'DockerImageName\.parse\(\s*"([^"]+)"\s*\)')
YAML_IMAGE_RE = re.compile(r"\s*(?:-\s*)?image:\s*[\"']?([^\"'\s#]+)")

SKIP_DIRS = {
    ".git", "target", "node_modules", "dist", ".bloop", ".bsp", ".metals", ".idea",
    ".vscode", ".coverage-data", "out", ".mill", ".scala-build",
}


def candidate_files(arg: Path):
    """Yield the YAML and Scala files under `arg` (or `arg` itself if it's a file)."""
    if arg.is_file():
        yield arg
        return
    for path in sorted(arg.rglob("*")):
        rel_parts = path.relative_to(arg).parts
        if any(part in SKIP_DIRS for part in rel_parts[:-1]):
            continue
        if path.is_file() and path.suffix in (".yml", ".yaml", ".scala"):
            yield path


def scan(path: Path, found: dict) -> None:
    """Add every image ref in `path` to `found` (image ref -> ["file:line", ...])."""
    regex = DOCKERIMAGE_RE if path.suffix == ".scala" else YAML_IMAGE_RE
    try:
        lines = path.read_text(errors="replace").splitlines()
    except OSError:
        return
    for lineno, line in enumerate(lines, 1):
        if line.lstrip().startswith(("#", "//")):
            continue  # a commented-out image isn't a real reference
        m = regex.search(line) if regex is DOCKERIMAGE_RE else regex.match(line)
        if m and "$" not in m.group(1):
            found.setdefault(m.group(1), []).append(f"{path}:{lineno}")


def parse_ref(ref: str):
    """'confluentinc/cp-kafka:8.3.1' -> ('confluentinc', 'cp-kafka', '8.3.1');
    'postgres:18-alpine'            -> ('library', 'postgres', '18-alpine')."""
    name, _, tag = ref.partition(":")
    tag = tag or "latest"
    if "/" in name:
        ns, repo = name.split("/", 1)
    else:
        ns, repo = "library", name
    return ns, repo, tag


def version_key(v: str):
    return tuple(int(n) for n in re.findall(r"\d+", v))


def hub_tags(ns: str, repo: str, pages: int = 3) -> Optional[list]:
    """Tag names for a Docker Hub repo, following pagination up to `pages` pages.
    None if the first page couldn't be fetched or parsed, or listed no tags."""
    names: list = []
    url = f"https://hub.docker.com/v2/namespaces/{ns}/repositories/{repo}/tags?page_size=100"
    for page in range(pages):
        # -f: an HTTP error is a non-zero exit, not an error body parsed as an empty tag list.
        result = subprocess.run(["curl", "-sfL", url], capture_output=True, text=True, timeout=20)
        try:
            data = json.loads(result.stdout) if result.returncode == 0 else None
        except json.JSONDecodeError:
            data = None
        if not isinstance(data, dict):
            if page == 0:
                return None
            break  # later pages are a bonus; the first page has the newest tags
        for t in data.get("results", []):
            if t.get("name"):
                names.append(t["name"])
        url = data.get("next")
        if not url:
            break
    return names or None


def latest_for(tag: str, tags: list) -> Optional[str]:
    """Newest tag of the same shape as `tag`, or None if the shape isn't recognised.

    Handles the two shapes this repo uses: `<major>[-variant]` (e.g. `18-alpine`,
    `21`) and full `<x>.<y>.<z>` semver (e.g. `8.3.1`).
    """
    m = re.fullmatch(r"(\d+)(-[A-Za-z][\w.-]*)?", tag)
    if m:
        suffix = m.group(2) or ""
        shape = re.compile(rf"^\d+{re.escape(suffix)}$")
        cands = [t for t in tags if shape.match(t)]
        return max(cands, key=version_key) if cands else None
    if re.fullmatch(r"\d+\.\d+\.\d+", tag):
        cands = [t for t in tags if re.fullmatch(r"\d+\.\d+\.\d+", t)]
        return max(cands, key=version_key) if cands else None
    return None


def main() -> None:
    args = [Path(a) for a in sys.argv[1:]] or [Path(".")]

    found: dict = {}  # image ref -> ["file:line", ...]
    for arg in args:
        if not arg.exists():
            print(f"warning: {arg} not found", file=sys.stderr)
            continue
        for path in candidate_files(arg):
            scan(path, found)

    if not found:
        # The whole tree was scanned, so this means the project pins no images, not
        # that a hardcoded path went stale: nothing to check is a pass, not a failure.
        print("No container image references found — nothing to check.")
        sys.exit(0)

    # Group by namespace/repo so a tag that disagrees across files is visible.
    by_repo: dict = {}
    for ref, locs in found.items():
        ns, repo, tag = parse_ref(ref)
        key = repo if ns == "library" else f"{ns}/{repo}"
        entry = by_repo.setdefault(key, {"ns": ns, "repo": repo, "tags": {}})
        entry["tags"].setdefault(tag, []).extend(locs)

    print(f"{'IMAGE':<26} {'CURRENT':<14} {'LATEST':<14} NOTE")
    exit_code = 0
    for key, info in sorted(by_repo.items()):
        current_tags = sorted(info["tags"])
        current = ", ".join(current_tags)
        if current_tags == ["latest"]:
            # Nothing to compare a floating `latest` against; flag it instead of a lookup.
            print(f"{key:<26} {current:<14} {'-':<14}  <-- unversioned (pin a tag; see check_unversioned_images.py)")
            for loc in info["tags"]["latest"]:
                print(f"    latest  {loc}")
            continue
        tags = hub_tags(info["ns"], info["repo"])

        latest: Optional[str] = None
        for t in current_tags:
            found_latest = latest_for(t, tags or [])
            if found_latest:
                latest = found_latest if latest is None else max([latest, found_latest], key=version_key)

        # The cross-file check needs no network, so it's reported even when the lookup failed.
        marker = ""
        if len(current_tags) > 1:
            marker = "  <-- INCONSISTENT tag across files (bump them together)"
            exit_code = max(exit_code, 1)
        if tags is None:
            marker += "  <-- Docker Hub lookup failed"
            exit_code = 2
        elif len(current_tags) > 1:
            pass  # an update suggestion is meaningless until the tags agree
        elif latest is None:
            marker = "  <-- tag shape not recognised, check Docker Hub manually"
        elif latest != current_tags[0]:
            if version_key(latest)[:1] > version_key(current_tags[0])[:1]:
                marker = f"  <-- MAJOR update available (review {key} release notes first)"
            else:
                marker = "  <-- update available"

        print(f"{key:<26} {current:<14} {str(latest or '?'):<14}{marker}")
        if marker:
            for tag in current_tags:
                for loc in info["tags"][tag]:
                    print(f"    {tag}  {loc}")

    sys.exit(exit_code)


if __name__ == "__main__":
    main()
