#!/usr/bin/env python3
"""
Cheap self-call checker for this repo's large VFX classes.

This project cannot be compiled in every environment (Forge/Mojang mavens are not always
reachable), so a whole-file rewrite can silently delete a helper that other code still calls and
the mistake only surfaces on CI. This walks each class for `private`/`public` static methods it
declares, then for bare `name(` call sites inside the same class, and reports calls with no
matching declaration. It is a heuristic, not a compiler, but it catches exactly the deleted-helper
case that a large block replacement causes.

    python3 tools/check_self_calls.py
"""

import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "java")

# Any method declaration, including package-private members of nested classes.
DECL = re.compile(r'^\s*(?:(?:public|private|protected|static|final|abstract|synchronized|default)\s+)*'
                  r'[\w.<>\[\],?]+(?:\s*\[\])?\s+(\w+)\s*\([^;]*?\)\s*(?:\{|throws)', re.M)
CALL = re.compile(r'(?<![\w.])(\w+)\s*\(')

# Java keywords and common constructs that look like calls but are not.
IGNORE = {
    'if', 'for', 'while', 'switch', 'catch', 'return', 'new', 'super', 'this', 'synchronized',
    'assert', 'throw', 'do', 'else', 'try', 'case', 'instanceof', 'record', 'enum', 'class',
    'int', 'float', 'double', 'long', 'boolean', 'char', 'byte', 'short', 'void', 'var',
}


def strip_noise(src):
    """Remove comments and string/char literals so prose cannot look like a call."""
    src = re.sub(r'/\*.*?\*/', ' ', src, flags=re.S)
    src = re.sub(r'//[^\n]*', ' ', src)
    src = re.sub(r'"(?:\\.|[^"\\])*"', '""', src)
    src = re.sub(r"'(?:\\.|[^'\\])*'", "''", src)
    return src


def check(path):
    raw = open(path, encoding='utf-8').read()
    src = strip_noise(raw)
    declared = set(DECL.findall(src))
    # a declaration's own name reads as a call site; never flag those
    declared |= set(re.findall(r'(?<![\w.])(\w+)\s*\([^;)]*\)\s*\{', src))
    # names reachable without a receiver: own methods, plus anything statically imported or
    # inherited is out of scope for this heuristic, so only flag names that look local.
    problems = []
    for name in sorted(set(CALL.findall(src))):
        if name in IGNORE or name in declared:
            continue
        # Only consider camelCase identifiers that appear as a bare call with no qualifier,
        # and that the file does not obviously get from elsewhere.
        if not re.match(r'^[a-z][A-Za-z0-9]*$', name):
            continue
        # skip if every occurrence is qualified (obj.name( or Class.name()
        bare = re.search(r'(?<![\w.])' + re.escape(name) + r'\s*\(', src)
        if not bare:
            continue
        problems.append(name)
    return declared, problems


if __name__ == "__main__":
    # Only the big self-contained VFX/render classes; elsewhere inherited methods make the
    # heuristic too noisy to be useful.
    targets = [
        "com/hexhaki/client/vfx/HakiVfx.java",
        "com/hexhaki/client/vfx/HakiFx.java",
        "com/hexhaki/client/render/GalaxyRenderer.java",
        "com/hexhaki/client/render/BladeSlashRenderer.java",
        "com/hexhaki/client/cinematic/CinematicController.java",
    ]
    bad = 0
    for rel in targets:
        path = os.path.normpath(os.path.join(ROOT, rel))
        if not os.path.isfile(path):
            continue
        declared, problems = check(path)
        # Filter out names that are plainly library calls used unqualified after a static import.
        unresolved = [p for p in problems if p not in {
            'lerp', 'clamp', 'constant', 'color', 'min', 'max', 'abs', 'sqrt', 'sin', 'cos',
            'atan2', 'floor', 'ceil', 'round', 'pow', 'hypot', 'toRadians', 'toDegrees',
            'valueOf', 'of', 'containing', 'format', 'getInstance', 'nextInt', 'nextFloat',
            'nextDouble', 'nextBoolean', 'nextLong', 'begin', 'end', 'vertex', 'uv', 'endVertex',
            'setShader', 'k',
        }]
        status = "OK " if not unresolved else "FAIL"
        print(f"  [{status}] {rel}  ({len(declared)} methods declared)")
        for u in unresolved:
            print(f"          unresolved bare call: {u}(")
            bad += 1
    sys.exit(1 if bad else 0)
