#!/usr/bin/env python3
"""Process-isolated DataScript suite. A timeout never consumes the next namespace.
Use --load-only locally; actual suite runs belong on a job venue. No upstream
assertion bodies are rewritten. Exit 1 for failures/errors/timeouts/missing summary.
"""
import argparse
import os
from pathlib import Path
import re
import signal
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parent.parent

def capture(cmd, **kw):
    return subprocess.check_output(cmd, text=True, **kw).strip()

def command(host):
    roots = [ROOT / x for x in ("src", "lib", "lib-ds-deps", "test/drivers", "test/upstream-ds/test")]
    if host == "jvm":
        cp = capture(["clojure", "-A:dev:ds-suite", "-Spath"], cwd=ROOT)
        return ["java", "--enable-native-access=ALL-UNNAMED", "--add-opens=java.base/java.lang=ALL-UNNAMED", "-cp", cp, "clojure.main", "-m", "contract.ds-suite"]
    cp = capture([str(ROOT / "scripts/eve_dev_cp.sh"), host], cwd=ROOT)
    cp = ":".join([cp, *map(str, roots)])
    if host == "nbb":
        return ["nbb", "--classpath", cp, "-m", "contract.ds-suite"]
    expression = '(require (quote eve.host-completion)) (require (quote contract.ds-suite)) (apply (resolve (quote contract.ds-suite/-main)) *command-line-args*)'
    if host == "bb":
        return ["bb", "-cp", cp, "-e", expression, "--"]
    # Basilisp run's trailing arguments bind *command-line-args* too.
    paths = [arg for p in reversed(cp.split(":")) for arg in ("-p", p)]
    return ["basilisp", "run", *paths, "-c", expression, "--"]

def run(cmd, timeout, log):
    start = time.monotonic()
    with log.open("w") as f:
        env = os.environ.copy()
        if cmd[0] == "basilisp":
            # Every process must compile assertions through the driver's counter.
            env["BASILISP_DO_NOT_CACHE_NAMESPACES"] = "true"
            env["PYTHONDONTWRITEBYTECODE"] = "1"
        cwd = env.get("EVE_ROOT", str(ROOT)) if cmd[0] == "nbb" else ROOT
        p = subprocess.Popen(cmd, cwd=cwd, env=env, stdin=subprocess.DEVNULL, stdout=f,
                             stderr=subprocess.STDOUT, start_new_session=True)
        try:
            rc = p.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            os.killpg(p.pid, signal.SIGTERM)
            try:
                p.wait(timeout=5)
            except subprocess.TimeoutExpired:
                os.killpg(p.pid, signal.SIGKILL)
                p.wait()
            rc = 124
    text = log.read_text(errors="replace")
    print(text, end="", flush=True)
    return rc, text, round(time.monotonic()-start, 2)

def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("host", choices=["jvm", "bb", "nbb", "lpy"])
    ap.add_argument("--timeout", type=int, default=240, help="seconds PER namespace (including load)")
    ap.add_argument("--load-only", action="store_true")
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("namespaces", nargs="*")
    args = ap.parse_args()
    # Fail closed before any classpath resolution or execution; budget = 3 GiB.
    free = int(subprocess.check_output(["df", "-Pk", str(ROOT)], text=True).splitlines()[1].split()[3])
    if free < 4194304 or free // 2 < 3145728:
        sys.exit("DISK-BOUND-HOLD need_kb=3145728 min_free_kb=4194304 half_free_kb=" + str(free//2))
    inodes = int(subprocess.check_output(["df", "-Pi", str(ROOT)], text=True).splitlines()[1].split()[3])
    if inodes < 1024:
        sys.exit("DISK-INODE-HOLD min_free_inodes=1024 free_inodes=" + str(inodes))
    print(f"DISK free_kb={free} need_kb=3145728 half_free_kb={free//2}", flush=True)
    args.out.mkdir(parents=True, exist_ok=True)
    cmd = command(args.host)
    if args.load_only:
        rc, text, _ = run(cmd + ["--load-only", *args.namespaces], args.timeout, args.out / "load.log")
        print(f"{args.host.upper()}-RC load-only={rc}", flush=True)
        return rc
    if args.namespaces:
        nses = args.namespaces
    else:
        rc, text, _ = run(cmd + ["--list"], args.timeout, args.out / "list.log")
        nses = re.findall(r"^DS-SUITE-NS (\S+)$", text, re.M)
        if rc or not nses:
            print(f"{args.host.upper()}-RC ds-suite=1 list_error={rc}", flush=True)
            return 1
    rows = []
    for ns in nses:
        print(f"DS-NAMESPACE-START host={args.host} ns={ns} timeout_s={args.timeout}", flush=True)
        rc, text, elapsed = run(cmd + [ns], args.timeout, args.out / (ns + ".log"))
        summaries = re.findall(r"^DS-SUITE-SUMMARY (.+)$", text, re.M)
        counts = dict(re.findall(r"(\w+)=(\d+)", summaries[-1])) if summaries else {}
        counts = {k: int(v) for k, v in counts.items()}
        status = ("timeout" if rc == 124 else "error" if not counts or counts.get("error", 0) or counts.get("loaded") != 1 else "fail" if counts.get("fail", 0) else "pass" if rc == 0 else "error")
        rows.append((ns, status, rc, elapsed, counts))
        print(f"DS-NAMESPACE-END host={args.host} ns={ns} status={status} rc={rc} seconds={elapsed}", flush=True)
    print(f"DS-NAMESPACE-TABLE host={args.host}\nnamespace\tstatus\trc\tseconds\tloaded\ttests\tpass\tfail\terror\tskipped", flush=True)
    for ns, status, rc, elapsed, c in rows:
        print("\t".join(map(str, [ns, status, rc, elapsed, *[c.get(k, 0) for k in ("loaded", "tests", "pass", "fail", "error", "skipped")]])), flush=True)
    result = int(any(row[1] != "pass" for row in rows))
    print(f"DS-HOST-SUMMARY host={args.host} nses={len(rows)} " + " ".join(f"{s}={sum(r[1] == s for r in rows)}" for s in ("pass", "fail", "error", "timeout")), flush=True)
    print(f"{args.host.upper()}-RC ds-suite={result}", flush=True)
    return result

if __name__ == "__main__":
    sys.exit(main())
