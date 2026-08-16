#!/usr/bin/env python3
"""Fail CI when connected test reports are missing, ran zero tests, ran only
a partial subset of the smoke suite, or report failures/errors.

connectedDebugAndroidTest can exit successfully while running no tests
at all (emulator/install failures, bad instrumentation config). Gradle
writes one XML result per test class, so a zero-TEST-file or zero-test
run is unambiguous evidence that the smoke gate did not actually smoke;
aggregated failures/errors are explicit smoke failures.

Additionally, a run that executes only a subset of the suite (for example
a single class or a single method) must not pass the gate either: every
class in EXPECTED_TEST_CLASSES has to appear with at least one executed
test, and the total executed-test count has to reach MIN_TEST_COUNT.

Usage:
  guard-zero-tests.py [--results-dir DIR]
  guard-zero-tests.py --selftest
"""

import argparse
import pathlib
import sys
import tempfile
import xml.etree.ElementTree as ET

# Smoke gate contract: these classes MUST have executed at least one test.
EXPECTED_TEST_CLASSES = (
    "com.ironlog.app.presentation.navigation.NavigationSmokeTest",
    "com.ironlog.app.data.local.IronLogDatabaseMigrationTest",
    "com.ironlog.app.data.backup.BackupLifecycleRoundTripTest",
    "com.ironlog.app.data.local.ProgressionCoachLifecycleTest",
    "com.ironlog.app.data.local.MetaPlanSkipDaoTest",
    "com.ironlog.app.data.local.WorkoutSetDaoContextScopeTest",
    "com.ironlog.app.data.local.ProgressionSnapshotTransactionTest",
    "com.ironlog.app.data.local.WorkoutSessionDateFilterTest",
)

# Absolute floor for the total number of executed tests. Comfortably above
# "one test per expected class" so a run that only scratches each class
# still fails the gate.
MIN_TEST_COUNT = 15


def _executed_classes(root):
    """Distinct names of classes that executed at least one test.

    The connected runner on emulators can merge every class into a single
    TEST-<device>.xml whose <testsuite> name is only the alphabetically first
    class; the real class names then live in each <testcase classname=...>.
    Prefer those, falling back to the suite name for reports without
    testcase elements (e.g. empty suites).
    """
    classnames = {tc.get("classname") for tc in root.iter("testcase")}
    classnames.discard(None)
    if classnames:
        return classnames
    name = root.get("name")
    return {name} if name else set()


def test_summary(directory):
    """Return (test_count, failure_count, error_count, xml_count, ran_classes).

    ran_classes contains the names of classes that executed at least one test.
    """
    xml_files = sorted(pathlib.Path(directory).rglob("TEST-*.xml"))
    tests = failures = errors = 0
    ran_classes = set()
    for xml_file in xml_files:
        try:
            root = ET.parse(xml_file).getroot()
        except ET.ParseError as exc:
            raise SystemExit(
                f"guard-zero-tests: unparseable result XML {xml_file}: {exc}"
            ) from exc
        file_tests = int(root.get("tests", 0))
        tests += file_tests
        failures += int(root.get("failures", 0))
        errors += int(root.get("errors", 0))
        if file_tests > 0:
            ran_classes |= _executed_classes(root)
    return tests, failures, errors, len(xml_files), ran_classes


def verify(directory):
    """Run the smoke gate; exits nonzero on missing, empty or partial runs."""
    tests, failures, errors, xml_count, ran_classes = test_summary(directory)
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
    missing = sorted(set(EXPECTED_TEST_CLASSES) - ran_classes)
    if missing:
        raise SystemExit(
            "guard-zero-tests: expected smoke test classes missing from the "
            f"results ({missing}); a partial run must not pass"
        )
    if tests < MIN_TEST_COUNT:
        raise SystemExit(
            f"guard-zero-tests: only {tests} tests ran across {xml_count} "
            f"report(s), expected at least {MIN_TEST_COUNT}; "
            "a partial run must not pass"
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


def _seed_full_suite(directory, tests_per_class, failures=0, errors=0):
    """Write one report per expected class so class/count checks pass."""
    directory.mkdir()
    for index, cls in enumerate(EXPECTED_TEST_CLASSES):
        (directory / f"TEST-{cls}.xml").write_text(
            f'<testsuite name="{cls}" tests="{tests_per_class}" '
            f'failures="{failures if index == 0 else 0}" '
            f'errors="{errors if index == 0 else 0}"/>',
            encoding="utf-8",
        )


def _selftest():
    with tempfile.TemporaryDirectory() as tmp:
        root = pathlib.Path(tmp)

        empty_dir = root / "no-reports"
        empty_dir.mkdir()
        assert test_summary(empty_dir) == (0, 0, 0, 0, set())

        zero_dir = root / "zero-tests"
        zero_dir.mkdir()
        (zero_dir / "TEST-empty.xml").write_text(
            '<testsuite name="empty" tests="0"/>', encoding="utf-8"
        )
        tests, failures, errors, xml_count, ran = test_summary(zero_dir)
        assert (tests, failures, errors, xml_count) == (0, 0, 0, 1)
        assert ran == set()
        try:
            verify(zero_dir)
            raise AssertionError("zero-test run must fail the gate")
        except SystemExit:
            pass

        ok_dir = root / "ok"
        _seed_full_suite(ok_dir, tests_per_class=2)
        tests, _, _, _, ran = test_summary(ok_dir)
        assert tests == len(EXPECTED_TEST_CLASSES) * 2
        assert ran == set(EXPECTED_TEST_CLASSES)
        verify(ok_dir)

        merged_dir = root / "merged"
        merged_dir.mkdir()
        # The emulator runner can merge every class into ONE report whose
        # <testsuite> name is only the first class; the class names live in
        # the <testcase classname=...> attributes.
        merged_dir.mkdir(exist_ok=True)
        merged_cases = "".join(
            f'<testcase classname="{cls}" name="t{i}a" time="0.1"/>'
            f'<testcase classname="{cls}" name="t{i}b" time="0.1"/>'
            for i, cls in enumerate(EXPECTED_TEST_CLASSES)
        )
        (merged_dir / "TEST-emulator-5554 - 15-_app-.xml").write_text(
            f'<testsuite name="{EXPECTED_TEST_CLASSES[0]}" tests="'
            f'{len(EXPECTED_TEST_CLASSES) * 2}" failures="0" errors="0">'
            f"{merged_cases}</testsuite>",
            encoding="utf-8",
        )
        tests, _, _, _, ran = test_summary(merged_dir)
        assert tests == len(EXPECTED_TEST_CLASSES) * 2
        assert ran == set(EXPECTED_TEST_CLASSES)
        verify(merged_dir)

        merged_partial_dir = root / "merged-partial"
        merged_partial_dir.mkdir()
        partial_cases = "".join(
            f'<testcase classname="{cls}" name="t{i}" time="0.1"/>'
            for i, cls in enumerate(EXPECTED_TEST_CLASSES[:-1])
        )
        (merged_partial_dir / "TEST-emulator-5554 - 15-_app-.xml").write_text(
            f'<testsuite name="{EXPECTED_TEST_CLASSES[0]}" tests="'
            f'{len(EXPECTED_TEST_CLASSES) - 1}" failures="0" errors="0">'
            f"{partial_cases}</testsuite>",
            encoding="utf-8",
        )
        try:
            verify(merged_partial_dir)
            raise AssertionError("a merged run missing a class must fail the gate")
        except SystemExit:
            pass

        partial_dir = root / "partial"
        partial_dir.mkdir()
        (partial_dir / "TEST-com.ironlog.app.presentation.navigation.NavigationSmokeTest.xml").write_text(
            '<testsuite name="com.ironlog.app.presentation.navigation.NavigationSmokeTest" '
            'tests="1" failures="0" errors="0"/>',
            encoding="utf-8",
        )
        try:
            verify(partial_dir)
            raise AssertionError("a single-class run must fail the gate")
        except SystemExit:
            pass

        thin_dir = root / "thin"
        _seed_full_suite(thin_dir, tests_per_class=1)
        try:
            verify(thin_dir)
            raise AssertionError("one test per class must fail the gate")
        except SystemExit:
            pass

        fail_dir = root / "failures"
        _seed_full_suite(fail_dir, tests_per_class=2, failures=1)
        try:
            verify(fail_dir)
            raise AssertionError("reported failures must fail the gate")
        except SystemExit:
            pass

        err_dir = root / "errors"
        _seed_full_suite(err_dir, tests_per_class=2, errors=1)
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
