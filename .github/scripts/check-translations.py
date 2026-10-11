#!/usr/bin/env python3
"""Check that every UI string exists in English and German on both platforms.

Android (every module's ``src/main/res`` and KMP ``src/androidMain/res``):
  * ``values/`` is English (default/fallback), ``values-de/`` is German.
  * Every ``<string>``, ``<plurals>`` and ``<string-array>`` in ``values/``
    that is not ``translatable="false"`` (or lives in ``donottranslate.xml``)
    must exist in ``values-de/`` (any file of the same res directory), and
    ``values-de/`` must not define names that are missing from ``values/`` or
    marked untranslatable there.
  * Format placeholders (``%1$s``, ``%d``, ``%2$.1f`` ...) must match between
    EN and DE. Non-positional placeholders are numbered in order, so ``%d``
    equals ``%1$d``; precision/width are ignored; ``%%``/``%n`` are not
    arguments. Strings with ``formatted="false"`` are skipped.
  * ``<plurals>`` need the quantities ``one`` and ``other`` in both languages;
    the placeholders are compared as the union over all quantities (a "one"
    item may spell the number out).
  * ``<string-array>`` needs the same number of items in both languages.

iOS (every ``*.xcstrings`` below ``iosApp/ThrymIOS``):
  * Every key needs a ``de`` and an ``en`` localization (``stringUnit`` or
    ``variations``) unless the key has ``"shouldTranslate": false``. Keys are
    the German source texts, so for a catalog whose ``sourceLanguage`` is
    ``de`` a missing ``de`` localization falls back to the key itself, exactly
    like Xcode does at runtime.
  * Format specifiers (``%@``, ``%lld``, ``%d``, ``%1$@`` ...) of each
    localization must match the key's specifiers (the arguments the code
    passes). Positional and non-positional forms are normalized; for
    variations (plural/device) and substitutions the union over all variants
    must match and no variant may use an argument the key does not have.
  * Plural variations need ``one`` and ``other``; values must not be empty.

A space is not accepted as printf flag, so German typography such as
"15 % weniger" is not mistaken for a placeholder.

Usage:
  check-translations.py [--root DIR] [--platform {all,android,ios}]
                        [--verbose | --max-items N]
  check-translations.py --selftest

Exit code 0 when everything is consistent, 1 when problems were found.
Standard library only.
"""

import argparse
import collections
import json
import os
import pathlib
import re
import sys
import tempfile
import xml.etree.ElementTree as ET

REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]

# Android resource layout.
ANDROID_SOURCE_SETS = ("main", "androidMain")
EN_VALUES_DIR = "values"
DE_VALUES_DIR = "values-de"
DO_NOT_TRANSLATE_FILES = ("donottranslate.xml",)
REQUIRED_PLURAL_QUANTITIES = ("one", "other")

# iOS string catalogs.
IOS_CATALOG_DIR = pathlib.Path("iosApp") / "ThrymIOS"
CATALOG_LANGUAGES = ("de", "en")
# Source languages whose keys are the source text itself, so a missing
# localization in that language falls back to the key (Xcode semantics).
IMPLICIT_SOURCE_LANGUAGES = ("de",)

SKIP_DIR_NAMES = {
    ".git",
    ".gradle",
    ".idea",
    ".kotlin",
    ".cxx",
    "build",
    "node_modules",
    "DerivedData",
}

# Java/Android format specifier. The space flag is deliberately not accepted
# ("15 % der" is German typography, not "% d").
ANDROID_SPEC = re.compile(
    r"%(?:(\d+)\$|(<))?[-#+0,(]*(?:\d+)?(?:\.\d+)?([tT][a-zA-Z]|[bBhHsScCdoxXeEfgGaA%n])"
)

# Apple printf-style specifier, including string-catalog substitutions
# (%#@name@). Again without the space flag.
IOS_SPEC = re.compile(
    r"%(?:(\d+)\$)?"
    r"(?:#@([A-Za-z0-9_]+)@"
    r"|[-+#0']*(?:\d+|\*)?(?:\.(?:\d+|\*))?(hh|h|ll|l|q|z|t|j|L)?([@dDiuUxXoOfFeEgGaAcCsSp%]))"
)
IOS_ARG_TOKEN = re.compile(r"%arg\b")


class Report:
    """Problems grouped by location (module/catalog) and category."""

    def __init__(self):
        self.problems = collections.OrderedDict()
        self.stats = collections.OrderedDict()

    def add(self, group, category, detail):
        categories = self.problems.setdefault(group, collections.OrderedDict())
        categories.setdefault(category, []).append(detail)

    def count(self, key, amount=1):
        self.stats[key] = self.stats.get(key, 0) + amount

    @property
    def total(self):
        return sum(len(d) for cats in self.problems.values() for d in cats.values())

    def categories(self):
        """Flat {(group, category): details} view, used by the selftest."""
        return {
            (group, category): details
            for group, cats in self.problems.items()
            for category, details in cats.items()
        }

    def print(self, max_items, stream=sys.stdout):
        for group, cats in self.problems.items():
            group_total = sum(len(d) for d in cats.values())
            print(f"{group}: {group_total} problem(s)", file=stream)
            for category, details in cats.items():
                print(f"  {category} ({len(details)}):", file=stream)
                shown = details if max_items is None else details[:max_items]
                for detail in shown:
                    print(f"    - {detail}", file=stream)
                if len(shown) < len(details):
                    print(
                        f"    ... and {len(details) - len(shown)} more "
                        "(--verbose shows all)",
                        file=stream,
                    )


# --------------------------------------------------------------------------
# Format signatures
# --------------------------------------------------------------------------


def _format_signature(signature):
    if not signature:
        return "{}"
    return "{" + ", ".join(f"%{i}${t}" for i, t in sorted(signature)) + "}"


def android_signature(text):
    """Set of (argument index, conversion) used by an Android format string."""
    signature = set()
    sequential = 0
    last_index = None
    for match in ANDROID_SPEC.finditer(text):
        position, relative, conversion = match.groups()
        if conversion in ("%", "n"):
            continue
        if position:
            index = int(position)
        elif relative:
            if last_index is None:
                continue
            index = last_index
        else:
            sequential += 1
            index = sequential
        last_index = index
        if conversion[0] in "tT":
            conversion = "t" + conversion[1:]
        else:
            conversion = conversion.lower()
        signature.add((index, conversion))
    return signature


def ios_type(length, conversion):
    """Normalize an Apple printf conversion to the argument type it reads."""
    if conversion == "@":
        return "@"
    if conversion in "dDiuUxXoO":
        letter = {"i": "d", "D": "d", "U": "u", "O": "o", "X": "x"}.get(
            conversion, conversion
        )
        if conversion in "DUO" or length in ("l", "ll", "q", "z", "t", "j"):
            width = "ll"  # 64-bit on all Apple platforms Thrym supports
        else:
            width = length or ""
        return width + letter
    if conversion in "fFeEgGaA":
        return ("L" if length == "L" else "") + conversion.lower()
    return conversion  # c, C, s, S, p keep their case-sensitive meaning


def ios_signature(text, substitutions=None, problems=None):
    """Set of (argument index, type) used by an Apple format string."""
    signature = set()
    sequential = 0
    for match in IOS_SPEC.finditer(text):
        position, substitution, length, conversion = match.groups()
        if conversion == "%":
            continue
        if position:
            index = int(position)
        else:
            sequential += 1
            index = sequential
        if substitution is not None:
            spec = (substitutions or {}).get(substitution)
            if not isinstance(spec, dict):
                if problems is not None:
                    problems.append(f"unknown substitution %#@{substitution}@")
                continue
            if isinstance(spec.get("argNum"), int):
                index = spec["argNum"]
            inner = ios_signature("%" + str(spec.get("formatSpecifier", "@")))
            arg_type = next(iter(inner))[1] if len(inner) == 1 else "@"
            signature.add((index, arg_type))
        else:
            signature.add((index, ios_type(length, conversion)))
    return signature


# --------------------------------------------------------------------------
# Android
# --------------------------------------------------------------------------


class AndroidEntry:
    __slots__ = ("kind", "name", "file", "translatable", "formatted", "texts")

    def __init__(self, kind, name, file, translatable, formatted, texts):
        self.kind = kind
        self.name = name
        self.file = file
        self.translatable = translatable
        self.formatted = formatted
        # string: {"": text}; plurals: {quantity: text}; array: [text, ...]
        self.texts = texts


def _read_xml(path):
    data = path.read_bytes()
    if data.startswith(b"\xef\xbb\xbf"):
        data = data[3:]
    # ElementTree drops comments by default, so commented-out <string>
    # elements are never seen.
    return ET.fromstring(data)


def _element_text(element):
    return "".join(element.itertext())


def parse_android_values(values_dir, report, group):
    """Return {name: AndroidEntry} for all string resources in one values dir."""
    entries = {}
    if not values_dir.is_dir():
        return entries
    for path in sorted(values_dir.glob("*.xml")):
        try:
            root = _read_xml(path)
        except (ET.ParseError, OSError) as exc:
            report.add(group, "unparseable resource file", f"{path.name}: {exc}")
            continue
        file_untranslatable = path.name in DO_NOT_TRANSLATE_FILES
        for element in root:
            if not isinstance(element.tag, str):
                continue
            kind = element.tag
            if kind not in ("string", "plurals", "string-array"):
                continue
            name = element.get("name")
            if not name:
                continue
            translatable = (
                not file_untranslatable
                and element.get("translatable", "true").strip().lower() != "false"
            )
            formatted = element.get("formatted", "true").strip().lower() != "false"
            if kind == "string":
                texts = {"": _element_text(element)}
            elif kind == "plurals":
                texts = collections.OrderedDict()
                for item in element.findall("item"):
                    quantity = item.get("quantity")
                    if quantity:
                        texts[quantity] = _element_text(item)
            else:
                texts = [_element_text(item) for item in element.findall("item")]
            rel = f"{values_dir.name}/{path.name}"
            if name in entries:
                report.add(
                    group,
                    f"duplicate name in {values_dir.name}/",
                    f"{name} ({entries[name].file} and {rel})",
                )
                continue
            entries[name] = AndroidEntry(kind, name, rel, translatable, formatted, texts)
    return entries


def _entry_signature(entry):
    if not entry.formatted:
        return None
    if entry.kind == "string-array":
        return None  # compared item by item
    signature = set()
    for text in entry.texts.values():
        signature |= android_signature(text)
    return signature


def check_android_res_dir(res_dir, group, report):
    en = parse_android_values(res_dir / EN_VALUES_DIR, report, group)
    de = parse_android_values(res_dir / DE_VALUES_DIR, report, group)
    report.count("android resource directories")
    report.count("android EN entries", sum(1 for e in en.values() if e.translatable))

    for name, entry in en.items():
        if not entry.translatable:
            continue
        other = de.get(name)
        if other is None:
            report.add(group, "missing in values-de/", f"{name} ({entry.file})")
            continue
        if other.kind != entry.kind:
            report.add(
                group,
                "resource type differs",
                f"{name}: <{entry.kind}> in {entry.file}, <{other.kind}> in {other.file}",
            )
            continue
        if entry.kind == "plurals":
            for language, candidate in (("values/", entry), ("values-de/", other)):
                missing = [q for q in REQUIRED_PLURAL_QUANTITIES if q not in candidate.texts]
                if missing:
                    report.add(
                        group,
                        "plurals without one/other",
                        f"{name} in {language}: missing {', '.join(missing)}",
                    )
        if entry.kind == "string-array":
            if len(entry.texts) != len(other.texts):
                report.add(
                    group,
                    "string-array item count differs",
                    f"{name}: {len(entry.texts)} in values/, {len(other.texts)} in values-de/",
                )
            elif entry.formatted and other.formatted:
                for index, (en_text, de_text) in enumerate(zip(entry.texts, other.texts)):
                    en_sig, de_sig = android_signature(en_text), android_signature(de_text)
                    if en_sig != de_sig:
                        report.add(
                            group,
                            "placeholder mismatch",
                            f"{name}[{index}]: en {_format_signature(en_sig)} "
                            f"vs de {_format_signature(de_sig)}",
                        )
            continue
        en_sig, de_sig = _entry_signature(entry), _entry_signature(other)
        if en_sig is None or de_sig is None:
            continue  # formatted="false" on either side
        if en_sig != de_sig:
            report.add(
                group,
                "placeholder mismatch",
                f"{name}: en {_format_signature(en_sig)} vs de {_format_signature(de_sig)}",
            )

    for name, entry in de.items():
        source = en.get(name)
        if source is None:
            report.add(group, "only in values-de/ (missing in values/)", f"{name} ({entry.file})")
        elif not source.translatable:
            report.add(
                group,
                "untranslatable in values/ but present in values-de/",
                f"{name} ({entry.file})",
            )
        elif not entry.translatable:
            report.add(
                group,
                "translatable=\"false\" inside values-de/",
                f"{name} ({entry.file}); mark it in values/ instead",
            )


def _walk(root):
    for dirpath, dirnames, _ in os.walk(root):
        dirnames[:] = sorted(
            d for d in dirnames if d not in SKIP_DIR_NAMES and not d.startswith(".")
        )
        yield pathlib.Path(dirpath)


def find_android_res_dirs(root):
    found = []
    for directory in _walk(root):
        if directory.name == "res" and directory.parent.name in ANDROID_SOURCE_SETS:
            if directory.parent.parent.name == "src":
                if (directory / EN_VALUES_DIR).is_dir() or (directory / DE_VALUES_DIR).is_dir():
                    found.append(directory)
    return found


def check_android(root, report):
    res_dirs = find_android_res_dirs(root)
    if not res_dirs:
        report.add("android", "setup", f"no src/{{main,androidMain}}/res directories found under {root}")
        return
    for res_dir in res_dirs:
        group = res_dir.relative_to(root).as_posix()
        check_android_res_dir(res_dir, group, report)


# --------------------------------------------------------------------------
# iOS string catalogs
# --------------------------------------------------------------------------


def _collect_variations(node, path, leaves, issues):
    """Collect (variant path, value) leaves of a localization/variation node."""
    unit = node.get("stringUnit")
    if isinstance(unit, dict) and isinstance(unit.get("value"), str):
        leaves.append((path or "value", unit["value"]))
    variations = node.get("variations")
    if isinstance(variations, dict):
        for kind, cases in variations.items():
            if not isinstance(cases, dict):
                continue
            if kind == "plural":
                missing = [q for q in REQUIRED_PLURAL_QUANTITIES if q not in cases]
                if missing:
                    issues.append(
                        f"plural variation at {path or 'top level'} misses {', '.join(missing)}"
                    )
            for case, child in cases.items():
                if isinstance(child, dict):
                    prefix = f"{path}/" if path else ""
                    _collect_variations(child, f"{prefix}{kind}.{case}", leaves, issues)


def _localization_signature(localization, issues):
    """Return (union signature, [(variant, signature)]) of one localization."""
    substitutions = localization.get("substitutions")
    if not isinstance(substitutions, dict):
        substitutions = {}
    leaves = []
    _collect_variations(localization, "", leaves, issues)
    variants = []
    for path, value in leaves:
        variants.append((path, value, ios_signature(value, substitutions, issues)))
    for name, spec in sorted(substitutions.items()):
        if not isinstance(spec, dict):
            continue
        sub_leaves = []
        _collect_variations(spec, f"substitution {name}", sub_leaves, issues)
        for path, value in sub_leaves:
            stripped = IOS_ARG_TOKEN.sub("", value)
            # Inside a substitution variant only positional specifiers can
            # refer to other arguments of the outer string.
            positional = {
                (index, arg_type)
                for index, arg_type in ios_signature(stripped)
                if re.search(rf"%{index}\$", stripped)
            }
            variants.append((path, value, positional))
    return leaves, variants


def check_catalog(path, group, report):
    try:
        text = path.read_text(encoding="utf-8-sig")
        catalog = json.loads(text)
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        report.add(group, "unparseable catalog", str(exc))
        return
    strings = catalog.get("strings") if isinstance(catalog, dict) else None
    if not isinstance(strings, dict):
        report.add(group, "unparseable catalog", "no top-level \"strings\" object")
        return
    source_language = catalog.get("sourceLanguage")
    report.count("ios catalogs")

    for key in sorted(strings):
        entry = strings[key]
        if not isinstance(entry, dict):
            report.add(group, "malformed entry", repr(key))
            continue
        if entry.get("shouldTranslate") is False:
            continue
        report.count("ios keys")
        stale = " [stale - remove it?]" if entry.get("extractionState") == "stale" else ""
        localizations = entry.get("localizations")
        if not isinstance(localizations, dict):
            localizations = {}
        key_signature = ios_signature(key)
        source = localizations.get(source_language) if source_language else None
        reference = key_signature
        if (
            not key_signature
            and isinstance(source, dict)
            and isinstance(source.get("stringUnit"), dict)
            and "variations" not in source
        ):
            # Identifier-style key: the explicit source text defines the
            # arguments.
            reference = ios_signature(str(source["stringUnit"].get("value", "")))

        for language in CATALOG_LANGUAGES:
            localization = localizations.get(language)
            if localization is None:
                if language == source_language and language in IMPLICIT_SOURCE_LANGUAGES:
                    continue  # the key itself is the source text
                report.add(group, f"missing \"{language}\"", f"{key!r}{stale}")
                continue
            if not isinstance(localization, dict):
                report.add(group, f"missing \"{language}\"", f"{key!r} (malformed){stale}")
                continue
            issues = []
            leaves, variants = _localization_signature(localization, issues)
            if not leaves:
                report.add(group, f"missing \"{language}\"", f"{key!r} (no value){stale}")
                continue
            for issue in issues:
                report.add(group, "malformed variations", f"{key!r} [{language}]: {issue}")
            for variant, value in leaves:
                if value == "" and key != "":
                    report.add(
                        group, "empty value", f"{key!r} [{language}] {variant}{stale}"
                    )
            union = set()
            for variant, _, signature in variants:
                union |= signature
                extra = signature - reference
                if extra and len(variants) > 1:
                    report.add(
                        group,
                        "format specifier mismatch",
                        f"{key!r} [{language}] {variant}: uses {_format_signature(extra)} "
                        f"not in key {_format_signature(reference)}",
                    )
            if union != reference and not (len(variants) > 1 and not (reference - union)):
                report.add(
                    group,
                    "format specifier mismatch",
                    f"{key!r} [{language}]: {_format_signature(union)} "
                    f"vs key {_format_signature(reference)}",
                )


def find_catalogs(root):
    catalog_root = root / IOS_CATALOG_DIR
    if not catalog_root.is_dir():
        return []
    return sorted(
        path
        for directory in _walk(catalog_root)
        for path in directory.glob("*.xcstrings")
        if path.is_file()
    )


def check_ios(root, report):
    catalogs = find_catalogs(root)
    if not catalogs:
        report.add("ios", "setup", f"no *.xcstrings found under {root / IOS_CATALOG_DIR}")
        return
    for path in catalogs:
        check_catalog(path, path.relative_to(root).as_posix(), report)


# --------------------------------------------------------------------------
# Entry points
# --------------------------------------------------------------------------


def run(root, platform):
    report = Report()
    if platform in ("all", "android"):
        check_android(root, report)
    if platform in ("all", "ios"):
        check_ios(root, report)
    return report


def _github_escape(value):
    return value.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def main():
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument("--root", type=pathlib.Path, default=REPO_ROOT)
    parser.add_argument("--platform", choices=("all", "android", "ios"), default="all")
    parser.add_argument("--max-items", type=int, default=15, help="details per category")
    parser.add_argument("--verbose", action="store_true", help="show every detail")
    parser.add_argument("--selftest", action="store_true")
    args = parser.parse_args()

    if args.selftest:
        _selftest()
        return 0

    root = args.root.resolve()
    report = run(root, args.platform)
    stats = ", ".join(f"{value} {key}" for key, value in report.stats.items())
    if report.total == 0:
        print(f"check-translations ({args.platform}): OK - {stats or 'nothing checked'}")
        return 0

    report.print(None if args.verbose else max(args.max_items, 0))
    summary = f"check-translations ({args.platform}): {report.total} problem(s)"
    print(f"{summary} - checked {stats}" if stats else summary)
    if os.environ.get("GITHUB_ACTIONS") == "true":
        for group, cats in report.problems.items():
            parts = ", ".join(f"{category}: {len(d)}" for category, d in cats.items())
            print(f"::error title=check-translations::{_github_escape(group + ' - ' + parts)}")
    return 1


# --------------------------------------------------------------------------
# Selftest
# --------------------------------------------------------------------------

_BOM = "﻿"
_RES_HEADER = (
    '<?xml version="1.0" encoding="utf-8"?>\n'
    "<!-- fixture header comment -->\n"
    '<resources xmlns:tools="http://schemas.android.com/tools" '
    'xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">\n'
)

_GOOD_EN = _BOM + _RES_HEADER + """
    <string name="title">Workouts</string>
    <string name="greeting">Hello %1$s, you have %2$d sets</string>
    <string name="nonpositional">%d sets</string>
    <string name="percent">100%% done</string>
    <string name="less">15% less weight</string>
    <string name="raw" formatted="false">Increase in %</string>
    <string name="brand" translatable="false">Thrym</string>
    <string name="wrapped">Weight: <xliff:g id="w">%1$.1f</xliff:g> kg</string>
    <!-- <string name="commented_out">never parsed</string> -->
    <plurals name="sets">
        <item quantity="one">One set</item>
        <item quantity="other">%1$d sets</item>
    </plurals>
    <string-array name="units"><item>kg</item><item>lb</item></string-array>
</resources>
"""

_GOOD_DE = _RES_HEADER + """
    <string name="title">Trainings</string>
    <!-- comment between entries -->
    <string name="greeting">Hallo %1$s, du hast %2$d Sätze</string>
    <string name="nonpositional">%1$d Sätze</string>
    <string name="percent">100 %% fertig</string>
    <string name="less">15 % weniger Gewicht, % der Sätze</string>
    <string name="raw" formatted="false">Steigerung in %</string>
    <string name="wrapped">Gewicht: <xliff:g id="w">%1$.2f</xliff:g> kg</string>
    <plurals name="sets">
        <item quantity="one">%1$d Satz</item>
        <item quantity="other">%1$d Sätze</item>
    </plurals>
    <string-array name="units"><item>kg</item><item>lb</item></string-array>
</resources>
"""

_GOOD_DONOTTRANSLATE = _RES_HEADER + """
    <string name="url">https://example.com</string>
</resources>
"""

_BAD_EN_1 = _RES_HEADER + """
    <string name="missing_de">Only English</string>
    <string name="swapped">%1$s of %2$d</string>
    <string name="dropped">%1$d kg</string>
    <string name="brand" translatable="false">Thrym</string>
    <plurals name="incomplete">
        <item quantity="one">One set</item>
        <item quantity="other">%1$d sets</item>
    </plurals>
    <plurals name="typed">
        <item quantity="one">One set</item>
        <item quantity="other">%1$d sets</item>
    </plurals>
    <string-array name="days"><item>Mon</item><item>Tue</item></string-array>
    <string name="dup">first</string>
</resources>
"""

_BAD_EN_2 = _RES_HEADER + """
    <string name="dup">second</string>
</resources>
"""

_BAD_DE = _RES_HEADER + """
    <string name="swapped">%1$d von %2$s</string>
    <string name="dropped">kg</string>
    <string name="brand">Thrym</string>
    <string name="dup">doppelt</string>
    <string name="only_de">Nur Deutsch</string>
    <plurals name="incomplete">
        <item quantity="other">%1$d Sätze</item>
    </plurals>
    <plurals name="typed">
        <item quantity="one">ein Satz</item>
        <item quantity="other">%1$s Sätze</item>
    </plurals>
    <string-array name="days"><item>Mo</item></string-array>
</resources>
"""


def _unit(value):
    return {"stringUnit": {"state": "translated", "value": value}}


def _plural(one, other):
    return {"variations": {"plural": {"one": _unit(one), "other": _unit(other)}}}


_GOOD_CATALOG = {
    "sourceLanguage": "de",
    "version": "1.0",
    "strings": {
        "Abbrechen": {"localizations": {"de": _unit("Abbrechen"), "en": _unit("Cancel")}},
        "%lld Sätze": {
            "localizations": {
                "de": _plural("%lld Satz", "%lld Sätze"),
                "en": _plural("One set", "%lld sets"),
            }
        },
        "%@ von %lld": {
            "localizations": {
                "de": _unit("%@ von %lld"),
                "en": _unit("%2$lld of %1$@"),
            }
        },
        "%lld Wiederholungen": {
            "localizations": {
                "de": _unit("%lld Wiederholungen"),
                "en": {
                    "stringUnit": {"state": "translated", "value": "%#@reps@"},
                    "substitutions": {
                        "reps": {
                            "argNum": 1,
                            "formatSpecifier": "lld",
                            "variations": {
                                "plural": {
                                    "one": _unit("%arg rep"),
                                    "other": _unit("%arg reps"),
                                }
                            },
                        }
                    },
                },
            }
        },
        "Intensität −15 %": {
            "localizations": {
                "de": _unit("Intensität −15 %"),
                "en": _unit("Intensity −15%"),
            }
        },
        "Satz %d": {"localizations": {"de": _unit("Satz %d"), "en": _unit("Set %i")}},
        "Implizit": {"localizations": {"en": _unit("Implicit")}},
        "Thrym": {"shouldTranslate": False},
    },
}

_BAD_CATALOG = {
    "sourceLanguage": "de",
    "version": "1.0",
    "strings": {
        "Speichern": {"localizations": {"de": _unit("Speichern")}},
        "Löschen": {},
        "%lld kg": {"localizations": {"de": _unit("%lld kg"), "en": _unit("%@ kg")}},
        "%lld Minuten": {"localizations": {"de": _unit("%@ Minuten"), "en": _unit("%lld minutes")}},
        "%lld Sätze von %@": {
            "localizations": {
                "de": _unit("%lld Sätze von %@"),
                "en": _plural("One set by %@", "%lld sets by %@"),
            }
        },
        "%lld Pläne": {
            "localizations": {
                "de": _unit("%lld Pläne"),
                "en": {"variations": {"plural": {"one": _unit("%lld plan")}}},
            }
        },
        "Leer": {"localizations": {"de": _unit("Leer"), "en": _unit("")}},
        "Ganzzahl %lld": {"localizations": {"de": _unit("Ganzzahl %lld"), "en": _unit("Integer %d")}},
    },
}

_EXTRA_CATALOG = {
    "sourceLanguage": "de",
    "version": "1.0",
    "strings": {
        "Bankdrücken": {"localizations": {"de": _unit("Bankdrücken"), "en": _unit("Bench press")}},
        "Kniebeuge": {"localizations": {"de": _unit("Kniebeuge")}},
    },
}


def _write(path, content, bom=False):
    path.parent.mkdir(parents=True, exist_ok=True)
    if not isinstance(content, str):
        content = json.dumps(content, ensure_ascii=False, indent=2)
    if bom and not content.startswith(_BOM):
        content = _BOM + content
    path.write_text(content, encoding="utf-8")


def _expect(categories, group, category, needle):
    details = categories.get((group, category), [])
    if not any(needle in detail for detail in details):
        raise AssertionError(
            f"expected {category!r} in {group} mentioning {needle!r}; got {details!r}"
        )


def _selftest():
    # Signature helpers.
    assert android_signature("%d of %s") == {(1, "d"), (2, "s")}
    assert android_signature("%2$s %1$d") == {(1, "d"), (2, "s")}
    assert android_signature("100%% and %n") == set()
    assert android_signature("15 % der Sätze") == set()
    assert android_signature("%1$.1f kg, %<.1f") == {(1, "f")}
    assert ios_signature("%@ und %lld") == {(1, "@"), (2, "lld")}
    assert ios_signature("%2$lld of %1$@") == {(1, "@"), (2, "lld")}
    assert ios_signature("%ld %qd %zd") == {(1, "lld"), (2, "lld"), (3, "lld")}
    assert ios_signature("%d %i") == {(1, "d"), (2, "d")}
    assert ios_signature("15 % der, 100 %% (%)") == set()
    assert ios_signature("%.1f %lf") == {(1, "f"), (2, "f")}

    with tempfile.TemporaryDirectory() as tmp:
        root = pathlib.Path(tmp)
        good = root / "feature" / "good" / "src" / "main" / "res"
        _write(good / "values" / "strings.xml", _GOOD_EN)
        _write(good / "values" / "donottranslate.xml", _GOOD_DONOTTRANSLATE)
        _write(good / "values-de" / "german.xml", _GOOD_DE, bom=True)
        _write(good / "values" / "themes.xml", _RES_HEADER + "<style name='T'/></resources>")

        bad = root / "feature" / "bad" / "src" / "main" / "res"
        _write(bad / "values" / "strings.xml", _BAD_EN_1)
        _write(bad / "values" / "more_strings.xml", _BAD_EN_2)
        _write(bad / "values-de" / "strings.xml", _BAD_DE)

        broken = root / "core" / "broken" / "src" / "main" / "res"
        _write(broken / "values" / "strings.xml", "<resources><string name='x'>x</resources>")
        _write(broken / "values-de" / "strings.xml", _RES_HEADER + "</resources>")

        # Build output must be ignored.
        ignored = root / "feature" / "good" / "build" / "src" / "main" / "res"
        _write(ignored / "values" / "strings.xml", _RES_HEADER + '<string name="z">z</string></resources>')

        catalogs = root / "iosApp" / "ThrymIOS"
        _write(catalogs / "Localizable.xcstrings", _GOOD_CATALOG, bom=True)
        _write(catalogs / "Bad.xcstrings", _BAD_CATALOG)
        _write(catalogs / "Exercises" / "Exercises.xcstrings", _EXTRA_CATALOG)
        _write(catalogs / "Broken.xcstrings", "{ not json")

        report = run(root, "all")
        cats = report.categories()
        good_groups = {
            "feature/good/src/main/res",
            "iosApp/ThrymIOS/Localizable.xcstrings",
        }
        leaked = {k: v for k, v in cats.items() if k[0] in good_groups}
        assert not leaked, f"consistent fixtures reported problems: {leaked}"
        assert not any("build" in group for group, _ in cats), cats

        g = "feature/bad/src/main/res"
        _expect(cats, g, "missing in values-de/", "missing_de")
        _expect(cats, g, "placeholder mismatch", "swapped")
        _expect(cats, g, "placeholder mismatch", "dropped")
        _expect(cats, g, "placeholder mismatch", "typed")
        _expect(cats, g, "plurals without one/other", "incomplete in values-de/: missing one")
        _expect(cats, g, "untranslatable in values/ but present in values-de/", "brand")
        _expect(cats, g, "only in values-de/ (missing in values/)", "only_de")
        _expect(cats, g, "duplicate name in values/", "dup")
        _expect(cats, g, "string-array item count differs", "days")
        _expect(cats, "core/broken/src/main/res", "unparseable resource file", "strings.xml")

        b = "iosApp/ThrymIOS/Bad.xcstrings"
        _expect(cats, b, 'missing "en"', "Speichern")
        _expect(cats, b, 'missing "en"', "Löschen")
        _expect(cats, b, "format specifier mismatch", "'%lld kg' [en]")
        _expect(cats, b, "format specifier mismatch", "'%lld Minuten' [de]")
        _expect(cats, b, "format specifier mismatch", "'%lld Sätze von %@' [en] plural.one")
        _expect(cats, b, "format specifier mismatch", "'Ganzzahl %lld' [en]")
        _expect(cats, b, "malformed variations", "misses other")
        _expect(cats, b, "empty value", "Leer")
        bad_minutes_en = [
            d for d in cats.get((b, "format specifier mismatch"), []) if "'%lld Minuten' [en]" in d
        ]
        assert not bad_minutes_en, bad_minutes_en
        _expect(cats, "iosApp/ThrymIOS/Exercises/Exercises.xcstrings", 'missing "en"', "Kniebeuge")
        _expect(cats, "iosApp/ThrymIOS/Broken.xcstrings", "unparseable catalog", "")

        android_only = run(root, "android")
        assert not any(group.startswith("iosApp/") for group, _ in android_only.categories())
        ios_only = run(root, "ios")
        assert not any(group.startswith(("feature/", "core/")) for group, _ in ios_only.categories())

    with tempfile.TemporaryDirectory() as tmp:
        empty = run(pathlib.Path(tmp), "all").categories()
        assert ("android", "setup") in empty and ("ios", "setup") in empty, empty

    print("check-translations: selftest OK")


if __name__ == "__main__":
    sys.exit(main())
