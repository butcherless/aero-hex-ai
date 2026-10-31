#!/usr/bin/env python3
"""Reports container images in YAML files that use `:latest` or no tag at all.

Renovate and Dependabot only bump tags that look like versions, and
check_container_images.py only reads this project's own files, so an unversioned
image is never reported anywhere else. It also hides real breakage: Docker reuses
a cached `:latest`, so a file can work on one machine and fail on a fresh one.

Pass the project directories to scan (default: the current directory). Like the other
checks in this skill, it only reports, and exits non-zero only if a directory is missing.
"""
import re
import sys
from pathlib import Path

IMAGE_RE = re.compile(r"""^\s*(?:-\s*)?image:\s*['"]?([^'"\s#]+)""")
SKIP_DIRS = {".git", "node_modules", "target", ".bloop", ".metals", "out", "dist"}


def is_unversioned(ref: str) -> bool:
    if "@" in ref or "$" in ref:  # pinned by digest, or set from a variable
        return False
    tag = ref.rsplit("/", 1)[-1].partition(":")[2]
    return tag in ("", "latest")


def yaml_files(root: Path):
    for path in sorted(root.rglob("*")):
        if path.suffix in (".yml", ".yaml") and path.is_file():
            if not SKIP_DIRS.intersection(path.relative_to(root).parts):
                yield path


def main() -> None:
    roots = [Path(arg) for arg in sys.argv[1:]] or [Path(".")]
    missing = [str(root) for root in roots if not root.is_dir()]
    if missing:
        print(f"Not a directory: {', '.join(missing)}", file=sys.stderr)
        sys.exit(1)

    found = 0
    for root in roots:
        for path in yaml_files(root):
            for number, line in enumerate(path.read_text(errors="replace").splitlines(), 1):
                match = IMAGE_RE.match(line)
                if match and is_unversioned(match.group(1)):
                    found += 1
                    print(f"{path}:{number}  {match.group(1)}  <-- unversioned image")
    if not found:
        print("No unversioned images.")


if __name__ == "__main__":
    main()
