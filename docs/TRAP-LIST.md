# Trap list — vendored baseline (DS-IN-EVE-PLAN v2 §6)

Derived mechanically by unit U0 from the vendored upstream baseline
(`src/eve/db/ds/**`, `src/data_readers.clj`; DataScript 1.8.1,
commit 34915cf673c02cbc64779fe56e0288d70fea63f8 — see
`src/UPSTREAM-SRC.txt`). Frozen: units apply rows, they do not
invent them (v2 §6). A row is closed when the owning unit's slice
goes green with the replacement in place; the diff gate
(`scripts/diff_upstream.sh`) fails any hunk in a verbatim-class
file whose line is not a row below.

## Derivation (the exact greps)

Scope: every `.clj`/`.cljc`/`.cljs` file under `src/`. One row per
hit. Patterns, in order:

1. `identical\?`
2. `\((not)?= ` on a line also matching
   `datom|set/|eavt|aevt|avet|vaet|\bdb\b` (case-insensitive) —
   the mechanical proxy for "`=` on db/set/datom"
3. `unsynchronized-mutable|volatile-mutable`
4. `_hash` in `db.cljc` (the hash-cache field and its `set!` sites)
5. `reset!` (not preceded by a name character)
6. `add-watch` (not preceded by a name character)
7. `compare-and-set!` (not preceded by a name character)
8. `\.-(e|a|v|tx)` not followed by a name character or `-`
9. `set/(slice|rslice)` in `db.cljc` — the index reads; the literal
   pattern `lazy-seq` has 0 hits in the baseline, and these call
   sites are where an unrealized seq over the set leaves an index
   read

Zero-hit patterns in this baseline: `add-watch` (0),
`volatile-mutable` (0 — the cljs arm of the same Datom field uses
`^:mutable`, covered by rows in §3/§4 via the clj arm's line),
`lazy-seq` literal (0, see §9).

Total rows: 195


## identical? — 11 rows

Replacement: Block identity is `eve/same?` (EVE-FIX F3), never `identical?` on a DB, a set node or a stored Datom (v2 §2). Where the test is really a no-change signal, restructure to the nil-return convention (v2 §5: `conj`/`disj` return nil when nothing changed).

| File | Line | Hit |
|---|---|---|
| `src/eve/db/ds/built_ins.cljc` | 89 | `'complement complement, 'identical? identical?,` |
| `src/eve/db/ds/db.cljc` | 381 | `(defn class-identical?` |
| `src/eve/db/ds/db.cljc` | 382 | `#?(:clj  {:inline (fn [x y] `(identical? (class ~x) (class ~y)))})` |
| `src/eve/db/ds/db.cljc` | 384 | `#?(:clj  (identical? (class x) (class y))` |
| `src/eve/db/ds/db.cljc` | 385 | `:cljs (identical? (type x) (type y))))` |
| `src/eve/db/ds/db.cljc` | 456 | `(not (class-identical? x y)) (class-compare x y)` |
| `src/eve/db/ds/db.cljc` | 460 | `(if (not (class-identical? x y))` |
| `src/eve/db/ds/db.cljc` | 1461 | `(or (identical? :db/current-tx e)` |
| `src/eve/db/ds/impl/entity.cljc` | 171 | `(identical? (.-db this) (.-db ^Entity that)) ; `=` and `hash` on db is expensive` |
| `src/eve/db/ds/impl/entity.cljc` | 177 | `;; A hash compatible with `identical?`. Consistent with `=`.` |
| `src/eve/db/ds/query.cljc` | 508 | `:cljs (identical? f vector))` |

## = on db/set/datom — 56 rows

Replacement: Where identity is meant: `eve/same?`. Where value equality is meant: compare delivered host data (host Datoms), never stored Eve values (v2 §2, §4: public returns are host data only).

| File | Line | Hit |
|---|---|---|
| `src/eve/db/ds/core.cljc` | 604 | `(if (= part :db.part/tx)` |
| `src/eve/db/ds/core.cljc` | 608 | `(if (= part :db.part/tx)` |
| `src/eve/db/ds/db.cljc` | 645 | `(when (= index :avet)` |
| `src/eve/db/ds/db.cljc` | 732 | `(->Eduction (filter (fn [^Datom d] (= tx (datom-tx d))))))` |
| `src/eve/db/ds/db.cljc` | 736 | `(= tx (datom-tx d)))))))` |
| `src/eve/db/ds/db.cljc` | 740 | `(->Eduction (filter (fn [^Datom d] (= tx (datom-tx d))))))` |
| `src/eve/db/ds/db.cljc` | 744 | `(->Eduction (filter (fn [^Datom d] (= tx (datom-tx d))))))` |
| `src/eve/db/ds/db.cljc` | 747 | `(= tx (datom-tx d))))))))` |
| `src/eve/db/ds/db.cljc` | 753 | `(->Eduction (filter (fn [^Datom d] (= tx (datom-tx d))))))` |
| `src/eve/db/ds/db.cljc` | 756 | `(= tx (datom-tx d)))) eavt)                 ;; _ _ v tx` |
| `src/eve/db/ds/db.cljc` | 758 | `(filter (fn [^Datom d] (= tx (datom-tx d))) eavt)                    ;; _ _ _ tx` |
| `src/eve/db/ds/db.cljc` | 880 | `(= :db/tupleAttrs k)                  [:db.type/tuple :db/index :db/tupleAttrs]` |
| `src/eve/db/ds/db.cljc` | 881 | `(= :db/tupleTypes k)                  [:db.type/tuple :db/tupleTypes]` |
| `src/eve/db/ds/db.cljc` | 882 | `(= :db/tupleType k)                   [:db.type/tuple :db/tupleType]` |
| `src/eve/db/ds/db.cljc` | 883 | `(= :db.unique/identity v)             [:db/unique :db.unique/identity :db/index]` |
| `src/eve/db/ds/db.cljc` | 884 | `(= :db.unique/value v)                [:db/unique :db.unique/value :db/index]` |
| `src/eve/db/ds/db.cljc` | 885 | `(= :db.cardinality/many v)            [:db.cardinality/many]` |
| `src/eve/db/ds/db.cljc` | 886 | `(= :db.type/ref v)                    [:db.type/ref :db/index]` |
| `src/eve/db/ds/db.cljc` | 887 | `(and (= :db/isComponent k) (true? v)) [:db/isComponent]` |
| `src/eve/db/ds/db.cljc` | 888 | `(and (= :db/index k) (true? v))       [:db/index]` |
| `src/eve/db/ds/db.cljc` | 945 | `(when (and comp? (not= (:db/valueType kv) :db.type/ref))` |
| `src/eve/db/ds/db.cljc` | 958 | `(= :db.type/tuple (:db/valueType kv))` |
| `src/eve/db/ds/db.cljc` | 976 | `(when (= :db.cardinality/many (:db/cardinality kv))` |
| `src/eve/db/ds/db.cljc` | 991 | `(when (= :db.cardinality/many (:db/cardinality (get schema attr)))` |
| `src/eve/db/ds/db.cljc` | 1130 | `(= (-schema db) (-schema other))` |
| `src/eve/db/ds/db.cljc` | 1291 | `(:db/tupleTypes schema-a) (= :db.type/ref (nth (:db/tupleTypes schema-a) idx nil))` |
| `src/eve/db/ds/db.cljc` | 1292 | `:else                     (= :db.type/ref (:db/tupleType schema-a))))` |
| `src/eve/db/ds/db.cljc` | 1358 | `(= eid (-> (-seek-datoms db :eavt eid nil nil nil) first :e)))` |
| `src/eve/db/ds/db.cljc` | 1430 | `(= :db/add op)` |
| `src/eve/db/ds/db.cljc` | 1469 | `(or (= e :db/current-tx)` |
| `src/eve/db/ds/db.cljc` | 1470 | `(= e ":db/current-tx") ;; for eve.db.ds.js interop` |
| `src/eve/db/ds/db.cljc` | 1471 | `(= e "datomic.tx")` |
| `src/eve/db/ds/db.cljc` | 1472 | `(= e "eve.db.ds.tx"))))` |
| `src/eve/db/ds/db.cljc` | 1733 | `:when  (not= a :db/id)` |
| `src/eve/db/ds/db.cljc` | 1764 | `(= (.-v old-datom) v)` |
| `src/eve/db/ds/db.cljc` | 1930 | `(= op :db.fn/call)` |
| `src/eve/db/ds/db.cljc` | 1947 | `(not= op :db/add))` |
| `src/eve/db/ds/db.cljc` | 1951 | `(or (= op :db.fn/cas)` |
| `src/eve/db/ds/db.cljc` | 1952 | `(= op :db/cas))` |
| `src/eve/db/ds/db.cljc` | 1967 | `(if (some (fn [^Datom d] (= (.-v d) ov)) datoms)` |
| `src/eve/db/ds/db.cljc` | 1994 | `(or (= op :db/add) (= op :db/retract))` |
| `src/eve/db/ds/db.cljc` | 2035 | `(= tuple-value db-value)))` |
| `src/eve/db/ds/db.cljc` | 2041 | `(= op :db/add)` |
| `src/eve/db/ds/db.cljc` | 2044 | `(and (= op :db/retract) (some? v))` |
| `src/eve/db/ds/db.cljc` | 2055 | `(or (= op :db.fn/retractAttribute)` |
| `src/eve/db/ds/db.cljc` | 2056 | `(= op :db/retract))` |
| `src/eve/db/ds/db.cljc` | 2064 | `(or (= op :db.fn/retractEntity)` |
| `src/eve/db/ds/db.cljc` | 2065 | `(= op :db/retractEntity))` |
| `src/eve/db/ds/impl/entity.cljc` | 76 | `(if (= attr ":db/id")` |
| `src/eve/db/ds/impl/entity.cljc` | 183 | `(if (= attr :db/id)` |
| `src/eve/db/ds/js.cljs` | 40 | `(= (first e) ":db.fn/call")` |
| `src/eve/db/ds/pull_api.cljc` | 65 | `(or (nil? datom) (not= (.-a datom) (.-name attr)))` |
| `src/eve/db/ds/pull_api.cljc` | 72 | `(if (or (nil? datom) (not= (.-a datom) (.-name attr)))` |
| `src/eve/db/ds/pull_api.cljc` | 97 | `(or (nil? datom) (not= (.-a datom) (.-name attr)))` |
| `src/eve/db/ds/pull_api.cljc` | 104 | `(if (or (nil? datom) (not= (.-a datom) (.-name attr)))` |
| `src/eve/db/ds/pull_api.cljc` | 139 | `(and (some? attr) (= :db/id (.-name attr)))` |

## unsynchronized-mutable / volatile-mutable — 1 rows

Replacement: Removed. `eve/deftype` fields are immutable; mutable hash caches are deleted by the type swap (v2 §3). A cache the algorithm still needs lives host-side, keyed outside the type (v1 R6).

| File | Line | Hit |
|---|---|---|
| `src/eve/db/ds/db.cljc` | 184 | `(deftype Datom #?(:clj [^int e a v ^int tx ^:unsynchronized-mutable ^int idx ^:unsynchronized-mutable ^int ...` |

## hash cache (_hash field and its set! sites) — 8 rows

Replacement: Removed with the mutable field: the Datom hash is recomputed from fields, or memoized host-side — never stored in the Eve value (v2 §3: mutable hash caches removed).

| File | Line | Hit |
|---|---|---|
| `src/eve/db/ds/db.cljc` | 184 | `(deftype Datom #?(:clj [^int e a v ^int tx ^:unsynchronized-mutable ^int idx ^:unsynchronized-mutable ^int ...` |
| `src/eve/db/ds/db.cljc` | 185 | `:cljs [^number e a v ^number tx ^:mutable ^number idx ^:mutable ^number _hash])` |
| `src/eve/db/ds/db.cljc` | 194 | `(-hash [d] (if (zero? _hash)` |
| `src/eve/db/ds/db.cljc` | 195 | `(set! _hash (hash-datom d))` |
| `src/eve/db/ds/db.cljc` | 196 | `_hash))` |
| `src/eve/db/ds/db.cljc` | 222 | `(if (zero? _hash)` |
| `src/eve/db/ds/db.cljc` | 224 | `(set! _hash h)` |
| `src/eve/db/ds/db.cljc` | 226 | `_hash))` |

## reset! — 5 rows

Replacement: Never `reset!` the conn's Eve atom (v2 §2): state changes go through the one `swap!` in `transact!` (v2 §4). If the target is a host atom (cache/registry), the row is N/A — the owning unit marks it so, naming the atom's owner.

| File | Line | Hit |
|---|---|---|
| `src/eve/db/ds/conn.cljc` | 143 | `(reset! conn db))` |
| `src/eve/db/ds/core.cljc` | 658 | `(js/setTimeout #(do (reset! res (f)) (reset! realized true)) 0)` |
| `src/eve/db/ds/db.cljc` | 1115 | `(reset! (.-hash db) (combine-hashes (hash (.-schema db))` |
| `src/eve/db/ds/db.cljc` | 1124 | `(reset! (.-hash db) (combine-hashes (hash (-schema db))` |
| `src/eve/db/ds/js.cljs` | 142 | `(reset! conn db)` |

## add-watch — 0 rows

Replacement: Never `add-watch` the conn's Eve atom (v2 §2): listeners live in the conn's host atom registry and fire after the swap (v2 §4).

(no hits in the baseline)

## compare-and-set! — 1 rows

Replacement: Never `compare-and-set!` the conn's Eve atom (v2 §2): installation goes through `swap!` and the `:kept` handle discipline (v2 §4).

| File | Line | Hit |
|---|---|---|
| `src/eve/db/ds/conn.cljc` | 15 | `(compare-and-set!` |

## field reads .-e / .-a / .-v / .-tx — 93 rows

Replacement: Inside db.cljc's own Datom definition these become the eve/deftype's own field access (U2 type swap). Everywhere else: never read a field off a stored Eve value — reads happen inside `$` through the type's accessors, and values that leave are host Datoms whose upstream accessor fns (`e`/`a`/`v`/`tx`) apply (v2 §2, §4).

| File | Line | Hit |
|---|---|---|
| `src/eve/db/ds/db.cljc` | 218 | `opts [(.-e d) (.-a d) (.-v d) (datom-tx d) (datom-added d)]))]` |
| `src/eve/db/ds/db.cljc` | 264 | `(-> (hash (.-e d))` |
| `src/eve/db/ds/db.cljc` | 265 | `(combine-hashes (hash (.-a d)))` |
| `src/eve/db/ds/db.cljc` | 266 | `(combine-hashes (hash (.-v d)))))` |
| `src/eve/db/ds/db.cljc` | 269 | `(and (== (.-e d) (.-e o))` |
| `src/eve/db/ds/db.cljc` | 270 | `(= (.-a d) (.-a o))` |
| `src/eve/db/ds/db.cljc` | 271 | `(= (.-v d) (.-v o))))` |
| `src/eve/db/ds/db.cljc` | 274 | `(list (.-e d) (.-a d) (.-v d) (datom-tx d) (datom-added d)))` |
| `src/eve/db/ds/db.cljc` | 282 | `:e     (.-e d)` |
| `src/eve/db/ds/db.cljc` | 283 | `:a     (.-a d)` |
| `src/eve/db/ds/db.cljc` | 284 | `:v     (.-v d)` |
| `src/eve/db/ds/db.cljc` | 291 | `"e"     (.-e d)` |
| `src/eve/db/ds/db.cljc` | 292 | `"a"     (.-a d)` |
| `src/eve/db/ds/db.cljc` | 293 | `"v"     (.-v d)` |
| `src/eve/db/ds/db.cljc` | 304 | `0 (.-e d)` |
| `src/eve/db/ds/db.cljc` | 305 | `1 (.-a d)` |
| `src/eve/db/ds/db.cljc` | 306 | `2 (.-v d)` |
| `src/eve/db/ds/db.cljc` | 313 | `0 (.-e d)` |
| `src/eve/db/ds/db.cljc` | 314 | `1 (.-a d)` |
| `src/eve/db/ds/db.cljc` | 315 | `2 (.-v d)` |
| `src/eve/db/ds/db.cljc` | 322 | `:e     (datom v       (.-a d) (.-v d) (datom-tx d) (datom-added d))` |
| `src/eve/db/ds/db.cljc` | 323 | `:a     (datom (.-e d) v       (.-v d) (datom-tx d) (datom-added d))` |
| `src/eve/db/ds/db.cljc` | 324 | `:v     (datom (.-e d) (.-a d) v       (datom-tx d) (datom-added d))` |
| `src/eve/db/ds/db.cljc` | 325 | `:tx    (datom (.-e d) (.-a d) (.-v d) v            (datom-added d))` |
| `src/eve/db/ds/db.cljc` | 326 | `:added (datom (.-e d) (.-a d) (.-v d) (datom-tx d) v)` |
| `src/eve/db/ds/db.cljc` | 339 | `(pr [(.-e d) (.-a d) (.-v d) (datom-tx d) (datom-added d)]))))` |
| `src/eve/db/ds/db.cljc` | 502 | `(int-compare (.-e d1) (.-e d2))` |
| `src/eve/db/ds/db.cljc` | 503 | `(cmp (.-a d1) (.-a d2))` |
| `src/eve/db/ds/db.cljc` | 504 | `(value-cmp (.-v d1) (.-v d2))` |
| `src/eve/db/ds/db.cljc` | 509 | `(cmp (.-a d1) (.-a d2))` |
| `src/eve/db/ds/db.cljc` | 510 | `(int-compare (.-e d1) (.-e d2))` |
| `src/eve/db/ds/db.cljc` | 511 | `(value-cmp (.-v d1) (.-v d2))` |
| `src/eve/db/ds/db.cljc` | 516 | `(cmp (.-a d1) (.-a d2))` |
| `src/eve/db/ds/db.cljc` | 517 | `(value-cmp (.-v d1) (.-v d2))` |
| `src/eve/db/ds/db.cljc` | 518 | `(int-compare (.-e d1) (.-e d2))` |
| `src/eve/db/ds/db.cljc` | 539 | `(int-compare (.-e d1) (.-e d2))` |
| `src/eve/db/ds/db.cljc` | 540 | `(cmp-attr-quick (.-a d1) (.-a d2))` |
| `src/eve/db/ds/db.cljc` | 541 | `(value-compare (.-v d1) (.-v d2))))` |
| `src/eve/db/ds/db.cljc` | 545 | `(int-compare (.-e d1) (.-e d2))` |
| `src/eve/db/ds/db.cljc` | 546 | `(cmp-attr-quick (.-a d1) (.-a d2))` |
| `src/eve/db/ds/db.cljc` | 547 | `(value-compare (.-v d1) (.-v d2))` |
| `src/eve/db/ds/db.cljc` | 552 | `(cmp-attr-quick (.-a d1) (.-a d2))` |
| `src/eve/db/ds/db.cljc` | 553 | `(int-compare (.-e d1) (.-e d2))` |
| `src/eve/db/ds/db.cljc` | 554 | `(value-compare (.-v d1) (.-v d2))` |
| `src/eve/db/ds/db.cljc` | 559 | `(cmp-attr-quick (.-a d1) (.-a d2))` |
| `src/eve/db/ds/db.cljc` | 560 | `(value-compare (.-v d1) (.-v d2))` |
| `src/eve/db/ds/db.cljc` | 561 | `(int-compare (.-e d1) (.-e d2))` |
| `src/eve/db/ds/db.cljc` | 735 | `(->Eduction (filter (fn [^Datom d] (and (pred (.-v d))` |
| `src/eve/db/ds/db.cljc` | 738 | `(->Eduction (filter (fn [^Datom d] (pred (.-v d))))))` |
| `src/eve/db/ds/db.cljc` | 746 | `(->Eduction (filter (fn [^Datom d] (and (pred (.-v d))` |
| `src/eve/db/ds/db.cljc` | 751 | `(->Eduction (filter (fn [^Datom d] (pred (.-v d)))))))` |
| `src/eve/db/ds/db.cljc` | 755 | `(filter (fn [^Datom d] (and (pred (.-v d))` |
| `src/eve/db/ds/db.cljc` | 757 | `(filter (fn [^Datom d] (pred (.-v d))) eavt)                         ;; _ _ v` |
| `src/eve/db/ds/db.cljc` | 1063 | `avet-datoms (filter (fn [^Datom d] (contains? indexed (.-a d))) datoms)` |
| `src/eve/db/ds/db.cljc` | 1141 | `(pr-sequential-writer w pr-writer "[" " " "]" opts [(.-e d) (.-a d) (.-v d) (datom-tx d)]))` |
| `src/eve/db/ds/db.cljc` | 1153 | `(apply pr (map (fn [^Datom d] [(.-e d) (.-a d) (.-v d) (datom-tx d)]) (-datoms db :eavt nil nil nil nil))))` |
| `src/eve/db/ds/db.cljc` | 1441 | `(is-attr? db (.-a datom) :db/unique))` |
| `src/eve/db/ds/db.cljc` | 1442 | `(when-some [found (not-empty (-datoms db :avet (.-a datom) (.-v datom) nil nil))]` |
| `src/eve/db/ds/db.cljc` | 1445 | `:attribute (.-a datom)` |
| `src/eve/db/ds/db.cljc` | 1556 | `(let [indexing? (indexing? db (.-a datom))]` |
| `src/eve/db/ds/db.cljc` | 1562 | `true      (advance-max-eid (.-e datom))` |
| `src/eve/db/ds/db.cljc` | 1564 | `(if-some [removing (fsearch db [(.-e datom) (.-a datom) (.-v datom)])]` |
| `src/eve/db/ds/db.cljc` | 1764 | `(= (.-v old-datom) v)` |
| `src/eve/db/ds/db.cljc` | 1769 | `(transact-report (datom e a (.-v old-datom) tx false))` |
| `src/eve/db/ds/db.cljc` | 1774 | `(transact-report report (datom (.-e d) (.-a d) (.-v d) tx false))))` |
| `src/eve/db/ds/db.cljc` | 1778 | `(filter (fn [^Datom d] (component? db (.-a d))))` |
| `src/eve/db/ds/db.cljc` | 1779 | `(map (fn [^Datom d] [:db.fn/retractEntity (.-v d)]))) datoms))` |
| `src/eve/db/ds/db.cljc` | 1967 | `(if (some (fn [^Datom d] (= (.-v d) ov)) datoms)` |
| `src/eve/db/ds/js.cljs` | 68 | `(d/datom (.-e d) (.-a d) (.-v d) (or (.-tx d) d/tx0) (or (.-added d) true))))` |
| `src/eve/db/ds/pprint.clj` | 10 | `(pp/write-out (.-e d))` |
| `src/eve/db/ds/pprint.clj` | 13 | `(pp/write-out (.-a d))` |
| `src/eve/db/ds/pprint.clj` | 16 | `(pp/write-out (.-v d))` |
| `src/eve/db/ds/pprint.clj` | 42 | `(pp/write-out [(.-e d) (.-a d) (.-v d) (db/datom-tx d)])` |
| `src/eve/db/ds/pull_api.cljc` | 65 | `(or (nil? datom) (not= (.-a datom) (.-name attr)))` |
| `src/eve/db/ds/pull_api.cljc` | 72 | `(if (or (nil? datom) (not= (.-a datom) (.-name attr)))` |
| `src/eve/db/ds/pull_api.cljc` | 77 | `(recur (conj! acc (.-v datom)) (next-seq datoms)))))` |
| `src/eve/db/ds/pull_api.cljc` | 97 | `(or (nil? datom) (not= (.-a datom) (.-name attr)))` |
| `src/eve/db/ds/pull_api.cljc` | 104 | `(if (or (nil? datom) (not= (.-a datom) (.-name attr)))` |
| `src/eve/db/ds/pull_api.cljc` | 108 | `:let [id (if (.-reverse? attr) (.-e datom) (.-v datom))]` |
| `src/eve/db/ds/pull_api.cljc` | 144 | `(compare (.-name attr) (.-a datom)))` |
| `src/eve/db/ds/pull_api.cljc` | 152 | `(.-a datom)` |
| `src/eve/db/ds/pull_api.cljc` | 153 | `#(dpp/parse-attr-name (.-db ^Context context) (.-a datom)))]` |
| `src/eve/db/ds/pull_api.cljc` | 187 | `(ref-frame context seen recursion-limits pattern attr (.-v datom))]` |
| `src/eve/db/ds/pull_api.cljc` | 191 | `(assoc! acc (.-as attr) ((.-xform attr) (.-v datom)))` |
| `src/eve/db/ds/pull_api.cljc` | 235 | `(ref-frame context seen recursion-limits pattern attr (.-e ^Datom (first-seq datoms)))]` |
| `src/eve/db/ds/pull_api.cljc` | 296 | `(= (.-e d) id)` |
| `src/eve/db/ds/pull_api.cljc` | 297 | `(<= (compare (.-a d) to) 0)))))]` |
| `src/eve/db/ds/serialize.cljc` | 79 | `(nil? (.-a d2)) -1` |
| `src/eve/db/ds/serialize.cljc` | 80 | `(<= (compare (.-a d1) (.-a d2)) 0) -1` |
| `src/eve/db/ds/serialize.cljc` | 161 | `(let [e  (.-e d)` |
| `src/eve/db/ds/serialize.cljc` | 162 | `a  (attrs-map (.-a d))` |
| `src/eve/db/ds/serialize.cljc` | 163 | `v  (write-v (.-v d))` |
| `src/eve/db/ds/serialize.cljc` | 164 | `tx (- (.-tx d) db/tx0)]` |

## lazy seq returned from an index read (set/slice, set/rslice) — 20 rows

Replacement: The slice result must not escape unrealized: realize it inside the read region (`into []` / `doall` inside `$`), or the bset's slice returns a realized seq (v2 §5). A lazy seq over slab blocks is a live pointer out of the atom (v1 R3).

| File | Line | Hit |
|---|---|---|
| `src/eve/db/ds/db.cljc` | 729 | `[(set/slice eavt (datom e a v tx) (datom e a v tx))                   ;; e a v tx` |
| `src/eve/db/ds/db.cljc` | 730 | `(set/slice eavt (datom e a v tx0) (datom e a v txmax))               ;; e a v _` |
| `src/eve/db/ds/db.cljc` | 731 | `(->> (set/slice eavt (datom e a nil tx0) (datom e a nil txmax))      ;; e a _ tx` |
| `src/eve/db/ds/db.cljc` | 733 | `(set/slice eavt (datom e a nil tx0) (datom e a nil txmax))           ;; e a _ _` |
| `src/eve/db/ds/db.cljc` | 734 | `(->> (set/slice eavt (datom e nil nil tx0) (datom e nil nil txmax))  ;; e _ v tx` |
| `src/eve/db/ds/db.cljc` | 737 | `(->> (set/slice eavt (datom e nil nil tx0) (datom e nil nil txmax))  ;; e _ v _` |
| `src/eve/db/ds/db.cljc` | 739 | `(->> (set/slice eavt (datom e nil nil tx0) (datom e nil nil txmax))  ;; e _ _ tx` |
| `src/eve/db/ds/db.cljc` | 741 | `(set/slice eavt (datom e nil nil tx0) (datom e nil nil txmax))       ;; e _ _ _` |
| `src/eve/db/ds/db.cljc` | 743 | `(->> (set/slice avet (datom e0 a v tx0) (datom emax a v txmax))` |
| `src/eve/db/ds/db.cljc` | 745 | `(->> (set/slice aevt (datom e0 a nil tx0) (datom emax a nil txmax))` |
| `src/eve/db/ds/db.cljc` | 749 | `(set/slice avet (datom e0 a v tx0) (datom emax a v txmax))` |
| `src/eve/db/ds/db.cljc` | 750 | `(->> (set/slice aevt (datom e0 a nil tx0) (datom emax a nil txmax))` |
| `src/eve/db/ds/db.cljc` | 752 | `(->> (set/slice aevt (datom e0 a nil tx0) (datom emax a nil txmax))  ;; _ a _ tx` |
| `src/eve/db/ds/db.cljc` | 754 | `(set/slice aevt (datom e0 a nil tx0) (datom emax a nil txmax))       ;; _ a _ _` |
| `src/eve/db/ds/db.cljc` | 764 | `(set/slice (get db index)` |
| `src/eve/db/ds/db.cljc` | 770 | `(set/slice (get db index)` |
| `src/eve/db/ds/db.cljc` | 776 | `(set/rslice (get db index)` |
| `src/eve/db/ds/db.cljc` | 783 | `(set/slice (.-avet db)` |
| `src/eve/db/ds/db.cljc` | 1032 | `(set/rslice eavt` |
| `src/eve/db/ds/db.cljc` | 1039 | `(set/rslice avet` |


## Addendum (U6, acceptance) — rows the frozen derivation could not foresee

The frozen list above was derived mechanically (U0) from trap
PATTERNS. Three bodies of work changed baseline lines no pattern
named; their rows are recorded here, each with its owning unit and
gap id, so the diff gate passes for the right reason — documented
hunks, not silence. The frozen 195 rows are untouched.

### core.cljc — reclassified GATED → REPORT_ONLY (U6, per U3's filing)

Plan v2 §3 never classified core.cljc; U0's vendor step gated it by
default. It is the rewrite's adapter surface, not a verbatim file:
U3 rebuilt it around the conn (resolve-db-arg, deliver-datoms, the
§4 handle API, keep!/release!/close-conn! — the additive vars are
listed in docs/API-VARS.md), and U6 removed U3's GAP-U3-3 stopgap
from it and threaded the handle through its entity wrapper. Like
db.cljc/conn.cljc/serialize.cljc (the §3 type-swap/new classes), it
is reported by the gate, not line-gated. Recorded in
scripts/diff_upstream.sh's class lists.

### pull_api.cljc — GAP-3 re-point + §2 cache-field removal (U3/U4)

U2's GAP-3 spec, adopted verbatim by U3 and confirmed by U4: the
LRU cache FIELDS the baseline read off the DB (.-pull-attrs,
.-pull-patterns) do not exist on the Eve DB (the §3 type swap
removed the cache fields with the hash caches), so the caches move
to pull_api's own Context; and the me.tonsky set/slice reads
re-point at the §5 bset (db/-datoms slices).

| `src/eve/db/ds/pull_api.cljc` | 8 | `[me.tonsky.persistent-sorted-set :as set])` |
| `src/eve/db/ds/pull_api.cljc` | 150 | `(let [datom-attr (lru/-get` |
| `src/eve/db/ds/pull_api.cljc` | 151 | `(.-pull-attrs (db/unfiltered-db (.-db ^Context context)))` |
| `src/eve/db/ds/pull_api.cljc` | 222 | `(set/slice (.-avet ^DB db) (db/datom db/e0 name id db/tx0) (db/datom db/emax name id db/txmax))` |
| `src/eve/db/ds/pull_api.cljc` | 277 | `(set/slice (.-eavt ^DB db) (db/datom id nil nil db/tx0) (db/datom id nil nil db/txmax))` |
| `src/eve/db/ds/pull_api.cljc` | 289 | `(set/slice (.-eavt ^DB db) (db/datom id from nil db/tx0) (db/datom id to nil db/txmax))` |
| `src/eve/db/ds/pull_api.cljc` | 334 | `{:pattern (lru/-get (.-pull-patterns (db/unfiltered-db db)) pattern #(dpp/parse-pattern db pattern))` |

### query.cljc — GAP-5 require re-point (U4)

Line 9 required me.tonsky.persistent-sorted-set.arrays, whose
make-array is an Eve vector in the vendored port, while query's
tuple machinery expects native arrays; GAP-5's arrays.cljc restores
the native contract and the require re-points at it.

| `src/eve/db/ds/query.cljc` | 9 | `[me.tonsky.persistent-sorted-set.arrays :as da]` |

### impl/entity.cljc — GAP-1/GAP-2 handle wiring (U6, item 6)

Entity equiv/hash keyed on the delivered DB value cannot work
(U4's probe4: equiv throws :eve/no-identity outside a region; hash
differs across deliveries of the same version). U6 threads U3's
§4 handle ({:atom a :tx n}) through Entity as a fifth field:
entity gains a 3-arity form, entity-attr / -lookup-backwards /
datoms->cache take the handle, equiv keys on (atom, tx) when both
sides carry one (else the baseline's same? fallback), and hash
keys on the handle's identity (else db/entity-db-hash). Every
change is line-for-line against the baseline lines below;
baseline line 171 (the equiv same? line) was already a frozen row.

| `src/eve/db/ds/impl/entity.cljc` | 14 | `(defn entity [db eid]` |
| `src/eve/db/ds/impl/entity.cljc` | 15 | `{:pre [(db/db? db)]}` |
| `src/eve/db/ds/impl/entity.cljc` | 16 | `(when-let [e (entid db eid)]` |
| `src/eve/db/ds/impl/entity.cljc` | 20 | `(defn- entity-attr [db a datoms]` |
| `src/eve/db/ds/impl/entity.cljc` | 23 | `(reduce #(conj %1 (entity db (:v %2))) #{} datoms)` |
| `src/eve/db/ds/impl/entity.cljc` | 26 | `(entity db (:v (first datoms)))` |
| `src/eve/db/ds/impl/entity.cljc` | 29 | `(defn- -lookup-backwards [db eid attr not-found]` |
| `src/eve/db/ds/impl/entity.cljc` | 32 | `(entity db (:e (first datoms)))` |
| `src/eve/db/ds/impl/entity.cljc` | 33 | `(reduce #(conj %1 (entity db (:e %2))) #{} datoms))` |
| `src/eve/db/ds/impl/entity.cljc` | 58 | `(deftype Entity [db eid touched cache]` |
| `src/eve/db/ds/impl/entity.cljc` | 79 | `(-> (-lookup-backwards db eid (db/reverse-ref attr) nil)` |
| `src/eve/db/ds/impl/entity.cljc` | 178 | `(#?(:clj System/identityHashCode :cljs goog/getUid) (.-db e))))` |
| `src/eve/db/ds/impl/entity.cljc` | 186 | `(-lookup-backwards (.-db this) (.-eid this) (db/reverse-ref attr) not-found)` |
| `src/eve/db/ds/impl/entity.cljc` | 192 | `(let [value (entity-attr (.-db this) attr datoms)]` |
| `src/eve/db/ds/impl/entity.cljc` | 208 | `(defn- datoms->cache [db datoms]` |
| `src/eve/db/ds/impl/entity.cljc` | 211 | `(assoc acc a (entity-attr db a part))))` |
| `src/eve/db/ds/impl/entity.cljc` | 220 | `(datoms->cache (.-db e))` |
