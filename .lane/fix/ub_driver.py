#!/usr/bin/env python3
"""Unused-binding renamer, lang-aware by textual proof.

For each kondo unused-binding finding (file, row, col, name):
  - verify the name token sits at (row, col)
  - find the enclosing top-level form
  - if the name occurs NOWHERE else in that form (word-boundary,
    outside strings/comments), rename the binding occurrence to
    _<name>: no pass can be using it.
  - otherwise record it as a skip for manual verdicts.
Processes findings in descending position order per file so
earlier offsets stay valid.
"""
import json, re, sys, collections

def form_extent(text, start):
    depth = 0; i = start; instr = False; esc = False
    while i < len(text):
        c = text[i]
        if instr:
            if esc: esc = False
            elif c == '\\': esc = True
            elif c == '"': instr = False
        else:
            if c == '"': instr = True
            elif c in '([{': depth += 1
            elif c in ')]}':
                depth -= 1
                if depth == 0: return i + 1
        i += 1
    raise ValueError("unbalanced")

def top_form(text, pos):
    # enclosing top-level form: scan top-level forms until one spans pos
    i = 0
    while i < len(text):
        c = text[i]
        if c in '([{':
            j = form_extent(text, i)
            if i <= pos < j:
                return i, j
            i = j
        elif c == '"':
            i = form_extent(text, i) if False else i + 1
            while i < len(text) and text[i] != '"':
                if text[i] == '\\': i += 1
                i += 1
            i += 1
        elif c == ';':
            while i < len(text) and text[i] != '\n': i += 1
        else:
            i += 1
    return None

def strip_strings_comments(s):
    out = []; i = 0; instr = False; esc = False
    while i < len(s):
        c = s[i]
        if instr:
            if esc: esc = False; out.append(' ')
            elif c == '\\': esc = True; out.append(' ')
            elif c == '"': instr = False; out.append(' ')
            else: out.append(' ')
        else:
            if c == '"': instr = True; out.append(' ')
            elif c == ';':
                while i < len(s) and s[i] != '\n': out.append(' '); i += 1
                continue
            else: out.append(c)
        i += 1
    return ''.join(out)

def main(findings_path):
    d = json.load(open(findings_path))
    ub = [x for x in d['findings'] if x['type'] == 'unused-binding']
    byfile = collections.defaultdict(list)
    for x in ub:
        m = re.search(r'Unused binding:?\s*(\S+)', x['message'])
        if not m:
            m = re.search(r'(\S+)\s*$', x['message'])
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
                skipped.append((path, row, name, 'position-mismatch: ' + text[pos:pos + 20]))
                continue
            tf = top_form(text, pos)
            if tf is None:
                skipped.append((path, row, name, 'no-top-form'))
                continue
            a, b = tf
            form = strip_strings_comments(text[a:b])
            rel = pos - a
            rest = form[:rel] + ' ' * len(name) + form[rel + len(name):]
            if re.search(r'(?<![\w\-\.\/\?\!\*\+\>\<\=\$\%\&])' + re.escape(name) + r'(?![\w\-\.\/\?\!\*\+\>\<\=\$\%\&])', rest):
                skipped.append((path, row, name, 'used-elsewhere-in-form langs=' + str(langs)))
                continue
            text = text[:pos] + '_' + name + text[pos + len(name):]
            renamed += 1
        open(path, 'w').write(text)
    print('renamed', renamed)
    for s in skipped:
        print('SKIP', s)

if __name__ == '__main__':
    main(sys.argv[1])
