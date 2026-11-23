import json, re, sys, collections

LANE = "/home/hatch/workspace/syntax-formalization/lanes/eve-db-ds"
d = json.load(open(f"{LANE}/census-raw3/kondo2.json"))
findings = d["findings"]

def ns_form_span(text):
    i = text.index("(ns ")
    depth = 0
    in_str = False
    esc = False
    for j in range(i, len(text)):
        c = text[j]
        if in_str:
            if esc: esc = False
            elif c == "\\": esc = True
            elif c == '"': in_str = False
            continue
        if c == '"': in_str = True
        elif c in "([{": depth += 1
        elif c in ")]}":
            depth -= 1
            if depth == 0: return i, j + 1
    raise ValueError("unbalanced")

def remove_refer_name(ns_text, lib, name):
    # find the lib spec containing lib, then its :refer [ ... ] vector, remove name token
    # locate occurrences of lib string
    for m in re.finditer(re.escape(lib) + r"(?![\w.-])", ns_text):
        # search forward for :refer within the same bracket spec (up to 400 chars)
        seg = ns_text[m.start():m.start()+600]
        rm = re.search(r":refer\s*\[([^\]]*)\]", seg)
        if not rm: continue
        inner = rm.group(1)
        names = inner.split()
        if name not in names: continue
        names = [n for n in names if n != name]
        if names:
            new_inner = " ".join(names)
            seg2 = seg[:rm.start(1)] + new_inner + seg[rm.end(1):]
        else:
            # remove the whole :refer [...] clause
            seg2 = seg[:rm.start()] + seg[rm.end():]
            seg2 = re.sub(r"\s+", " ", seg2, count=1) if False else seg2
        return ns_text[:m.start()] + seg2 + ns_text[m.start()+len(seg):], True
    return ns_text, False

def remove_require(ns_text, lib):
    # remove a [...] spec or bare lib token in :require clauses containing lib as first element
    pat = re.compile(r"\[\s*" + re.escape(lib) + r"(?![\w.-])(?:[^\[\]]|\[[^\]]*\])*\]")
    m = pat.search(ns_text)
    if m:
        return ns_text[:m.start()] + ns_text[m.end():], True
    # bare symbol in :require ( ... lib ... )
    pat2 = re.compile(r"(?<![\w./-])" + re.escape(lib) + r"(?![\w.-])")
    m = pat2.search(ns_text)
    if m:
        return ns_text[:m.start()] + ns_text[m.end():], True
    return ns_text, False

def remove_import(ns_text, cls):
    # (:import [pkg Cls1 Cls2]) or (:import pkg.Cls)
    pat = re.compile(r"\[\s*([\w.]+)\s+([^\]]*)\]")
    for m in pat.finditer(ns_text):
        names = m.group(2).split()
        if cls in names:
            names = [n for n in names if n != cls]
            if names:
                rep = "[" + m.group(1) + " " + " ".join(names) + "]"
                return ns_text[:m.start()] + rep + ns_text[m.end():], True
            else:
                return ns_text[:m.start()] + ns_text[m.end():], True
    pat2 = re.compile(r"(?<![\w.-])[\w.]*\." + re.escape(cls) + r"(?![\w.-])")
    m = pat2.search(ns_text)
    if m:
        return ns_text[:m.start()] + ns_text[m.end():], True
    return ns_text, False

mode = sys.argv[1]  # dry|apply
types = sys.argv[2].split(",")
scopes = sys.argv[3].split(",") if len(sys.argv) > 3 else ["test-other", "upstream"]

def scope(fn):
    if fn.startswith("src/"): return "src"
    if "upstream-ds" in fn: return "upstream"
    return "test-other"

work = collections.defaultdict(list)
for x in findings:
    if x["type"] in types and scope(x["filename"]) in scopes:
        work[x["filename"]].append(x)

changed, failed = [], []
for fn, xs in sorted(work.items()):
    path = f"{LANE}/{fn}"
    text = open(path).read()
    s, e = ns_form_span(text)
    ns_text = text[s:e]
    orig = ns_text
    done_refers = set()
    for x in xs:
        if x["type"] == "unused-referred-var" and x["refer"] in done_refers:
            continue
        if x["type"] == "unused-referred-var":
            libs = [x["referred-ns"]]
            if x["referred-ns"] == "cljs.test": libs.append("clojure.test")
            if x["referred-ns"] == "clojure.test": libs.append("cljs.test")
            ok = False
            for lib in libs:
                ns_text, ok = remove_refer_name(ns_text, lib, x["refer"])
                if ok: break
            if not ok and x.get("_already_gone"):
                ok = True
        elif x["type"] == "unused-namespace":
            lib = re.search(r"namespace ([\w.-]+) is required", x["message"]).group(1)
            ns_text, ok = remove_require(ns_text, lib)
        elif x["type"] == "unused-import":
            cls = re.search(r"Unused import ([\w.$]+)", x["message"]).group(1)
            ns_text, ok = remove_import(ns_text, cls)
        else:
            ok = False
        if not ok:
            failed.append((fn, x["type"], x["message"]))
        elif x["type"] == "unused-referred-var":
            done_refers.add(x["refer"])
    if ns_text != orig:
        changed.append(fn)
        if mode == "apply":
            open(path, "w").write(text[:s] + ns_text + text[e:])
        else:
            print("WOULD CHANGE", fn)
    else:
        for x in xs: failed.append((fn, x["type"], x["message"] + " [no-op]"))
print("changed:", len(changed))
for f in failed: print("FAILED:", f)
