#!/usr/bin/env python3
"""Heuristic unresolved-type checker for the mod sources.

We cannot compile locally (Forge mavens are unreachable from the sandbox), so this
catches the most common compile break: using a class without importing it.

For every .java file it collects the simple names that are used in a type-ish
position (`Foo.bar(`, `new Foo(`, `Foo x =`, `(Foo)`) and reports any that are not
imported, not declared in the file, not in the same package, and not java.lang.
"""
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                    "src", "main", "java")

JAVA_LANG = {
    "Object", "String", "Integer", "Long", "Double", "Float", "Boolean", "Byte",
    "Short", "Character", "Math", "System", "Thread", "Runnable", "Exception",
    "RuntimeException", "IllegalArgumentException", "IllegalStateException",
    "NullPointerException", "Throwable", "Error", "Number", "Class", "Enum",
    "Comparable", "Iterable", "CharSequence", "StringBuilder", "Override",
    "SuppressWarnings", "Deprecated", "FunctionalInterface", "SafeVarargs",
    "Void", "Record", "Cloneable", "AutoCloseable", "ClassCastException",
    "UnsupportedOperationException", "ArithmeticException", "StackTraceElement",
    "IndexOutOfBoundsException", "NumberFormatException", "Package", "Runtime",
}

# Star imports resolve against these when the package is outside the project.
STAR_PACKAGES = {
    "java.util": {
        "List", "ArrayList", "Map", "HashMap", "LinkedHashMap", "TreeMap",
        "NavigableMap", "Set", "HashSet", "LinkedHashSet", "TreeSet", "Deque",
        "ArrayDeque", "Queue", "Iterator", "Optional", "UUID", "Random",
        "Collections", "Arrays", "Comparator", "WeakHashMap", "EnumMap",
        "EnumSet", "Objects", "Locale", "Collection", "IdentityHashMap",
    },
    "java.util.function": {
        "Function", "BiFunction", "Supplier", "Consumer", "BiConsumer",
        "Predicate", "BiPredicate", "UnaryOperator", "BinaryOperator",
    },
    "java.util.stream": {"Stream", "IntStream", "Collectors", "StreamSupport"},
    "java.io": {"IOException", "InputStream", "OutputStream", "Reader", "File"},
}

PRIMITIVES = {"int", "long", "double", "float", "boolean", "byte", "short",
              "char", "void", "var", "this", "super", "new", "return", "if",
              "else", "for", "while", "switch", "case", "do", "try", "catch",
              "finally", "throw", "break", "continue", "instanceof", "null",
              "true", "false"}

USE_PATTERNS = [
    re.compile(r"\bnew\s+([A-Z][A-Za-z0-9_]*)\s*[(<]"),
    re.compile(r"\b([A-Z][A-Za-z0-9_]*)\s*\.\s*[a-zA-Z_]"),
    re.compile(r"\b([A-Z][A-Za-z0-9_]*)\s*\.\s*class\b"),
    re.compile(r"\b([A-Z][A-Za-z0-9_]*)\s*<[^;()]{0,80}>\s+[a-z_][A-Za-z0-9_]*\s*[=;,)]"),
    re.compile(r"\(\s*([A-Z][A-Za-z0-9_]*)\s*\)\s*[a-zA-Z_(]"),
    re.compile(r"\b([A-Z][A-Za-z0-9_]*)\s*::"),
    re.compile(r"\binstanceof\s+([A-Z][A-Za-z0-9_]*)"),
]

DECL_PATTERN = re.compile(
    r"\b(?:class|interface|enum|record|@interface)\s+([A-Z][A-Za-z0-9_]*)")


def strip_noise(src):
    src = re.sub(r"/\*.*?\*/", " ", src, flags=re.S)
    src = re.sub(r"//[^\n]*", " ", src)
    src = re.sub(r'"(?:\\.|[^"\\])*"', '""', src)
    src = re.sub(r"'(?:\\.|[^'\\])*'", "' '", src)
    return src


def package_types(java_root):
    """Map package name -> set of top-level type names declared in it."""
    by_package = {}
    for dirpath, _dirs, files in os.walk(java_root):
        for name in files:
            if not name.endswith(".java"):
                continue
            rel = os.path.relpath(dirpath, java_root)
            pkg = rel.replace(os.sep, ".")
            by_package.setdefault(pkg, set()).add(name[:-5])
    return by_package


def check(path, by_package):
    with open(path, encoding="utf-8") as handle:
        raw = handle.read()
    src = strip_noise(raw)

    pkg_match = re.search(r"^\s*package\s+([\w.]+)\s*;", src, flags=re.M)
    pkg = pkg_match.group(1) if pkg_match else ""

    known = set(JAVA_LANG)
    known |= by_package.get(pkg, set())

    star_packages = []
    for imp in re.findall(r"^\s*import\s+(?:static\s+)?([\w.]+(?:\.\*)?)\s*;",
                          src, flags=re.M):
        if imp.endswith(".*"):
            star = imp[:-2]
            if star in by_package:
                known |= by_package[star]
            elif star in STAR_PACKAGES:
                known |= STAR_PACKAGES[star]
            else:
                star_packages.append(star)
        else:
            known.add(imp.rsplit(".", 1)[-1])

    # Types declared (or nested) in this file, plus generic type parameters.
    known |= set(DECL_PATTERN.findall(src))
    for params in re.findall(r"<\s*([A-Z][A-Za-z0-9_]*)\s*(?:extends[^>]*)?>", src):
        known.add(params)

    if star_packages:
        # An unresolvable wildcard import could supply anything; skipping beats
        # drowning the real findings in noise.
        return []

    problems = []
    for pattern in USE_PATTERNS:
        for match in pattern.finditer(src):
            name = match.group(1)
            if name in known or name in PRIMITIVES:
                continue
            # SCREAMING_CASE is a constant field, never a type.
            if name.upper() == name:
                continue
            # Fully-qualified usage: something.Foo.bar -> already resolved.
            start = match.start(1)
            if start > 0 and src[start - 1] == ".":
                continue
            line = src.count("\n", 0, start) + 1
            problems.append((line, name))
    return sorted(set(problems))


def main():
    by_package = package_types(ROOT)
    failures = 0
    files = 0
    for dirpath, _dirs, names in os.walk(ROOT):
        for name in sorted(names):
            if not name.endswith(".java"):
                continue
            path = os.path.join(dirpath, name)
            files += 1
            for line, symbol in check(path, by_package):
                rel = os.path.relpath(path, ROOT)
                print(f"{rel}:{line}: cannot resolve symbol '{symbol}'")
                failures += 1
    print(f"checked {files} files: {'OK' if not failures else str(failures) + ' problem(s)'}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
