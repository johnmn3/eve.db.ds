#!/usr/bin/env python3
"""Unused-binding renamer v2: scope = innermost list form that has
the binding vector/map as a DIRECT child (method, arity, let, fn).
Rename iff the name occurs nowhere else in that scope (strings and
comments stripped), which proves no reader-conditional arm or pass
in the scope uses it."""
import json, re, sys, collections

OPEN = '([{'; CLOSE = ')]}'

def strip_sc(s):
    out = []; i = 0; instr = False; esc = False
    while i < len(s):
        c = s[i]
        if instr:
            if esc: esc = False
            elif c == '\\': esc = True
            elif c == '"': instr = False
            out.append(' ')
        else:
            if c == '"': instr = True; out.append(' ')
            elif c == ';':
                while i < len(s) and s[i] != '\n': out.append(' '); i += 1
                continue
            else: out.append(c)
        i += 1
    return ''.join(out)

class Node:
    __slots__ = ('kind', 'start', 'end', 'children', 'parent')
    def __init__(self, kind, start, parent):
        self.kind = kind; self.start = start; self.end = None
        self.children = []; self.parent = parent

def parse(text):
    root = Node('root', 0, None)
    stack = [root]
    i = 0; instr = False; esc = False
    while i < len(text):
        c = text[i]
        if instr:
            if esc: esc = False
            elif c == '\\': esc = True
            elif c == '"': instr = False
            i += 1; continue
        if c == '"':
            instr = True; i += 1; continue
        if c == ';':
            while i < len(text) and text[i] != '\n': i += 1
            continue
        if c in OPEN:
            n = Node(c, i, stack[-1]); stack[-1].children.append(n); stack.append(n)
        elif c in CLOSE:
            if len(stack) > 1:
                n = stack.pop(); n.end = i + 1
        i += 1
    root.end = len(text)
    return root

def path_to(node, pos):
    path = [node]
    while True:
        nxt = None
        for ch in path[-1].children:
            if ch.start <= pos < (ch.end or 0):
                nxt = ch; break
        if nxt is None: return path
        path.append(nxt)

def scope_for(root, pos):
    path = path_to(root, pos)
    # ancestors are list nodes; find the deepest list ancestor whose
    # child on the path is a vector/map (the binding form)
    for idx in range(len(path) - 1, 0, -1):
        anc = path[idx - 1]; child = path[idx]
        if anc.kind == '(' and child.kind in '[{':
            return anc
    # fallback: deepest list ancestor
    for idx in range(len(path) - 1, 0, -1):
        if path[idx - 1].kind == '(':
            return path[idx - 1]
    return None

def main(findings_path):
    d = json.load(open(findings_path))
    ub = [x for x in d['findings'] if x['type'] == 'unused-binding']
    byfile = collections.defaultdict(list)
    for x in ub:
        m = re.search(r'[Uu]nused binding:?\s*(\S+)', x['message'])
        name = m.group(1).strip()
        byfile[x['filename']].append((x['row'], x['col'], name, tuple(x.get('langs') or [])))
    renamed = 0; skipped = []
    for path, items in sorted(byfile.items()):
        text = open(path).read()
        lines = text.split('\n')
        offsets = [0]
        for ln in lines: offsets.append(offsets[-1] + len(ln) + 1)
        items.sort(key=lambda t: (t[0], t[1]), reverse=True)
        for row, col, name, langs in items:
            pos = offsets[row - 1] + (col - 1)
            if text[pos:pos + len(name)] != name:
                skipped.append((path, row, name, 'position-mismatch'))
                continue
            root = parse(text)
            sc = scope_for(root, pos)
            if sc is None:
                skipped.append((path, row, name, 'no-scope'))
                continue
            scope_text = strip_sc(text[sc.start:sc.end])
            rel = pos - sc.start
            rest = scope_text[:rel] + ' ' * len(name) + scope_text[rel + len(name):]
            if re.search(r'(?<![\w\-\.\/\?\!\*\+\>\<\=\$\%\&])' + re.escape(name) + r'(?![\w\-\.\/\?\!\*\+\>\<\=\$\%\&])', rest):
                skipped.append((path, row, name, 'used-in-scope langs=' + str(langs)))
                continue
            text = text[:pos] + '_' + text[pos:]
            # fix offsets for subsequent (earlier) positions: they are
            # all before pos, so unaffected.
            renamed += 1
        open(path, 'w').write(text)
    print('renamed', renamed)
    for s in skipped: print('SKIP', s)

if __name__ == '__main__':
    main(sys.argv[1])
