#!/usr/bin/env python3
"""Validate release build outputs and generate a SHA-256 manifest.

Designed for CI where release artifacts are intentionally unsigned:
this script only verifies presence/size of the core Gradle outputs and
records their hashes. It must never claim signature or Play-readiness.

Default paths match Gradle/AGP conventions used by this project. The
mapping file and merged manifest are located by recursive search so the
script survives AGP intermediate-path changes. A non-strict run only
hashes what exists; a strict run (used by CI) fails on any missing core
artifact.

Usage:
  release-artifacts.py [--strict] [--out MANIFEST] [--root BUILD_DIR]
  release-artifacts.py --selftest
"""

import argparse
import hashlib
import pathlib
import sys
import tempfile


def _sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def find_first(root, name):
    """Return the first file with the given name under root, or None."""
    candidates = sorted(pathlib.Path(root).rglob(name))
    return candidates[0] if candidates else None


def collect_core(build_dir):
    build_dir = pathlib.Path(build_dir)
    apk_dir = build_dir / "outputs/apk/release"
    bundle_dir = build_dir / "outputs/bundle/release"
    return {
        "apk": find_first(apk_dir, "*.apk"),
        "aab": find_first(bundle_dir, "*.aab"),
        "mapping": find_first(build_dir / "outputs/mapping", "mapping.txt"),
        "merged_manifest": find_first(
            build_dir / "intermediates/merged_manifests", "AndroidManifest.xml"
        ),
    }


def build_manifest(core, build_dir, strict):
    lines = []
    failures = []
    for label, path in core.items():
        if path is None or path.stat().st_size == 0:
            failures.append(label)
            continue
        lines.append(
            f"{_sha256(path)}  {path.relative_to(build_dir).as_posix()}"
        )
    if strict and failures:
        raise SystemExit(
            "release-artifacts: missing or empty core artifact(s): "
            + ", ".join(sorted(failures))
        )
    return "\n".join(lines) + "\n"


def _selftest():
    with tempfile.TemporaryDirectory() as tmp:
        root = pathlib.Path(tmp)
        apk = root / "outputs/apk/release/app-release-unsigned.apk"
        aab = root / "outputs/bundle/release/app-release.aab"
        mapping = root / "outputs/mapping/release/mapping.txt"
        manifest = root / "intermediates/merged_manifests/release/AndroidManifest.xml"
        for path in (apk, aab, mapping, manifest):
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("x", encoding="utf-8")

        core = collect_core(root)
        assert set(core) == {"apk", "aab", "mapping", "merged_manifest"}
        assert all(core.values()), core
        out = build_manifest(core, root, strict=True)
        assert out.count("\n") == 4 and "SHA256SUMS" not in out, out

        empty = root / "outputs/mapping/release/mapping.txt"
        empty.write_text("", encoding="utf-8")
        try:
            build_manifest(core, root, strict=True)
            raise AssertionError("empty mapping.txt must fail strict mode")
        except SystemExit:
            pass

        core["mapping"] = None
        relaxed = build_manifest(core, root, strict=False)
        assert "mapping" not in relaxed and relaxed.count("\n") == 3, relaxed

        missing = collect_core(root / "does-not-exist")
        assert all(value is None for value in missing.values())
    print("release-artifacts: selftest OK")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--strict", action="store_true")
    parser.add_argument("--root", default="app/build")
    parser.add_argument("--out", default="app/build/release/SHA256SUMS.txt")
    parser.add_argument("--selftest", action="store_true")
    args = parser.parse_args()

    if args.selftest:
        _selftest()
        return 0

    core = collect_core(args.root)
    manifest = build_manifest(core, args.root, strict=args.strict)
    out_path = pathlib.Path(args.out)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(manifest, encoding="utf-8")
    print(out_path)
    return 0


if __name__ == "__main__":
    sys.exit(main())
