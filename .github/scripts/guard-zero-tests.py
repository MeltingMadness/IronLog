#!/usr/bin/env python3
"""Fail CI when connected test reports are missing, ran zero tests, or
report failures/errors.

connectedDebugAndroidTest can exit successfully while running no tests
at all (emulator/install failures, bad instrumentation config). Gradle
writes one XML result per test class, so a zero-TEST-file or zero-test
run is unambiguous evidence that the smoke gate did not actually smoke;
aggregated failures/errors are explicit smoke failures.

Usage:
  guard-zero-tests.py [--results-dir DIR]
  guard-zero-tests.py --selftest
"""

import argparse
import pathlib
import sys
import tempfile
import xml.etree.ElementTree as ET


def test_summary(directory):
    """Return (test_count, failure_count, error_count, xml_count)."""
    xml_files = sorted(pathlib.Path(directory).rglob("TEST-*.xml"))
    tests = failures = errors = 0
    for xml_file in xml_files:
        try:
            root = ET.parse(xml_file).getroot()
        except ET.ParseError as exc:
            raise SystemExit(
                f"guard-zero-tests: unparseable result XML {xml_file}: {exc}"
            ) from exc
        tests += int(root.get("tests", 0))
        failures += int(root.get("failures", 0))
        errors += int(root.get("errors", 0))
    return tests, failures, errors, len(xml_files)


def verify(directory):
    """Run the smoke gate; exits nonzero on missing, empty or failing runs."""
    tests, failures, errors, xml_count = test_summary(directory)
    if xml_count == 0:
        raise SystemExit(
            f"guard-zero-tests: no TEST-*.xml reports under {directory}; "
            "connected smoke tests did not run"
        )
    if tests == 0:
        raise SystemExit(
            f"guard-zero-tests: {xml_count} report(s) found but 0 tests ran; "
            "smoke gate is empty and must not pass"
        )
    if failures > 0 or errors > 0:
        raise SystemExit(
            f"guard-zero-tests: {tests} tests across {xml_count} report(s), "
            f"{failures} failures, {errors} errors - smoke gate FAILED"
        )
    print(
        f"guard-zero-tests: {tests} tests across {xml_count} report(s), "
        f"{failures} failures, {errors} errors - smoke gate OK"
    )


def _selftest():
    with tempfile.TemporaryDirectory() as tmp:
        root = pathlib.Path(tmp)
        empty_dir = root / "no-reports"
        empty_dir.mkdir()
        assert test_summary(empty_dir) == (0, 0, 0, 0)

        zero_dir = root / "zero-tests"
        zero_dir.mkdir()
        (zero_dir / "TEST-empty.xml").write_text(
            '<testsuite name="empty" tests="0"/>', encoding="utf-8"
        )
        assert test_summary(zero_dir) == (0, 0, 0, 1)

        ok_dir = root / "ok"
        ok_dir.mkdir()
        (ok_dir / "TEST-a.xml").write_text(
            '<testsuite name="a" tests="3" failures="0" errors="0"/>',
            encoding="utf-8",
        )
        (ok_dir / "TEST-b.xml").write_text(
            '<testsuite name="b" tests="2" failures="0" errors="0"/>',
            encoding="utf-8",
        )
        assert test_summary(ok_dir) == (5, 0, 0, 2)
        verify(ok_dir)

        fail_dir = root / "failures"
        fail_dir.mkdir()
        (fail_dir / "TEST-fail.xml").write_text(
            '<testsuite name="f" tests="2" failures="1" errors="0"/>',
            encoding="utf-8",
        )
        assert test_summary(fail_dir) == (2, 1, 0, 1)
        try:
            verify(fail_dir)
            raise AssertionError("reported failures must fail the gate")
        except SystemExit:
            pass

        err_dir = root / "errors"
        err_dir.mkdir()
        (err_dir / "TEST-error.xml").write_text(
            '<testsuite name="e" tests="1" failures="0" errors="1"/>',
            encoding="utf-8",
        )
        assert test_summary(err_dir) == (1, 0, 1, 1)
        try:
            verify(err_dir)
            raise AssertionError("reported errors must fail the gate")
        except SystemExit:
            pass

        bad_dir = root / "bad"
        bad_dir.mkdir()
        (bad_dir / "TEST-broken.xml").write_text("<testsuite", encoding="utf-8")
        try:
            test_summary(bad_dir)
            raise AssertionError("broken XML must raise SystemExit")
        except SystemExit:
            pass
    print("guard-zero-tests: selftest OK")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--results-dir",
        default="app/build/outputs/androidTest-results/connected/debug",
    )
    parser.add_argument("--selftest", action="store_true")
    args = parser.parse_args()

    if args.selftest:
        _selftest()
        return 0

    verify(args.results_dir)
    return 0


if __name__ == "__main__":
    sys.exit(main())
