#!/usr/bin/env bash
# perf_shootout.sh — THE shootout for the eve.db.ds code in THIS repo
# (John, 2026-11-11: perf tests run from eve.db.ds; 2026-11-15: one
# shootout everybody uses, every member a full participant, a fast
# smoke mode).
#
# The operator's shootout definition: cold process, put one datum on
# a disk-backed DataScript DB, cap 10,000 ms — a run over the cap is
# a failure, recorded "10s+". Implemented by the repo's own
# harness/shootout.sh, median reported.
#
# Modes:
#   full (default)        case `one` = test/drivers/contract/one.cljc,
#                         x5 per host (SHOOTOUT_RUNS) — the classic row.
#   smoke (--smoke or SMOKE=1)
#                         the short cases of harness/ds_harness/smoke.cljc,
#                         x3 per host (SHOOTOUT_RUNS):
#                           one    fresh durable domain, one datum
#                           reopen reopen the durable DB a prior `one`
#                                  left (untimed prep), add one datum
#                           small  fresh domain, 20 entities, 1 q, 1 pull
#                         plus a host-start column (the bare host, no Eve),
#                         a warm-up column (one untimed run that fills the
#                         host's durable code caches — JVM AOT, basilisp
#                         .lpyc, the cljs build — once per source tree)
#                         and the in-process :main ms, so wall − main =
#                         process start + namespace load.
#
# Members (HOSTS, default: every member): jvm bb nbb cljs lpy jolt jank
# clje lg glj. `cljs` is Eve's :cljs host — the same harness compiled
# ahead of time by shadow-cljs (harness/run-cljs.sh, a durable build per
# source tree, built in the warm-up) and run on node; `nbb` stays the
# SCI-interpreted row, so the two numbers are not conflated. A member whose toolchain is missing, or whose run fails,
# prints a clear SKIP/FAIL row — never a silent hole.
#
# Env (set at filing time):
#   DS_DIR     eve.db.ds checkout at the merged tip   (REQUIRED)
#   EVE_DIR    Eve checkout at the merged release tip (REQUIRED)
#   OUT        results dir (default: $DS_DIR/perf-results)
#   HOSTS      members to run (default: all, above)
#   CASES      smoke cases (default: "one reopen small")
#   SHOOTOUT_RUNS  runs per row (default 5 full, 3 smoke); a host whose
#              first case fails is stopped there (two failed runs end a row)
#   BB / NBB / BASILISK / JOLT / JANK / CLOJERL / LG / GLJ   binaries
set -u
SMOKE="${SMOKE:-0}"
for a in "$@"; do case "$a" in --smoke) SMOKE=1 ;; --full) SMOKE=0 ;; esac; done
OUT="${OUT:-${DS_DIR:-.}/perf-results}"
mkdir -p "$OUT"
CAP_MS=10000
LOAD1=$(cut -d' ' -f1 /proc/loadavg)
ALL_HOSTS="jvm bb nbb cljs lpy jolt jank clje lg glj"
echo "PERF-SHOOTOUT mode=$([ "$SMOKE" = 1 ] && echo smoke || echo full) load1=$LOAD1 out=$OUT"

verdict () { # median-ms-or-empty -> prints ms or 10s+
  if [ -z "$1" ] || [ "$1" = "none" ]; then echo "FAIL";
  elif [ "$1" -gt "$CAP_MS" ]; then echo "10s+ ($1)";
  else echo "$1"; fi
}

if [ -z "${DS_DIR:-}" ] || [ -z "${EVE_DIR:-}" ]; then
  echo "SHOOTOUT-SKIPPED DS_DIR/EVE_DIR not set"
  echo "SHOOTOUT-DONE table=none"
  exit 0
fi
export EVE_LOCAL="$EVE_DIR" EVE_ROOT="$EVE_DIR"
export BB="${BB:-$(command -v bb || echo "$HOME/workspace/tools/bin/bb")}"
export SHOOTOUT_WORK="$OUT/shootout-runs"
mkdir -p "$SHOOTOUT_WORK"
H="$DS_DIR/harness"

bin_of () { # host -> the binary it needs (for the SKIP check)
  case "$1" in
    jvm) echo clojure ;; bb) echo "$BB" ;; nbb) echo "${NBB:-npx}" ;; cljs) echo node ;;
    lpy) echo "${BASILISK:-basilisp}" ;; jolt) echo "${JOLT:-jolt}" ;;
    jank) echo "${JANK:-jank}" ;; clje) echo "${CLOJERL:-clojerl}" ;;
    lg) echo "${LG:-$EVE_DIR/target/lg-eve/lg-eve}" ;;
    glj) echo "${GLJ:-$EVE_DIR/target/glj-eve/glj-eve}" ;; *) echo "-" ;;
  esac
}

runner () { # host mode... -> the command (the domain prefix is appended by shootout.sh)
  local h="$1"; shift
  case "$h" in
    jvm) if [ "$1" = one ] && [ "$SMOKE" != 1 ]; then echo "bash $H/run-jvm.sh contract.one";
         else echo "bash $H/run-jvm.sh ds-harness.smoke ${*:2}"; fi ;;
    bb)  echo "bash $H/run-bb.sh $*" ;;
    nbb) echo "bash $H/run-nbb.sh $*" ;;
    cljs) echo "bash $H/run-cljs.sh $*" ;;
    lpy) echo "bash $H/run-lpy.sh $*" ;;
    *)   echo "bash $H/run-host.sh $h $*" ;;
  esac
}

host_start () { # host -> a command that starts the bare host (no Eve), or empty
  case "$1" in
    jvm) echo "java -version" ;; bb) echo "$BB -e nil" ;;
    nbb) if [ -x "$EVE_DIR/node_modules/.bin/nbb" ]; then echo "$EVE_DIR/node_modules/.bin/nbb -e nil"; else echo "$(bin_of nbb) -y nbb@1.6.214 -e nil"; fi ;;
    cljs) echo "node -e 0" ;;
    lpy) echo "$(bin_of lpy) run -c nil" ;; jolt) echo "$(bin_of jolt) -e nil" ;;
    *) echo "" ;;
  esac
}

ms_of () { local s=$(date +%s%N); "$@" >/dev/null 2>&1; echo $(( ($(date +%s%N)-s)/1000000 )); }

if [ "$SMOKE" = 1 ]; then
  export SHOOTOUT_RUNS="${SHOOTOUT_RUNS:-3}"
  CASES="${CASES:-one reopen small}"
else
  CASES="one"
fi

T="$OUT/shootout-table.md"
: > "$T"
if [ "$SMOKE" = 1 ]; then
  hdr="| host | host-start ms | warm-up ms |"; sep="|---|---|---|"
  for c in $CASES; do hdr="$hdr $c wall ms (main) |"; sep="$sep---|"; done
else
  hdr="| host | one-datum cold total, median of ${SHOOTOUT_RUNS:-5} (cap 10,000 ms) |"; sep="|---|---|"
fi
echo "$hdr" >> "$T"; echo "$sep" >> "$T"

for h in ${HOSTS:-$ALL_HOSTS}; do
  b=$(bin_of "$h")
  if [ "$b" = "-" ] || ! command -v "$b" >/dev/null 2>&1; then
    row="| $h | SKIP: no toolchain ($b not found) |"
    echo "$row" >> "$T"; echo "HOST-DONE host=$h SKIP no-toolchain=$b"; continue
  fi
  row="| $h |"
  if [ "$SMOKE" = 1 ]; then
    hs=$(host_start "$h"); if [ -n "$hs" ]; then row="$row $(ms_of $hs) |"; else row="$row — |"; fi
    # untimed-for-the-median warm-up: fills the host's durable code caches
    # (run-jvm.sh JVM_AOT, run-lpy.sh LPY_CACHE, jolt's own) once per tree
    W0="$SHOOTOUT_WORK/$h-warm"; rm -f "$W0".* 2>/dev/null
    ws=$(date +%s%N)
    timeout 1200 $(runner "$h" smoke one) "$W0" > "$W0.log" 2>&1
    row="$row $(( ($(date +%s%N)-ws)/1000000 )) |"
    if ! grep -q ONE-DONE "$W0.log"; then
      # the warm-up already shows the member's failure: report it, skip its cases
      # the most specific line wins: a compiler's file:line message, then the usual causes
      clean=$(sed 's/\x1b\[[0-9;]*m//g' "$W0.log" | grep -v -i -e reflection -e '^warning' -e '^-* *error -*$')
      why=""
      for pat in '\[line [0-9]*, col [0-9]*\]' 'caused by' 'not found' 'could not' 'unable to' error exception; do
        why=$(printf '%s\n' "$clean" | grep -i -m1 -e "$pat") && break
      done
      why=$(printf '%s' "$why" | sed 's/^ *//; s/|/\//g' | cut -c1-160)
      row="$row FAIL: $why |"; echo "$row" >> "$T"; echo "HOST-DONE host=$h FAIL warm-up: $why"; continue
    fi
  fi
  for c in $CASES; do
    f="$OUT/shootout-$h$([ "$SMOKE" = 1 ] && echo "-$c").txt"
    if [ "$SMOKE" = 1 ]; then
      cmd=$(runner "$h" smoke "$c")
      if [ "$c" = reopen ]; then
        SHOOTOUT_PREP="$(runner "$h" smoke one)" bash "$H/shootout.sh" "$h-$c" $cmd > "$f" 2>&1
      else
        bash "$H/shootout.sh" "$h-$c" $cmd > "$f" 2>&1
      fi
    else
      bash "$H/shootout.sh" "$h" $(runner "$h" one) > "$f" 2>&1
    fi
    med=$(grep -o ' median-ms=[0-9a-z]*' "$f" | tail -1 | cut -d= -f2)
    mm=$(grep -o 'main-median-ms=[0-9]*' "$f" | tail -1 | cut -d= -f2)
    v=$(verdict "$med")
    if [ "$v" = FAIL ]; then
      v="FAIL: $(grep -m1 'fail-tail' "$f" | sed 's/^ *fail-tail: //; s/|/\//g' | cut -c1-120)"
      row="$row $v |"
      echo "HOST-DONE host=$h case=$c median=$v"
      # the same failure would repeat on the host's other cases: stop the host
      break
    fi
    row="$row $v${mm:+ ($mm)} |"
    echo "HOST-DONE host=$h case=$c median=$v main=${mm:-none}"
  done
  echo "$row" >> "$T"
done
echo "SHOOTOUT-DONE table=$T load1-end=$(cut -d' ' -f1 /proc/loadavg)"
