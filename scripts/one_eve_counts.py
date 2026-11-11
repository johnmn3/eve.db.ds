#!/usr/bin/env python3
"""Lexical counts: comments/strings excluded, nested #? forms counted once.
Plain sites exclude macro templates and definitions routed through Eve.
The two unqualified original LRU :lpy/:clje sites were already Eve macros.
"""
import pathlib
import re
import subprocess
import sys


def code_only(source):
    out = list(source)
    string = comment = escape = False
    for i, ch in enumerate(source):
        if comment:
            if ch == "\n": comment = False
            else: out[i] = " "
        elif string:
            if ch != "\n": out[i] = " "
            if escape: escape = False
            elif ch == "\\": escape = True
            elif ch == '"': string = False
        elif ch == ";":
            comment = True
            out[i] = " "
        elif ch == '"':
            string = True
            out[i] = " "
    return "".join(out)


def inventory(ref=None):
    paths = subprocess.check_output(['git', 'ls-tree', '-r', '--name-only', ref, 'src'], text=True).splitlines() if ref else [str(p) for p in pathlib.Path('src').rglob('*') if p.is_file()]
    branches, types = [], []
    for path in sorted(paths):
        if not path.endswith(('.clj', '.cljc', '.cljs', '.clje', '.lpy')): continue
        text = subprocess.check_output(['git', 'show', ref + ':' + path], text=True) if ref else pathlib.Path(path).read_text()
        code = code_only(text)
        for match in re.finditer(r'#[?]@?\(', code):
            branches.append((path, code.count('\n', 0, match.start()) + 1))
        for match in re.finditer(r'\((deftype|defrecord|defrecord-updatable)\s+([A-Za-z][^\s\[()]*)', code):
            line = code.count('\n', 0, match.start()) + 1
            # An unconditional Eve deftype refer excludes clojure.core/deftype.
            if match.group(1) == 'deftype' and re.search(r':refer-clojure\s+:exclude\s+\[[^\]]*\bdeftype\b', code) and 'eve.deftype' in code: continue
            # The original LRU reader arms explicitly refer Eve's macro.
            if path.endswith('/lru.cljc') and match.group(2) == 'LRU' and line in (51, 76): continue
            types.append((path, line, match.group(1), match.group(2)))
    return branches, types


if __name__ == '__main__':
    branches, types = inventory(sys.argv[1] if len(sys.argv) > 1 else None)
    print('host-branches=' + str(len(branches)))
    print('plain-type-sites=' + str(len(types)))
