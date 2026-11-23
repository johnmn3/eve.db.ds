# Eve mmap commit log ("jlog"): scope, spec and spike plan

*Written 2026-11-23 15:00 ET for John's dispatch (t130u, t131u). Grounded in eve main `9536ef2` and eve.db.ds main `24d8754`.*

Citation forms:
- `E:path:line` means `git -C /workspace/eve-perf/eve show 9536ef2:path`.
- `DS:path:line` means eve.db.ds `24d8754`.
- `BL:n` means `/workspace/eve-perf/BASELINE.md` line n.

The contention-shootout harness is not committed anywhere yet. It exists only as untracked files in `/workspace/eve-perf/ds-cx`:
- `scripts/contention_shootout.sh`
- `scripts/contention_shootout_check.clj`
- `test/drivers/contract/ds_shootout.cljc`
- `test/drivers/contract/ds_shootout_cljs.cljs`

The implementer must copy these, not assume they are on a branch.

---

## 1. Problem and evidence

### 1.1 What the journal is today

A durable disk domain (`persistent-atom-domain`, default `:durable? true` → `:journal? true`, E:src/eve/atom.cljc:2124-2160) opens `<base>.journal` beside `<base>.root` (E:src/eve/atom.cljc:974-989).

Each record is `[magic EVJ1][len][epoch][table-len][2048-byte atom table][crc32]`, big-endian (E:src/eve/atom.cljc:734-756).

The journal decides nothing about liveness. Running processes coordinate only through the root CAS and the epoch bump (E:src/eve/atom.cljc:1663-1666). The journal exists for durability and nothing else.

The file mechanics are reimplemented in 11 per-host adapters under `adapters/journal-file/*`. Each one does its own lock, append, scan, truncate and force (`hosts.edn` lines 14, 20, 41, 45, 76, 80, 84, 88, 107, 120, 132, 148).

### 1.2 Why it loses data across runtimes (re-derived)

1. **The locks are from different families.**
   - The JVM takes `FileChannel.lock` (E:adapters/journal-file/java/src/eve/journal_file.clj:69-94, `.lock` at 91), which is an fcntl lock on Linux.
   - jolt takes `lockf` on the fd (E:adapters/journal-file/jolt/src/eve/journal_file.clj:244-269, call at 258).
   - glj uses fcntl `F_SETLKW` (E:impls/glojure/host/journal.go).
   - Node uses a lock **directory**: mkdir plus an owner file (E:adapters/journal-file/node/src/eve/journal_file.cljs:136-181, mkdir at 156). Its own docstring admits it "does not exclude the POSIX seats" (same file :1-15).
   - Result: a node writer and a JVM or jolt writer can hold "the journal lock" at the same time.

2. **The append offsets race.**
   - The JVM does `(.position fc (.size fc))` and then writes (java journal_file.clj:180-186; brief's ":190" is `journal-write-at!`).
   - jolt does `pwrite` at `lseek(SEEK_END)` (jolt journal_file.clj:330-336, `file-size` at 199-204).
   - Node writes through an `"a+"` (O_APPEND) fd (node journal_file.cljs:53-58, 248-254).
   - A node record appended between the JVM's or jolt's size read and its write is overwritten. Records can also land in file order ≠ epoch order.

3. **The scan stops at the first framing failure.** On a bad magic or length it returns `{:torn? true}` with whatever came before (java :154-157, node :233-236). A CRC failure alone is skipped (java :173-178). "Newest" means **last in file order**, not highest epoch.

4. **Recovery rolls the root back.**
   - Line numbers: at 9536ef2, `journal-recover!` is E:src/eve/atom.cljc:878-928. The brief's 858-913 are from 38e6da6.
   - On a non-live domain it installs `newest` whenever its epoch differs from the root's, or its table differs at the same epoch (:908-923). That includes an epoch that is **lower** than the root's.
   - It then truncates and rewrites that one record (:924-928). Every later commit is gone.

5. **Close does the same thing.** `journal-anchor!` (E:src/eve/atom.cljc:930-953), called from `journal-checkpoint!` on every close and `sync!` (:2179-2190, :2199, :2229-2238), truncates the file down to the file-order newest record.
   - With mixed lock families, it can truncate under a concurrent node append.
   - If file-order newest is older than the root, the next open installs it and rolls back. This is a second route to the same loss, with no overwrite needed.

### 1.3 Shootout numbers

8 s legs, one fresh shared store, `scratch/cx/res-*/verdict.txt`, BL:862:

| leg | tx | result after all writers exit and a fresh open |
|---|---:|---|
| jvm×2 | 694 | PASS, all exact |
| jolt×2 | 705 | PASS |
| jvm+jolt | 618 | PASS (618 entities, :n 2,472/2,472, cas 156) |
| jolt+cljs | 611 | PASS on data. Only failure is the separate cljs `:advanced` d/q-nil bug (BL:859) |
| **jvm+cljs** | 595 | **FAIL: 7 entities of 595 tx, 37 datoms, shared :n 25 of 2,380 swaps, DB cas counter 2 of 150** |
| **jvm+jolt+cljs** | 743 | **FAIL: 82 of 743 tx, :n 323 of 2,972** |

During the run, every writer saw every other writer's commits (BL:862). So the live CAS path is correct, and the loss happens entirely in journal close and recovery. The pairs that pass are exactly the pairs whose locks are in the same fcntl family.

### 1.4 Further durability holes found while re-deriving (system crash, not process crash)

- **H1: on jolt, glj, beam and bb (lock-only), every slab or root force is a silent no-op.**
  - `mem/sync-region!` does nothing unless the region implements `ISyncableRegion` (E:src/eve/mem.cljc:124-144).
  - `JoltRegion` (E:adapters/region/jolt-ffi/src/eve/region.clj:186), `GljRegion` (E:adapters/region/glojure/src/eve/region.glj:17), `CljeRegion` and bb's lock-only `BbMmapRegion` (E:adapters/region/jvm-nio-lock/src/eve/region.clj:224) do not implement it. `git grep ISyncableRegion` lists only clr, ctypes, dart, jank, js, jvm-nio-lock's native `BbNativeRegion`, jvm-varhandle, letgo and vs.
  - jolt's per-commit durability today is therefore just an fsync of the journal fd (jolt journal_file.clj:350-355). Slab pages referenced by a durable record may never have reached disk.
  - This is also why jolt's disk-swap reads 0.7× the JVM (805,664 vs 1,165,527 ns, BL:287, BL:300).
- **H2: J1 records can name other processes' unforced blocks.**
  - `journal-flush!` sweeps only this commit's touched classes and then serialises the **whole live table** (E:src/eve/atom.cljc:797-805).
  - Journal domains skip the pre-CAS sweep (:1653-1662). So process B's freshly published pointer can enter A's durable record before B has forced B's blocks.
  - J10 has the same shape: the sweep runs before the table is read.
- **H3: the whole-domain force misses two of the descriptor columns.** `sync-jvm-slab-regions!` skips the desc-flags and desc-pointer columns (E:src/eve/deftype_proto/alloc.cljc:1320-1333). Its neighbour's docstring at :1335-1342 says so.
- **H4: the Node msync assumes 4 KiB pages.** It hard-codes `page = 4096` (E:native/mmap_cas.cc:743-759), so on 16 KiB-page hosts (Apple Silicon) an unaligned start returns EINVAL.
- **H5: a kill -9 mid-swap leaks `ROOT_INFLIGHT` forever.** Nothing resets it except `init-domain-root!` (E:src/eve/atom.cljc:357). Every later held write then spins forever (`with-held-write*` :303-325, `with-exclusive-hold*` :275-291, which is every swap on a lock-only bb seat). This is pre-existing and out of scope, except that recovery (§4.4) repairs it on non-live domains.

---

## 2. Goals and non-goals

### Goals

- **G1 Durability (J1, the default).** When `swap!`/`reset!`/`d/transact!` returns on any host, the commit survives:
  - a process crash (kill -9);
  - an OS crash or power loss, on storage that honours the flush (msync MS_SYNC / fdatasync).
- **G2 Cross-runtime correctness.** Any mix of hosts committing to one domain at once loses no acknowledged commit, in the live state or after reopen. The shootout (§6.2) passes for every pair and for all hosts together.
- **G3 One implementation.** The log format, the write protocol and recovery are written once, in cljc, in eve. Hosts supply only region primitives they already have for the atom (§4.6). No OS file locks.
- **G4 Monotone recovery.** Recovery never installs a state older than the newest **valid** record. It never truncates or erases a valid record.

### Crash model

- Process death at any instruction.
- OS crash or power loss: pages not covered by a completed msync may hold old, new or torn contents, at 512 B or 4 KiB granularity.
- Aligned 4-byte stores are not torn in the page cache.
- The storage honours MS_SYNC/fdatasync. On macOS, plain fsync/msync do not flush the drive cache, so durability there equals today's (open question Q4).

### Semantics kept

- Acknowledged ⇒ durable.
- Unacknowledged commits may survive or vanish.
- Per-atom: recovery yields a state at least as new as every acknowledged commit.
- No multi-atom atomicity, same as today.
- `:journal-batch N` / `:journal-window-ms` (J10) keep their "durable at batch boundary or `sync!`" meaning.
- `:durable? false` keeps "no log; `sync!` is the barrier".

### Non-goals

- Replication.
- Reader tailing or log shipping.
- Value-level (datom) logging. Records stay commit metadata, the atom table only.
- Changing the CAS, epoch, pin or retire protocol.
- Journal batching policy (that waits on John's durability decision).
- Fixing H5 for live domains.

### Performance (current cost, then targets)

**Current per-commit cost, all J1 default:**
- JVM disk-swap: 845,778 ns (probe, BL:260-266). Of disk-swap wall time, `seg-force!` (slab msync) is 34.1% and `journal-force!` (fsync) 27.6%, both under the journal lock (BL:205).
- jolt: 1,028 µs default vs 141 µs `:durable? false`. `journal-flush!` takes 857 of 2,757 µs per swap under timing wrappers (BL:323-325). jolt pays no slab force (H1).
- perf-all disk-swap (BL:287): jvm 1.17 ms, cljs 1.32, jank 3.95, jolt 0.81, nbb 2.39, bb 3.17, glj 13.2.

**Primitive costs on this box** (ext4 on /dev/vdb, 2,068-byte record, median of 400, box loaded, load average 2.3):

| primitive | median |
|---|---|
| pwrite at file size + fdatasync on a growing file (today's JVM/jolt shape) | 131–160 µs |
| mmap copy + msync of the record range, sparse file | 102–137 µs |
| mmap copy + msync of the record range, fallocated file | 78–115 µs |
| pwrite at offset + fdatasync, fallocated file | 82–111 µs |

Script: `/tmp/jspec/syncbench2.py`, run 14:53 ET. The device flush dominates. mmap+msync and pwrite+fdatasync cost the same, and preallocation saves about 20–30%.

So the gains come from these, not from syscall count:
- no global lock held across the flush, so concurrent writers' flushes overlap;
- a preallocated file, so no size or metadata update per commit;
- one record flush per commit instead of journal fsync plus lock traffic.

**Targets:**
- **T1 Single writer.** disk-swap on JVM, cljs, jank and nbb no worse than 9536ef2 by more than 5% on 5 of 5 interleaved pairs (`abx6`). The target is about 15–25% faster from dropping the size and lock path.
  - jolt and glj will get **slower**, because their slab forces become real (H1). That must be reported as a correctness cost, never hidden, and never offset by changing what disk-swap measures.
- **T2 Contention.** Aggregate committed tx/s for k writers on one domain (k = 2, 4, all hosts) at or above solo tx/s on the slowest participant. Per-runtime together/solo slowdown within 1.5× of the `nodurable` control leg's slowdown.
- **T3 Recovery.** Opening a crashed domain with the default ring (1,024 slots) takes under 50 ms on the JVM for the log scan.

---

## 3. Scope

### Added
- `src/eve/jlog.cljc`: layout, reserve and append, scan, recovery pick, header rebuild, and the legacy EVJ1 reader for migration.
- `src/eve/crc32.cljc`: a portable slicing-by-8 CRC, with an optional host-override hook.
- `ISyncableRegion` on `JoltRegion` (adapters/region/jolt-ffi), `GljRegion` (adapters/region/glojure plus impls/glojure/region) and bb's lock-only `BbMmapRegion` (adapters/region/jvm-nio-lock).
- Node addon msync page size from sysconf (native/mmap_cas.cc).
- Tests from §6.

### Changed
- `src/eve/atom.cljc`:
  - The journal section (:734-957) is replaced by jlog calls.
  - In `swap-attempt-vals!` (:1599-1745), journal domains now take the pre-CAS force (:1659), and the J1/J10 branches call jlog (:1671-1706).
  - `open-mmap-domain!` (:959-1006): `.jlog` open, recovery hold, slot claim inside the hold.
  - `journal-checkpoint!` and `release-domain!` (:2179-2200) lose the truncating anchor.
  - `hold-holder-stale?` (:233-239) learns `HOLD_RECOVERING`.
- `src/eve/mem.cljc` `sync-region!` (:135-144): a durable domain fails at open instead of no-op.
- `src/eve/deftype_proto/alloc.cljc` `sync-jvm-slab-regions!` (:1320-1333): include the desc-flags and desc-pointer columns (H3).
- `hosts.edn`, `deps.edn`, `bb.edn`: drop the `adapters/journal-file/*` paths in P3.
- Docs: `adapters/README.md`, `doc/memory-model.md`, `doc/atom-domains.md`, `CHANGELOG.md`.
- eve.db.ds: `harness/shootout.sh:20` (sibling list) only.

### Deleted (P3, after migration ships)
- All 11 `adapters/journal-file/*` dirs: beam, clr, dart, glojure, jank, java, jolt, letgo, node, python, unsupported, and their `eve.journal-file` namespaces.
- `impls/glojure/host/journal.go`.
- The jolt slicing-by-8 CRC moves into the `eve.crc32` override, so it is not lost.
- Callers to update: `contract-test/drivers/contract/one_cljs.cljs`, `one_nbb.cljc`, `contract-test/drivers/probe_glj_load.clj`, DS:test/drivers/probe_glj_load.clj:14, and `test/eve/journal_open_test.cljc`.

### Back-compat
- Migration is in §4.7.
- `persistent-atom-domain` options are unchanged. `:journal-batch` and `:journal-window-ms` keep their meaning.
- New options: `:jlog-slots` and `:jlog-stripes`.

## 3b. Prior art: Chronicle Queue and Chronicle Map (t131u)

John's framing (t131u): "something like a super fast chronicle map impl'd on disk, using eve-like techniques, which should allow for fast journal semantics." Facts below come only from OpenHFT's public docs, fetched 2026-11-23:
- [Chronicle Queue README](https://github.com/OpenHFT/Chronicle-Queue/blob/ea/README.adoc)
- [Chronicle Map CM_Features](https://github.com/OpenHFT/Chronicle-Map/blob/ea/docs/CM_Features.adoc)
- [Chronicle Map spec/1-design-goals](https://github.com/OpenHFT/Chronicle-Map/blob/ea/spec/1-design-goals.md)
- [Chronicle Map CM_Tutorial §Recovery](https://github.com/OpenHFT/Chronicle-Map/blob/ea/docs/CM_Tutorial.adoc)

**Chronicle Queue** is a persisted, broker-less, append-only log on memory-mapped files.
- It "supports multiple writers to a queue via locking, and multiple lock-less concurrent readers" (README §Overview).
- Each excerpt gets "a 4-byte header" holding its length, with bits reserved "for a number of internal operations, such as locking, to make Chronicle Queue thread-safe across both processors and threads" (README, "a document").
- In v5 the queue-wide write lock moved into a separate table-store file, `metadata.cq4t` (README, v4→v5 notes).
- So in current releases writers **serialise on a write lock**. A per-document header word is not a lock-free multi-writer tail CAS, and the parent's summary was slightly off here.
- Files roll by cycle (minutely, daily and so on). Positions are found through index arrays, tuned by `indexSpacing` and `indexCount` ("indexCount² is the maximum number of indexed queue entries").
- **Durability:** "If your application dies, the operating system keeps running for seconds longer, so no data is lost". That is page-cache durability, not per-write fsync. An async mode exists for slow filesystems.

**Chronicle Map** is an off-heap key-value store, optionally persisted to one memory-mapped file, shared by many processes.
- It is "split into independent segments; each segment has … a hash table … a lock in shared memory (implemented via CAS loops)" (CM_Features).
- **Durability** is "no; Chronicle Map can be persisted to disk, but with no guarantee as to how frequently this happens" (CM_Features). The design goals say one could "call `msync` after each operation", but "it would be better to use a data store designed for durability".
- After an OS crash or power loss, the store must be opened with `recoverPersistedTo()`, which needs **exclusive** access and "identifies and purges corrupted entries … some entries updated shortly before the failure could be lost" (design goals; tutorial §Recovery).
- It assumes aligned 4- or 8-byte disk writes are atomic (design goals §Memory-mapping).

**What eve already is, and where the jlog borrows or differs:**

| concern | Chronicle | eve jlog (this spec) |
|---|---|---|
| shared state | Map: segmented hash table, per-segment CAS spin locks | eve atoms: persistent HAMT/vector values in mmapped slabs. One CAS on the root slot pointer per commit, lock-free. No segment locks to recover. |
| log claim | Queue: one write lock (`metadata.cq4t`) across appenders | **Lock-free:** CAS on a tail word, plus per-slot sequence words (bounded MPMC ring, §4.3). **Striped:** `stripe-count` S independent tails and rings, chosen by worker slot (§4.1), so writers in different processes don't fight over one cache line. S = 1 in the spike, and S is measured in Phase 3. |
| index | Queue: sparse index arrays over a rolling file | Not needed for recovery. The ring is fixed-size (default 1,024 × 4 KiB), and recovery scans all slots, about 2 MiB of CRC. Each slot is self-describing (seq, stripe, nonce, CRC), so the slot *is* the index. A non-authoritative per-stripe "last completed seq" hint word in the header (§4.1) is the index if T3 ever needs one. |
| retention and rollover | Queue: roll cycles, files kept | Ring wraparound overwrites slots older than one lap. The newest records and the root are all recovery needs. Retention of the *values* named by surviving records is the existing retention floor (§4.5). |
| durability | page cache by default, no per-write fsync (both) | **Explicit flush ordering per commit:** value blocks forced before the root CAS, then the record forced, then ack (§4.3). Page-cache-only is still available as `:durable? false` with `sync!`. |
| crash recovery | Map: exclusive `recoverPersistedTo()`, purges corrupt entries | Exclusive via the root's existing `ROOT_HOLD` word (no OS lock). Picks the max-epoch valid record, never truncates, and never installs anything older than the newest valid record (§4.4). |

The short version: take Chronicle's "mmap file plus header words" mechanics, replace the Queue's write lock with eve's CAS discipline, and add the explicit flush ordering that both Chronicle products leave out by design.

---

## 4. Spec

### 4.1 Files and on-disk layout

- New file `<base>.jlog`, beside `<base>.root`.
- For eve.db.ds this is `<dir>.jlog`, next to `<dir>.root`, `<dir>.rmap`, `<dir>.slab0`… (E:src/eve/atom.cljc:967-975; E:src/eve/deftype_proto/alloc.cljc:2120-2160).
- All words are u32 in the region's own word order, the order the `.root` words already use on every host. Table bytes are raw root bytes.

**Parameters:**
- N = slots per stripe, a power of two, default 1,024 (open option `:jlog-slots`).
- S = stripe count, a power of two, default 1 (`:jlog-stripes`, Phase 3).
- P = 4,096 = slot bytes. One record per 4 KiB page; on 16 KiB-page hosts, 4 records share a page (harmless, §4.3 step 9).

**Header, page 0 (bytes 0–4095):**

| off | field | notes |
|---:|---|---|
| 0 | magic `EVL1` 0x45564C31 | written **last** at creation; joiners wait for it, like `await-domain-root!` E:src/eve/atom.cljc:715-731 |
| 4 | version = 1 | |
| 8 | slot-bytes = 4096 | |
| 12 | N | |
| 16 | S | |
| 20 | nonce | random u32 per file creation; every record carries it |
| 24 | data-off | byte offset of stripe 0 slot 0 (4 KiB aligned) |
| 28 | recovered-epoch | informational |
| 32 | group-durable-seq | reserved for Phase 4 group commit; 0 means unused |
| 64 + 64·k | stripe k block (k < S, so S ≤ 63) | |
| +0 | tail_k | next seq to reserve, **CAS target** |
| +4 | hint_k | last completed seq, a non-authoritative hint |
| +8..63 | reserved 0 | each stripe gets its own 64 B cache line |

**Slot-state arrays**, at page 1 onward:
- `sstate[k][i]` at `4096 + 4·(k·N + i)`: the seq slot i is ready to accept (initially i).
- `sowner[k][i]` at `4096 + 4·S·N + 4·(k·N + i)`: 1 + the worker slot of the current reserver.
- `data-off` = round-up(4096 + 8·S·N, 4096).

**Data:** stripe k, slot i is at `data-off + (k·N + i)·4096`.

File size = data-off + S·N·4096. The default is 12,288 + 4 MiB. The creator preallocates (`posix_fallocate` where the host has it, else write zeros), so no per-commit metadata changes.

### 4.2 Record format (one 4096-byte slot)

| off | field |
|---:|---|
| 0 | magic `EVR2` 0x45565232 |
| 4 | seq (u32, per stripe) |
| 8 | stripe k |
| 12 | epoch label: the root epoch read **after** this writer's own epoch bump and before the table read |
| 16 | kind: 1 COMMIT, 2 ANCHOR (checkpoint or recovery) |
| 20 | nonce (= header nonce) |
| 24 | writer: 1 + worker slot (0 for recovery or creator) |
| 28 | table-len = 2048 (`MAX_ATOM_SLOTS × ATOM_SLOT_SIZE`, E:src/eve/deftype_proto/data.cljc:426-428) |
| 32..2079 | atom table bytes (`ATOM_TABLE_START`, 2048 B) |
| 2080 | crc32 (IEEE) over bytes [0, 2080) |
| 2084..4095 | zero |

A slot (k, i) is **valid** when all of these hold:
- magic matches;
- crc matches;
- nonce = header nonce;
- stripe = k;
- seq mod N = i;
- table-len = 2048.

**CRC:** a new portable cljc `eve.crc32`, a slicing-by-8 port of jolt's Chez one (E:adapters/journal-file/jolt/src/eve/journal_file.clj:84-176, commit 4f00ed0), with goldens against `java.util.zip.CRC32`. Hosts may override it through an optional adapter fn: JVM `CRC32`, jolt's Chez `eve-jf-crc32`, Node `zlib.crc32` where present.

### 4.3 Write protocol (J1, per commit)

Everything below runs inside the existing write bracket: `with-domain-write` → in-flight for lock-free hosts, or under the hold for lock-only hosts (E:src/eve/atom.cljc:327-339, :1759-1767). The lock-only bb seat's plain RMW on log words is therefore exclusive, exactly as for the root.

1. Build the new value (unchanged, E:src/eve/atom.cljc:1612-1631). Run `tally/hold-root!` on new-ptr (:1644).
2. **Pre-CAS force (fixes H2).**
   - `alloc/sync-touched!` over `@tally/*touches*` ∪ logged allocs ∪ `take-unforced!`. This is exactly the force-ordering form at :1659-1662, now taken by journal domains too.
   - Invariant: **no pointer becomes visible in the root before the blocks it reaches are durable.** So any snapshot of the live table that any process reads names only forced blocks.
   - Requires real `-sync-range!` on every host (§4.6; fixes H1).
3. CAS the slot pointer (:1663). On loss, take the existing retry path with no log activity.
4. Bump the epoch (:1666). Run `free-unreached!` and `drop-root!` on old (:1670, :1683). Their writes stay unforced and are noted in `:unforced`. On a crash they leak at worst; they are never a use-after-free (argument at :660-669, unchanged).
5. **Reserve** in stripe k = (worker slot) mod S:
   - `t ← load tail_k`, `i ← t & (N−1)`, `st ← load sstate[k][i]`.
   - If `st = t`: CAS tail_k t→t+1. On success, store `sowner[k][i] ← 1+worker` and own seq t. On failure, go back to the start of step 5.
   - If `st` is behind t (serial compare): the previous lap's writer of seq t−N is still in progress. Spin, yield, then sleep 1 ms. If `sowner[k][i]` names a worker whose heartbeat is stale (`heartbeat-stale?`, the domain's existing death rule, HEARTBEAT_TIMEOUT_MS 30,000, E:src/eve/deftype_proto/data.cljc:409), reclaim with CAS `sstate[k][i]` st→t, then go back to the start of step 5.
   - If `st` is ahead of t: the tail read was stale, so go back to the start of step 5.
6. **Label:** `e ← load ROOT_EPOCH` (E:src/eve/deftype_proto/data.cljc:379). Then `tbl ← -read-bytes ATOM_TABLE_START 2048`.
   - No seqlock is needed. The table is read after e, so every slot pointer in it is at least as new as at e, and every one was published after its blocks were forced (step 2).
   - Labelling with e, which may be older than the table, is the conservative direction for retention (§4.5).
7. Build the 4096-byte record in a reusable per-domain buffer and compute the CRC.
8. `-write-bytes!` the record at the slot offset.
9. `-sync-range!` (slot offset, 4096). **This is the commit point.** Concurrent writers' msyncs touch disjoint pages, so they overlap rather than serialise.
10. Store `sstate[k][i] ← t+N` (the slot is ready for the next lap). Store `hint_k ← t` as a plain store; it is only a hint. Neither needs a flush, because recovery never reads them.
11. Floor update: the FLUSH-ADVANCE rule, unchanged (E:src/eve/atom.cljc:809-837).
12. Return. `swap!` acknowledges.

**Why lapping is safe:** a slot can never be reused while its previous writer is in steps 5–9, because the step-5 `sstate` check is a bounded MPMC ring (Vyukov-style). A slow writer, such as a JVM GC pause, applies back-pressure instead of being overwritten. A dead one is reclaimed only by the domain's existing death rule.

**J10 / window batch** (E:src/eve/atom.cljc:842-865):
- No pre-CAS force; commits count toward the batch as now.
- At the boundary: **first** read the label and table (step 6), **then** run `sync-sio!` (whole domain), **then** steps 5 and 7–10.
- Reading the table before the sweep is what makes the sweep cover every pointer in the snapshot. It fixes H2's J10 shape.

**Checkpoint** (`sync!`, close, E:src/eve/atom.cljc:2179-2200):
- `sync-domain!`: whole domain plus root. H3 is fixed here: the whole-domain force must include the desc-flags and desc-pointer columns.
- Then append an ANCHOR record through steps 5–10.
- **No truncation, no rewrite.** `journal-anchor!` is deleted.

**Compaction:** none is needed. Ring wraparound reclaims space, and the root is the long-term state. `<base>.jlog` never grows.

### 4.4 Recovery (open of an existing domain)

Recovery replaces `journal-recover!` and runs at the same point, before the slot claim (E:src/eve/atom.cljc:989-994).

1. Map `<base>.jlog`.
   - If it is missing or has magic 0 while the root exists: go to **migration** (§4.7).
   - If it has the wrong magic or version: throw. Never overwrite.
2. Take the **recovery hold**. This is exclusive and uses no OS lock.
   - Lock-free host: `wait-hold-clear!`, then CAS `ROOT_HOLD` 0 → `HOLD_RECOVERING`. That is a new constant, `MAX_WORKERS+2`, beside `HOLD_JOINING` at E:src/eve/atom.cljc:95, and `hold-holder-stale?` :233-239 treats it like `HOLD_JOINING`, as stale after HEARTBEAT_TIMEOUT_MS.
   - Lock-only host: `-with-file-lock` on the hold word, then store the hold, as `with-exclusive-hold*` :275-291 does.
   - Joiners' `claim-slot-as-participant!` (:707-713) and all writers already back off on a set hold.
3. If `domain-live?` (:867-876): skip the install. Set floor ← the max valid label, or the root epoch. Release the hold and join normally.
4. Otherwise (not live), all under the hold:
   - **a. Repair H5:** store `ROOT_INFLIGHT ← 0`, since no live participant can be in flight.
   - **b. Scan** all S·N slots and keep the valid ones (§4.2).
   - **c. Pick** rec* = max by epoch label, using serial arithmetic relative to the label of the max-seq valid record. Break ties by higher seq, then lower stripe.
   - **d.** If rec* exists and (rec*.label ≠ root epoch, or rec*.table ≠ root table): write the table and epoch into the root, then `sync-region!` the root. This is today's W1/W2 rule (:908-923): the CRC-checked record beats the unchecked root table.
     - It never "picks file-last": no older valid record can win over a newer one.
     - Records are never erased.
   - **e. Rebuild the header.** For each stripe, tail_k ← (max valid seq in k) + 1, or 0. Set `sstate` so each slot is ready for the next seq ≡ i mod N that is ≥ tail_k. Clear `sowner`. Store `recovered-epoch`. msync the header pages.
   - **f.** Append an ANCHOR record of the (possibly installed) root through steps 5–10.
   - **g.** Floor ← rec*.label, or the root epoch.
   - **h.** Claim this process's worker slot with `mmap-claim-slot!` **inside the hold** (direct call, not `with-root-write*`, which would wait on our own hold), then release the hold. That closes the window in which a second opener could see "not live" after our install.
5. **Fresh domain** (`create?`, :2153): the creator writes a zeroed, preallocated `.jlog`, the header with nonce and `sstate[i] = i`, and the magic **last**, then msyncs. Joiners wait for the magic.

**Correctness sketch:**
- An acknowledged commit C with epoch E_c had its own record durable with label ≥ E_c. Its label was read after C's bump, and the table it holds was read after C's CAS.
- The max-label valid record R* has label ≥ E_c, and its table was read after the epoch reached that label. That is after C's CAS, so it holds C's pointer or a newer one.
- The only records that can be torn are those of writers still in steps 8–9 at the crash, and those writers had not acknowledged. So R* always covers every acknowledged commit.

### 4.5 Retention floor and the retire machinery

This is unchanged in mechanism: `:floor` and `:last-epoch` (E:src/eve/atom.cljc:974-988, :809-837), and its use in `flush-zero-queue!` (:699-703).

The bound stays sound. Every process's floor is at most the label of a record it completed, which is at most the label of R* at any crash. So blocks reachable from R* are never freed.

Mixed snapshots are labelled low (step 6), which only makes the floor more conservative. No log-specific retention is added. Slots overwritten by wraparound hold records older than R*, which recovery never needs.

### 4.6 Adapter primitive contract

These are the only things the jlog needs from a host. All of them are existing `eve.mem` protocol methods on the region the host already uses for `.root`.

| primitive | protocol | contract |
|---|---|---|
| open/map a file | `region/open-mmap-region path size opts` | MAP_SHARED; grow-only; never truncate |
| 32-bit CAS | `-cas-i32!` (E:src/eve/mem.cljc:60) | atomic across processes when `-atomic-rmw?`. Lock-only regions may be plain, because they run only under the hold. |
| load/store i32 | `-load-i32` / `-store-i32!` | seq-cst or acquire/release, as the root uses |
| bytes | `-read-bytes` / `-write-bytes!` | bulk copy |
| flush a range | `-sync-range!` (`ISyncableRegion`, E:src/eve/mem.cljc:124-144) | **Mandatory for a durable domain.** On return, the pages covering the range are on stable storage. Round to the host's real page size. Errors throw; never swallow them. |
| preallocate | new optional `region/preallocate! path bytes` | `posix_fallocate`; fallback is writing zeros |

Rule change in `eve.mem/sync-region!`: a **durable** domain whose region lacks `ISyncableRegion` must **fail at open** ("host cannot make durable commits; open with `:durable? false`"). It must never silently no-op (H1).

Per host:

| host | region adapter | CAS | msync status | work |
|---|---|---|---|---|
| JVM | jvm-varhandle | VarHandle | `MappedByteBuffer.force(idx,len)` (region.clj:460-482, 771-779) | none |
| jolt (Chez) | jolt-ffi | FFI atomics | **missing** | add `ISyncableRegion` to `JoltRegion` (region.clj:186) via `ffi/defcfn "msync"` (the file already binds `mmap` through ffi at :105), MS_SYNC, page-rounded |
| node / cljs / nbb | js `NodeMmapRegion` + `native/mmap_cas.cc` | addon `cas32` | addon `msync` (cc:743-759; region.cljs:306-314) | page size from `sysconf(_SC_PAGESIZE)` instead of the hard-coded 4096 (H4) |
| jank | jank region | C++ atomics | `msync_range` (region.jank:62-65, 198-200) | none |
| glj | glojure `GljRegion` | Go `sync/atomic` | **missing** | add `ISyncableRegion`: `unix.Msync`/`syscall.Syscall(SYS_MSYNC…)`, or `Fdatasync` on the region's fd (see below), in `impls/glojure/region` |
| bb | jvm-nio-lock: native `BbNativeRegion` / lock-only `BbMmapRegion` | native libc / plain-under-hold | native: libc msync (region.clj:509, 561, 665-667); lock-only: **missing** | lock-only: `.force` on its MappedByteBuffer segments, or fdatasync on its channel |
| lpy, clr, dart, letgo, vs | own regions | yes | `ISyncableRegion` present | none for the spike |
| beam | `CljeRegion` | NIF | **missing** | out of the spike; fails at open when durable |

**Where msync is missing,** this is an accepted substitute: `fdatasync(fd)` of the *same file*. On Linux that flushes every dirty page of the file, mmap-dirtied ones included, so it is a superset of the range. It costs more under contention but is correct.

**macOS:** msync/fsync do not flush the drive cache (`F_FULLFSYNC` does). See Q4.

### 4.7 Back-compat and migration

Legacy `<base>.journal` (EVJ1) migration runs once, under the recovery hold, on a **non-live** open:
1. Read the legacy file with a cljc legacy reader over a mapped region. This is pure byte parsing and replaces the 11 per-host scanners.
2. Resync on magic after a framing failure, rather than stopping.
3. Pick the max-epoch valid record (not file-last). If its epoch is ≥ the root epoch, install it as in §4.4 step 4d. If it is lower, keep the root: a legacy record is never allowed to roll the root back, which is exactly the shootout loss.
4. Write the new `.jlog` with an ANCHOR record.
5. Rename `.journal` → `.journal.migrated`. Do not delete it (Q2).

Live open of a legacy domain by a new binary: refuse with a clear error while any old-binary process is live.

**Mixed binaries:** old binaries ignore `.jlog` and would keep appending to `.journal`. The proposed guard is to bump `ROOT_MAGIC_V2` → V3 at migration. Old binaries already refuse an unknown magic (`await-domain-root!`, E:src/eve/atom.cljc:715-731). This needs John (Q1).

### 4.8 eve.db.ds interplay

- A persistent conn is one eve atom in the domain `{:persistent dir}` (DS:src/eve/db/ds/conn.cljc:154-178), closed through `eve/close!` (:219-233). No ds code changes are needed: `d/transact!` = `swap!`, so acknowledgement = durable as above.
- New sibling file `<dir>.jlog`, plus `<dir>.journal.migrated` after migration.
- `rm -rf "$dir" "$dir".*` cleanups already cover it (probe/jseam/dsabe.sh:9, results/dsrt/run.sh).
- DS:harness/shootout.sh:20 lists siblings explicitly (`"$D".root "$D".slab* "$D".rmap "$D".journal`) and must add `"$D".jlog "$D".journal.migrated`. That is the only ds edit, and it goes as a separate ds PR.
- Disk: about 4 MiB preallocated per store. ds test suites that create many stores must respect the fleet disk rule: half of free disk, and 50k inodes kept free.

### 4.9 Failure modes

| # | event | outcome |
|---|---|---|
| F1 | crash before step 3 CAS | nothing published; forced blocks leak at most |
| F2 | crash after CAS, before step 8 | commit visible, not acknowledged. Process crash: the root (page cache) keeps it. Power loss: R* is older. Allowed. |
| F3 | torn record (crash during 8–9) | CRC fails, slot ignored, R* = the next newest valid. Not acknowledged, so allowed. |
| F4 | writer dies holding a reservation (between 5 and 10) | the slot stays at `sstate = t`. A next-lap reserver waits, then reclaims after the owner's heartbeat goes stale (30 s). Recovery rebuilds `sstate`. |
| F5 | slow writer (GC pause) laps | impossible by construction: the ring back-pressures |
| F6 | stale bytes from an older lap or file | seq mod N, stripe and nonce checks reject misplaced records; a CRC-valid older-lap record is just an older candidate |
| F7 | torn root table (8 KiB root spans a page boundary at 8192; the table is 6272..8320) | R* differs, so it is installed (W2 rule) |
| F8 | root newer than every valid record (unacknowledged commits flushed by writeback) | installs R* (rolls back unacknowledged only). Same as today's W1. Acknowledged commits are always ≤ R*. |
| F9 | two openers recover at once | the recovery hold serialises them; the second sees live (slot claimed inside the hold) |
| F10 | recoverer dies mid-recovery | the hold goes stale after 30 s and is cleared by `wait-hold-clear!`. Recovery is idempotent (install the same R*, rebuild the header). |
| F11 | msync or fsync error (EIO, ENOSPC) | throw from `swap!` **after** publish. The commit is visible but not acknowledged. Mark the domain `:durability-failed` so later commits throw, and surface the error. Never retry silently (fsyncgate). |
| F12 | host region without sync on a durable domain | open fails (§4.6) |
| F13 | epoch u32 wrap | labels are compared by serial arithmetic. Valid while live labels span < 2³¹ commits; a ring of 1,024 records cannot exceed that. |
| F14 | lock-only (bb) writer | its log ops run under its exclusive hold, so plain RMW is safe. H5 (leaked in-flight count on a live domain) still blocks it, as today. |
| F15 | a pre-jlog binary on a migrated store | refused by the magic bump (Q1) |

---

## 5. Spike plan

**Spike, time-box 1 working day.** Stop at the box limit and report regardless.

Build `src/eve/jlog.cljc`, standalone and not yet wired into `eve.atom`:
- create, open, reserve, append (steps 5–10 on a caller-given table), scan and recover-pick;
- S = 1, N configurable;
- portable `eve.crc32`;
- jolt `ISyncableRegion` (FFI msync).

Driver `test/eve/jlog_spike.cljc`, built for JVM, jolt and node:
- **(a)** k processes (JVM, jolt and node together, 2 of each) append records with a writer-tagged counter as the "table" for 10 s on one `.jlog`, N = 64 to force many laps. Each prints its acknowledged seqs.
- **(b)** kill -9 one writer at a random time and the rest at the end.
- **(c)** scan from each runtime.

**Exit criteria:**
1. All three runtimes compute the same R* and the same valid-record set from the same file.
2. Every acknowledged (writer, counter) is present in, or covered by, a valid record with label ≥ it.
3. No reservation is ever handed out twice; the check is that (stripe, seq) is unique across the acknowledgement logs.
4. Laps occur (more than 10 × N appends) and back-pressure is observed with no overwrite of an in-progress slot.
5. The per-append median (JVM) is ≤ the pwrite+fdatasync baseline on the same box ± 10%.
6. The record bytes are identical across hosts (golden test).

If 2 or 3 fail twice the same way: stop and report.

**Phases:**

| phase | content | exit criteria |
|---|---|---|
| P1 wire-in | `eve.atom` uses jlog behind `:log :jlog` (default still legacy). Pre-CAS force for journal domains. Recovery hold plus §4.4. Checkpoint without truncation. H3 fix. | Eve JVM/cljs/jolt gates at base. Shootout legs jvm×2, jolt×2, jvm+jolt, jvm+cljs, jolt+cljs, jvm+jolt+cljs all **PASS on data** with `:log :jlog`. Kill -9 suite (§6.3) green on those. |
| P2 hosts | node page size (H4). glj and bb lock-only sync. Fail-at-open for unsyncable durable hosts. Jank and nbb legs. Legacy migration (§4.7). | Shootout PASS on every pair of {jvm, jolt, cljs, jank, bb, nbb, glj-if-it-opens} plus all together. Migration test: legacy journals written by JVM, node and jolt (including an interleaved-corrupt one) migrate to the max-epoch state. |
| P3 default and cleanup | `:jlog` default. Magic bump (pending Q1). Delete legacy writers. Stripes S ∈ {1, 4, 8} measured. Docs. | All gates (§6.5) at base. Perf: T1 and T2 with abx6/r5seq 5 interleaved pairs, every host disclosed (jolt and glj slowdowns labelled as real-force cost). S default chosen by measurement. |
| P4 (optional) | group commit: a writer whose seq ≤ `group-durable-seq` skips its msync. Preallocation tuning. | ≥ 20% tx/s at k = 4 with no data PROBLEM |

---

## 6. Test plan

### 6.1 Adapter conformance

New `test/eve/jlog_conformance.cljc`, run on JVM, jolt, node, jank, glj and bb:
- CAS linearisability: 2 processes × 1e5 `cas-i32!` increments, final count exact.
- `-write-bytes!` then `-read-bytes` round trip at unaligned offsets.
- `-sync-range!` exists, accepts unaligned ranges, and returns. On Linux, check with `strace -f -e msync,fdatasync` that one call happens per append; this is the method from the durability-spike report (E:test/eve/durability_spike_test.clj:1-8).
- `eve.crc32` goldens against `java.util.zip.CRC32`.
- Byte-identical record encoding across hosts.

### 6.2 Cross-runtime contention shootout

Use the ds-cx harness, copied in from the untracked files:

```
EVE_DIR=<tree> RUNTIMES="..." PHASES="solo together" DUR_MS=30000 scripts/contention_shootout.sh
```

- Legs: every pair from {jvm, jolt, cljs, jank, bb, nbb, glj-if-loadable}, plus all together.
- Repeat each 3×.
- Plus one control run with `WORKLOAD_FLAGS=nodurable`.

**Pass** is all of the following, checked by `contention_shootout_check.clj` after every writer exits **and** after a fresh reopen from each runtime:
- entities = transactions;
- datoms = 2 + 5 × transactions;
- every writer tag's DB seqs are exactly 0..n−1;
- shared `:n` = total swaps;
- per-writer `:cnt` and `:by` exact;
- DB cas counter = cas increments that landed;
- every mailbox's final seq = writes;
- all readers agree on the final DB;
- saw-atom and saw-scan show every writer saw the others.

**Allowed problems:** only ones that also appear in the `nodurable` control. Today those are the cljs `:advanced` d/q-nil reader line (BL:859) and the `:db/cas` op throws under investigation (BL:864). Any data-count PROBLEM fails the leg.

**Perf:** report the solo → together ns/op tables per runtime. T2 thresholds apply.

### 6.3 kill -9 crash tests

Per runtime and per pair:
- Writers log each acknowledged (tag, seq) to stdout as soon as `swap!` or `transact!` returns.
- The harness sends SIGKILL to (a) one random writer at U(0.5, 5) s, and (b) all writers at once.
- Then reopen from each runtime and check:
  - every acknowledged item is present exactly once;
  - unacknowledged items are present at most once;
  - counters are between acknowledged and attempted;
  - recovery never throws.
- 50 iterations per configuration.

Also: kill during recovery (F10), then reopen. Kill a writer between reserve and complete, using a test hook that sleeps at step 8, then confirm the next lap reclaims after the heartbeat timeout (F4).

Note: kill -9 does **not** test power loss, because the page cache survives (as E:test/eve/durability_spike_test.clj:1-8 already states).

### 6.4 Power loss and torn-write fuzzing

- **Image method:** extend `durability_crash_recorder`/`reader` (E:test/eve/durability_crash_recorder.clj, durability_crash_reader.clj). After each acknowledged commit, snapshot the domain files. Then derive images:
  - (i) the newest 1–3 log slots torn at every 512 B boundary, or zeroed, or with bit flips;
  - (ii) the root table page reverted or torn (F7);
  - (iii) wrong nonce or stripe, and seq-mismatched copies (F6);
  - (iv) header `sstate`/`tail` garbage (recovery must rebuild it).
- **Check:** recovery picks R* = the max valid label, every acknowledged commit up to the image point is present, and nothing throws.
- Port W1–W4 from E:test/eve/journal_recovery_test.clj to the jlog.
- **Optional, if the box allows device-mapper:** `dm-log-writes` replay for true unflushed-write loss.

### 6.5 Gates

Every PR head must match base on all of these, with failing names identical to the base logs:

| gate | base |
|---|---|
| jolt jtest-all | 197/3208/14/48, unhandled 0, rrb clean 3/3 |
| Eve JVM `clojure -J-Xmx10g -M:jvm-test` | 782/29499/28-29/115 (election-by-lease is a timing flake) |
| cljs all | 504/31219/21/116 |
| dsgate JVM U3 | 42/248/0/0 |
| dsgate bset | 5/816/0/0 |
| dsgate jolt | 47/1044/1/4 |

`clj-kondo` must be no worse than base.

---

## 7. Rules for the implementing agent

- **Branches and PRs:**
  - Work on a private branch, for example `lane-m/eve-jlog`.
  - Open a PR to **SeniorCareMarket/eve** for John's review. **Never merge or fast-forward main.**
  - The one eve.db.ds edit (§4.8) goes as its own PR to johnmn3/eve.db.ds.
  - Verify every push with `git ls-remote`.
- **Lint:** clj-replace, then clj-paren-repair, then the fleet cljfmt 0.16.6 fix on **changed forms only** (a whole-file run rewrites untouched forms), then clj-kondo from `/workspace/tools/clj-kondo`, which must be no worse than before. Never edit lint configs.
- **Memory:** no fns in Eve memory; use the deftype registry.
- **Benchmarks:** never change what a benchmark measures. disk-swap stays disk-swap. A jolt or glj slowdown from real forcing is reported, not tuned away by changing policy.
- **Git:** never force-push or tag.
- **Stop rule:** two failures the same way means stop and report.
- **Times:** all times in ET.
- **Never write to** `claude/brave-davinci-qpb4s4` or `lane-m/davinci-rebase-pair1-mac`.
- **Worktrees:** never edit or check out in the `eve-par`/`eve-pa`/`eve-pc`/`eve-p2` worktrees; other workers use them. Make your own worktree.
- **Disk:** jobs check `df -h` and `df -i`, use at most half the free disk, and keep 50k inodes free. Clean store siblings with `rm -rf "$p" "$p".*`.
- **Deletion:** no deleting user data without a yes. Legacy `.journal` files are renamed, not deleted (Q2).
- **Durability policy:** no journal-batching default changes; batching policy waits on John's durability decision.
- **Disclosure:** don't post jolt bug repros publicly. Eve is private commercial work.

---

## 8. Open questions for John

- **Q1 Format guard.** Bump the root magic to V3 when a store is migrated, so pre-jlog binaries refuse it instead of silently writing a `.journal` nobody reads? This makes migrated stores unreadable by old builds.
- **Q2 Legacy files.** After migration, keep `<base>.journal.migrated` forever, or may the code delete it once the `.jlog` anchor is durable?
- **Q3 Publishing.** jolt's (and glj's) current disk-swap numbers exclude slab forcing (H1). jolt's 0.7× JVM disk-swap cell is not like-for-like. Hold the jolt disk numbers out of the jolt-community table until P2 lands, or publish them with that caveat?
- **Q4 macOS.** Should durable commits on macOS use `F_FULLFSYNC` (true drive-cache flush, much slower), or keep today's fsync/msync semantics?
