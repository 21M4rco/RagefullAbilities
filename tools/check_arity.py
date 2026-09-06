#!/usr/bin/env python3
"""
Call-site arity check for this repo's static helpers.

CI has twice rejected a commit where a helper's signature and its call site drifted apart during
a bulk edit. This compares declared parameter counts against call-site argument counts for static
methods declared in the same file.

Angle brackets are deliberately NOT treated as nesting: `m.variant()>0` is a comparison, not a
generic, and counting it as a bracket miscounts every argument list that contains one.

    python3 tools/check_arity.py
"""
import glob, os, re, sys

def strip_noise(src):
    src = re.sub(r'/\*.*?\*/', ' ', src, flags=re.S)
    src = re.sub(r'//[^\n]*', ' ', src)
    src = re.sub(r'"(?:\\.|[^"\\])*"', '""', src)
    src = re.sub(r"'(?:\\.|[^'\\])*'", "''", src)
    return src

def split_args(text):
    if not text.strip():
        return 0
    count, depth = 1, 0
    for ch in text:
        if ch in '([{':
            depth += 1
        elif ch in ')]}':
            depth -= 1
        elif ch == ',' and depth == 0:
            count += 1
    return count

def main():
    bad = 0
    for path in sorted(glob.glob('src/main/java/com/hexhaki/**/*.java', recursive=True)):
        src = strip_noise(open(path, encoding='utf-8').read())
        decls = {}
        for m in re.finditer(r'(?:private|public|protected)\s+static\s+[\w.<>\[\],?]+\s+(\w+)\s*\(([^)]*)\)\s*\{', src):
            decls.setdefault(m.group(1), set()).add(split_args(m.group(2)))
        for name, counts in decls.items():
            for m in re.finditer(r'(?<![\w.])' + re.escape(name) + r'\s*\(', src):
                i, depth, start = m.end(), 1, m.end()
                while i < len(src) and depth > 0:
                    if src[i] == '(':
                        depth += 1
                    elif src[i] == ')':
                        depth -= 1
                    i += 1
                if split_args(src[start:i - 1]) not in counts:
                    print(f"  ARITY {os.path.basename(path)}: {name}( "
                          f"called with {split_args(src[start:i-1])}, declared {sorted(counts)}")
                    bad += 1
    print("  arity:", "OK" if not bad else f"{bad} mismatch(es)")
    return 1 if bad else 0

if __name__ == "__main__":
    sys.exit(main())
