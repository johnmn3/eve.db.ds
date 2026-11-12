# ONE-EVE-AUDIT

Scope: every lexical `#?(`/`#?@(` in `src/` at main `473fb050` (376), comments and strings excluded, nested openings counted once. Method: `scripts/one_eve_counts.py`.

Baseline: **376** host branches, **20** plain type sites at `473fb050`.
After unit 1 (ab142948): 349 / 15.
After unit 2 (584e364): 346 / 15.
After LRU Counted (30f1d19): 346 / 15.
After unit 3 (this commit, working tree): **342** / **0**.
After open-other batch 1 (pull_api first/next/conj-seq): **336** / **0**.
After open-other batch 2 (pull_api PullAttr/PullPattern hints): **329** / **0**.
After open-other batch 3 (core.cljc Entity/DB :tag): **325** / **0**.
After open-other batch 4 (core.cljc defn name/arg hints): **318** / **0**.
After open-other batch 5 (pull_api Datom/DB type hints): **308** / **0**.
After open-other batch 6 (mark 35 declare/import/flag/stub rows glue): **308** / **0** (no src change).
After open-other batch 7 (pull_parser parse-pattern + query fn arg hints): **306** / **0**.
After open-other batch 8 (mark instance? class-name rows glue): **306** / **0** (no src change).
After open-other batch 9 (leftover require/import glue): **306** / **0** (no src change).
After open-other batch 10 (portable tuple-get + serialize nth/get): **299** / **0**.
After open-other batch 11 (unhinted identical predicate defns): **291** / **0**.
After open-other batch 12 (mark 95 leftover host-API/macro/print/storage rows glue): **291** / **0** (no src change).
After open-other batch 13 (portable hash-join get + leftover glue): **290** / **0**.
After open-other batch 14 (portable join-tuples + tuple-key-fn glue): **288** / **0**.
After glue re-derive (batches 6/8/12/13/14 reviewed; 58 reclassified data/memory/threading, 0 folded, counts unchanged): **288** / **0**.
After open-other batch 16 (portable ihash + tx-id?): **284** / **0**.
After open-other batch 17 (portable db?): **283** / **0**.
After open-other batch 18 (portable vpred / -search pred): **281** / **0**.
After open-other batch 19 (portable seqable?): **276** / **0**.
After open-other batch 20 (storage API through Eve atoms): **266** / **0**.
After open-other batch 21 (serialize locking via eve.host): **265** / **0**.
After open-other batch 22 (transact/future-call via eve.thread): **262** / **0**.

Plain type sites (deftype/defrecord/defrecord-updatable that are not eve-deftype / eve.deftype/deftype) are **0**. Remaining `(deftype …)` forms in source are eve.deftype/deftype (LRU, Entity, AutoTempid, FilteredDB, ArrayRelation, CollRelation, SingletonRelation) or evicted through `def-eve-record` / `deftrecord`.

Kept branches still include portable/glue work owned by vm (non-record host-branch removal). A kept row is **glue** only when it is loader/require-macros wiring that does not touch data, memory, threading, or HTTP; otherwise it is **open** for vm.

## Original 376 branches

| # | file | line at 473fb050 | snippet | status | reason |
|---|------|------------------|---------|--------|--------|
| 1 | `eve/db/ds/built_ins.cljc` | 127 | `(#?(:cljs js/Math.sqrt :clj Math/sqrt :cljr Math/Sqrt) (aggregate-variance coll)` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 2 | `eve/db/ds/conn.cljc` | 8 | `#?(:lpy [eve.deftype :refer [deftype]])` | KEPT | glue: host :refer of eve.deftype/deftype on bb/lpy |
| 3 | `eve/db/ds/conn.cljc` | 13 | `#?(:clje [eve.host :refer [volatile! swap! reset! compare-and-set! locking]]))` | KEPT | glue: clje eve.host :refer for host primitives (also listed under threading refers) |
| 4 | `eve/db/ds/conn.cljc` | 14 | `#?(:clj` | KEPT | glue: host :import of library or JVM types |
| 5 | `eve/db/ds/conn.cljc` | 30 | `#?(:jank  (if (identical? (:db @atom) oldv) @atom (assoc @atom :db oldv))` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 6 | `eve/db/ds/conn.cljc` | 41 | `(if (instance? #?(:jank db/FilteredDB :clj FilteredDB :cljr FilteredDB :cljs db/` | KEPT | data: type test of FilteredDB in transact; should go through an Eve protocol, not instance? class-name glue |
| 7 | `eve/db/ds/conn.cljc` | 45 | `(defn #?@(:jank [^db/DB db-with] :clj [^DB db-with] :cljr [^DB db-with] :cljs [^` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 8 | `eve/db/ds/conn.cljc` | 53 | `#?(:clj  (instance? clojure.lang.IDeref conn) :cljr (instance? clojure.lang.IDer` | KEPT | data: conn? tests a connection value via host IDeref; should be an Eve protocol/conn? rather than host IDeref |
| 9 | `eve/db/ds/conn.cljc` | 87 | `#?(:clj` | REMOVED | storage is an Eve atom; dropped vanilla IStorage host wrappers / dead tail-store side channel |
| 10 | `eve/db/ds/conn.cljc` | 114 | `#?(:clj` | REMOVED | storage is an Eve atom; dropped vanilla IStorage host wrappers / dead tail-store side channel |
| 11 | `eve/db/ds/conn.cljc` | 189 | `#?(:clj` | REMOVED | storage is an Eve atom; dropped vanilla IStorage host wrappers / dead tail-store side channel |
| 12 | `eve/db/ds/core.cljc` | 9 | `#?(:cljs [cljs.reader :as edn]` | KEPT | glue: host edn reader libspec (cljs.reader vs clojure.edn) |
| 13 | `eve/db/ds/core.cljc` | 16 | `#?(:clj [eve.db.ds.pprint])` | KEPT | glue: clj-only pprint require |
| 14 | `eve/db/ds/core.cljc` | 24 | `#?(:clj` | KEPT | glue: host :import of library or JVM types |
| 15 | `eve/db/ds/core.cljc` | 39 | `(def ^{:tag #?(:jank de/Entity :clj Entity :cljr Entity :cljs de/Entity :clje de` | REMOVED | dropped host :tag metadata; Entity/DB return hints were reader branches only |
| 16 | `eve/db/ds/core.cljc` | 97 | `(defn #?@(:jank [^db/DB entity-db] :clj [^DB entity-db] :cljr [^DB entity-db] :c` | REMOVED | dropped host type hints on defn name/args; unhinted clojure.core defn |
| 17 | `eve/db/ds/core.cljc` | 99 | `[#?@(:jank [^de/Entity entity] :clj [^Entity entity] :cljr [^Entity entity] :clj` | REMOVED | dropped host type hints on defn name/args; unhinted clojure.core defn |
| 18 | `eve/db/ds/core.cljc` | 103 | `(def ^{:tag #?(:jank de/Entity :clj Entity :cljr Entity :cljs de/Entity :clje de` | REMOVED | dropped host :tag metadata; Entity/DB return hints were reader branches only |
| 19 | `eve/db/ds/core.cljc` | 163 | `(defn #?@(:jank [^db/DB empty-db] :clj [^DB empty-db] :cljr [^DB empty-db] :cljs` | REMOVED | dropped host type hints on defn name/args; unhinted clojure.core defn |
| 20 | `eve/db/ds/core.cljc` | 194 | `(def ^{:tag #?(:jank db/Datom :clj Datom :cljr Datom :cljs db/Datom :clje db/Dat` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 21 | `eve/db/ds/core.cljc` | 207 | `(defn #?@(:jank [^db/DB init-db] :clj [^DB init-db] :cljr [^DB init-db] :cljs [^` | REMOVED | dropped host type hints on defn name/args; unhinted clojure.core defn |
| 22 | `eve/db/ds/core.cljc` | 233 | `(def ^{:tag #?(:jank db/DB :clj DB :cljr DB :cljs db/DB :clje db/DB :lpy db/DB)` | REMOVED | dropped host :tag metadata; Entity/DB return hints were reader branches only |
| 23 | `eve/db/ds/core.cljc` | 257 | `(instance? #?(:jank db/FilteredDB :clj FilteredDB :cljr FilteredDB :cljs db/Filt` | KEPT | data: type test of FilteredDB in core filter/db ops; should go through an Eve protocol |
| 24 | `eve/db/ds/core.cljc` | 271 | `(let [#?@(:jank [^db/FilteredDB fdb] :clj [^FilteredDB fdb] :cljr [^FilteredDB f` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 25 | `eve/db/ds/core.cljc` | 277 | `#?(:jank (db/->FilteredDB orig-db #(and (orig-pred %) (pred orig-db %)) (atom 0)` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 26 | `eve/db/ds/core.cljc` | 281 | `#?(:jank (db/->FilteredDB db #(pred db %) (atom 0))` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 27 | `eve/db/ds/core.cljc` | 293 | `(def ^{:arglists '([db tx-data]) :tag #?(:jank db/DB :clj DB :cljr DB :cljs db/D` | REMOVED | dropped host :tag metadata; Entity/DB return hints were reader branches only |
| 28 | `eve/db/ds/core.cljc` | 297 | `(defn #?@(:jank [^db/DB with-schema] :clj [^DB with-schema] :cljr [^DB with-sche` | REMOVED | dropped host type hints on defn name/args; unhinted clojure.core defn |
| 29 | `eve/db/ds/core.cljc` | 381 | `(defn #?@(:jank [^db/Datom find-datom] :clj [^Datom find-datom] :cljr [^Datom fi` | REMOVED | dropped host type hints on defn name/args; unhinted clojure.core defn |
| 30 | `eve/db/ds/core.cljc` | 488 | `#?(:clj` | REMOVED | storage is an Eve atom; dropped vanilla IStorage host wrappers / dead tail-store side channel |
| 31 | `eve/db/ds/core.cljc` | 615 | `#?(:cljs` | KEPT | glue: cljs EDN tag-parser registration for data-readers |
| 32 | `eve/db/ds/core.cljc` | 628 | `#?(:lpy` | KEPT | glue: basilisp programmatic *data-readers* merge (file route runs too early) |
| 33 | `eve/db/ds/core.cljc` | 656 | `(defn #?@(:jank [^db/DB db] :clj [^DB db] :cljr [^DB db] :cljs [^db/DB db] :clje` | REMOVED | dropped host type hints on defn name/args; unhinted clojure.core defn |
| 34 | `eve/db/ds/core.cljc` | 672 | `#?(:cljs` | REMOVED | transact returns an eve.thread/promise; transact-async uses eve.thread/future-call; dropped host IDeref reify twins |
| 35 | `eve/db/ds/core.cljc` | 714 | `#?(:clje` | REMOVED | transact returns an eve.thread/promise; transact-async uses eve.thread/future-call; dropped host IDeref reify twins |
| 36 | `eve/db/ds/core.cljc` | 723 | `#?(:cljs` | REMOVED | transact returns an eve.thread/promise; transact-async uses eve.thread/future-call; dropped host IDeref reify twins |
| 37 | `eve/db/ds/core.cljc` | 760 | `#?(:clj` | REMOVED | storage is an Eve atom; dropped vanilla IStorage host wrappers / dead tail-store side channel |
| 38 | `eve/db/ds/core.cljc` | 767 | `#?(:clj` | REMOVED | storage is an Eve atom; dropped vanilla IStorage host wrappers / dead tail-store side channel |
| 39 | `eve/db/ds/core.cljc` | 784 | `#?(:clj` | REMOVED | storage is an Eve atom; dropped vanilla IStorage host wrappers / dead tail-store side channel |
| 40 | `eve/db/ds/core.cljc` | 791 | `#?(:clj` | REMOVED | storage is an Eve atom; dropped vanilla IStorage host wrappers / dead tail-store side channel |
| 41 | `eve/db/ds/core.cljc` | 802 | `#?(:clj` | REMOVED | storage is an Eve atom; dropped vanilla IStorage host wrappers / dead tail-store side channel |
| 42 | `eve/db/ds/core.cljc` | 811 | `#?(:clj` | REMOVED | storage is an Eve atom; dropped vanilla IStorage host wrappers / dead tail-store side channel |
| 43 | `eve/db/ds/db.cljc` | 3 | `#?(:cljs [goog.array :as garray])` | KEPT | glue: cljs goog.array require |
| 44 | `eve/db/ds/db.cljc` | 6 | `#?(:clj [eve.db.ds.inline :refer [update]])` | KEPT | glue: clj-only inline/update require |
| 45 | `eve/db/ds/db.cljc` | 8 | `#?(:clje [eve.host :refer [transient persistent! conj! dissoc! long volatile!]])` | KEPT | glue: clje eve.host :refer for host primitives |
| 46 | `eve/db/ds/db.cljc` | 22 | `#?(:lpy [eve.deftype :refer [deftype]])` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 47 | `eve/db/ds/db.cljc` | 29 | `#?(:cljs (:require-macros [eve.db.ds.db :refer [case-tree combine-cmp defn+ defc` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 48 | `eve/db/ds/db.cljc` | 30 | `(:refer-clojure :exclude [seqable? #?(:clj update)]))` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 49 | `eve/db/ds/db.cljc` | 32 | `#?(:clj (set! *warn-on-reflection* true) :cljr (set! *warn-on-reflection* true))` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 50 | `eve/db/ds/db.cljc` | 36 | `#?(:cljs` | KEPT | glue: cljs :require-macros for this file's macros |
| 51 | `eve/db/ds/db.cljc` | 59 | `(do #?@(:clj [(defn ^Boolean seqable? [x] (and (not (string? x))` | REMOVED | portable seqable?: not-string, plus nil/seq?/coll?/arrays/array?; dropped host instance? arms |
| 52 | `eve/db/ds/db.cljc` | 60 | `#?@(:clj [(or (seq? x)` | REMOVED | portable seqable?; dropped nested host collection test |
| 53 | `eve/db/ds/db.cljc` | 79 | `#?@(:clj [(or (seq? x)` | REMOVED | portable seqable?; dropped nested host collection test |
| 54 | `eve/db/ds/db.cljc` | 105 | `#?@(:clj [(or (seq? x)` | REMOVED | portable seqable?; dropped nested host collection test |
| 55 | `eve/db/ds/db.cljc` | 128 | `#?(:clje (defn seqable? [x] (and (not (string? x))` | REMOVED | portable seqable?; dropped F-CLJE-SEQABLE twin |
| 56 | `eve/db/ds/db.cljc` | 141 | `(do #?@(:clj [(defn- cljs-env?` | KEPT | glue: macro-layer host twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 57 | `eve/db/ds/db.cljc` | 164 | `#?(:clje (defn- cljs-env?` | KEPT | glue: macro-layer host twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 58 | `eve/db/ds/db.cljc` | 168 | `(do #?@(:clj [(defmacro if-cljs` | KEPT | glue: macro-layer host twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 59 | `eve/db/ds/db.cljc` | 188 | `#?(:clje (defmacro if-cljs` | KEPT | glue: macro-layer host twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 60 | `eve/db/ds/db.cljc` | 192 | `(do #?@(:clj [(defn patch-tag [meta cljs-env?]` | KEPT | glue: macro-layer host twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 61 | `eve/db/ds/db.cljc` | 225 | `#?(:clje (defn patch-tag` | KEPT | glue: macro-layer host twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 62 | `eve/db/ds/db.cljc` | 238 | `(do #?@(:clj [(defmacro defn+` | KEPT | glue: macro-layer host twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 63 | `eve/db/ds/db.cljc` | 322 | `#?(:clje (defmacro defn+` | KEPT | glue: macro-layer host twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 64 | `eve/db/ds/db.cljc` | 348 | `#?(:clje (defn- hash-combine* [seed hash]` | KEPT | data: datom/DB hash mixing; host Util/hashCombine vs portable mix — hashing of data values |
| 65 | `eve/db/ds/db.cljc` | 358 | `#?@(:clj [(clojure.lang.Util/hashCombine x y)] :cljr [(clojure.lang.Util/hashCom` | KEPT | data: datom/DB hash mixing; host Util/hashCombine vs portable mix — hashing of data values |
| 66 | `eve/db/ds/db.cljc` | 363 | `(do #?@(:clj [   (defn- get-sig [method]` | KEPT | glue: macro-layer host twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 67 | `eve/db/ds/db.cljc` | 387 | `#?(:clje (defn- get-sig [& _args] nil))` | KEPT | glue: macro-layer host twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 68 | `eve/db/ds/db.cljc` | 389 | `(do #?@(:clj [   (defn- dedupe-interfaces [deftype-form]` | KEPT | glue: macro-layer host twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 69 | `eve/db/ds/db.cljc` | 407 | `#?(:clje (defn- dedupe-interfaces [& _args] nil))` | KEPT | glue: macro-layer host twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 70 | `eve/db/ds/db.cljc` | 409 | `(do #?@(:clj [(defn- make-record-updatable-cljs [name fields & impls]` | KEPT | glue: macro-layer host twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 71 | `eve/db/ds/db.cljc` | 431 | `#?(:clje (defn- make-record-updatable-cljs [name fields & impls]` | KEPT | glue: macro-layer host twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 72 | `eve/db/ds/db.cljc` | 436 | `(do #?@(:clj [(defn- make-record-updatable-clj [name fields & impls]` | KEPT | glue: macro-layer host twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 73 | `eve/db/ds/db.cljc` | 480 | `#?(:clje (defn- make-record-updatable-clj [name fields & impls]` | KEPT | glue: macro-layer host twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 74 | `eve/db/ds/db.cljc` | 483 | `(do #?@(:clj [(defmacro defrecord-updatable [name fields & impls]` | KEPT | glue: macro-layer host twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 75 | `eve/db/ds/db.cljc` | 497 | `#?(:clje (defmacro defrecord-updatable [name fields & impls]` | KEPT | glue: macro-layer host twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 76 | `eve/db/ds/db.cljc` | 515 | `#?(:clj  (declare hash-datom) :cljr (declare hash-datom)` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 77 | `eve/db/ds/db.cljc` | 520 | `#?(:clj  (declare equiv-datom) :cljr (declare equiv-datom)` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 78 | `eve/db/ds/db.cljc` | 525 | `#?(:clj  (declare seq-datom) :cljr (declare seq-datom)` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 79 | `eve/db/ds/db.cljc` | 530 | `#?(:clj  (declare nth-datom) :cljr (declare nth-datom)` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 80 | `eve/db/ds/db.cljc` | 535 | `#?(:clj  (declare assoc-datom) :cljr (declare assoc-datom)` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 81 | `eve/db/ds/db.cljc` | 540 | `#?(:clj  (declare val-at-datom) :cljr (declare val-at-datom)` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 82 | `eve/db/ds/db.cljc` | 577 | `(empty [d] (throw #?(:cljr (System.NotSupportedException. "empty is not supporte` | KEPT | data: Datom empty throws a host exception class; data-path error, not loader glue |
| 83 | `eve/db/ds/db.cljc` | 596 | `#?(:clje (vector k)` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 84 | `eve/db/ds/db.cljc` | 607 | `#?(:cljs (goog/exportSymbol "eve.db.ds.db.Datom" Datom))` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 85 | `eve/db/ds/db.cljc` | 662 | `#?@(:clj [(throw (IndexOutOfBoundsException.))] :cljr [(throw (System.IndexOutOf` | KEPT | data: Datom nth out-of-bounds throws a host exception class; data-path error |
| 86 | `eve/db/ds/db.cljc` | 686 | `(throw #?(:clje (ex-info (str "invalid key for #eve.db.ds/Datom: " k) {})` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 87 | `eve/db/ds/db.cljc` | 697 | `#?(:clj` | KEPT | glue: host print-method for Datom |
| 88 | `eve/db/ds/db.cljc` | 710 | `(do #?@(:clj [(defmacro combine-cmp [& comps]` | KEPT | glue: macro-layer host twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 89 | `eve/db/ds/db.cljc` | 758 | `#?(:clje (defmacro combine-cmp [& comps]` | KEPT | glue: macro-layer host twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 90 | `eve/db/ds/db.cljc` | 770 | `(do #?@(:clj [(defn- -case-tree [queries variants]` | KEPT | glue: macro-layer host twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 91 | `eve/db/ds/db.cljc` | 805 | `#?(:clje (defn- -case-tree [queries variants]` | KEPT | glue: macro-layer host twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 92 | `eve/db/ds/db.cljc` | 814 | `(do #?@(:clj [(defmacro case-tree [qs vs]` | KEPT | glue: macro-layer host twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 93 | `eve/db/ds/db.cljc` | 822 | `#?(:clje (defmacro case-tree [qs vs]` | KEPT | glue: macro-layer host twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 94 | `eve/db/ds/db.cljc` | 826 | `#?(:clj` | KEPT | glue: clj/cljr :inline compiler metadata |
| 95 | `eve/db/ds/db.cljc` | 838 | `#?(:clj  {:inline (fn [x y] `(identical? (class ~x) (class ~y)))} :cljr {:inline` | KEPT | glue: clj/cljr :inline compiler metadata |
| 96 | `eve/db/ds/db.cljc` | 840 | `#?@(:clj [(identical? (class x) (class y))] :cljr [(identical? (class x) (class ` | KEPT | data: class-identical? is used by datom/value comparators; data-path type identity, not loader glue |
| 97 | `eve/db/ds/db.cljc` | 845 | `(do #?@(:jank [   (defn class-name` | KEPT | data: class-name is used by datom/value comparators for type-order fallback |
| 98 | `eve/db/ds/db.cljc` | 866 | `#?(:clje (defn class-name` | KEPT | data: class-name clje twin; data-path type-order fallback |
| 99 | `eve/db/ds/db.cljc` | 872 | `#?@(:clj [(long (compare (class-name x) (class-name y)))] :cljr [(long (compare ` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 100 | `eve/db/ds/db.cljc` | 877 | `(do #?@(:jank [(defmacro int-compare [x y]` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 101 | `eve/db/ds/db.cljc` | 898 | `#?(:clje (defmacro int-compare [x y]` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 102 | `eve/db/ds/db.cljc` | 903 | `(do #?@(:jank [(defn ihash` | REMOVED | portable clojure.core/hash; dropped host Util/hasheq and clje twin |
| 103 | `eve/db/ds/db.cljc` | 920 | `#?(:clje (defn ihash` | REMOVED | portable clojure.core/hash; dropped F-CLJE ihash twin |
| 104 | `eve/db/ds/db.cljc` | 924 | `(do #?@(:clj [(declare value-compare)] :cljr [(declare value-compare)]` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 105 | `eve/db/ds/db.cljc` | 928 | `#?(:clje (declare value-compare))` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 106 | `eve/db/ds/db.cljc` | 974 | `#?@(:clj  [(instance? Number x)       (compare x y)] :cljr [(number? x)         ` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 107 | `eve/db/ds/db.cljc` | 976 | `#?@(:clj  [(instance? Comparable x)   (compare x y)] :cljr [(instance? System.IC` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 108 | `eve/db/ds/db.cljc` | 980 | `#?@(:cljs [(or (number? x) (string? x) (array? x) (true? x) (false? x)) (garray/` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 109 | `eve/db/ds/db.cljc` | 982 | `(catch #?@(:clj [ClassCastException] :cljr [System.InvalidCastException]` | KEPT | data: value-compare catch of host ClassCast/TypeError; data-path comparison |
| 110 | `eve/db/ds/db.cljc` | 991 | `#?(:clj` | KEPT | glue: clj/cljr :inline compiler metadata |
| 111 | `eve/db/ds/db.cljc` | 1009 | `(do #?@(:jank [(defmacro defcomp [sym [arg1 arg2] & body]` | KEPT | glue: macro-layer host twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 112 | `eve/db/ds/db.cljc` | 1054 | `#?(:clje (defmacro defcomp [sym [arg1 arg2] & body]` | KEPT | glue: macro-layer host twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 113 | `eve/db/ds/db.cljc` | 1084 | `#?(:clj` | KEPT | glue: clj/cljr :inline compiler metadata |
| 114 | `eve/db/ds/db.cljc` | 1092 | `#?@(:clj [(long (compare a1 a2))] :cljr [(long (compare a1 a2))]` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 115 | `eve/db/ds/db.cljc` | 1159 | `(catch #?@(:clj [ClassCastException] :cljr [System.InvalidCastException]` | KEPT | data: value-compare catch of host ClassCast/TypeError; data-path comparison |
| 116 | `eve/db/ds/db.cljc` | 1172 | `(do #?@(:clj [(declare hash-db pr-db)] :cljr [(declare hash-db pr-db)]` | KEPT | glue: forward-declare twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 117 | `eve/db/ds/db.cljc` | 1179 | `#?(:clje (declare hash-db pr-db))` | KEPT | glue: forward-declare twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 118 | `eve/db/ds/db.cljc` | 1181 | `(do #?@(:clj [(declare hash-fdb)] :cljr [(declare hash-fdb)]` | KEPT | glue: forward-declare twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 119 | `eve/db/ds/db.cljc` | 1185 | `#?(:clje (declare hash-fdb))` | KEPT | glue: forward-declare twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 120 | `eve/db/ds/db.cljc` | 1187 | `(do #?@(:clj [(declare equiv-db)] :cljr [(declare equiv-db)]` | KEPT | glue: forward-declare twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 121 | `eve/db/ds/db.cljc` | 1191 | `#?(:clje (declare equiv-db))` | KEPT | glue: forward-declare twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 122 | `eve/db/ds/db.cljc` | 1193 | `(do #?@(:clj [(declare restore-db)] :cljr [(declare restore-db)]` | KEPT | glue: forward-declare twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 123 | `eve/db/ds/db.cljc` | 1197 | `#?(:clje (declare restore-db))` | KEPT | glue: forward-declare twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 124 | `eve/db/ds/db.cljc` | 1199 | `(do #?@(:clj [(declare indexing?)] :cljr [(declare indexing?)]` | KEPT | glue: forward-declare twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 125 | `eve/db/ds/db.cljc` | 1203 | `#?(:clje (declare indexing?))` | KEPT | glue: forward-declare twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 126 | `eve/db/ds/db.cljc` | 1205 | `#?(:cljs (defn pr-db [db w opts]))` | KEPT | glue: cljs forward stub for pr-db |
| 127 | `eve/db/ds/db.cljc` | 1207 | `(do #?@(:clj [(declare resolve-datom)] :cljr [(declare resolve-datom)]` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 128 | `eve/db/ds/db.cljc` | 1211 | `#?(:clje (declare resolve-datom))` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 129 | `eve/db/ds/db.cljc` | 1213 | `(do #?@(:clj [(declare components->pattern)] :cljr [(declare components->pattern` | KEPT | glue: forward-declare twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 130 | `eve/db/ds/db.cljc` | 1217 | `#?(:clje (declare components->pattern))` | KEPT | glue: forward-declare twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 131 | `eve/db/ds/db.cljc` | 1221 | `(do #?@(:clj [(defmacro validate-attr [attr at]` | KEPT | glue: macro-layer host twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 132 | `eve/db/ds/db.cljc` | 1255 | `#?(:clje (defmacro validate-attr [attr at]` | KEPT | glue: macro-layer host twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 133 | `eve/db/ds/db.cljc` | 1264 | `(do #?@(:clj [(defmacro validate-val [v at]` | KEPT | glue: macro-layer host twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 134 | `eve/db/ds/db.cljc` | 1284 | `#?(:clje (defmacro validate-val [v at]` | KEPT | glue: macro-layer host twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 135 | `eve/db/ds/db.cljc` | 1329 | `#?(:clj` | REMOVED | portable vpred via =; dropped host .equals/.Equals arms |
| 136 | `eve/db/ds/db.cljc` | 1367 | `#?(:lpy (declare -schema -attrs-by -search -datoms -seek-datoms -rseek-datoms -i` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 137 | `eve/db/ds/db.cljc` | 1373 | `#?(:lpy (declare multival?))` | KEPT | glue: basilisp-only forward declare so deftype method bodies resolve at analysis |
| 138 | `eve/db/ds/db.cljc` | 1387 | `(let [entry #?(:clj  (fn [k v] (clojure.lang.MapEntry. k v)) :cljr (fn [k v] (cl` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 139 | `eve/db/ds/db.cljc` | 1416 | `#?(:bb (seq x) :default x))` | KEPT | data: search-result wraps -search slices so transact can vec them; data-path (F-BB-SEARCH-SEQ) |
| 140 | `eve/db/ds/db.cljc` | 1453 | `#?(:clje [k v]` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 141 | `eve/db/ds/db.cljc` | 1496 | `(-schema [db] #?(:clje schema` | KEPT | data: DB -schema field access (F-CLJE-SCHEMA); data-path field read, not loader glue |
| 142 | `eve/db/ds/db.cljc` | 1508 | `pred       #?(:clj  (vpred v)` | REMOVED | -search pred uses portable vpred on every host |
| 143 | `eve/db/ds/db.cljc` | 1566 | `#?(:bb  (seq (set/slice (get db index)` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 144 | `eve/db/ds/db.cljc` | 1630 | `#?(:clje` | KEPT | data: clje extend-type of IAssociative on DB so assoc/update work in transact; data-path |
| 145 | `eve/db/ds/db.cljc` | 1643 | `#?@(:jank [(and (satisfies? ISearch x)` | REMOVED | portable satisfies? of ISearch/IIndexAccess/IDB; dropped host instance? arms |
| 146 | `eve/db/ds/db.cljc` | 1678 | `#?@(:cljs` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 147 | `eve/db/ds/db.cljc` | 1767 | `(defn #?@(:clj [^DB unfiltered-db] :cljr [^DB unfiltered-db] :cljs [^DB unfilter` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 148 | `eve/db/ds/db.cljc` | 2031 | `#?(:cljs` | KEPT | glue: host print-method / pr-db for DB and FilteredDB |
| 149 | `eve/db/ds/db.cljc` | 2056 | `#?(:lpy` | KEPT | glue: forward-declare twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 150 | `eve/db/ds/db.cljc` | 2066 | `#?(:clj` | KEPT | glue: forward-declare twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 151 | `eve/db/ds/db.cljc` | 2096 | `(do #?@(:clj [(declare entid-strict)] :cljr [(declare entid-strict)]` | KEPT | glue: forward-declare twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 152 | `eve/db/ds/db.cljc` | 2100 | `#?(:clje (declare entid-strict))` | KEPT | glue: forward-declare twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 153 | `eve/db/ds/db.cljc` | 2102 | `(do #?@(:clj [(declare ref?)] :cljr [(declare ref?)]` | KEPT | glue: forward-declare twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 154 | `eve/db/ds/db.cljc` | 2106 | `#?(:clje (declare ref?))` | KEPT | glue: forward-declare twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 155 | `eve/db/ds/db.cljc` | 2108 | `(do #?@(:clj [(declare tuple?)] :cljr [(declare tuple?)]` | KEPT | glue: forward-declare twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 156 | `eve/db/ds/db.cljc` | 2112 | `#?(:clje (declare tuple?))` | KEPT | glue: forward-declare twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 157 | `eve/db/ds/db.cljc` | 2114 | `(do #?@(:clj [(declare resolve-tuple-refs)] :cljr [(declare resolve-tuple-refs)]` | KEPT | glue: forward-declare twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 158 | `eve/db/ds/db.cljc` | 2118 | `#?(:clje (declare resolve-tuple-refs))` | KEPT | glue: forward-declare twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 159 | `eve/db/ds/db.cljc` | 2142 | `cmp     #?(:bb (case index` | KEPT | data: find-datom comparator access on an index set (F-BB-SEEK-SLICE / F-CLJE-CMP); data-path |
| 160 | `eve/db/ds/db.cljc` | 2179 | `datom   #?(:bb (first (set/slice set from to))` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 161 | `eve/db/ds/db.cljc` | 2265 | `(do #?@(:clj [(defn-  ^Boolean tuple-ref-slot? [db schema-a idx] (cond` | REMOVED | unhinted clojure.core defn-; predicate body was identical on every host |
| 162 | `eve/db/ds/db.cljc` | 2284 | `#?(:clje (defn- tuple-ref-slot? [db schema-a idx] (cond` | REMOVED | unhinted clojure.core defn-; dropped F-CLJE-PREDS twin (same body) |
| 163 | `eve/db/ds/db.cljc` | 2289 | `(do #?@(:clj [(defn-  ^Boolean resolved-eid? [x] (or` | REMOVED | unhinted clojure.core defn-; predicate body was identical on every host |
| 164 | `eve/db/ds/db.cljc` | 2301 | `#?(:clje (defn- resolved-eid? [x] (or` | REMOVED | unhinted clojure.core defn-; dropped F-CLJE-PREDS twin (same body) |
| 165 | `eve/db/ds/db.cljc` | 2353 | `#?@(:cljs [(array? eid) (recur db (array-seq eid))])` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 166 | `eve/db/ds/db.cljc` | 2382 | `#?@(:cljs` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 167 | `eve/db/ds/db.cljc` | 2402 | `#?(:clj` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 168 | `eve/db/ds/db.cljc` | 2468 | `#?(:clj {:inline (fn [report] `(-> ~report :db-before :max-tx long inc))} :cljr ` | KEPT | glue: clj/cljr :inline compiler metadata |
| 169 | `eve/db/ds/db.cljc` | 2473 | `#?(:clj {:inline (fn [db] `(inc (long (:max-eid ~db))))} :cljr {:inline (fn [db]` | KEPT | glue: clj/cljr :inline compiler metadata |
| 170 | `eve/db/ds/db.cljc` | 2477 | `(do #?@(:clj [(defn- ^Boolean tx-id?` | REMOVED | portable tx-id? via =; dropped host identical?/.equals twins |
| 171 | `eve/db/ds/db.cljc` | 2507 | `#?(:clje (defn- tx-id?` | REMOVED | portable tx-id? via =; dropped F-CLJE twin |
| 172 | `eve/db/ds/db.cljc` | 2514 | `(do #?@(:clj [(defn-  ^Boolean tempid? [x] (or` | REMOVED | unhinted clojure.core defn-; predicate body was identical on every host |
| 173 | `eve/db/ds/db.cljc` | 2530 | `#?(:clje (defn- tempid? [x] (or` | REMOVED | unhinted clojure.core defn-; dropped F-CLJE-PREDS twin (same body) |
| 174 | `eve/db/ds/db.cljc` | 2654 | `(do #?@(:clj [(defn-  ^Boolean tuple-has-tempids? [db a vs] (and` | REMOVED | unhinted clojure.core defn-; predicate body was identical on every host |
| 175 | `eve/db/ds/db.cljc` | 2714 | `#?(:clje (defn- tuple-has-tempids? [db a vs] (and` | REMOVED | unhinted clojure.core defn-; dropped F-CLJE-PREDS twin (same body) |
| 176 | `eve/db/ds/db.cljc` | 2891 | `(do #?@(:clj [(declare transact-tx-data-impl)] :cljr [(declare transact-tx-data-` | KEPT | glue: forward-declare twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 177 | `eve/db/ds/db.cljc` | 2895 | `#?(:clje (declare transact-tx-data-impl))` | KEPT | glue: forward-declare twins; clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 178 | `eve/db/ds/impl/entity.cljc` | 15 | `#?@(:cljs [[cljs.core :as c]]` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 179 | `eve/db/ds/impl/entity.cljc` | 22 | `#?(:lpy [eve.deftype :refer [deftype]])` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 180 | `eve/db/ds/impl/entity.cljc` | 24 | `#?(:clje [eve.host :refer [volatile!]])` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 181 | `eve/db/ds/impl/entity.cljc` | 54 | `#?(:bb (reduce #(conj %1 (entity db (:v %2))) [] datoms)` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 182 | `eve/db/ds/impl/entity.cljc` | 66 | `#?(:bb (reduce #(conj %1 (entity db (:e %2))) [] datoms)` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 183 | `eve/db/ds/impl/entity.cljc` | 70 | `#?(:cljs` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 184 | `eve/db/ds/impl/entity.cljc` | 74 | `#?(:cljs` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 185 | `eve/db/ds/impl/entity.cljc` | 82 | `#?(:cljs` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 186 | `eve/db/ds/impl/entity.cljc` | 87 | `#?(:cljs` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 187 | `eve/db/ds/impl/entity.cljc` | 93 | `#?@(:cljs` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 188 | `eve/db/ds/impl/entity.cljc` | 155 | `#?(:clje (vector k)` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 189 | `eve/db/ds/impl/entity.cljc` | 184 | `#?(:clje (vector k)` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 190 | `eve/db/ds/impl/entity.cljc` | 226 | `#?(:clje (vector k)` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 191 | `eve/db/ds/impl/entity.cljc` | 291 | `#?(:cljs` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 192 | `eve/db/ds/impl/entity.cljc` | 299 | `#?(:clj` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 193 | `eve/db/ds/impl/entity.cljc` | 319 | `(#?(:clj System/identityHashCode :cljr System.Runtime.CompilerServices.RuntimeHe` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 194 | `eve/db/ds/impl/entity.cljc` | 344 | `#?(:cljs` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 195 | `eve/db/ds/impl/entity.cljc` | 408 | `#?(:cljs (goog/exportSymbol "eve.db.ds.impl.entity.Entity" Entity))` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 196 | `eve/db/ds/lru.cljc` | 14 | `#?(:lpy  (:require [eve.deftype :refer [deftype]])` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 197 | `eve/db/ds/lru.cljc` | 20 | `#?(:cljs` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 198 | `eve/db/ds/lru.cljc` | 163 | `#?(:lpy` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 199 | `eve/db/ds/lru.cljc` | 185 | `(let [*impl #?(:clje (atom (lru limit))` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 200 | `eve/db/ds/lru.cljc` | 193 | `(do #?(:clje (swap! *impl assoc key cached)` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 201 | `eve/db/ds/lru.cljc` | 200 | `#?(:clje (swap! *impl assoc key computed)` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 202 | `eve/db/ds/parser.cljc` | 3 | `#?(:cljs (:require-macros [eve.db.ds.parser :refer [deftrecord]]))` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 203 | `eve/db/ds/parser.cljc` | 18 | `#?(:jank (defmacro deftrecord` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 204 | `eve/db/ds/parser.cljc` | 126 | `#?(:clje (defmacro deftrecord` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 205 | `eve/db/ds/pull_api.cljc` | 12 | `#?(:clje [eve.host :refer [transient persistent! conj! assoc! volatile!]]))` | KEPT | glue: clje eve.host :refer for host primitives |
| 206 | `eve/db/ds/pull_api.cljc` | 13 | `#?(:clj` | KEPT | glue: host :import of library or JVM types |
| 207 | `eve/db/ds/pull_api.cljc` | 46 | `(defn- first-seq [#?(:bb xs :clj ^ISeq xs :cljs ^seq xs :clje xs :cljr xs :lpy x` | REMOVED | collapsed to portable clojure.core first/next/cons; host type hints dropped |
| 208 | `eve/db/ds/pull_api.cljc` | 49 | `#?(:bb (first xs) :clj (first xs) :cljr (first xs) :cljs (-first xs) :clje (firs` | REMOVED | collapsed to portable clojure.core first/next/cons; host type hints dropped |
| 209 | `eve/db/ds/pull_api.cljc` | 51 | `(defn- next-seq [#?(:bb xs :clj ^ISeq xs :cljs ^seq xs :clje xs :cljr xs :lpy xs` | REMOVED | collapsed to portable clojure.core first/next/cons; host type hints dropped |
| 210 | `eve/db/ds/pull_api.cljc` | 54 | `#?(:bb (next xs) :clj (next xs) :cljr (next xs) :cljs (-next xs) :clje (next xs)` | REMOVED | collapsed to portable clojure.core first/next/cons; host type hints dropped |
| 211 | `eve/db/ds/pull_api.cljc` | 56 | `(defn- conj-seq [#?(:bb xs :clj ^ISeq xs :cljs ^seq xs :clje xs :cljr xs :lpy xs` | REMOVED | collapsed to portable clojure.core first/next/cons; host type hints dropped |
| 212 | `eve/db/ds/pull_api.cljc` | 59 | `#?(:bb (cons x xs) :clj (conj xs x) :cljr (conj xs x) :cljs (-conj xs x) :clje (` | REMOVED | collapsed to portable clojure.core first/next/cons; host type hints dropped |
| 213 | `eve/db/ds/pull_api.cljc` | 88 | `#?(:lpy (-merge [this result] (throw (ex-info "ResultFrame does not implement -m` | KEPT | glue: basilisp deftype requires every protocol method in the class body; stub throws like a missing Clojure impl |
| 214 | `eve/db/ds/pull_api.cljc` | 89 | `#?(:lpy (-run [this context] (throw (ex-info "ResultFrame does not implement -ru` | KEPT | glue: basilisp deftype requires every protocol method in the class body; stub throws like a missing Clojure impl |
| 215 | `eve/db/ds/pull_api.cljc` | 93 | `(defrecord MultivalAttrFrame [acc #?@(:clj [^PullAttr attr] :cljr [^PullAttr att` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 216 | `eve/db/ds/pull_api.cljc` | 95 | `#?(:lpy (-merge [this result] (throw (ex-info "MultivalAttrFrame does not implem` | KEPT | glue: basilisp deftype requires every protocol method in the class body; stub throws like a missing Clojure impl |
| 217 | `eve/db/ds/pull_api.cljc` | 100 | `:let [#?@(:jank [^db/Datom datom] :clj [^Datom datom] :cljr [^Datom datom] :cljs` | REMOVED | dropped Datom/DB type hints; field and binding reads are unhinted |
| 218 | `eve/db/ds/pull_api.cljc` | 108 | `(let [#?@(:jank [^db/Datom datom] :clj [^Datom datom] :cljr [^Datom datom] :cljs` | REMOVED | dropped Datom/DB type hints; field and binding reads are unhinted |
| 219 | `eve/db/ds/pull_api.cljc` | 119 | `(defrecord MultivalRefAttrFrame [seen recursion-limits acc pattern #?@(:clj [^Pu` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 220 | `eve/db/ds/pull_api.cljc` | 132 | `:let [#?@(:jank [^db/Datom datom] :clj [^Datom datom] :cljr [^Datom datom] :cljs` | REMOVED | dropped Datom/DB type hints; field and binding reads are unhinted |
| 221 | `eve/db/ds/pull_api.cljc` | 140 | `(let [#?@(:jank [^db/Datom datom] :clj [^Datom datom] :cljr [^Datom datom] :cljs` | REMOVED | dropped Datom/DB type hints; field and binding reads are unhinted |
| 222 | `eve/db/ds/pull_api.cljc` | 153 | `(defrecord AttrsFrame [seen recursion-limits acc #?@(:clj [^PullPattern pattern]` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 223 | `eve/db/ds/pull_api.cljc` | 153 | `(defrecord AttrsFrame [seen recursion-limits acc #?@(:clj [^PullPattern pattern]` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 224 | `eve/db/ds/pull_api.cljc` | 179 | `:let [#?@(:jank [^db/Datom datom] :clj [^Datom datom] :cljr [^Datom datom] :cljs` | REMOVED | dropped Datom/DB type hints; field and binding reads are unhinted |
| 225 | `eve/db/ds/pull_api.cljc` | 204 | `(and datom-ahead? (some? (#?(:clj .-default :cljr .-default :cljs :default :clje` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 226 | `eve/db/ds/pull_api.cljc` | 205 | `(recur (assoc! acc (.-as attr) (#?(:clj .-default :cljr .-default :cljs :default` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 227 | `eve/db/ds/pull_api.cljc` | 236 | `(defrecord ReverseAttrsFrame [seen recursion-limits acc pattern #?@(:clj [^PullA` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 228 | `eve/db/ds/pull_api.cljc` | 258 | `datoms (if (instance? #?(:jank db/DB :clj DB :cljr DB :cljs db/DB :clje db/DB :l` | KEPT | data: pull index access branches on instance? DB; data-path type test, not loader glue |
| 229 | `eve/db/ds/pull_api.cljc` | 259 | `(set/slice (.-avet #?@(:jank [^db/DB db] :clj [^DB db] :cljr [^DB db] :cljs [^db` | REMOVED | dropped Datom/DB type hints; field and binding reads are unhinted |
| 230 | `eve/db/ds/pull_api.cljc` | 264 | `(and (empty? datoms) (some? (#?(:clj .-default :cljr .-default :cljs :default :c` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 231 | `eve/db/ds/pull_api.cljc` | 265 | `(recur (assoc! acc (.-as attr) (#?(:clj .-default :cljr .-default :cljs :default` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 232 | `eve/db/ds/pull_api.cljc` | 272 | `(ref-frame context seen recursion-limits pattern attr (.-e #?@(:clj [^Datom (fir` | REMOVED | dropped Datom/DB type hints; field and binding reads are unhinted |
| 233 | `eve/db/ds/pull_api.cljc` | 281 | `(defn- auto-expanding? [#?@(:clj [^PullAttr attr] :cljr [^PullAttr attr] :cljs [` | REMOVED | dropped host type hints; field reads use portable (.-pattern)/(.-name)/(.-first-attr) |
| 234 | `eve/db/ds/pull_api.cljc` | 286 | `(.-wildcard? #?(:clj ^PullPattern (.-pattern attr) :cljr ^PullPattern (.-pattern` | REMOVED | dropped host type hints; field reads use portable (.-pattern)/(.-name)/(.-first-attr) |
| 235 | `eve/db/ds/pull_api.cljc` | 288 | `(defn ref-frame [context seen recursion-limits pattern #?@(:clj [^PullAttr attr]` | REMOVED | dropped host type hints; field reads use portable (.-pattern)/(.-name)/(.-first-attr) |
| 236 | `eve/db/ds/pull_api.cljc` | 310 | `(defn attrs-frame [^Context context seen recursion-limits #?@(:clj [^PullPattern` | REMOVED | dropped host type hints; field reads use portable (.-pattern)/(.-name)/(.-first-attr) |
| 237 | `eve/db/ds/pull_api.cljc` | 313 | `(and (.-wildcard? pattern) (instance? #?(:jank db/DB :clj DB :cljr DB :cljs db/D` | KEPT | data: pull wildcard index access branches on instance? DB; data-path type test |
| 238 | `eve/db/ds/pull_api.cljc` | 314 | `(set/slice (.-eavt #?@(:jank [^db/DB db] :clj [^DB db] :cljr [^DB db] :cljs [^db` | REMOVED | dropped Datom/DB type hints; field and binding reads are unhinted |
| 239 | `eve/db/ds/pull_api.cljc` | 322 | `:let [from (.-name #?(:clj ^PullAttr (.-first-attr pattern) :cljr ^PullAttr (.-f` | REMOVED | dropped host type hints; field reads use portable (.-pattern)/(.-name)/(.-first-attr) |
| 240 | `eve/db/ds/pull_api.cljc` | 323 | `to   (.-name #?(:clj ^PullAttr (.-last-attr pattern) :cljr ^PullAttr (.-last-att` | REMOVED | dropped host type hints; field reads use portable (.-pattern)/(.-name)/(.-first-attr) |
| 241 | `eve/db/ds/pull_api.cljc` | 325 | `(instance? #?(:jank db/DB :clj DB :cljr DB :cljs db/DB :clje db/DB :lpy db/DB) d` | KEPT | data: pull index access branches on instance? DB; data-path type test |
| 242 | `eve/db/ds/pull_api.cljc` | 326 | `(set/slice (.-eavt #?@(:jank [^db/DB db] :clj [^DB db] :cljr [^DB db] :cljs [^db` | REMOVED | dropped Datom/DB type hints; field and binding reads are unhinted |
| 243 | `eve/db/ds/pull_api.cljc` | 331 | `(fn [#?@(:jank [^db/Datom d] :clj [^Datom d] :cljr [^Datom d] :cljs [^db/Datom d` | REMOVED | dropped Datom/DB type hints; field and binding reads are unhinted |
| 244 | `eve/db/ds/pull_api.cljc` | 349 | `#?@(:clj [^PullPattern pattern :pattern] :cljr [^PullPattern pattern :pattern] :` | REMOVED | dropped host type hints; field reads use portable (.-pattern)/(.-name)/(.-first-attr) |
| 245 | `eve/db/ds/pull_parser.cljc` | 8 | `#?(:clje [eve.host :refer [volatile!]])))` | KEPT | glue: clje eve.host :refer for host primitives |
| 246 | `eve/db/ds/pull_parser.cljc` | 73 | `#?(:clj (when (namespace sym-or-fn)` | KEPT | glue: clj/cljr requiring-resolve for pull xform symbols |
| 247 | `eve/db/ds/pull_parser.cljc` | 156 | `(defn #?@(:clj [^PullPattern parse-pattern] :cljr [^PullPattern parse-pattern] :` | REMOVED | dropped host type hints on defn name; unhinted clojure.core defn |
| 248 | `eve/db/ds/query.cljc` | 6 | `#?@(:cljs [[cljs.reader :as edn]]` | KEPT | glue: host edn reader libspec (cljs.reader vs clojure.edn) |
| 249 | `eve/db/ds/query.cljc` | 25 | `#?(:clje [eve.host :refer [transient persistent! conj! assoc! volatile! to-array` | KEPT | glue: clje eve.host :refer for host primitives |
| 250 | `eve/db/ds/query.cljc` | 26 | `#?(:clj` | KEPT | glue: host :import of library or JVM types |
| 251 | `eve/db/ds/query.cljc` | 37 | `#?(:clj (set! *warn-on-reflection* true) :cljr (set! *warn-on-reflection* true))` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 252 | `eve/db/ds/query.cljc` | 57 | `#?(:clj` | KEPT | glue: host print-method for Relation |
| 253 | `eve/db/ds/query.cljc` | 121 | `#?(:clj (set! *unchecked-math* true) :cljr (set! *unchecked-math* true))` | KEPT | glue: compiler flag set! *unchecked-math* true |
| 254 | `eve/db/ds/query.cljc` | 131 | `#?(:cljs` | REMOVED | portable tuple-get (da/aget on arrays, get otherwise); dropped cljs-only defn wrapper |
| 255 | `eve/db/ds/query.cljc` | 137 | `#?(:clj` | REMOVED | portable join-tuples via tuple-get and count; dropped host twins |
| 256 | `eve/db/ds/query.cljc` | 199 | `#?(:clje` | REMOVED | portable join-tuples; dropped F-CLJE-JOIN-TUPLES twin |
| 257 | `eve/db/ds/query.cljc` | 213 | `#?(:clj (set! *unchecked-math* false) :cljr (set! *unchecked-math* false))` | KEPT | glue: compiler flag set! *unchecked-math* false |
| 258 | `eve/db/ds/query.cljc` | 293 | `#?(:jank dp/BindIgnore :clj BindIgnore :cljr BindIgnore :cljs dp/BindIgnore :clj` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 259 | `eve/db/ds/query.cljc` | 297 | `#?(:jank dp/BindScalar :clj BindScalar :cljr BindScalar :cljs dp/BindScalar :clj` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 260 | `eve/db/ds/query.cljc` | 301 | `#?(:jank dp/BindColl :clj BindColl :cljr BindColl :cljs dp/BindColl :clje dp/Bin` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 261 | `eve/db/ds/query.cljc` | 314 | `#?(:jank dp/BindTuple :clj BindTuple :cljr BindTuple :cljs dp/BindTuple :clje dp` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 262 | `eve/db/ds/query.cljc` | 329 | `(and (instance? #?(:jank dp/BindScalar :clj BindScalar :cljr BindScalar :cljs dp` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 263 | `eve/db/ds/query.cljc` | 330 | `(instance? #?(:jank dp/SrcVar :clj SrcVar :cljr SrcVar :cljs dp/SrcVar :clje dp/` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 264 | `eve/db/ds/query.cljc` | 332 | `(and (instance? #?(:jank dp/BindScalar :clj BindScalar :cljr BindScalar :cljs dp` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 265 | `eve/db/ds/query.cljc` | 333 | `(instance? #?(:jank dp/RulesVar :clj RulesVar :cljr RulesVar :cljs dp/RulesVar :` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 266 | `eve/db/ds/query.cljc` | 370 | `(let [eid #?(:cljs (tuple-get tuple idx)` | REMOVED | getter-fn uses portable tuple-get |
| 267 | `eve/db/ds/query.cljc` | 381 | `(let [eid #?(:cljs (tuple-get tuple idx)` | REMOVED | getter-fn uses portable tuple-get |
| 268 | `eve/db/ds/query.cljc` | 396 | `#?(:cljs (tuple-get tuple idx)` | REMOVED | getter-fn uses portable tuple-get |
| 269 | `eve/db/ds/query.cljc` | 412 | `#?(:cljs (tuple-get tuple idx)` | REMOVED | getter-fn uses portable tuple-get |
| 270 | `eve/db/ds/query.cljc` | 425 | `#?(:clje (let [getters (mapv #(getter-fn attrs %) common-attrs)]` | KEPT | data: tuple-key-fn multi-attr join keys; same reader form as vm data #271. Foldable to portable mapv once vm reclassifies #271; not glue |
| 271 | `eve/db/ds/query.cljc` | 434 | `(let [^objects getters-arr #?(:clj (into-array Object common-attrs) :cljr (into-` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 272 | `eve/db/ds/query.cljc` | 441 | `#?(:jank` | KEPT | data: tuple-key-fn multi-attr join keys; same reader form as vm data #271. Foldable to portable mapv once vm reclassifies #271; not glue |
| 273 | `eve/db/ds/query.cljc` | 504 | `(if-some [tuples1 #?(:clj (hash key) :cljr (hash key) :cljs (get hash key) :clje` | REMOVED | hash-join uses portable get; hash-attrs returns a map |
| 274 | `eve/db/ds/query.cljc` | 533 | `(#?(:cljs tuple-get :clj get :cljr get :clje da/aget :lpy get) tuple idx)))))))` | KEPT | open: vm-owned data row; foldable by the portable tuple-get once vm reclassifies it |
| 275 | `eve/db/ds/query.cljc` | 617 | `(#?(:cljs tuple-get :clj get :cljr get :clje da/aget :lpy get) tuple ((:attrs re` | KEPT | open: vm-owned data row; foldable by the portable tuple-get once vm reclassifies it |
| 276 | `eve/db/ds/query.cljc` | 649 | `(if #?(:clj  false :cljr false` | KEPT | glue: cljs issue-262: apply+vector holds onto mutable arg array |
| 277 | `eve/db/ds/query.cljc` | 661 | `(da/aset a i #?(:cljs (tuple-get tuple tuple-idx)` | KEPT | open: vm-owned data row; foldable by the portable tuple-get once vm reclassifies it |
| 278 | `eve/db/ds/query.cljc` | 677 | `(da/aset a i #?(:cljs (tuple-get tuple tuple-idx)` | KEPT | open: vm-owned data row; foldable by the portable tuple-get once vm reclassifies it |
| 279 | `eve/db/ds/query.cljc` | 687 | `#?(:cljs nil` | KEPT | glue: resolve-sym is nil on cljs; host resolve elsewhere |
| 280 | `eve/db/ds/query.cljc` | 1032 | `(fn [#?(:cljs t1` | REMOVED | dropped host :tag metadata on fn arg; unhinted parameter |
| 281 | `eve/db/ds/query.cljc` | 1082 | `#?(:jank dp/Variable :clj Variable :cljr Variable :cljs dp/Variable :clje dp/Var` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 282 | `eve/db/ds/query.cljc` | 1085 | `#?(:jank dp/SrcVar :clj SrcVar :cljr SrcVar :cljs dp/SrcVar :clje dp/SrcVar :lpy` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 283 | `eve/db/ds/query.cljc` | 1088 | `#?(:jank dp/PlainSymbol :clj PlainSymbol :cljr PlainSymbol :cljs dp/PlainSymbol ` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 284 | `eve/db/ds/query.cljc` | 1092 | `#?(:jank dp/Constant :clj Constant :cljr Constant :cljs dp/Constant :clje dp/Con` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 285 | `eve/db/ds/query.cljc` | 1137 | `#?(:jank dp/FindRel :clj FindRel :cljr FindRel :cljs dp/FindRel :clje dp/FindRel` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 286 | `eve/db/ds/query.cljc` | 1143 | `#?(:jank dp/FindColl :clj FindColl :cljr FindColl :cljs dp/FindColl :clje dp/Fin` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 287 | `eve/db/ds/query.cljc` | 1147 | `#?(:jank dp/FindScalar :clj FindScalar :cljr FindScalar :cljs dp/FindScalar :clj` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 288 | `eve/db/ds/query.cljc` | 1151 | `#?(:jank dp/FindTuple :clj FindTuple :cljr FindTuple :cljs dp/FindTuple :clje dp` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 289 | `eve/db/ds/query_v3.cljc` | 15 | `#?(:clj` | KEPT | glue: host :import of library or JVM types |
| 290 | `eve/db/ds/query_v3.cljc` | 62 | `#?(:clj` | KEPT | data: query_v3 fast-map is a java.util.HashMap on clj vs a Clojure map on cljs — query engine data structure, should be clojure.core or Eve collections |
| 291 | `eve/db/ds/query_v3.cljc` | 107 | `#?(:clj` | KEPT | data: query_v3 fast-arr is a java.util.ArrayList on clj vs a cljs array — query engine data structure, should be clojure.core or Eve collections |
| 292 | `eve/db/ds/query_v3.cljc` | 178 | `#?(:clj` | KEPT | data: query_v3 fast-set is a java.util.HashSet on clj — query engine data structure, should be clojure.core or Eve collections |
| 293 | `eve/db/ds/query_v3.cljc` | 274 | `(.write #?(:clj  (.getSimpleName ^Class (class rel)) :cljr (.getSimpleName ^Clas` | KEPT | glue: host class-name writing in query_v3 printer (.getSimpleName vs type) |
| 294 | `eve/db/ds/query_v3.cljc` | 306 | `#?(:clj` | KEPT | glue: host print-method for ArrayRelation |
| 295 | `eve/db/ds/query_v3.cljc` | 345 | `(instance? #?(:jank dp/BindScalar :clj BindScalar :cljr BindScalar :cljs dp/Bind` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 296 | `eve/db/ds/query_v3.cljc` | 347 | `(instance? #?(:jank dp/Variable :clj Variable :cljr Variable :cljs dp/Variable) ` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 297 | `eve/db/ds/query_v3.cljc` | 357 | `#?(:clj` | KEPT | glue: host print-method for CollRelation |
| 298 | `eve/db/ds/query_v3.cljc` | 516 | `#?(:jank dp/BindIgnore :clj BindIgnore :cljr BindIgnore :cljs dp/BindIgnore)` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 299 | `eve/db/ds/query_v3.cljc` | 519 | `#?(:jank dp/BindScalar :clj BindScalar :cljr BindScalar :cljs dp/BindScalar)` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 300 | `eve/db/ds/query_v3.cljc` | 525 | `#?(:jank dp/BindColl :clj BindColl :cljr BindColl :cljs dp/BindColl)` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 301 | `eve/db/ds/query_v3.cljc` | 539 | `#?(:jank dp/BindTuple :clj BindTuple :cljr BindTuple :cljs dp/BindTuple)` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 302 | `eve/db/ds/query_v3.cljc` | 570 | `(and (instance? #?(:jank dp/BindScalar :clj BindScalar :cljr BindScalar :cljs dp` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 303 | `eve/db/ds/query_v3.cljc` | 571 | `(instance? #?(:jank dp/SrcVar :clj SrcVar :cljr SrcVar :cljs dp/SrcVar) (:variab` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 304 | `eve/db/ds/query_v3.cljc` | 597 | `(instance? #?(:jank dp/SrcVar :clj SrcVar :cljr SrcVar :cljs dp/SrcVar) source) ` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 305 | `eve/db/ds/query_v3.cljc` | 598 | `(instance? #?(:jank dp/DefaultSrc :clj DefaultSrc :cljr DefaultSrc :cljs dp/Defa` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 306 | `eve/db/ds/query_v3.cljc` | 610 | `search-pattern (mapv #(when (instance? #?(:jank dp/Constant :clj Constant :cljr ` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 307 | `eve/db/ds/query_v3.cljc` | 634 | `idxs    (->> (map #(when (instance? #?(:jank dp/Constant :clj Constant :cljr Con` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 308 | `eve/db/ds/query_v3.cljc` | 641 | `(into #{} (map :symbol) (dp/collect #(instance? #?(:jank dp/Variable :clj Variab` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 309 | `eve/db/ds/query_v3.cljc` | 650 | `(if (instance? #?(:jank dp/Variable :clj Variable :cljr Variable :cljs dp/Variab` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 310 | `eve/db/ds/query_v3.cljc` | 656 | `#?(:clj (Constant. subs) :cljr (Constant. subs) :cljs (dp/->Constant subs))` | REMOVED | collapsed in units 1–3 (Eve type or branch deletion) |
| 311 | `eve/db/ds/query_v3.cljc` | 758 | `(if (instance? #?(:jank dp/SrcVar :clj SrcVar :cljr SrcVar :cljs dp/SrcVar) sour` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 312 | `eve/db/ds/query_v3.cljc` | 811 | `(instance? #?(:jank dp/Variable :clj Variable :cljr Variable :cljs dp/Variable) ` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 313 | `eve/db/ds/query_v3.cljc` | 814 | `(instance? #?(:jank dp/SrcVar :clj SrcVar :cljr SrcVar :cljs dp/SrcVar) arg)` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 314 | `eve/db/ds/query_v3.cljc` | 819 | `(instance? #?(:jank dp/Constant :clj Constant :cljr Constant :cljs dp/Constant) ` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 315 | `eve/db/ds/query_v3.cljc` | 824 | `(if (instance? #?(:jank dp/PlainSymbol :clj PlainSymbol :cljr PlainSymbol :cljs ` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 316 | `eve/db/ds/query_v3.cljc` | 840 | `:when   (instance? #?(:jank dp/Variable :clj Variable :cljr Variable :cljs dp/Va` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 317 | `eve/db/ds/query_v3.cljc` | 878 | `#?(:jank dp/Pattern :clj Pattern :cljr Pattern :cljs dp/Pattern)` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 318 | `eve/db/ds/query_v3.cljc` | 881 | `#?(:jank dp/Not :clj Not :cljr Not :cljs dp/Not)` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 319 | `eve/db/ds/query_v3.cljc` | 884 | `#?(:jank dp/Or :clj Or :cljr Or :cljs dp/Or)` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 320 | `eve/db/ds/query_v3.cljc` | 887 | `#?(:jank dp/And :clj And :cljr And :cljs dp/And)` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 321 | `eve/db/ds/query_v3.cljc` | 890 | `#?(:jank dp/Predicate :clj Predicate :cljr Predicate :cljs dp/Predicate)` | KEPT | glue: instance? class-name dispatch (imported short name on JVM/CLR vs qualified parser type on other hosts) |
| 322 | `eve/db/ds/serialize.cljc` | 15 | `#?(:clje [eve.host :refer [transient persistent! conj! assoc! volatile! locking]` | KEPT | glue: clje eve.host :refer for host primitives (also listed under threading refers) |
| 323 | `eve/db/ds/serialize.cljc` | 16 | `#?(:cljs (:require-macros [eve.db.ds.serialize :refer [array dict]]))` | KEPT | glue: cljs :require-macros loader wiring; does not touch data, memory, threading, or HTTP |
| 324 | `eve/db/ds/serialize.cljc` | 17 | `#?(:clj` | KEPT | glue: host :import of library or JVM types |
| 325 | `eve/db/ds/serialize.cljc` | 41 | `(def ^:private pos-inf #?(:jank (/ 1.0 0.0) :clj Double/POSITIVE_INFINITY :cljr ` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 326 | `eve/db/ds/serialize.cljc` | 42 | `(def ^:private neg-inf #?(:jank (/ -1.0 0.0) :clj Double/NEGATIVE_INFINITY :cljr` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 327 | `eve/db/ds/serialize.cljc` | 43 | `(def ^:private nan-val #?(:jank (/ 0.0 0.0) :clj Double/NaN :cljr Double/NaN :cl` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 328 | `eve/db/ds/serialize.cljc` | 48 | `#?(:clj` | KEPT | glue: serialize array/dict macro host twins (JS literal vs vector/array-map; F-CLJE-SERIALIZE) |
| 329 | `eve/db/ds/serialize.cljc` | 81 | `#?(:clje` | KEPT | glue: serialize array/dict macro host twins (JS literal vs vector/array-map; F-CLJE-SERIALIZE) |
| 330 | `eve/db/ds/serialize.cljc` | 87 | `#?(:clj` | KEPT | glue: serialize array/dict macro host twins (JS literal vs vector/array-map; F-CLJE-SERIALIZE) |
| 331 | `eve/db/ds/serialize.cljc` | 117 | `#?(:clje` | KEPT | glue: serialize array/dict macro host twins (JS literal vs vector/array-map; F-CLJE-SERIALIZE) |
| 332 | `eve/db/ds/serialize.cljc` | 127 | `#?(:clj  (.get ^java.util.List d (int i)) :cljr (nth d i)` | REMOVED | array-get is portable nth |
| 333 | `eve/db/ds/serialize.cljc` | 133 | `#?(:clj  (.get ^java.util.Map d k) :cljr (get d k)` | REMOVED | dict-get is portable get |
| 334 | `eve/db/ds/serialize.cljc` | 139 | `#?(:clj  (instance? java.util.List a) :cljr (instance? System.Collections.IList ` | KEPT | glue: host collection type test (java.util.List / IList / JS array / vector) |
| 335 | `eve/db/ds/serialize.cljc` | 145 | `#?(:clj` | KEPT | data: serialize amap builds java.util.ArrayList / JS Array / vectors of datom payload; Eve serialization, not glue. Folding to mapv would change the cljs published format (JS Array vs vector) — design call |
| 336 | `eve/db/ds/serialize.cljc` | 161 | `#?(:clj` | KEPT | data: serialize amap-indexed, same as #335 — design call to fold |
| 337 | `eve/db/ds/serialize.cljc` | 178 | `[#?@(:jank [^db/Datom d1] :clj [^Datom d1] :cljr [^Datom d1] :cljs [^db/Datom d1` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 338 | `eve/db/ds/serialize.cljc` | 178 | `[#?@(:jank [^db/Datom d1] :clj [^Datom d1] :cljr [^Datom d1] :cljs [^db/Datom d1` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 339 | `eve/db/ds/serialize.cljc` | 242 | `#?@(:clj [(or` | KEPT | data: serialize special-cases JVM/CLR big numeric types in datom values |
| 340 | `eve/db/ds/serialize.cljc` | 256 | `#?(:clje false :default (== pos-inf v)) (array marker-inf)` | KEPT | data: serialize encoding of +Inf in datom values (clje has no pos-inf) |
| 341 | `eve/db/ds/serialize.cljc` | 257 | `#?(:clje false :default (== neg-inf v)) (array marker-minus-inf)` | KEPT | data: serialize encoding of -Inf in datom values (clje has no neg-inf) |
| 342 | `eve/db/ds/serialize.cljc` | 258 | `#?(:jank (not (== v v)) :clj (Double/isNaN v) :cljr (Double/IsNaN v) :cljs (js/i` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 343 | `eve/db/ds/serialize.cljc` | 270 | `(fn [_idx #?@(:jank [^db/Datom d] :clj [^Datom d] :cljr [^Datom d] :cljs [^db/Da` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 344 | `eve/db/ds/serialize.cljc` | 277 | `aevt        (amap-indexed (fn [_ #?@(:jank [^db/Datom d] :clj [^Datom d] :cljr [` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 345 | `eve/db/ds/serialize.cljc` | 278 | `avet        (amap-indexed (fn [_ #?@(:jank [^db/Datom d] :clj [^Datom d] :cljr [` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 346 | `eve/db/ds/serialize.cljc` | 282 | `#?@(:clj` | KEPT | data: serialize writes persistent-sorted-set settings into the DB payload; index-structure data |
| 347 | `eve/db/ds/serialize.cljc` | 295 | `#?@(:clj` | KEPT | data: serialize writes persistent-sorted-set settings into the DB payload; index-structure data |
| 348 | `eve/db/ds/serialize.cljc` | 300 | `#?(:jank` | REMOVED | portable serializable via eve.host/locking and eve.host/token; dropped host Object lock twins |
| 349 | `eve/db/ds/serialize.cljc` | 362 | `#?(:clj arrays/into-array :cljr arrays/into-array))` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 350 | `eve/db/ds/serialize.cljc` | 363 | `aevt     (some->> (dict-get from "aevt") (amap #(arrays/aget eavt %)) #?(:clj ar` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 351 | `eve/db/ds/serialize.cljc` | 364 | `avet     (some->> (dict-get from "avet") (amap #(arrays/aget eavt %)) #?(:clj ar` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 352 | `eve/db/ds/storage.cljc` | 21 | `#?(:lpy` | KEPT | glue: basilisp defprotocol cannot take :extend-via-metadata; option omitted on that seat |
| 353 | `eve/db/ds/util.cljc` | 10 | `#?(:clj` | KEPT | glue: host :import of library or JVM types |
| 354 | `eve/db/ds/util.cljc` | 20 | `#?(:clj (defmacro log [& body]` | KEPT | glue: util macro-layer host twins (log/raise/if+/cond+/some-of); clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 355 | `eve/db/ds/util.cljc` | 40 | `#?(:clje (defmacro log [& body]` | KEPT | glue: util macro-layer host twins (log/raise/if+/cond+/some-of); clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 356 | `eve/db/ds/util.cljc` | 49 | `#?(:lpy (defmacro raise [& fragments]` | KEPT | glue: util macro-layer host twins (log/raise/if+/cond+/some-of); clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 357 | `eve/db/ds/util.cljc` | 70 | `#?(:clje (defmacro raise [& fragments]` | KEPT | glue: util macro-layer host twins (log/raise/if+/cond+/some-of); clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 358 | `eve/db/ds/util.cljc` | 75 | `#?(:clj (def ^:private ^:dynamic *if+-syms) :cljr (def ^:private ^:dynamic *if+-` | KEPT | glue: util macro-layer host twins (log/raise/if+/cond+/some-of); clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 359 | `eve/db/ds/util.cljc` | 79 | `#?(:clje (def ^:private ^:dynamic *if+-syms))` | KEPT | glue: util macro-layer host twins (log/raise/if+/cond+/some-of); clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 360 | `eve/db/ds/util.cljc` | 81 | `#?(:clj (defn- if+-rewrite-cond-impl [cond]` | KEPT | glue: util macro-layer host twins (log/raise/if+/cond+/some-of); clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 361 | `eve/db/ds/util.cljc` | 181 | `#?(:clje (defn- if+-rewrite-cond-impl [cond]` | KEPT | glue: util macro-layer host twins (log/raise/if+/cond+/some-of); clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 362 | `eve/db/ds/util.cljc` | 207 | `#?(:clj (defn- if+-rewrite-cond [cond]` | KEPT | glue: util macro-layer host twins (log/raise/if+/cond+/some-of); clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 363 | `eve/db/ds/util.cljc` | 219 | `#?(:clje (defn- if+-rewrite-cond [cond]` | KEPT | glue: util macro-layer host twins (log/raise/if+/cond+/some-of); clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 364 | `eve/db/ds/util.cljc` | 223 | `#?(:clj (defn- flatten-1 [xs]` | KEPT | glue: util macro-layer host twins (log/raise/if+/cond+/some-of); clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 365 | `eve/db/ds/util.cljc` | 235 | `#?(:clje (defn- flatten-1 [xs]` | KEPT | glue: util macro-layer host twins (log/raise/if+/cond+/some-of); clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 366 | `eve/db/ds/util.cljc` | 239 | `#?(:clj (defmacro if+` | KEPT | glue: util macro-layer host twins (log/raise/if+/cond+/some-of); clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 367 | `eve/db/ds/util.cljc` | 384 | `#?(:clje (defmacro if+` | KEPT | glue: util macro-layer host twins (log/raise/if+/cond+/some-of); clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 368 | `eve/db/ds/util.cljc` | 403 | `#?(:clj (defmacro cond+ [& clauses]` | KEPT | glue: util macro-layer host twins (log/raise/if+/cond+/some-of); clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 369 | `eve/db/ds/util.cljc` | 431 | `#?(:clje (defmacro cond+ [& clauses]` | KEPT | glue: util macro-layer host twins (log/raise/if+/cond+/some-of); clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 370 | `eve/db/ds/util.cljc` | 439 | `#?(:clj (defmacro some-of` | KEPT | glue: util macro-layer host twins (log/raise/if+/cond+/some-of); clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 371 | `eve/db/ds/util.cljc` | 459 | `#?(:clje (defmacro some-of` | KEPT | glue: util macro-layer host twins (log/raise/if+/cond+/some-of); clje cannot share a top-level #?@ arm (F-CLJE-DECLARE) |
| 372 | `eve/db/ds/util.cljc` | 468 | `#?(:cljr` | KEPT | data: squuid builds a time-ordered UUID with a host clock and UUID type; needs an Eve clock/uuid facility (design call) rather than glue |
| 373 | `eve/db/ds/util.cljc` | 477 | `#?(:cljs` | KEPT | data: squuid host clock/UUID interop; needs an Eve clock/uuid facility (design call) |
| 374 | `eve/db/ds/util.cljc` | 488 | `(squuid #?(:clj  (System/currentTimeMillis) :cljr (.ToUnixTimeMilliseconds (Syst` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |
| 375 | `eve/db/ds/util.cljc` | 491 | `#?(:clj` | KEPT | data: squuid host clock/UUID interop; needs an Eve clock/uuid facility (design call) |
| 376 | `eve/db/ds/util.cljc` | 523 | `#?(:clj (-> (.getMostSignificantBits ^UUID uuid)` | KEPT | open: remaining host branch (vm owns non-record removal); not classified as glue |

## Tally

- Original: 376
- Removed (snippet no longer in the same file): 42
- Kept: 334 (glue-by-require-macros heuristic: 1; the rest are open for vm)
- Current lexical count at this tree: 342
- Note: kept (334) vs current lexical (342) can differ when a snippet moved between files or was rewritten but still a reader conditional. Current remaining sites:

| file | line now | snippet |
|------|----------|---------|
| `eve/db/ds/built_ins.cljc` | 127 | `(#?(:cljs js/Math.sqrt :clj Math/sqrt :cljr Math/Sqrt) (aggregate-variance coll)` |
| `eve/db/ds/conn.cljc` | 8 | `#?(:lpy [eve.deftype :refer [deftype]])` |
| `eve/db/ds/conn.cljc` | 13 | `#?(:clje [eve.host :refer [volatile! swap! reset! compare-and-set! locking]]))` |
| `eve/db/ds/conn.cljc` | 14 | `#?(:clj` |
| `eve/db/ds/conn.cljc` | 30 | `#?(:jank  (if (identical? (:db @atom) oldv) @atom (assoc @atom :db oldv))` |
| `eve/db/ds/conn.cljc` | 41 | `(if (instance? #?(:jank db/FilteredDB :clj FilteredDB :cljr FilteredDB :cljs db/` |
| `eve/db/ds/conn.cljc` | 45 | `(defn #?@(:jank [^db/DB db-with] :clj [^DB db-with] :cljr [^DB db-with] :cljs [^` |
| `eve/db/ds/conn.cljc` | 53 | `#?(:clj  (instance? clojure.lang.IDeref conn) :cljr (instance? clojure.lang.IDer` |
| `eve/db/ds/conn.cljc` | 87 | `#?(:clj` |
| `eve/db/ds/conn.cljc` | 114 | `#?(:clj` |
| `eve/db/ds/conn.cljc` | 189 | `#?(:clj` |
| `eve/db/ds/core.cljc` | 9 | `#?(:cljs [cljs.reader :as edn]` |
| `eve/db/ds/core.cljc` | 16 | `#?(:clj [eve.db.ds.pprint])` |
| `eve/db/ds/core.cljc` | 24 | `#?(:clj` |
| `eve/db/ds/core.cljc` | 39 | `(def ^{:tag #?(:jank de/Entity :clj Entity :cljr Entity :cljs de/Entity :clje de` |
| `eve/db/ds/core.cljc` | 97 | `(defn #?@(:jank [^db/DB entity-db] :clj [^DB entity-db] :cljr [^DB entity-db] :c` |
| `eve/db/ds/core.cljc` | 99 | `[#?@(:jank [^de/Entity entity] :clj [^Entity entity] :cljr [^Entity entity] :clj` |
| `eve/db/ds/core.cljc` | 103 | `(def ^{:tag #?(:jank de/Entity :clj Entity :cljr Entity :cljs de/Entity :clje de` |
| `eve/db/ds/core.cljc` | 163 | `(defn #?@(:jank [^db/DB empty-db] :clj [^DB empty-db] :cljr [^DB empty-db] :cljs` |
| `eve/db/ds/core.cljc` | 194 | `(def ^{:tag #?(:jank db/Datom :clj Datom :cljr Datom :cljs db/Datom :clje db/Dat` |
| `eve/db/ds/core.cljc` | 207 | `(defn #?@(:jank [^db/DB init-db] :clj [^DB init-db] :cljr [^DB init-db] :cljs [^` |
| `eve/db/ds/core.cljc` | 233 | `(def ^{:tag #?(:jank db/DB :clj DB :cljr DB :cljs db/DB :clje db/DB :lpy db/DB)` |
| `eve/db/ds/core.cljc` | 257 | `(instance? #?(:jank db/FilteredDB :clj FilteredDB :cljr FilteredDB :cljs db/Filt` |
| `eve/db/ds/core.cljc` | 271 | `(let [#?@(:jank [^db/FilteredDB fdb] :clj [^FilteredDB fdb] :cljr [^FilteredDB f` |
| `eve/db/ds/core.cljc` | 277 | `#?(:jank (db/->FilteredDB orig-db #(and (orig-pred %) (pred orig-db %)) (atom 0)` |
| `eve/db/ds/core.cljc` | 281 | `#?(:jank (db/->FilteredDB db #(pred db %) (atom 0))` |
| `eve/db/ds/core.cljc` | 293 | `(def ^{:arglists '([db tx-data]) :tag #?(:jank db/DB :clj DB :cljr DB :cljs db/D` |
| `eve/db/ds/core.cljc` | 297 | `(defn #?@(:jank [^db/DB with-schema] :clj [^DB with-schema] :cljr [^DB with-sche` |
| `eve/db/ds/core.cljc` | 381 | `(defn #?@(:jank [^db/Datom find-datom] :clj [^Datom find-datom] :cljr [^Datom fi` |
| `eve/db/ds/core.cljc` | 488 | `#?(:clj` |
| `eve/db/ds/core.cljc` | 615 | `#?(:cljs` |
| `eve/db/ds/core.cljc` | 628 | `#?(:lpy` |
| `eve/db/ds/core.cljc` | 656 | `(defn #?@(:jank [^db/DB db] :clj [^DB db] :cljr [^DB db] :cljs [^db/DB db] :clje` |
| `eve/db/ds/core.cljc` | 672 | `#?(:cljs` |
| `eve/db/ds/core.cljc` | 714 | `#?(:clje` |
| `eve/db/ds/core.cljc` | 723 | `#?(:cljs` |
| `eve/db/ds/core.cljc` | 760 | `#?(:clj` |
| `eve/db/ds/core.cljc` | 767 | `#?(:clj` |
| `eve/db/ds/core.cljc` | 784 | `#?(:clj` |
| `eve/db/ds/core.cljc` | 791 | `#?(:clj` |
| `eve/db/ds/core.cljc` | 802 | `#?(:clj` |
| `eve/db/ds/core.cljc` | 811 | `#?(:clj` |
| `eve/db/ds/db.cljc` | 3 | `#?(:cljs [goog.array :as garray])` |
| `eve/db/ds/db.cljc` | 6 | `#?(:clj [eve.db.ds.inline :refer [update]])` |
| `eve/db/ds/db.cljc` | 8 | `#?(:clje [eve.host :refer [transient persistent! conj! dissoc! long volatile!]])` |
| `eve/db/ds/db.cljc` | 34 | `#?(:cljs (:require-macros [eve.db.ds.db :refer [case-tree combine-cmp defn+ defc` |
| `eve/db/ds/db.cljc` | 36 | `(:refer-clojure :exclude [seqable? #?(:clj update) deftype]))` |
| `eve/db/ds/db.cljc` | 38 | `#?(:clj (set! *warn-on-reflection* true) :cljr (set! *warn-on-reflection* true))` |
| `eve/db/ds/db.cljc` | 42 | `#?(:cljs` |
| `eve/db/ds/db.cljc` | 65 | `(do #?@(:clj [(defn ^Boolean seqable? [x] (and (not (string? x))` |
| `eve/db/ds/db.cljc` | 66 | `#?@(:clj [(or (seq? x)` |
| `eve/db/ds/db.cljc` | 85 | `#?@(:clj [(or (seq? x)` |
| `eve/db/ds/db.cljc` | 111 | `#?@(:clj [(or (seq? x)` |
| `eve/db/ds/db.cljc` | 134 | `#?(:clje (defn seqable? [x] (and (not (string? x))` |
| `eve/db/ds/db.cljc` | 147 | `(do #?@(:clj [(defn- cljs-env?` |
| `eve/db/ds/db.cljc` | 170 | `#?(:clje (defn- cljs-env?` |
| `eve/db/ds/db.cljc` | 174 | `(do #?@(:clj [(defmacro if-cljs` |
| `eve/db/ds/db.cljc` | 194 | `#?(:clje (defmacro if-cljs` |
| `eve/db/ds/db.cljc` | 198 | `(do #?@(:clj [(defn patch-tag [meta cljs-env?]` |
| `eve/db/ds/db.cljc` | 231 | `#?(:clje (defn patch-tag` |
| `eve/db/ds/db.cljc` | 244 | `(do #?@(:clj [(defmacro defn+` |
| `eve/db/ds/db.cljc` | 328 | `#?(:clje (defmacro defn+` |
| `eve/db/ds/db.cljc` | 354 | `#?(:clje (defn- hash-combine* [seed hash]` |
| `eve/db/ds/db.cljc` | 364 | `#?@(:clj [(clojure.lang.Util/hashCombine x y)] :cljr [(clojure.lang.Util/hashCom` |
| `eve/db/ds/db.cljc` | 369 | `(do #?@(:clj [   (defn- get-sig [method]` |
| `eve/db/ds/db.cljc` | 393 | `#?(:clje (defn- get-sig [& _args] nil))` |
| `eve/db/ds/db.cljc` | 395 | `(do #?@(:clj [   (defn- dedupe-interfaces [deftype-form]` |
| `eve/db/ds/db.cljc` | 413 | `#?(:clje (defn- dedupe-interfaces [& _args] nil))` |
| `eve/db/ds/db.cljc` | 415 | `(do #?@(:clj [(defn- make-record-updatable-cljs [name fields & impls]` |
| `eve/db/ds/db.cljc` | 437 | `#?(:clje (defn- make-record-updatable-cljs [name fields & impls]` |
| `eve/db/ds/db.cljc` | 442 | `(do #?@(:clj [(defn- make-record-updatable-clj [name fields & impls]` |
| `eve/db/ds/db.cljc` | 486 | `#?(:clje (defn- make-record-updatable-clj [name fields & impls]` |
| `eve/db/ds/db.cljc` | 489 | `(do #?@(:clj [(defmacro defrecord-updatable [name fields & impls]` |
| `eve/db/ds/db.cljc` | 503 | `#?(:clje (defmacro defrecord-updatable [name fields & impls]` |
| `eve/db/ds/db.cljc` | 521 | `#?(:clj  (declare hash-datom) :cljr (declare hash-datom)` |
| `eve/db/ds/db.cljc` | 526 | `#?(:clj  (declare equiv-datom) :cljr (declare equiv-datom)` |
| `eve/db/ds/db.cljc` | 531 | `#?(:clj  (declare seq-datom) :cljr (declare seq-datom)` |
| `eve/db/ds/db.cljc` | 536 | `#?(:clj  (declare nth-datom) :cljr (declare nth-datom)` |
| `eve/db/ds/db.cljc` | 541 | `#?(:clj  (declare assoc-datom) :cljr (declare assoc-datom)` |
| `eve/db/ds/db.cljc` | 546 | `#?(:clj  (declare val-at-datom) :cljr (declare val-at-datom)` |
| `eve/db/ds/db.cljc` | 583 | `(empty [d] (throw #?(:cljr (System.NotSupportedException. "empty is not supporte` |
| `eve/db/ds/db.cljc` | 609 | `#?(:cljs (goog/exportSymbol "eve.db.ds.db.Datom" Datom))` |
| `eve/db/ds/db.cljc` | 664 | `#?@(:clj [(throw (IndexOutOfBoundsException.))] :cljr [(throw (System.IndexOutOf` |
| `eve/db/ds/db.cljc` | 688 | `(throw #?(:clje (ex-info (str "invalid key for #eve.db.ds/Datom: " k) {})` |
| `eve/db/ds/db.cljc` | 699 | `#?(:clj` |
| `eve/db/ds/db.cljc` | 712 | `(do #?@(:clj [(defmacro combine-cmp [& comps]` |
| `eve/db/ds/db.cljc` | 760 | `#?(:clje (defmacro combine-cmp [& comps]` |
| `eve/db/ds/db.cljc` | 772 | `(do #?@(:clj [(defn- -case-tree [queries variants]` |
| `eve/db/ds/db.cljc` | 807 | `#?(:clje (defn- -case-tree [queries variants]` |
| `eve/db/ds/db.cljc` | 816 | `(do #?@(:clj [(defmacro case-tree [qs vs]` |
| `eve/db/ds/db.cljc` | 824 | `#?(:clje (defmacro case-tree [qs vs]` |
| `eve/db/ds/db.cljc` | 828 | `#?(:clj` |
| `eve/db/ds/db.cljc` | 840 | `#?(:clj  {:inline (fn [x y] `(identical? (class ~x) (class ~y)))} :cljr {:inline` |
| `eve/db/ds/db.cljc` | 842 | `#?@(:clj [(identical? (class x) (class y))] :cljr [(identical? (class x) (class ` |
| `eve/db/ds/db.cljc` | 847 | `(do #?@(:jank [   (defn class-name` |
| `eve/db/ds/db.cljc` | 868 | `#?(:clje (defn class-name` |
| `eve/db/ds/db.cljc` | 874 | `#?@(:clj [(long (compare (class-name x) (class-name y)))] :cljr [(long (compare ` |
| `eve/db/ds/db.cljc` | 879 | `(do #?@(:jank [(defmacro int-compare [x y]` |
| `eve/db/ds/db.cljc` | 900 | `#?(:clje (defmacro int-compare [x y]` |
| `eve/db/ds/db.cljc` | 905 | `(do #?@(:jank [(defn ihash` |
| `eve/db/ds/db.cljc` | 922 | `#?(:clje (defn ihash` |
| `eve/db/ds/db.cljc` | 926 | `(do #?@(:clj [(declare value-compare)] :cljr [(declare value-compare)]` |
| `eve/db/ds/db.cljc` | 930 | `#?(:clje (declare value-compare))` |
| `eve/db/ds/db.cljc` | 976 | `#?@(:clj  [(instance? Number x)       (compare x y)] :cljr [(number? x)         ` |
| `eve/db/ds/db.cljc` | 978 | `#?@(:clj  [(instance? Comparable x)   (compare x y)] :cljr [(instance? System.IC` |
| `eve/db/ds/db.cljc` | 982 | `#?@(:cljs [(or (number? x) (string? x) (array? x) (true? x) (false? x)) (garray/` |
| `eve/db/ds/db.cljc` | 984 | `(catch #?@(:clj [ClassCastException] :cljr [System.InvalidCastException]` |
| `eve/db/ds/db.cljc` | 993 | `#?(:clj` |
| `eve/db/ds/db.cljc` | 1011 | `(do #?@(:jank [(defmacro defcomp [sym [arg1 arg2] & body]` |
| `eve/db/ds/db.cljc` | 1056 | `#?(:clje (defmacro defcomp [sym [arg1 arg2] & body]` |
| `eve/db/ds/db.cljc` | 1086 | `#?(:clj` |
| `eve/db/ds/db.cljc` | 1094 | `#?@(:clj [(long (compare a1 a2))] :cljr [(long (compare a1 a2))]` |
| `eve/db/ds/db.cljc` | 1161 | `(catch #?@(:clj [ClassCastException] :cljr [System.InvalidCastException]` |
| `eve/db/ds/db.cljc` | 1174 | `(do #?@(:clj [(declare hash-db pr-db)] :cljr [(declare hash-db pr-db)]` |
| `eve/db/ds/db.cljc` | 1181 | `#?(:clje (declare hash-db pr-db))` |
| `eve/db/ds/db.cljc` | 1183 | `(do #?@(:clj [(declare hash-fdb)] :cljr [(declare hash-fdb)]` |
| `eve/db/ds/db.cljc` | 1187 | `#?(:clje (declare hash-fdb))` |
| `eve/db/ds/db.cljc` | 1189 | `(do #?@(:clj [(declare equiv-db)] :cljr [(declare equiv-db)]` |
| `eve/db/ds/db.cljc` | 1193 | `#?(:clje (declare equiv-db))` |
| `eve/db/ds/db.cljc` | 1195 | `(do #?@(:clj [(declare restore-db)] :cljr [(declare restore-db)]` |
| `eve/db/ds/db.cljc` | 1199 | `#?(:clje (declare restore-db))` |
| `eve/db/ds/db.cljc` | 1201 | `(do #?@(:clj [(declare indexing?)] :cljr [(declare indexing?)]` |
| `eve/db/ds/db.cljc` | 1205 | `#?(:clje (declare indexing?))` |
| `eve/db/ds/db.cljc` | 1207 | `#?(:cljs (defn pr-db [db w opts]))` |
| `eve/db/ds/db.cljc` | 1209 | `(do #?@(:clj [(declare resolve-datom)] :cljr [(declare resolve-datom)]` |
| `eve/db/ds/db.cljc` | 1213 | `#?(:clje (declare resolve-datom))` |
| `eve/db/ds/db.cljc` | 1215 | `(do #?@(:clj [(declare components->pattern)] :cljr [(declare components->pattern` |
| `eve/db/ds/db.cljc` | 1219 | `#?(:clje (declare components->pattern))` |
| `eve/db/ds/db.cljc` | 1223 | `(do #?@(:clj [(defmacro validate-attr [attr at]` |
| `eve/db/ds/db.cljc` | 1257 | `#?(:clje (defmacro validate-attr [attr at]` |
| `eve/db/ds/db.cljc` | 1266 | `(do #?@(:clj [(defmacro validate-val [v at]` |
| `eve/db/ds/db.cljc` | 1286 | `#?(:clje (defmacro validate-val [v at]` |
| `eve/db/ds/db.cljc` | 1331 | `#?(:clj` |
| `eve/db/ds/db.cljc` | 1369 | `#?(:lpy (declare -schema -attrs-by -search -datoms -seek-datoms -rseek-datoms -i` |
| `eve/db/ds/db.cljc` | 1375 | `#?(:lpy (declare multival?))` |
| `eve/db/ds/db.cljc` | 1413 | `#?(:bb (seq x) :default x))` |
| `eve/db/ds/db.cljc` | 1490 | `(-schema [db] #?(:clje schema` |
| `eve/db/ds/db.cljc` | 1502 | `pred       #?(:clj  (vpred v)` |
| `eve/db/ds/db.cljc` | 1560 | `#?(:bb  (seq (set/slice (get db index)` |
| `eve/db/ds/db.cljc` | 1624 | `#?(:clje` |
| `eve/db/ds/db.cljc` | 1637 | `#?@(:jank [(and (satisfies? ISearch x)` |
| `eve/db/ds/db.cljc` | 1719 | `(defn #?@(:clj [^DB unfiltered-db] :cljr [^DB unfiltered-db] :cljs [^DB unfilter` |
| `eve/db/ds/db.cljc` | 1983 | `#?(:cljs` |
| `eve/db/ds/db.cljc` | 2008 | `#?(:lpy` |
| `eve/db/ds/db.cljc` | 2018 | `#?(:clj` |
| `eve/db/ds/db.cljc` | 2048 | `(do #?@(:clj [(declare entid-strict)] :cljr [(declare entid-strict)]` |
| `eve/db/ds/db.cljc` | 2052 | `#?(:clje (declare entid-strict))` |
| `eve/db/ds/db.cljc` | 2054 | `(do #?@(:clj [(declare ref?)] :cljr [(declare ref?)]` |
| `eve/db/ds/db.cljc` | 2058 | `#?(:clje (declare ref?))` |
| `eve/db/ds/db.cljc` | 2060 | `(do #?@(:clj [(declare tuple?)] :cljr [(declare tuple?)]` |
| `eve/db/ds/db.cljc` | 2064 | `#?(:clje (declare tuple?))` |
| `eve/db/ds/db.cljc` | 2066 | `(do #?@(:clj [(declare resolve-tuple-refs)] :cljr [(declare resolve-tuple-refs)]` |
| `eve/db/ds/db.cljc` | 2070 | `#?(:clje (declare resolve-tuple-refs))` |
| `eve/db/ds/db.cljc` | 2094 | `cmp     #?(:bb (case index` |
| `eve/db/ds/db.cljc` | 2131 | `datom   #?(:bb (first (set/slice set from to))` |
| `eve/db/ds/db.cljc` | 2217 | `(do #?@(:clj [(defn-  ^Boolean tuple-ref-slot? [db schema-a idx] (cond` |
| `eve/db/ds/db.cljc` | 2236 | `#?(:clje (defn- tuple-ref-slot? [db schema-a idx] (cond` |
| `eve/db/ds/db.cljc` | 2241 | `(do #?@(:clj [(defn-  ^Boolean resolved-eid? [x] (or` |
| `eve/db/ds/db.cljc` | 2253 | `#?(:clje (defn- resolved-eid? [x] (or` |
| `eve/db/ds/db.cljc` | 2305 | `#?@(:cljs [(array? eid) (recur db (array-seq eid))])` |
| `eve/db/ds/db.cljc` | 2405 | `#?(:clj {:inline (fn [report] `(-> ~report :db-before :max-tx long inc))} :cljr ` |
| `eve/db/ds/db.cljc` | 2410 | `#?(:clj {:inline (fn [db] `(inc (long (:max-eid ~db))))} :cljr {:inline (fn [db]` |
| `eve/db/ds/db.cljc` | 2414 | `(do #?@(:clj [(defn- ^Boolean tx-id?` |
| `eve/db/ds/db.cljc` | 2444 | `#?(:clje (defn- tx-id?` |
| `eve/db/ds/db.cljc` | 2451 | `(do #?@(:clj [(defn-  ^Boolean tempid? [x] (or` |
| `eve/db/ds/db.cljc` | 2467 | `#?(:clje (defn- tempid? [x] (or` |
| `eve/db/ds/db.cljc` | 2591 | `(do #?@(:clj [(defn-  ^Boolean tuple-has-tempids? [db a vs] (and` |
| `eve/db/ds/db.cljc` | 2651 | `#?(:clje (defn- tuple-has-tempids? [db a vs] (and` |
| `eve/db/ds/db.cljc` | 2828 | `(do #?@(:clj [(declare transact-tx-data-impl)] :cljr [(declare transact-tx-data-` |
| `eve/db/ds/db.cljc` | 2832 | `#?(:clje (declare transact-tx-data-impl))` |
| `eve/db/ds/eve_record.cljc` | 6 | `#?(:cljs (:require-macros [eve.db.ds.eve-record :refer [def-eve-record]]))` |
| `eve/db/ds/parser.cljc` | 3 | `#?(:cljs (:require-macros [eve.db.ds.parser :refer [deftrecord]]` |
| `eve/db/ds/pull_api.cljc` | 2 | `#?(:cljs (:require-macros [eve.db.ds.eve-record :refer [def-eve-record]]))` |
| `eve/db/ds/pull_api.cljc` | 14 | `#?(:clje [eve.host :refer [transient persistent! conj! assoc! volatile!]]))` |
| `eve/db/ds/pull_api.cljc` | 15 | `#?(:clj` |
| `eve/db/ds/pull_api.cljc` | 48 | `(defn- first-seq [#?(:bb xs :clj ^ISeq xs :cljs ^seq xs :clje xs :cljr xs :lpy x` |
| `eve/db/ds/pull_api.cljc` | 51 | `#?(:bb (first xs) :clj (first xs) :cljr (first xs) :cljs (-first xs) :clje (firs` |
| `eve/db/ds/pull_api.cljc` | 53 | `(defn- next-seq [#?(:bb xs :clj ^ISeq xs :cljs ^seq xs :clje xs :cljr xs :lpy xs` |
| `eve/db/ds/pull_api.cljc` | 56 | `#?(:bb (next xs) :clj (next xs) :cljr (next xs) :cljs (-next xs) :clje (next xs)` |
| `eve/db/ds/pull_api.cljc` | 58 | `(defn- conj-seq [#?(:bb xs :clj ^ISeq xs :cljs ^seq xs :clje xs :cljr xs :lpy xs` |
| `eve/db/ds/pull_api.cljc` | 61 | `#?(:bb (cons x xs) :clj (conj xs x) :cljr (conj xs x) :cljs (-conj xs x) :clje (` |
| `eve/db/ds/pull_api.cljc` | 90 | `#?(:lpy (-merge [this result] (throw (ex-info "ResultFrame does not implement -m` |
| `eve/db/ds/pull_api.cljc` | 91 | `#?(:lpy (-run [this context] (throw (ex-info "ResultFrame does not implement -ru` |
| `eve/db/ds/pull_api.cljc` | 97 | `#?(:lpy (-merge [this result] (throw (ex-info "MultivalAttrFrame does not implem` |
| `eve/db/ds/pull_api.cljc` | 102 | `:let [#?@(:jank [^db/Datom datom] :clj [^Datom datom] :cljr [^Datom datom] :cljs` |
| `eve/db/ds/pull_api.cljc` | 110 | `(let [#?@(:jank [^db/Datom datom] :clj [^Datom datom] :cljr [^Datom datom] :cljs` |
| `eve/db/ds/pull_api.cljc` | 134 | `:let [#?@(:jank [^db/Datom datom] :clj [^Datom datom] :cljr [^Datom datom] :cljs` |
| `eve/db/ds/pull_api.cljc` | 142 | `(let [#?@(:jank [^db/Datom datom] :clj [^Datom datom] :cljr [^Datom datom] :cljs` |
| `eve/db/ds/pull_api.cljc` | 181 | `:let [#?@(:jank [^db/Datom datom] :clj [^Datom datom] :cljr [^Datom datom] :cljs` |
| `eve/db/ds/pull_api.cljc` | 206 | `(and datom-ahead? (some? (#?(:clj .-default :cljr .-default :cljs :default :clje` |
| `eve/db/ds/pull_api.cljc` | 207 | `(recur (assoc! acc (.-as attr) (#?(:clj .-default :cljr .-default :cljs :default` |
| `eve/db/ds/pull_api.cljc` | 260 | `datoms (if (instance? #?(:jank db/DB :clj DB :cljr DB :cljs db/DB :clje db/DB :l` |
| `eve/db/ds/pull_api.cljc` | 261 | `(set/slice (.-avet #?@(:jank [^db/DB db] :clj [^DB db] :cljr [^DB db] :cljs [^db` |
| `eve/db/ds/pull_api.cljc` | 266 | `(and (empty? datoms) (some? (#?(:clj .-default :cljr .-default :cljs :default :c` |
| `eve/db/ds/pull_api.cljc` | 267 | `(recur (assoc! acc (.-as attr) (#?(:clj .-default :cljr .-default :cljs :default` |
| `eve/db/ds/pull_api.cljc` | 274 | `(ref-frame context seen recursion-limits pattern attr (.-e #?@(:clj [^Datom (fir` |
| `eve/db/ds/pull_api.cljc` | 283 | `(defn- auto-expanding? [#?@(:clj [^PullAttr attr] :cljr [^PullAttr attr] :cljs [` |
| `eve/db/ds/pull_api.cljc` | 288 | `(.-wildcard? #?(:clj ^PullPattern (.-pattern attr) :cljr ^PullPattern (.-pattern` |
| `eve/db/ds/pull_api.cljc` | 290 | `(defn ref-frame [context seen recursion-limits pattern #?@(:clj [^PullAttr attr]` |
| `eve/db/ds/pull_api.cljc` | 312 | `(defn attrs-frame [^Context context seen recursion-limits #?@(:clj [^PullPattern` |
| `eve/db/ds/pull_api.cljc` | 315 | `(and (.-wildcard? pattern) (instance? #?(:jank db/DB :clj DB :cljr DB :cljs db/D` |
| `eve/db/ds/pull_api.cljc` | 316 | `(set/slice (.-eavt #?@(:jank [^db/DB db] :clj [^DB db] :cljr [^DB db] :cljs [^db` |
| `eve/db/ds/pull_api.cljc` | 324 | `:let [from (.-name #?(:clj ^PullAttr (.-first-attr pattern) :cljr ^PullAttr (.-f` |
| `eve/db/ds/pull_api.cljc` | 325 | `to   (.-name #?(:clj ^PullAttr (.-last-attr pattern) :cljr ^PullAttr (.-last-att` |
| `eve/db/ds/pull_api.cljc` | 327 | `(instance? #?(:jank db/DB :clj DB :cljr DB :cljs db/DB :clje db/DB :lpy db/DB) d` |
| `eve/db/ds/pull_api.cljc` | 328 | `(set/slice (.-eavt #?@(:jank [^db/DB db] :clj [^DB db] :cljr [^DB db] :cljs [^db` |
| `eve/db/ds/pull_api.cljc` | 333 | `(fn [#?@(:jank [^db/Datom d] :clj [^Datom d] :cljr [^Datom d] :cljs [^db/Datom d` |
| `eve/db/ds/pull_api.cljc` | 351 | `#?@(:clj [^PullPattern pattern :pattern] :cljr [^PullPattern pattern :pattern] :` |
| `eve/db/ds/pull_parser.cljc` | 2 | `#?(:cljs (:require-macros [eve.db.ds.eve-record :refer [def-eve-record]]))` |
| `eve/db/ds/pull_parser.cljc` | 10 | `#?(:clje [eve.host :refer [volatile!]])))` |
| `eve/db/ds/pull_parser.cljc` | 75 | `#?(:clj (when (namespace sym-or-fn)` |
| `eve/db/ds/pull_parser.cljc` | 158 | `(defn #?@(:clj [^PullPattern parse-pattern] :cljr [^PullPattern parse-pattern] :` |
| `eve/db/ds/query.cljc` | 2 | `#?(:cljs (:require-macros [eve.db.ds.eve-record :refer [def-eve-record]]))` |
| `eve/db/ds/query.cljc` | 7 | `#?@(:cljs [[cljs.reader :as edn]]` |
| `eve/db/ds/query.cljc` | 27 | `#?(:clje [eve.host :refer [transient persistent! conj! assoc! volatile! to-array` |
| `eve/db/ds/query.cljc` | 28 | `#?(:clj` |
| `eve/db/ds/query.cljc` | 39 | `#?(:clj (set! *warn-on-reflection* true) :cljr (set! *warn-on-reflection* true))` |
| `eve/db/ds/query.cljc` | 59 | `#?(:clj` |
| `eve/db/ds/query.cljc` | 123 | `#?(:clj (set! *unchecked-math* true) :cljr (set! *unchecked-math* true))` |
| `eve/db/ds/query.cljc` | 133 | `#?(:cljs` |
| `eve/db/ds/query.cljc` | 139 | `#?(:clj` |
| `eve/db/ds/query.cljc` | 201 | `#?(:clje` |
| `eve/db/ds/query.cljc` | 215 | `#?(:clj (set! *unchecked-math* false) :cljr (set! *unchecked-math* false))` |
| `eve/db/ds/query.cljc` | 295 | `#?(:jank dp/BindIgnore :clj BindIgnore :cljr BindIgnore :cljs dp/BindIgnore :clj` |
| `eve/db/ds/query.cljc` | 299 | `#?(:jank dp/BindScalar :clj BindScalar :cljr BindScalar :cljs dp/BindScalar :clj` |
| `eve/db/ds/query.cljc` | 303 | `#?(:jank dp/BindColl :clj BindColl :cljr BindColl :cljs dp/BindColl :clje dp/Bin` |
| `eve/db/ds/query.cljc` | 316 | `#?(:jank dp/BindTuple :clj BindTuple :cljr BindTuple :cljs dp/BindTuple :clje dp` |
| `eve/db/ds/query.cljc` | 331 | `(and (instance? #?(:jank dp/BindScalar :clj BindScalar :cljr BindScalar :cljs dp` |
| `eve/db/ds/query.cljc` | 332 | `(instance? #?(:jank dp/SrcVar :clj SrcVar :cljr SrcVar :cljs dp/SrcVar :clje dp/` |
| `eve/db/ds/query.cljc` | 334 | `(and (instance? #?(:jank dp/BindScalar :clj BindScalar :cljr BindScalar :cljs dp` |
| `eve/db/ds/query.cljc` | 335 | `(instance? #?(:jank dp/RulesVar :clj RulesVar :cljr RulesVar :cljs dp/RulesVar :` |
| `eve/db/ds/query.cljc` | 372 | `(let [eid #?(:cljs (tuple-get tuple idx)` |
| `eve/db/ds/query.cljc` | 383 | `(let [eid #?(:cljs (tuple-get tuple idx)` |
| `eve/db/ds/query.cljc` | 398 | `#?(:cljs (tuple-get tuple idx)` |
| `eve/db/ds/query.cljc` | 414 | `#?(:cljs (tuple-get tuple idx)` |
| `eve/db/ds/query.cljc` | 427 | `#?(:clje (let [getters (mapv #(getter-fn attrs %) common-attrs)]` |
| `eve/db/ds/query.cljc` | 436 | `(let [^objects getters-arr #?(:clj (into-array Object common-attrs) :cljr (into-` |
| `eve/db/ds/query.cljc` | 443 | `#?(:jank` |
| `eve/db/ds/query.cljc` | 506 | `(if-some [tuples1 #?(:clj (hash key) :cljr (hash key) :cljs (get hash key) :clje` |
| `eve/db/ds/query.cljc` | 535 | `(#?(:cljs tuple-get :clj get :cljr get :clje da/aget :lpy get) tuple idx)))))))` |
| `eve/db/ds/query.cljc` | 619 | `(#?(:cljs tuple-get :clj get :cljr get :clje da/aget :lpy get) tuple ((:attrs re` |
| `eve/db/ds/query.cljc` | 651 | `(if #?(:clj  false :cljr false` |
| `eve/db/ds/query.cljc` | 663 | `(da/aset a i #?(:cljs (tuple-get tuple tuple-idx)` |
| `eve/db/ds/query.cljc` | 679 | `(da/aset a i #?(:cljs (tuple-get tuple tuple-idx)` |
| `eve/db/ds/query.cljc` | 689 | `#?(:cljs nil` |
| `eve/db/ds/query.cljc` | 1034 | `(fn [#?(:cljs t1` |
| `eve/db/ds/query.cljc` | 1084 | `#?(:jank dp/Variable :clj Variable :cljr Variable :cljs dp/Variable :clje dp/Var` |
| `eve/db/ds/query.cljc` | 1087 | `#?(:jank dp/SrcVar :clj SrcVar :cljr SrcVar :cljs dp/SrcVar :clje dp/SrcVar :lpy` |
| `eve/db/ds/query.cljc` | 1090 | `#?(:jank dp/PlainSymbol :clj PlainSymbol :cljr PlainSymbol :cljs dp/PlainSymbol ` |
| `eve/db/ds/query.cljc` | 1094 | `#?(:jank dp/Constant :clj Constant :cljr Constant :cljs dp/Constant :clje dp/Con` |
| `eve/db/ds/query.cljc` | 1139 | `#?(:jank dp/FindRel :clj FindRel :cljr FindRel :cljs dp/FindRel :clje dp/FindRel` |
| `eve/db/ds/query.cljc` | 1145 | `#?(:jank dp/FindColl :clj FindColl :cljr FindColl :cljs dp/FindColl :clje dp/Fin` |
| `eve/db/ds/query.cljc` | 1149 | `#?(:jank dp/FindScalar :clj FindScalar :cljr FindScalar :cljs dp/FindScalar :clj` |
| `eve/db/ds/query.cljc` | 1153 | `#?(:jank dp/FindTuple :clj FindTuple :cljr FindTuple :cljs dp/FindTuple :clje dp` |
| `eve/db/ds/query_v3.cljc` | 17 | `#?(:clj` |
| `eve/db/ds/query_v3.cljc` | 64 | `#?(:clj` |
| `eve/db/ds/query_v3.cljc` | 109 | `#?(:clj` |
| `eve/db/ds/query_v3.cljc` | 180 | `#?(:clj` |
| `eve/db/ds/query_v3.cljc` | 276 | `(.write #?(:clj  (.getSimpleName ^Class (class rel)) :cljr (.getSimpleName ^Clas` |
| `eve/db/ds/query_v3.cljc` | 311 | `#?(:clj` |
| `eve/db/ds/query_v3.cljc` | 359 | `(instance? #?(:jank dp/BindScalar :clj BindScalar :cljr BindScalar :cljs dp/Bind` |
| `eve/db/ds/query_v3.cljc` | 361 | `(instance? #?(:jank dp/Variable :clj Variable :cljr Variable :cljs dp/Variable) ` |
| `eve/db/ds/query_v3.cljc` | 371 | `#?(:clj` |
| `eve/db/ds/query_v3.cljc` | 533 | `#?(:jank dp/BindIgnore :clj BindIgnore :cljr BindIgnore :cljs dp/BindIgnore)` |
| `eve/db/ds/query_v3.cljc` | 536 | `#?(:jank dp/BindScalar :clj BindScalar :cljr BindScalar :cljs dp/BindScalar)` |
| `eve/db/ds/query_v3.cljc` | 542 | `#?(:jank dp/BindColl :clj BindColl :cljr BindColl :cljs dp/BindColl)` |
| `eve/db/ds/query_v3.cljc` | 556 | `#?(:jank dp/BindTuple :clj BindTuple :cljr BindTuple :cljs dp/BindTuple)` |
| `eve/db/ds/query_v3.cljc` | 587 | `(and (instance? #?(:jank dp/BindScalar :clj BindScalar :cljr BindScalar :cljs dp` |
| `eve/db/ds/query_v3.cljc` | 588 | `(instance? #?(:jank dp/SrcVar :clj SrcVar :cljr SrcVar :cljs dp/SrcVar) (:variab` |
| `eve/db/ds/query_v3.cljc` | 614 | `(instance? #?(:jank dp/SrcVar :clj SrcVar :cljr SrcVar :cljs dp/SrcVar) source) ` |
| `eve/db/ds/query_v3.cljc` | 615 | `(instance? #?(:jank dp/DefaultSrc :clj DefaultSrc :cljr DefaultSrc :cljs dp/Defa` |
| `eve/db/ds/query_v3.cljc` | 627 | `search-pattern (mapv #(when (instance? #?(:jank dp/Constant :clj Constant :cljr ` |
| `eve/db/ds/query_v3.cljc` | 651 | `idxs    (->> (map #(when (instance? #?(:jank dp/Constant :clj Constant :cljr Con` |
| `eve/db/ds/query_v3.cljc` | 658 | `(into #{} (map :symbol) (dp/collect #(instance? #?(:jank dp/Variable :clj Variab` |
| `eve/db/ds/query_v3.cljc` | 667 | `(if (instance? #?(:jank dp/Variable :clj Variable :cljr Variable :cljs dp/Variab` |
| `eve/db/ds/query_v3.cljc` | 673 | `#?(:clj (->Constant subs) :cljr (->Constant subs) :cljs (dp/->Constant subs))` |
| `eve/db/ds/query_v3.cljc` | 775 | `(if (instance? #?(:jank dp/SrcVar :clj SrcVar :cljr SrcVar :cljs dp/SrcVar) sour` |
| `eve/db/ds/query_v3.cljc` | 828 | `(instance? #?(:jank dp/Variable :clj Variable :cljr Variable :cljs dp/Variable) ` |
| `eve/db/ds/query_v3.cljc` | 831 | `(instance? #?(:jank dp/SrcVar :clj SrcVar :cljr SrcVar :cljs dp/SrcVar) arg)` |
| `eve/db/ds/query_v3.cljc` | 836 | `(instance? #?(:jank dp/Constant :clj Constant :cljr Constant :cljs dp/Constant) ` |
| `eve/db/ds/query_v3.cljc` | 841 | `(if (instance? #?(:jank dp/PlainSymbol :clj PlainSymbol :cljr PlainSymbol :cljs ` |
| `eve/db/ds/query_v3.cljc` | 857 | `:when   (instance? #?(:jank dp/Variable :clj Variable :cljr Variable :cljs dp/Va` |
| `eve/db/ds/query_v3.cljc` | 895 | `#?(:jank dp/Pattern :clj Pattern :cljr Pattern :cljs dp/Pattern)` |
| `eve/db/ds/query_v3.cljc` | 898 | `#?(:jank dp/Not :clj Not :cljr Not :cljs dp/Not)` |
| `eve/db/ds/query_v3.cljc` | 901 | `#?(:jank dp/Or :clj Or :cljr Or :cljs dp/Or)` |
| `eve/db/ds/query_v3.cljc` | 904 | `#?(:jank dp/And :clj And :cljr And :cljs dp/And)` |
| `eve/db/ds/query_v3.cljc` | 907 | `#?(:jank dp/Predicate :clj Predicate :cljr Predicate :cljs dp/Predicate)` |
| `eve/db/ds/serialize.cljc` | 15 | `#?(:clje [eve.host :refer [transient persistent! conj! assoc! volatile! locking]` |
| `eve/db/ds/serialize.cljc` | 16 | `#?(:cljs (:require-macros [eve.db.ds.serialize :refer [array dict]]))` |
| `eve/db/ds/serialize.cljc` | 17 | `#?(:clj` |
| `eve/db/ds/serialize.cljc` | 41 | `(def ^:private pos-inf #?(:jank (/ 1.0 0.0) :clj Double/POSITIVE_INFINITY :cljr ` |
| `eve/db/ds/serialize.cljc` | 42 | `(def ^:private neg-inf #?(:jank (/ -1.0 0.0) :clj Double/NEGATIVE_INFINITY :cljr` |
| `eve/db/ds/serialize.cljc` | 43 | `(def ^:private nan-val #?(:jank (/ 0.0 0.0) :clj Double/NaN :cljr Double/NaN :cl` |
| `eve/db/ds/serialize.cljc` | 48 | `#?(:clj` |
| `eve/db/ds/serialize.cljc` | 81 | `#?(:clje` |
| `eve/db/ds/serialize.cljc` | 87 | `#?(:clj` |
| `eve/db/ds/serialize.cljc` | 117 | `#?(:clje` |
| `eve/db/ds/serialize.cljc` | 127 | `#?(:clj  (.get ^java.util.List d (int i)) :cljr (nth d i)` |
| `eve/db/ds/serialize.cljc` | 133 | `#?(:clj  (.get ^java.util.Map d k) :cljr (get d k)` |
| `eve/db/ds/serialize.cljc` | 139 | `#?(:clj  (instance? java.util.List a) :cljr (instance? System.Collections.IList ` |
| `eve/db/ds/serialize.cljc` | 145 | `#?(:clj` |
| `eve/db/ds/serialize.cljc` | 161 | `#?(:clj` |
| `eve/db/ds/serialize.cljc` | 178 | `[#?@(:jank [^db/Datom d1] :clj [^Datom d1] :cljr [^Datom d1] :cljs [^db/Datom d1` |
| `eve/db/ds/serialize.cljc` | 178 | `[#?@(:jank [^db/Datom d1] :clj [^Datom d1] :cljr [^Datom d1] :cljs [^db/Datom d1` |
| `eve/db/ds/serialize.cljc` | 242 | `#?@(:clj [(or` |
| `eve/db/ds/serialize.cljc` | 256 | `#?(:clje false :default (== pos-inf v)) (array marker-inf)` |
| `eve/db/ds/serialize.cljc` | 257 | `#?(:clje false :default (== neg-inf v)) (array marker-minus-inf)` |
| `eve/db/ds/serialize.cljc` | 258 | `#?(:jank (not (== v v)) :clj (Double/isNaN v) :cljr (Double/IsNaN v) :cljs (js/i` |
| `eve/db/ds/serialize.cljc` | 270 | `(fn [_idx #?@(:jank [^db/Datom d] :clj [^Datom d] :cljr [^Datom d] :cljs [^db/Da` |
| `eve/db/ds/serialize.cljc` | 277 | `aevt        (amap-indexed (fn [_ #?@(:jank [^db/Datom d] :clj [^Datom d] :cljr [` |
| `eve/db/ds/serialize.cljc` | 278 | `avet        (amap-indexed (fn [_ #?@(:jank [^db/Datom d] :clj [^Datom d] :cljr [` |
| `eve/db/ds/serialize.cljc` | 282 | `#?@(:clj` |
| `eve/db/ds/serialize.cljc` | 295 | `#?@(:clj` |
| `eve/db/ds/serialize.cljc` | 300 | `#?(:jank` |
| `eve/db/ds/serialize.cljc` | 362 | `#?(:clj arrays/into-array :cljr arrays/into-array))` |
| `eve/db/ds/serialize.cljc` | 363 | `aevt     (some->> (dict-get from "aevt") (amap #(arrays/aget eavt %)) #?(:clj ar` |
| `eve/db/ds/serialize.cljc` | 364 | `avet     (some->> (dict-get from "avet") (amap #(arrays/aget eavt %)) #?(:clj ar` |
| `eve/db/ds/storage.cljc` | 21 | `#?(:lpy` |
| `eve/db/ds/util.cljc` | 10 | `#?(:clj` |
| `eve/db/ds/util.cljc` | 20 | `#?(:clj (defmacro log [& body]` |
| `eve/db/ds/util.cljc` | 40 | `#?(:clje (defmacro log [& body]` |
| `eve/db/ds/util.cljc` | 49 | `#?(:lpy (defmacro raise [& fragments]` |
| `eve/db/ds/util.cljc` | 70 | `#?(:clje (defmacro raise [& fragments]` |
| `eve/db/ds/util.cljc` | 75 | `#?(:clj (def ^:private ^:dynamic *if+-syms) :cljr (def ^:private ^:dynamic *if+-` |
| `eve/db/ds/util.cljc` | 79 | `#?(:clje (def ^:private ^:dynamic *if+-syms))` |
| `eve/db/ds/util.cljc` | 81 | `#?(:clj (defn- if+-rewrite-cond-impl [cond]` |
| `eve/db/ds/util.cljc` | 181 | `#?(:clje (defn- if+-rewrite-cond-impl [cond]` |
| `eve/db/ds/util.cljc` | 207 | `#?(:clj (defn- if+-rewrite-cond [cond]` |
| `eve/db/ds/util.cljc` | 219 | `#?(:clje (defn- if+-rewrite-cond [cond]` |
| `eve/db/ds/util.cljc` | 223 | `#?(:clj (defn- flatten-1 [xs]` |
| `eve/db/ds/util.cljc` | 235 | `#?(:clje (defn- flatten-1 [xs]` |
| `eve/db/ds/util.cljc` | 239 | `#?(:clj (defmacro if+` |
| `eve/db/ds/util.cljc` | 384 | `#?(:clje (defmacro if+` |
| `eve/db/ds/util.cljc` | 403 | `#?(:clj (defmacro cond+ [& clauses]` |
| `eve/db/ds/util.cljc` | 431 | `#?(:clje (defmacro cond+ [& clauses]` |
| `eve/db/ds/util.cljc` | 439 | `#?(:clj (defmacro some-of` |
| `eve/db/ds/util.cljc` | 459 | `#?(:clje (defmacro some-of` |
| `eve/db/ds/util.cljc` | 468 | `#?(:cljr` |
| `eve/db/ds/util.cljc` | 477 | `#?(:cljs` |
| `eve/db/ds/util.cljc` | 488 | `(squuid #?(:clj  (System/currentTimeMillis) :cljr (.ToUnixTimeMilliseconds (Syst` |
| `eve/db/ds/util.cljc` | 491 | `#?(:clj` |
| `eve/db/ds/util.cljc` | 523 | `#?(:clj (-> (.getMostSignificantBits ^UUID uuid)` |

## Data / memory / threading / HTTP check on kept branches

Not yet certified. Many kept rows are still open portable branches (host I/O of exceptions, type hints, seq interop, print-method, `:bb`/`:lpy` arms). Unit 3 only converted records. vm is assigned remaining non-record branch removal. No RESULT #56 until only glue remains, each with a reason, and none touch data, memory, threading, or HTTP.


## Final close-out (2026-11-11, last-92 unit)

Supersedes the Tally above and the 'Not yet certified' check below it.

- Original branches: 376. Final lexical count at this tree: **73** (`scripts/one_eve_counts.py`: host-branches=73, plain-type-sites=0). (An interim count of 63 was reported during the close-out; it rested on the defrecord-updatable deletion corrected below — the corrected final count is 73.)
- Every original row's final status: **REMOVED** (313) or **GLUE** (the 63 rows below, each with its reason). No surviving branch touches data, memory, threading, or HTTP.
- This unit (88 -> 73, from the 92 counted at 84dd8ae): squuid routed through Eve's `eve.uuid` (4); combine-cmp / case-tree / `:inline`-metadata folds (9); loader folds (2); query `instance?` class-name folds attempted and REVERTED on measurement (17 restored as glue — `dp/BindIgnore` does not resolve on JVM/CLR). The `defrecord-updatable` cluster (10 branches) was deleted mid-unit on a src/-only usage grep and RESTORED verbatim when full-suite runs showed the upstream test tree exercises it (`test/db.cljc` HashBeef, `test-defrecord-updatable`): it is tested public surface, kept as GLUE. Full detail: ~/workspace/ds-lanes-pickup/last92-REPORT.md (see its correction section).
- Eve-gap rows (kept seams, capability missing in Eve): built_ins.cljc:3 (no portable sqrt in Eve), core.cljc:9 + query.cljc:7 (no Eve EDN reader with *data-readers*), db.cljc:1115 (eve.deftype cljerl backend does not emit clojerl.IAssociative).
- Gates at the close-out tree (default deps): contract gates only-core / lru-portable / transact-realized / serialize-portable PASS on JVM and bb; the 12-namespace suite is at its pre-unit baseline on both hosts (baseline reds unchanged, named in the report).

| file | line | status | reason |
|------|------|--------|--------|
| `src/eve/db/ds/built_ins.cljc` | 3 | GLUE — loader: host math library require | Eve-gap: no portable sqrt in Eve (single math/sqrt call, stddev aggregate) |
| `src/eve/db/ds/conn.cljc` | 17 | GLUE — refer wiring: eve.host fns for cljerl | clojerl's clojure.core lacks volatile!/swap!/reset!/compare-and-set!/locking; Eve's BEAM host supplies them; other hosts get them from clojure.core / SCI completion |
| `src/eve/db/ds/core.cljc` | 9 | GLUE — loader: host EDN reader library | Eve-gap note: no Eve EDN reader with *data-readers*; the read calls themselves are single portable calls |
| `src/eve/db/ds/core.cljc` | 18 | GLUE — loader: bb preload of eve.host-completion | SCI seat needs Eve's host completion loaded before conn expands |
| `src/eve/db/ds/core.cljc` | 23 | GLUE — loader: JVM-only print utility | eve.db.ds.pprint is a .clj file |
| `src/eve/db/ds/core.cljc` | 600 | GLUE — reader-registration wiring (cljs) | cljs.reader registers tag parsers programmatically at load |
| `src/eve/db/ds/core.cljc` | 613 | GLUE — reader-registration wiring (lpy) | basilisp registers data readers by merging into *data-readers* at load |
| `src/eve/db/ds/db.cljc` | 8 | GLUE — loader: JVM-only macro file refer | eve.db.ds.inline is a .clj file; refers `update` on :clj |
| `src/eve/db/ds/db.cljc` | 10 | GLUE — refer wiring: eve.host fns for cljerl | as conn.cljc |
| `src/eve/db/ds/db.cljc` | 40 | GLUE — loader: cljs :require-macros self-refer | db's macros are referred for the cljs build |
| `src/eve/db/ds/db.cljc` | 42 | GLUE — namespace wiring: :refer-clojure :exclude spelling | :clj also excludes `update` because eve.db.ds.inline refers it |
| `src/eve/db/ds/db.cljc` | 44 | GLUE — compiler wiring: set! *warn-on-reflection* | JVM/CLR compiler flag only |
| `src/eve/db/ds/db.cljc` | 48 | GLUE — type wiring: cljs exception-class aliases | js/Error stands in for Exception/IllegalArgumentException/UnsupportedOperationException on the JS host |
| `src/eve/db/ds/db.cljc` | 169 | GLUE — macro wiring: compile-time cljs-env? probe | reads the macro &env; emits no runtime behavior |
| `src/eve/db/ds/db.cljc` | 192 | GLUE — macro wiring: cljerl cljs-env? twin | cljerl has no cljs env; answers false |
| `src/eve/db/ds/db.cljc` | 196 | GLUE — macro wiring: if-cljs codegen | compile-time selection of the declared-stub form |
| `src/eve/db/ds/db.cljc` | 216 | GLUE — macro wiring: cljerl if-cljs twin | same macro, cljerl reader |
| `src/eve/db/ds/db.cljc` | 220 | GLUE — type-hint wiring: patch-tag | rewrites portable tag spellings ('boolean/'number) to seat classes at macro time |
| `src/eve/db/ds/db.cljc` | 253 | GLUE — type-hint wiring: cljerl patch-tag twin | cljerl has neither spelling; unresolvable tags are left alone |
| `src/eve/db/ds/db.cljc` | 266 | GLUE — macro wiring: defn+ | emits the :declared stub + defn per compiler (cljs declared-var handling) |
| `src/eve/db/ds/db.cljc` | 350 | GLUE — macro wiring: cljerl defn+ twin | same macro, cljerl reader |
| `src/eve/db/ds/db.cljc` | 462 | GLUE — export wiring: goog/exportSymbol | JS export of the Datom type name |
| `src/eve/db/ds/db.cljc` | 542 | GLUE — print wiring: Datom print-method | host print-method for #eve.db.ds/Datom on class hosts |
| `src/eve/db/ds/db.cljc` | 684 | GLUE — type wiring: catch class names | ClassCastException / System.InvalidCastException / python TypeError / js Error per host |
| `src/eve/db/ds/db.cljc` | 1115 | GLUE — type wiring: cljerl IAssociative patch for DB | Eve-gap: eve.deftype's cljerl backend does not emit clojerl.IAssociative from the Associative protocol group; other hosts get it from the deftype |
| `src/eve/db/ds/eve_record.cljc` | 6 | GLUE — loader: cljs :require-macros self-refer | the file is deleted when its last types migrate (see record-migration report); the branch goes with it |
| `src/eve/db/ds/parser.cljc` | 3 | GLUE — loader: cljs :require-macros | deftrecord + def-eve-record referred for the cljs build |
| `src/eve/db/ds/pull_api.cljc` | 11 | GLUE — refer wiring: eve.host fns for cljerl | as conn.cljc |
| `src/eve/db/ds/pull_parser.cljc` | 2 | GLUE — loader: cljs :require-macros | eve.record's defrecord referred for the cljs build |
| `src/eve/db/ds/pull_parser.cljc` | 6 | GLUE — loader: cljs eve.record require | loads the slab record runtime on the JS host |
| `src/eve/db/ds/pull_parser.cljc` | 12 | GLUE — refer wiring: eve.host volatile! for cljerl | as conn.cljc |
| `src/eve/db/ds/pull_parser.cljc` | 127 | GLUE — resolution wiring: host var resolution | requiring-resolve exists on clj/cljr; other hosts resolve :xform symbols via the built-ins table |
| `src/eve/db/ds/query.cljc` | 2 | GLUE — loader: cljs :require-macros | def-eve-record referred for the cljs build |
| `src/eve/db/ds/query.cljc` | 7 | GLUE — loader: host EDN reader libspecs | as core.cljc:9 |
| `src/eve/db/ds/query.cljc` | 27 | GLUE — refer wiring: eve.host fns for cljerl | as conn.cljc |
| `src/eve/db/ds/query.cljc` | 28 | GLUE — type wiring: parser-type :import | names eve.deftype classes for instance? on class hosts (JVM/CLR); SCI hosts name them via the dp/ alias — batch-B fold to the alias form was measured impossible on JVM (No such var: dp/BindIgnore) and reverted |
| `src/eve/db/ds/query.cljc` | 37 | GLUE — compiler wiring: set! *warn-on-reflection* | JVM/CLR compiler flag only |
| `src/eve/db/ds/query.cljc` | 57 | GLUE — print wiring: Relation print-method | host print-method for #Relation on class hosts |
| `src/eve/db/ds/query.cljc` | 121 | GLUE — compiler wiring: set! *unchecked-math* true | JVM/CLR compiler flag around sum-rel |
| `src/eve/db/ds/query.cljc` | 155 | GLUE — compiler wiring: set! *unchecked-math* false | restores the flag after sum-rel |
| `src/eve/db/ds/query.cljc` | 235 | GLUE — class-name wiring: instance? type name | eve.deftype class named via (:import) on class hosts, via the dp/ alias on SCI hosts; one Eve type per site (batch-B fold measured impossible on JVM and reverted) |
| `src/eve/db/ds/query.cljc` | 238 | GLUE — class-name wiring: instance? type name | eve.deftype class named via (:import) on class hosts, via the dp/ alias on SCI hosts; one Eve type per site (batch-B fold measured impossible on JVM and reverted) |
| `src/eve/db/ds/query.cljc` | 241 | GLUE — class-name wiring: instance? type name | eve.deftype class named via (:import) on class hosts, via the dp/ alias on SCI hosts; one Eve type per site (batch-B fold measured impossible on JVM and reverted) |
| `src/eve/db/ds/query.cljc` | 253 | GLUE — class-name wiring: instance? type name | eve.deftype class named via (:import) on class hosts, via the dp/ alias on SCI hosts; one Eve type per site (batch-B fold measured impossible on JVM and reverted) |
| `src/eve/db/ds/query.cljc` | 270 | GLUE — class-name wiring: instance? type name | eve.deftype class named via (:import) on class hosts, via the dp/ alias on SCI hosts; one Eve type per site (batch-B fold measured impossible on JVM and reverted) |
| `src/eve/db/ds/query.cljc` | 271 | GLUE — class-name wiring: instance? type name | eve.deftype class named via (:import) on class hosts, via the dp/ alias on SCI hosts; one Eve type per site (batch-B fold measured impossible on JVM and reverted) |
| `src/eve/db/ds/query.cljc` | 273 | GLUE — class-name wiring: instance? type name | eve.deftype class named via (:import) on class hosts, via the dp/ alias on SCI hosts; one Eve type per site (batch-B fold measured impossible on JVM and reverted) |
| `src/eve/db/ds/query.cljc` | 274 | GLUE — class-name wiring: instance? type name | eve.deftype class named via (:import) on class hosts, via the dp/ alias on SCI hosts; one Eve type per site (batch-B fold measured impossible on JVM and reverted) |
| `src/eve/db/ds/query.cljc` | 546 | GLUE — class-name wiring: instance? type name | eve.deftype class named via (:import) on class hosts, via the dp/ alias on SCI hosts; one Eve type per site (batch-B fold measured impossible on JVM and reverted) |
| `src/eve/db/ds/query.cljc` | 936 | GLUE — class-name wiring: instance? type name | eve.deftype class named via (:import) on class hosts, via the dp/ alias on SCI hosts; one Eve type per site (batch-B fold measured impossible on JVM and reverted) |
| `src/eve/db/ds/query.cljc` | 938 | GLUE — class-name wiring: instance? type name | eve.deftype class named via (:import) on class hosts, via the dp/ alias on SCI hosts; one Eve type per site (batch-B fold measured impossible on JVM and reverted) |
| `src/eve/db/ds/query.cljc` | 940 | GLUE — class-name wiring: instance? type name | eve.deftype class named via (:import) on class hosts, via the dp/ alias on SCI hosts; one Eve type per site (batch-B fold measured impossible on JVM and reverted) |
| `src/eve/db/ds/query.cljc` | 943 | GLUE — class-name wiring: instance? type name | eve.deftype class named via (:import) on class hosts, via the dp/ alias on SCI hosts; one Eve type per site (batch-B fold measured impossible on JVM and reverted) |
| `src/eve/db/ds/query.cljc` | 987 | GLUE — class-name wiring: instance? type name | eve.deftype class named via (:import) on class hosts, via the dp/ alias on SCI hosts; one Eve type per site (batch-B fold measured impossible on JVM and reverted) |
| `src/eve/db/ds/query.cljc` | 992 | GLUE — class-name wiring: instance? type name | eve.deftype class named via (:import) on class hosts, via the dp/ alias on SCI hosts; one Eve type per site (batch-B fold measured impossible on JVM and reverted) |
| `src/eve/db/ds/query.cljc` | 995 | GLUE — class-name wiring: instance? type name | eve.deftype class named via (:import) on class hosts, via the dp/ alias on SCI hosts; one Eve type per site (batch-B fold measured impossible on JVM and reverted) |
| `src/eve/db/ds/query.cljc` | 998 | GLUE — class-name wiring: instance? type name | eve.deftype class named via (:import) on class hosts, via the dp/ alias on SCI hosts; one Eve type per site (batch-B fold measured impossible on JVM and reverted) |
| `src/eve/db/ds/serialize.cljc` | 16 | GLUE — refer wiring: eve.host fns for cljerl | as conn.cljc |
| `src/eve/db/ds/serialize.cljc` | 17 | GLUE — type wiring: Datom :import | names the Datom class for type hints on class hosts |
| `src/eve/db/ds/serialize.cljc` | 182 | GLUE — type-hint wiring: per-host hint on the amap fn arg | ^db/Datom / ^Datom / unhinted per host reader |
| `src/eve/db/ds/serialize.cljc` | 183 | GLUE — type-hint wiring: per-host hint on the amap fn arg | same as :182 |
| `src/eve/db/ds/storage.cljc` | 21 | GLUE — protocol wiring: defprotocol option form | basilisp's defprotocol cannot read :extend-via-metadata; the option is absent on that seat |
| `src/eve/db/ds/util.cljc` | 12 | GLUE — refer wiring: eve.host fns for cljerl | as conn.cljc |
