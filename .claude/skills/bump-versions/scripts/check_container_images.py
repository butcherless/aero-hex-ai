#!/usr/bin/env python3
"""Reports the latest available tag for each container image this project pins, and
checks that the Postgres tag is identical everywhere it appears.

Two places pin container images, and only the first is covered by
.github/dependabot.yml's `docker-compose` ecosystem update:

  - docker-compose.yml
        -> postgres, confluentinc/cp-kafka
  - infrastructure/integration-tests/.../PostgresContainerSupport.scala
        -> postgres  (a Testcontainers `DockerImageName.parse("...")` string
           literal — no manifest, so Dependabot never sees it; it must be bumped
           in lockstep with the compose file's postgres tag)

Like the plugin/actions checks in this skill, this only reports — it never
rewrites a file. It queries the Docker Hub registry API for available tags.
Exit code is 1 if the same image is pinned to different tags across files.
"""
import json
import re
import subprocess
import sys
from pathlib import Path
from typing import Optional

DOCKERIMAGE_RE = re.compile(r'DockerImageName\.parse\(\s*"([^"]+)"\s*\)')

DEFAULT_COMPOSE = "docker-compose.yml"
DEFAULT_SCALA = (
    "infrastructure/integration-tests/src/test/scala/dev/cmartin/aerohex/it/"
    "support/PostgresContainerSupport.scala"
)


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


def hub_tags(ns: str, repo: str, pages: int = 3) -> list:
    """Tag names for a Docker Hub repo, following pagination up to `pages` pages."""
    names: list = []
    url = f"https://hub.docker.com/v2/namespaces/{ns}/repositories/{repo}/tags?page_size=100"
    for _ in range(pages):
        result = subprocess.run(["curl", "-s", url], capture_output=True, text=True, timeout=20)
        try:
            data = json.loads(result.stdout)
        except json.JSONDecodeError:
            break
        for t in data.get("results", []):
            if t.get("name"):
                names.append(t["name"])
        url = data.get("next")
        if not url:
            break
    return names


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
    compose = Path(sys.argv[1] if len(sys.argv) > 1 else DEFAULT_COMPOSE)
    scala = Path(sys.argv[2] if len(sys.argv) > 2 else DEFAULT_SCALA)

    found: dict = {}  # image ref -> ["file:line", ...]
    if compose.exists():
        for lineno, line in enumerate(compose.read_text().splitlines(), 1):
            m = re.match(r"\s*image:\s*[\"']?([^\"'\s]+)", line)
            if m:
                found.setdefault(m.group(1), []).append(f"{compose}:{lineno}")
    else:
        print(f"warning: {compose} not found", file=sys.stderr)
    if scala.exists():
        for lineno, line in enumerate(scala.read_text().splitlines(), 1):
            m = DOCKERIMAGE_RE.search(line)
            if m:
                found.setdefault(m.group(1), []).append(f"{scala}:{lineno}")
    else:
        print(f"warning: {scala} not found", file=sys.stderr)

    if not found:
        print("No container image references found.", file=sys.stderr)
        sys.exit(1)

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
        tags = hub_tags(info["ns"], info["repo"])

        latest: Optional[str] = None
        for t in current_tags:
            found_latest = latest_for(t, tags)
            if found_latest:
                latest = found_latest if latest is None else max([latest, found_latest], key=version_key)

        marker = ""
        if len(current_tags) > 1:
            marker = "  <-- INCONSISTENT tag across files (bump them together)"
            exit_code = 1
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
