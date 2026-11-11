#!/usr/bin/env python3
"""Compare Checkstyle/PMD reports with the committed quality baseline."""

from __future__ import annotations

import argparse
import json
import os
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def relative_path(raw: str, root: Path) -> str:
    path = Path(raw)
    if not path.is_absolute():
        path = (Path.cwd() / path).resolve()
    else:
        path = path.resolve()
    try:
        return path.relative_to(root).as_posix()
    except ValueError:
        return path.as_posix()


def parse_checkstyle(path: Path, root: Path) -> tuple[Counter, list[str]]:
    counts: Counter[str] = Counter()
    errors: list[str] = []
    if not path.is_file():
        return counts, [f"missing Checkstyle report: {path}"]
    try:
        tree = ET.parse(path)
    except ET.ParseError as exc:
        return counts, [f"cannot parse Checkstyle report {path}: {exc}"]

    for file_node in tree.getroot():
        if local_name(file_node.tag) != "file":
            continue
        filename = relative_path(file_node.attrib.get("name", ""), root)
        for node in file_node:
            if local_name(node.tag) != "error":
                continue
            source = node.attrib.get("source", "")
            rule = source.rsplit(".", 1)[-1].removesuffix("Check") or "CheckstyleError"
            counts[f"checkstyle|{filename}|{rule}"] += 1
    return counts, errors


def parse_pmd(path: Path, root: Path) -> tuple[Counter, list[str]]:
    counts: Counter[str] = Counter()
    errors: list[str] = []
    if not path.is_file():
        return counts, [f"missing PMD report: {path}"]
    try:
        tree = ET.parse(path)
    except ET.ParseError as exc:
        return counts, [f"cannot parse PMD report {path}: {exc}"]

    root_node = tree.getroot()
    for node in root_node:
        tag = local_name(node.tag)
        if tag == "error":
            filename = relative_path(node.attrib.get("filename", ""), root)
            message = node.attrib.get("msg", node.text or "PMD processing error")
            errors.append(f"PMD processing error in {filename}: {message}")
            continue
        if tag != "file":
            continue
        filename = relative_path(node.attrib.get("name", ""), root)
        for violation in node:
            if local_name(violation.tag) != "violation":
                continue
            rule = violation.attrib.get("rule", "PmdViolation")
            counts[f"pmd|{filename}|{rule}"] += 1
    return counts, errors


def split_key(key: str) -> tuple[str, str, str]:
    tool, filename, rule = key.split("|", 2)
    return tool, filename, rule


def summarize(counts: Counter[str]) -> str:
    tools: Counter[str] = Counter()
    rules: Counter[str] = Counter()
    for key, value in counts.items():
        tool, _filename, rule = split_key(key)
        tools[tool] += value
        rules[f"{tool}:{rule}"] += value
    parts = [f"{tool}={tools[tool]}" for tool in sorted(tools)]
    if rules:
        top = ", ".join(f"{name}={count}" for name, count in rules.most_common(8))
        parts.append(f"top: {top}")
    return "; ".join(parts) if parts else "no violations"


def print_regressions(regressions: list[tuple[str, int, int]], limit: int = 25) -> None:
    for key, current, baseline in regressions[:limit]:
        tool, filename, rule = split_key(key)
        print(f"  +{current - baseline:3}  {tool:10} {rule:28} {filename}")
    if len(regressions) > limit:
        print(f"  ... {len(regressions) - limit} more")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--checkstyle", type=Path, required=True)
    parser.add_argument("--pmd", type=Path, required=True)
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument(
        "--update-baseline",
        action="store_true",
        help="write the current reports as the new baseline",
    )
    args = parser.parse_args()

    root = args.root.resolve()
    checkstyle, checkstyle_errors = parse_checkstyle(args.checkstyle.resolve(), root)
    pmd, pmd_errors = parse_pmd(args.pmd.resolve(), root)
    current = checkstyle + pmd
    errors = checkstyle_errors + pmd_errors

    if args.update_baseline:
        if errors:
            for error in errors:
                print(f"ERROR: {error}", file=sys.stderr)
            return 1
        baseline = {
            "version": 1,
            "project": root.name,
            "violations": dict(sorted(current.items())),
        }
        args.baseline.parent.mkdir(parents=True, exist_ok=True)
        args.baseline.write_text(json.dumps(baseline, indent=2) + "\n", encoding="utf-8")
        print(f"Baseline written: {args.baseline}")
        print(f"  checkstyle: {sum(checkstyle.values())} violations")
        print(f"  pmd:        {sum(pmd.values())} violations")
        print(f"  summary:    {summarize(current)}")
        return 0

    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1
    if not args.baseline.is_file():
        print(
            f"ERROR: baseline not found: {args.baseline}\n"
            "Run scripts/quality.sh --update-baseline after reviewing the reports.",
            file=sys.stderr,
        )
        return 1

    try:
        stored = json.loads(args.baseline.read_text(encoding="utf-8"))
        baseline = Counter(stored["violations"])
    except (json.JSONDecodeError, KeyError, TypeError) as exc:
        print(f"ERROR: invalid baseline {args.baseline}: {exc}", file=sys.stderr)
        return 1

    regressions: list[tuple[str, int, int]] = []
    improvements: list[tuple[str, int, int]] = []
    for key, value in current.items():
        allowed = baseline.get(key, 0)
        if value > allowed:
            regressions.append((key, value, allowed))
        elif value < allowed:
            improvements.append((key, value, allowed))
    for key, allowed in baseline.items():
        if key not in current and allowed > 0:
            improvements.append((key, 0, allowed))

    regressions.sort(key=lambda item: (-(item[1] - item[2]), item[0]))
    improvements.sort(key=lambda item: (-(item[2] - item[1]), item[0]))

    print("Quality gate summary")
    print(f"  checkstyle: {sum(checkstyle.values())} violations")
    print(f"  pmd:        {sum(pmd.values())} violations")
    print(f"  summary:    {summarize(current)}")
    if improvements:
        delta = sum(old - new for _key, new, old in improvements)
        print(f"  improved:   {delta} violation(s) below the baseline")
    if regressions:
        print(f"  regressions: {sum(cur - old for _key, cur, old in regressions)} new/higher violation(s)")
        print_regressions(regressions)
        print(
            "\nFAIL: fix the new violations or, after an intentional refactor, "
            "run scripts/quality.sh --update-baseline.",
            file=sys.stderr,
        )
        return 1

    print("PASS: no quality regression against the baseline.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
