# Remaining 306 host branches — classification for vm

Method: lexical #?(/#?@( in src/ at unit-3 tip 9e9b441 (open-other batches 1–5 and 7 removed 36 branches; 342→306; batches 6 and 8 marked 67 as glue, open-other 216→147). Comments/strings excluded, nested counted once.

**Glue** = loader/require-macros/import/refer/type-hint/instance?-class-name/warn-on-reflection/export wiring that does not itself implement data, memory, threading, or HTTP.

**Data / memory / threading / HTTP** = still touch those areas; vm should target these first.

**Open-other** = remaining host branches (math, host interop, etc.) that are not obviously glue and not in the four forbidden areas by snippet.


## data (39)

- #1 `eve/db/ds/built_ins.cljc:127` — `(#?(:cljs js/Math.sqrt :clj Math/sqrt :cljr Math/Sqrt) (aggregate-variance coll)`
- #20 `eve/db/ds/core.cljc:194` — `(def ^{:tag #?(:jank db/Datom :clj Datom :cljr Datom :cljs db/Datom :clje db/Dat`
- #76 `eve/db/ds/db.cljc:515` — `#?(:clj  (declare hash-datom) :cljr (declare hash-datom)`
- #77 `eve/db/ds/db.cljc:520` — `#?(:clj  (declare equiv-datom) :cljr (declare equiv-datom)`
- #78 `eve/db/ds/db.cljc:525` — `#?(:clj  (declare seq-datom) :cljr (declare seq-datom)`
- #79 `eve/db/ds/db.cljc:530` — `#?(:clj  (declare nth-datom) :cljr (declare nth-datom)`
- #80 `eve/db/ds/db.cljc:535` — `#?(:clj  (declare assoc-datom) :cljr (declare assoc-datom)`
- #81 `eve/db/ds/db.cljc:540` — `#?(:clj  (declare val-at-datom) :cljr (declare val-at-datom)`
- #86 `eve/db/ds/db.cljc:686` — `(throw #?(:clje (ex-info (str "invalid key for #eve.db.ds/Datom: " k) {})`
- #99 `eve/db/ds/db.cljc:872` — `#?@(:clj [(long (compare (class-name x) (class-name y)))] :cljr [(long (compare `
- #100 `eve/db/ds/db.cljc:877` — `(do #?@(:jank [(defmacro int-compare [x y]`
- #101 `eve/db/ds/db.cljc:898` — `#?(:clje (defmacro int-compare [x y]`
- #104 `eve/db/ds/db.cljc:924` — `(do #?@(:clj [(declare value-compare)] :cljr [(declare value-compare)]`
- #105 `eve/db/ds/db.cljc:928` — `#?(:clje (declare value-compare))`
- #106 `eve/db/ds/db.cljc:974` — `#?@(:clj  [(instance? Number x)       (compare x y)] :cljr [(number? x)         `
- #107 `eve/db/ds/db.cljc:976` — `#?@(:clj  [(instance? Comparable x)   (compare x y)] :cljr [(instance? System.IC`
- #114 `eve/db/ds/db.cljc:1092` — `#?@(:clj [(long (compare a1 a2))] :cljr [(long (compare a1 a2))]`
- #127 `eve/db/ds/db.cljc:1207` — `(do #?@(:clj [(declare resolve-datom)] :cljr [(declare resolve-datom)]`
- #128 `eve/db/ds/db.cljc:1211` — `#?(:clje (declare resolve-datom))`
- #136 `eve/db/ds/db.cljc:1367` — `#?(:lpy (declare -schema -attrs-by -search -datoms -seek-datoms -rseek-datoms -i`
- #143 `eve/db/ds/db.cljc:1566` — `#?(:bb  (seq (set/slice (get db index)`
- #160 `eve/db/ds/db.cljc:2179` — `datom   #?(:bb (first (set/slice set from to))`
- #165 `eve/db/ds/db.cljc:2353` — `#?@(:cljs [(array? eid) (recur db (array-seq eid))])`
- #225 `eve/db/ds/pull_api.cljc:204` — `(and datom-ahead? (some? (#?(:clj .-default :cljr .-default :cljs :default :clje`
- #230 `eve/db/ds/pull_api.cljc:264` — `(and (empty? datoms) (some? (#?(:clj .-default :cljr .-default :cljs :default :c`
- #271 `eve/db/ds/query.cljc:434` — `(let [^objects getters-arr #?(:clj (into-array Object common-attrs) :cljr (into-`
- #274 `eve/db/ds/query.cljc:533` — `(#?(:cljs tuple-get :clj get :cljr get :clje da/aget :lpy get) tuple idx)))))))`
- #275 `eve/db/ds/query.cljc:617` — `(#?(:cljs tuple-get :clj get :cljr get :clje da/aget :lpy get) tuple ((:attrs re`
- #277 `eve/db/ds/query.cljc:661` — `(da/aset a i #?(:cljs (tuple-get tuple tuple-idx)`
- #278 `eve/db/ds/query.cljc:677` — `(da/aset a i #?(:cljs (tuple-get tuple tuple-idx)`
- #325 `eve/db/ds/serialize.cljc:41` — `(def ^:private pos-inf #?(:jank (/ 1.0 0.0) :clj Double/POSITIVE_INFINITY :cljr `
- #326 `eve/db/ds/serialize.cljc:42` — `(def ^:private neg-inf #?(:jank (/ -1.0 0.0) :clj Double/NEGATIVE_INFINITY :cljr`
- #327 `eve/db/ds/serialize.cljc:43` — `(def ^:private nan-val #?(:jank (/ 0.0 0.0) :clj Double/NaN :cljr Double/NaN :cl`
- #342 `eve/db/ds/serialize.cljc:258` — `#?(:jank (not (== v v)) :clj (Double/isNaN v) :cljr (Double/IsNaN v) :cljs (js/i`
- #349 `eve/db/ds/serialize.cljc:362` — `#?(:clj arrays/into-array :cljr arrays/into-array))`
- #350 `eve/db/ds/serialize.cljc:363` — `aevt     (some->> (dict-get from "aevt") (amap #(arrays/aget eavt %)) #?(:clj ar`
- #351 `eve/db/ds/serialize.cljc:364` — `avet     (some->> (dict-get from "avet") (amap #(arrays/aget eavt %)) #?(:clj ar`
- #374 `eve/db/ds/util.cljc:488` — `(squuid #?(:clj  (System/currentTimeMillis) :cljr (.ToUnixTimeMilliseconds (Syst`
- #376 `eve/db/ds/util.cljc:523` — `#?(:clj (-> (.getMostSignificantBits ^UUID uuid)`

## memory (3)

- #5 `eve/db/ds/conn.cljc:30` — `#?(:jank  (if (identical? (:db @atom) oldv) @atom (assoc @atom :db oldv))`
- #25 `eve/db/ds/core.cljc:277` — `#?(:jank (db/->FilteredDB orig-db #(and (orig-pred %) (pred orig-db %)) (atom 0)`
- #26 `eve/db/ds/core.cljc:281` — `#?(:jank (db/->FilteredDB db #(pred db %) (atom 0))`

## threading (0 bodies; 2 glue refers)

No remaining branch is a locking/future/thread body. These two are :clje eve.host *refers* (glue), which mention `locking` only as an imported name:

- #3 `eve/db/ds/conn.cljc:13` — `#?(:clje [eve.host :refer [volatile! swap! reset! compare-and-set! locking]]))`
- #322 `eve/db/ds/serialize.cljc:15` — `#?(:clje [eve.host :refer [transient persistent! conj! assoc! volatile! locking]`

**Threading-implementation count for vm: 0.** Treat the two rows as glue.

## http (0)

None of the remaining 334 kept original branches (or 342 current lexical branches) implement HTTP. The three snippet hits below were false positives (`seqable?` / `garray` array compare) and belong under data:

- #51 `eve/db/ds/db.cljc:59` — `(do #?@(:clj [(defn ^Boolean seqable? [x] (and (not (string? x))`
- #55 `eve/db/ds/db.cljc:128` — `#?(:clje (defn seqable? [x] (and (not (string? x))`
- #108 `eve/db/ds/db.cljc:980` — `#?@(:cljs [(or (number? x) (string? x) (array? x) (true? x) (false? x)) (garray/`

**HTTP count for vm: 0.**

## glue (104)

- #2 `eve/db/ds/conn.cljc:8` — `#?(:lpy [eve.deftype :refer [deftype]])`
- #6 `eve/db/ds/conn.cljc:41` — `(if (instance? #?(:jank db/FilteredDB :clj FilteredDB :cljr FilteredDB :cljs db/`
- #7 `eve/db/ds/conn.cljc:45` — `(defn #?@(:jank [^db/DB db-with] :clj [^DB db-with] :cljr [^DB db-with] :cljs [^`
- #8 `eve/db/ds/conn.cljc:53` — `#?(:clj  (instance? clojure.lang.IDeref conn) :cljr (instance? clojure.lang.IDer`
- #12 `eve/db/ds/core.cljc:9` — `#?(:cljs [cljs.reader :as edn]`
- #13 `eve/db/ds/core.cljc:16` — `#?(:clj [eve.db.ds.pprint])`
- #23 `eve/db/ds/core.cljc:257` — `(instance? #?(:jank db/FilteredDB :clj FilteredDB :cljr FilteredDB :cljs db/Filt`
- #24 `eve/db/ds/core.cljc:271` — `(let [#?@(:jank [^db/FilteredDB fdb] :clj [^FilteredDB fdb] :cljr [^FilteredDB f`
- #43 `eve/db/ds/db.cljc:3` — `#?(:cljs [goog.array :as garray])`
- #44 `eve/db/ds/db.cljc:6` — `#?(:clj [eve.db.ds.inline :refer [update]])`
- #45 `eve/db/ds/db.cljc:8` — `#?(:clje [eve.host :refer [transient persistent! conj! dissoc! long volatile!]])`
- #49 `eve/db/ds/db.cljc:32` — `#?(:clj (set! *warn-on-reflection* true) :cljr (set! *warn-on-reflection* true))`
- #84 `eve/db/ds/db.cljc:607` — `#?(:cljs (goog/exportSymbol "eve.db.ds.db.Datom" Datom))`
- #147 `eve/db/ds/db.cljc:1767` — `(defn #?@(:clj [^DB unfiltered-db] :cljr [^DB unfiltered-db] :cljs [^DB unfilter`
- #205 `eve/db/ds/pull_api.cljc:12` — `#?(:clje [eve.host :refer [transient persistent! conj! assoc! volatile!]]))`
- #228 `eve/db/ds/pull_api.cljc:258` — `datoms (if (instance? #?(:jank db/DB :clj DB :cljr DB :cljs db/DB :clje db/DB :l`
- #237 `eve/db/ds/pull_api.cljc:313` — `(and (.-wildcard? pattern) (instance? #?(:jank db/DB :clj DB :cljr DB :cljs db/D`
- #241 `eve/db/ds/pull_api.cljc:325` — `(instance? #?(:jank db/DB :clj DB :cljr DB :cljs db/DB :clje db/DB :lpy db/DB) d`
- #245 `eve/db/ds/pull_parser.cljc:8` — `#?(:clje [eve.host :refer [volatile!]])))`
- #248 `eve/db/ds/query.cljc:6` — `#?@(:cljs [[cljs.reader :as edn]]`
- #249 `eve/db/ds/query.cljc:25` — `#?(:clje [eve.host :refer [transient persistent! conj! assoc! volatile! to-array`
- #251 `eve/db/ds/query.cljc:37` — `#?(:clj (set! *warn-on-reflection* true) :cljr (set! *warn-on-reflection* true))`
- #262 `eve/db/ds/query.cljc:329` — `(and (instance? #?(:jank dp/BindScalar :clj BindScalar :cljr BindScalar :cljs dp`
- #264 `eve/db/ds/query.cljc:332` — `(and (instance? #?(:jank dp/BindScalar :clj BindScalar :cljr BindScalar :cljs dp`
- #295 `eve/db/ds/query_v3.cljc:345` — `(instance? #?(:jank dp/BindScalar :clj BindScalar :cljr BindScalar :cljs dp/Bind`
- #296 `eve/db/ds/query_v3.cljc:347` — `(instance? #?(:jank dp/Variable :clj Variable :cljr Variable :cljs dp/Variable) `
- #302 `eve/db/ds/query_v3.cljc:570` — `(and (instance? #?(:jank dp/BindScalar :clj BindScalar :cljr BindScalar :cljs dp`
- #308 `eve/db/ds/query_v3.cljc:641` — `(into #{} (map :symbol) (dp/collect #(instance? #?(:jank dp/Variable :clj Variab`
- #309 `eve/db/ds/query_v3.cljc:650` — `(if (instance? #?(:jank dp/Variable :clj Variable :cljr Variable :cljs dp/Variab`
- #312 `eve/db/ds/query_v3.cljc:811` — `(instance? #?(:jank dp/Variable :clj Variable :cljr Variable :cljs dp/Variable) `
- #316 `eve/db/ds/query_v3.cljc:840` — `:when   (instance? #?(:jank dp/Variable :clj Variable :cljr Variable :cljs dp/Va`
- #323 `eve/db/ds/serialize.cljc:16` — `#?(:cljs (:require-macros [eve.db.ds.serialize :refer [array dict]]))`
- #337 `eve/db/ds/serialize.cljc:178` — `[#?@(:jank [^db/Datom d1] :clj [^Datom d1] :cljr [^Datom d1] :cljs [^db/Datom d1`
- #338 `eve/db/ds/serialize.cljc:178` — `[#?@(:jank [^db/Datom d1] :clj [^Datom d1] :cljr [^Datom d1] :cljs [^db/Datom d1`
- #343 `eve/db/ds/serialize.cljc:270` — `(fn [_idx #?@(:jank [^db/Datom d] :clj [^Datom d] :cljr [^Datom d] :cljs [^db/Da`
- #344 `eve/db/ds/serialize.cljc:277` — `aevt        (amap-indexed (fn [_ #?@(:jank [^db/Datom d] :clj [^Datom d] :cljr [`
- #345 `eve/db/ds/serialize.cljc:278` — `avet        (amap-indexed (fn [_ #?@(:jank [^db/Datom d] :clj [^Datom d] :cljr [`
- #4 `eve/db/ds/conn.cljc:14` — `#?(:clj`
- #14 `eve/db/ds/core.cljc:24` — `#?(:clj`
- #50 `eve/db/ds/db.cljc:36` — `#?(:cljs`
- #116 `eve/db/ds/db.cljc:1172` — `(do #?@(:clj [(declare hash-db pr-db)] :cljr [(declare hash-db pr-db)]`
- #117 `eve/db/ds/db.cljc:1179` — `#?(:clje (declare hash-db pr-db))`
- #118 `eve/db/ds/db.cljc:1181` — `(do #?@(:clj [(declare hash-fdb)] :cljr [(declare hash-fdb)]`
- #119 `eve/db/ds/db.cljc:1185` — `#?(:clje (declare hash-fdb))`
- #120 `eve/db/ds/db.cljc:1187` — `(do #?@(:clj [(declare equiv-db)] :cljr [(declare equiv-db)]`
- #121 `eve/db/ds/db.cljc:1191` — `#?(:clje (declare equiv-db))`
- #122 `eve/db/ds/db.cljc:1193` — `(do #?@(:clj [(declare restore-db)] :cljr [(declare restore-db)]`
- #123 `eve/db/ds/db.cljc:1197` — `#?(:clje (declare restore-db))`
- #124 `eve/db/ds/db.cljc:1199` — `(do #?@(:clj [(declare indexing?)] :cljr [(declare indexing?)]`
- #125 `eve/db/ds/db.cljc:1203` — `#?(:clje (declare indexing?))`
- #126 `eve/db/ds/db.cljc:1205` — `#?(:cljs (defn pr-db [db w opts]))`
- #129 `eve/db/ds/db.cljc:1213` — `(do #?@(:clj [(declare components->pattern)] :cljr [(declare components->pattern`
- #130 `eve/db/ds/db.cljc:1217` — `#?(:clje (declare components->pattern))`
- #137 `eve/db/ds/db.cljc:1373` — `#?(:lpy (declare multival?))`
- #151 `eve/db/ds/db.cljc:2096` — `(do #?@(:clj [(declare entid-strict)] :cljr [(declare entid-strict)]`
- #152 `eve/db/ds/db.cljc:2100` — `#?(:clje (declare entid-strict))`
- #153 `eve/db/ds/db.cljc:2102` — `(do #?@(:clj [(declare ref?)] :cljr [(declare ref?)]`
- #154 `eve/db/ds/db.cljc:2106` — `#?(:clje (declare ref?))`
- #155 `eve/db/ds/db.cljc:2108` — `(do #?@(:clj [(declare tuple?)] :cljr [(declare tuple?)]`
- #156 `eve/db/ds/db.cljc:2112` — `#?(:clje (declare tuple?))`
- #157 `eve/db/ds/db.cljc:2114` — `(do #?@(:clj [(declare resolve-tuple-refs)] :cljr [(declare resolve-tuple-refs)]`
- #158 `eve/db/ds/db.cljc:2118` — `#?(:clje (declare resolve-tuple-refs))`
- #176 `eve/db/ds/db.cljc:2891` — `(do #?@(:clj [(declare transact-tx-data-impl)] :cljr [(declare transact-tx-data-`
- #177 `eve/db/ds/db.cljc:2895` — `#?(:clje (declare transact-tx-data-impl))`
- #206 `eve/db/ds/pull_api.cljc:13` — `#?(:clj`
- #213 `eve/db/ds/pull_api.cljc:88` — `#?(:lpy (-merge [this result] (throw (ex-info "ResultFrame does not implement -m`
- #214 `eve/db/ds/pull_api.cljc:89` — `#?(:lpy (-run [this context] (throw (ex-info "ResultFrame does not implement -ru`
- #216 `eve/db/ds/pull_api.cljc:95` — `#?(:lpy (-merge [this result] (throw (ex-info "MultivalAttrFrame does not implem`
- #253 `eve/db/ds/query.cljc:121` — `#?(:clj (set! *unchecked-math* true) :cljr (set! *unchecked-math* true))`
- #257 `eve/db/ds/query.cljc:213` — `#?(:clj (set! *unchecked-math* false) :cljr (set! *unchecked-math* false))`
- #324 `eve/db/ds/serialize.cljc:17` — `#?(:clj`
- #353 `eve/db/ds/util.cljc:10` — `#?(:clj`

- #258 `eve/db/ds/query.cljc:293` — `#?(:jank dp/BindIgnore :clj BindIgnore :cljr BindIgnore :cljs dp/BindIgnore :clj`
- #259 `eve/db/ds/query.cljc:297` — `#?(:jank dp/BindScalar :clj BindScalar :cljr BindScalar :cljs dp/BindScalar :clj`
- #260 `eve/db/ds/query.cljc:301` — `#?(:jank dp/BindColl :clj BindColl :cljr BindColl :cljs dp/BindColl :clje dp/Bin`
- #261 `eve/db/ds/query.cljc:314` — `#?(:jank dp/BindTuple :clj BindTuple :cljr BindTuple :cljs dp/BindTuple :clje dp`
- #263 `eve/db/ds/query.cljc:330` — `(instance? #?(:jank dp/SrcVar :clj SrcVar :cljr SrcVar :cljs dp/SrcVar :clje dp/`
- #265 `eve/db/ds/query.cljc:333` — `(instance? #?(:jank dp/RulesVar :clj RulesVar :cljr RulesVar :cljs dp/RulesVar :`
- #281 `eve/db/ds/query.cljc:1082` — `#?(:jank dp/Variable :clj Variable :cljr Variable :cljs dp/Variable :clje dp/Var`
- #282 `eve/db/ds/query.cljc:1085` — `#?(:jank dp/SrcVar :clj SrcVar :cljr SrcVar :cljs dp/SrcVar :clje dp/SrcVar :lpy`
- #283 `eve/db/ds/query.cljc:1088` — `#?(:jank dp/PlainSymbol :clj PlainSymbol :cljr PlainSymbol :cljs dp/PlainSymbol `
- #284 `eve/db/ds/query.cljc:1092` — `#?(:jank dp/Constant :clj Constant :cljr Constant :cljs dp/Constant :clje dp/Con`
- #285 `eve/db/ds/query.cljc:1137` — `#?(:jank dp/FindRel :clj FindRel :cljr FindRel :cljs dp/FindRel :clje dp/FindRel`
- #286 `eve/db/ds/query.cljc:1143` — `#?(:jank dp/FindColl :clj FindColl :cljr FindColl :cljs dp/FindColl :clje dp/Fin`
- #287 `eve/db/ds/query.cljc:1147` — `#?(:jank dp/FindScalar :clj FindScalar :cljr FindScalar :cljs dp/FindScalar :clj`
- #288 `eve/db/ds/query.cljc:1151` — `#?(:jank dp/FindTuple :clj FindTuple :cljr FindTuple :cljs dp/FindTuple :clje dp`
- #298 `eve/db/ds/query_v3.cljc:516` — `#?(:jank dp/BindIgnore :clj BindIgnore :cljr BindIgnore :cljs dp/BindIgnore)`
- #299 `eve/db/ds/query_v3.cljc:519` — `#?(:jank dp/BindScalar :clj BindScalar :cljr BindScalar :cljs dp/BindScalar)`
- #300 `eve/db/ds/query_v3.cljc:525` — `#?(:jank dp/BindColl :clj BindColl :cljr BindColl :cljs dp/BindColl)`
- #301 `eve/db/ds/query_v3.cljc:539` — `#?(:jank dp/BindTuple :clj BindTuple :cljr BindTuple :cljs dp/BindTuple)`
- #303 `eve/db/ds/query_v3.cljc:571` — `(instance? #?(:jank dp/SrcVar :clj SrcVar :cljr SrcVar :cljs dp/SrcVar) (:variab`
- #304 `eve/db/ds/query_v3.cljc:597` — `(instance? #?(:jank dp/SrcVar :clj SrcVar :cljr SrcVar :cljs dp/SrcVar) source) `
- #305 `eve/db/ds/query_v3.cljc:598` — `(instance? #?(:jank dp/DefaultSrc :clj DefaultSrc :cljr DefaultSrc :cljs dp/Defa`
- #306 `eve/db/ds/query_v3.cljc:610` — `search-pattern (mapv #(when (instance? #?(:jank dp/Constant :clj Constant :cljr `
- #307 `eve/db/ds/query_v3.cljc:634` — `idxs    (->> (map #(when (instance? #?(:jank dp/Constant :clj Constant :cljr Con`
- #311 `eve/db/ds/query_v3.cljc:758` — `(if (instance? #?(:jank dp/SrcVar :clj SrcVar :cljr SrcVar :cljs dp/SrcVar) sour`
- #313 `eve/db/ds/query_v3.cljc:814` — `(instance? #?(:jank dp/SrcVar :clj SrcVar :cljr SrcVar :cljs dp/SrcVar) arg)`
- #314 `eve/db/ds/query_v3.cljc:819` — `(instance? #?(:jank dp/Constant :clj Constant :cljr Constant :cljs dp/Constant) `
- #315 `eve/db/ds/query_v3.cljc:824` — `(if (instance? #?(:jank dp/PlainSymbol :clj PlainSymbol :cljr PlainSymbol :cljs `
- #317 `eve/db/ds/query_v3.cljc:878` — `#?(:jank dp/Pattern :clj Pattern :cljr Pattern :cljs dp/Pattern)`
- #318 `eve/db/ds/query_v3.cljc:881` — `#?(:jank dp/Not :clj Not :cljr Not :cljs dp/Not)`
- #319 `eve/db/ds/query_v3.cljc:884` — `#?(:jank dp/Or :clj Or :cljr Or :cljs dp/Or)`
- #320 `eve/db/ds/query_v3.cljc:887` — `#?(:jank dp/And :clj And :cljr And :cljs dp/And)`
- #321 `eve/db/ds/query_v3.cljc:890` — `#?(:jank dp/Predicate :clj Predicate :cljr Predicate :cljs dp/Predicate)`


## glue-candidate (0)


## open-other (147)

- #9 `eve/db/ds/conn.cljc:87` — `#?(:clj`
- #10 `eve/db/ds/conn.cljc:114` — `#?(:clj`
- #11 `eve/db/ds/conn.cljc:189` — `#?(:clj`
- #30 `eve/db/ds/core.cljc:488` — `#?(:clj`
- #31 `eve/db/ds/core.cljc:615` — `#?(:cljs`
- #32 `eve/db/ds/core.cljc:628` — `#?(:lpy`
- #34 `eve/db/ds/core.cljc:672` — `#?(:cljs`
- #35 `eve/db/ds/core.cljc:714` — `#?(:clje`
- #36 `eve/db/ds/core.cljc:723` — `#?(:cljs`
- #37 `eve/db/ds/core.cljc:760` — `#?(:clj`
- #38 `eve/db/ds/core.cljc:767` — `#?(:clj`
- #39 `eve/db/ds/core.cljc:784` — `#?(:clj`
- #40 `eve/db/ds/core.cljc:791` — `#?(:clj`
- #41 `eve/db/ds/core.cljc:802` — `#?(:clj`
- #42 `eve/db/ds/core.cljc:811` — `#?(:clj`
- #52 `eve/db/ds/db.cljc:60` — `#?@(:clj [(or (seq? x)`
- #53 `eve/db/ds/db.cljc:79` — `#?@(:clj [(or (seq? x)`
- #54 `eve/db/ds/db.cljc:105` — `#?@(:clj [(or (seq? x)`
- #56 `eve/db/ds/db.cljc:141` — `(do #?@(:clj [(defn- cljs-env?`
- #57 `eve/db/ds/db.cljc:164` — `#?(:clje (defn- cljs-env?`
- #58 `eve/db/ds/db.cljc:168` — `(do #?@(:clj [(defmacro if-cljs`
- #59 `eve/db/ds/db.cljc:188` — `#?(:clje (defmacro if-cljs`
- #60 `eve/db/ds/db.cljc:192` — `(do #?@(:clj [(defn patch-tag [meta cljs-env?]`
- #61 `eve/db/ds/db.cljc:225` — `#?(:clje (defn patch-tag`
- #62 `eve/db/ds/db.cljc:238` — `(do #?@(:clj [(defmacro defn+`
- #63 `eve/db/ds/db.cljc:322` — `#?(:clje (defmacro defn+`
- #64 `eve/db/ds/db.cljc:348` — `#?(:clje (defn- hash-combine* [seed hash]`
- #65 `eve/db/ds/db.cljc:358` — `#?@(:clj [(clojure.lang.Util/hashCombine x y)] :cljr [(clojure.lang.Util/hashCom`
- #66 `eve/db/ds/db.cljc:363` — `(do #?@(:clj [   (defn- get-sig [method]`
- #67 `eve/db/ds/db.cljc:387` — `#?(:clje (defn- get-sig [& _args] nil))`
- #68 `eve/db/ds/db.cljc:389` — `(do #?@(:clj [   (defn- dedupe-interfaces [deftype-form]`
- #69 `eve/db/ds/db.cljc:407` — `#?(:clje (defn- dedupe-interfaces [& _args] nil))`
- #70 `eve/db/ds/db.cljc:409` — `(do #?@(:clj [(defn- make-record-updatable-cljs [name fields & impls]`
- #71 `eve/db/ds/db.cljc:431` — `#?(:clje (defn- make-record-updatable-cljs [name fields & impls]`
- #72 `eve/db/ds/db.cljc:436` — `(do #?@(:clj [(defn- make-record-updatable-clj [name fields & impls]`
- #73 `eve/db/ds/db.cljc:480` — `#?(:clje (defn- make-record-updatable-clj [name fields & impls]`
- #74 `eve/db/ds/db.cljc:483` — `(do #?@(:clj [(defmacro defrecord-updatable [name fields & impls]`
- #75 `eve/db/ds/db.cljc:497` — `#?(:clje (defmacro defrecord-updatable [name fields & impls]`
- #82 `eve/db/ds/db.cljc:577` — `(empty [d] (throw #?(:cljr (System.NotSupportedException. "empty is not supporte`
- #85 `eve/db/ds/db.cljc:662` — `#?@(:clj [(throw (IndexOutOfBoundsException.))] :cljr [(throw (System.IndexOutOf`
- #87 `eve/db/ds/db.cljc:697` — `#?(:clj`
- #88 `eve/db/ds/db.cljc:710` — `(do #?@(:clj [(defmacro combine-cmp [& comps]`
- #89 `eve/db/ds/db.cljc:758` — `#?(:clje (defmacro combine-cmp [& comps]`
- #90 `eve/db/ds/db.cljc:770` — `(do #?@(:clj [(defn- -case-tree [queries variants]`
- #91 `eve/db/ds/db.cljc:805` — `#?(:clje (defn- -case-tree [queries variants]`
- #92 `eve/db/ds/db.cljc:814` — `(do #?@(:clj [(defmacro case-tree [qs vs]`
- #93 `eve/db/ds/db.cljc:822` — `#?(:clje (defmacro case-tree [qs vs]`
- #94 `eve/db/ds/db.cljc:826` — `#?(:clj`
- #95 `eve/db/ds/db.cljc:838` — `#?(:clj  {:inline (fn [x y] `(identical? (class ~x) (class ~y)))} :cljr {:inline`
- #96 `eve/db/ds/db.cljc:840` — `#?@(:clj [(identical? (class x) (class y))] :cljr [(identical? (class x) (class `
- #97 `eve/db/ds/db.cljc:845` — `(do #?@(:jank [   (defn class-name`
- #98 `eve/db/ds/db.cljc:866` — `#?(:clje (defn class-name`
- #102 `eve/db/ds/db.cljc:903` — `(do #?@(:jank [(defn ihash`
- #103 `eve/db/ds/db.cljc:920` — `#?(:clje (defn ihash`
- #109 `eve/db/ds/db.cljc:982` — `(catch #?@(:clj [ClassCastException] :cljr [System.InvalidCastException]`
- #110 `eve/db/ds/db.cljc:991` — `#?(:clj`
- #111 `eve/db/ds/db.cljc:1009` — `(do #?@(:jank [(defmacro defcomp [sym [arg1 arg2] & body]`
- #112 `eve/db/ds/db.cljc:1054` — `#?(:clje (defmacro defcomp [sym [arg1 arg2] & body]`
- #113 `eve/db/ds/db.cljc:1084` — `#?(:clj`
- #115 `eve/db/ds/db.cljc:1159` — `(catch #?@(:clj [ClassCastException] :cljr [System.InvalidCastException]`
- #131 `eve/db/ds/db.cljc:1221` — `(do #?@(:clj [(defmacro validate-attr [attr at]`
- #132 `eve/db/ds/db.cljc:1255` — `#?(:clje (defmacro validate-attr [attr at]`
- #133 `eve/db/ds/db.cljc:1264` — `(do #?@(:clj [(defmacro validate-val [v at]`
- #134 `eve/db/ds/db.cljc:1284` — `#?(:clje (defmacro validate-val [v at]`
- #135 `eve/db/ds/db.cljc:1329` — `#?(:clj`
- #139 `eve/db/ds/db.cljc:1416` — `#?(:bb (seq x) :default x))`
- #141 `eve/db/ds/db.cljc:1496` — `(-schema [db] #?(:clje schema`
- #142 `eve/db/ds/db.cljc:1508` — `pred       #?(:clj  (vpred v)`
- #144 `eve/db/ds/db.cljc:1630` — `#?(:clje`
- #145 `eve/db/ds/db.cljc:1643` — `#?@(:jank [(and (satisfies? ISearch x)`
- #148 `eve/db/ds/db.cljc:2031` — `#?(:cljs`
- #149 `eve/db/ds/db.cljc:2056` — `#?(:lpy`
- #150 `eve/db/ds/db.cljc:2066` — `#?(:clj`
- #159 `eve/db/ds/db.cljc:2142` — `cmp     #?(:bb (case index`
- #161 `eve/db/ds/db.cljc:2265` — `(do #?@(:clj [(defn-  ^Boolean tuple-ref-slot? [db schema-a idx] (cond`
- #162 `eve/db/ds/db.cljc:2284` — `#?(:clje (defn- tuple-ref-slot? [db schema-a idx] (cond`
- #163 `eve/db/ds/db.cljc:2289` — `(do #?@(:clj [(defn-  ^Boolean resolved-eid? [x] (or`
- #164 `eve/db/ds/db.cljc:2301` — `#?(:clje (defn- resolved-eid? [x] (or`
- #168 `eve/db/ds/db.cljc:2468` — `#?(:clj {:inline (fn [report] `(-> ~report :db-before :max-tx long inc))} :cljr `
- #169 `eve/db/ds/db.cljc:2473` — `#?(:clj {:inline (fn [db] `(inc (long (:max-eid ~db))))} :cljr {:inline (fn [db]`
- #170 `eve/db/ds/db.cljc:2477` — `(do #?@(:clj [(defn- ^Boolean tx-id?`
- #171 `eve/db/ds/db.cljc:2507` — `#?(:clje (defn- tx-id?`
- #172 `eve/db/ds/db.cljc:2514` — `(do #?@(:clj [(defn-  ^Boolean tempid? [x] (or`
- #173 `eve/db/ds/db.cljc:2530` — `#?(:clje (defn- tempid? [x] (or`
- #174 `eve/db/ds/db.cljc:2654` — `(do #?@(:clj [(defn-  ^Boolean tuple-has-tempids? [db a vs] (and`
- #175 `eve/db/ds/db.cljc:2714` — `#?(:clje (defn- tuple-has-tempids? [db a vs] (and`
- #226 `eve/db/ds/pull_api.cljc:205` — `(recur (assoc! acc (.-as attr) (#?(:clj .-default :cljr .-default :cljs :default`
- #231 `eve/db/ds/pull_api.cljc:265` — `(recur (assoc! acc (.-as attr) (#?(:clj .-default :cljr .-default :cljs :default`
- #246 `eve/db/ds/pull_parser.cljc:73` — `#?(:clj (when (namespace sym-or-fn)`
- #250 `eve/db/ds/query.cljc:26` — `#?(:clj`
- #252 `eve/db/ds/query.cljc:57` — `#?(:clj`
- #254 `eve/db/ds/query.cljc:131` — `#?(:cljs`
- #255 `eve/db/ds/query.cljc:137` — `#?(:clj`
- #256 `eve/db/ds/query.cljc:199` — `#?(:clje`
- #266 `eve/db/ds/query.cljc:370` — `(let [eid #?(:cljs (tuple-get tuple idx)`
- #267 `eve/db/ds/query.cljc:381` — `(let [eid #?(:cljs (tuple-get tuple idx)`
- #268 `eve/db/ds/query.cljc:396` — `#?(:cljs (tuple-get tuple idx)`
- #269 `eve/db/ds/query.cljc:412` — `#?(:cljs (tuple-get tuple idx)`
- #270 `eve/db/ds/query.cljc:425` — `#?(:clje (let [getters (mapv #(getter-fn attrs %) common-attrs)]`
- #272 `eve/db/ds/query.cljc:441` — `#?(:jank`
- #273 `eve/db/ds/query.cljc:504` — `(if-some [tuples1 #?(:clj (hash key) :cljr (hash key) :cljs (get hash key) :clje`
- #276 `eve/db/ds/query.cljc:649` — `(if #?(:clj  false :cljr false`
- #279 `eve/db/ds/query.cljc:687` — `#?(:cljs nil`
- #289 `eve/db/ds/query_v3.cljc:15` — `#?(:clj`
- #290 `eve/db/ds/query_v3.cljc:62` — `#?(:clj`
- #291 `eve/db/ds/query_v3.cljc:107` — `#?(:clj`
- #292 `eve/db/ds/query_v3.cljc:178` — `#?(:clj`
- #293 `eve/db/ds/query_v3.cljc:274` — `(.write #?(:clj  (.getSimpleName ^Class (class rel)) :cljr (.getSimpleName ^Clas`
- #294 `eve/db/ds/query_v3.cljc:306` — `#?(:clj`
- #297 `eve/db/ds/query_v3.cljc:357` — `#?(:clj`
- #328 `eve/db/ds/serialize.cljc:48` — `#?(:clj`
- #329 `eve/db/ds/serialize.cljc:81` — `#?(:clje`
- #330 `eve/db/ds/serialize.cljc:87` — `#?(:clj`
- #331 `eve/db/ds/serialize.cljc:117` — `#?(:clje`
- #332 `eve/db/ds/serialize.cljc:127` — `#?(:clj  (.get ^java.util.List d (int i)) :cljr (nth d i)`
- #333 `eve/db/ds/serialize.cljc:133` — `#?(:clj  (.get ^java.util.Map d k) :cljr (get d k)`
- #334 `eve/db/ds/serialize.cljc:139` — `#?(:clj  (instance? java.util.List a) :cljr (instance? System.Collections.IList `
- #335 `eve/db/ds/serialize.cljc:145` — `#?(:clj`
- #336 `eve/db/ds/serialize.cljc:161` — `#?(:clj`
- #339 `eve/db/ds/serialize.cljc:242` — `#?@(:clj [(or`
- #340 `eve/db/ds/serialize.cljc:256` — `#?(:clje false :default (== pos-inf v)) (array marker-inf)`
- #341 `eve/db/ds/serialize.cljc:257` — `#?(:clje false :default (== neg-inf v)) (array marker-minus-inf)`
- #346 `eve/db/ds/serialize.cljc:282` — `#?@(:clj`
- #347 `eve/db/ds/serialize.cljc:295` — `#?@(:clj`
- #348 `eve/db/ds/serialize.cljc:300` — `#?(:jank`
- #352 `eve/db/ds/storage.cljc:21` — `#?(:lpy`
- #354 `eve/db/ds/util.cljc:20` — `#?(:clj (defmacro log [& body]`
- #355 `eve/db/ds/util.cljc:40` — `#?(:clje (defmacro log [& body]`
- #356 `eve/db/ds/util.cljc:49` — `#?(:lpy (defmacro raise [& fragments]`
- #357 `eve/db/ds/util.cljc:70` — `#?(:clje (defmacro raise [& fragments]`
- #358 `eve/db/ds/util.cljc:75` — `#?(:clj (def ^:private ^:dynamic *if+-syms) :cljr (def ^:private ^:dynamic *if+-`
- #359 `eve/db/ds/util.cljc:79` — `#?(:clje (def ^:private ^:dynamic *if+-syms))`
- #360 `eve/db/ds/util.cljc:81` — `#?(:clj (defn- if+-rewrite-cond-impl [cond]`
- #361 `eve/db/ds/util.cljc:181` — `#?(:clje (defn- if+-rewrite-cond-impl [cond]`
- #362 `eve/db/ds/util.cljc:207` — `#?(:clj (defn- if+-rewrite-cond [cond]`
- #363 `eve/db/ds/util.cljc:219` — `#?(:clje (defn- if+-rewrite-cond [cond]`
- #364 `eve/db/ds/util.cljc:223` — `#?(:clj (defn- flatten-1 [xs]`
- #365 `eve/db/ds/util.cljc:235` — `#?(:clje (defn- flatten-1 [xs]`
- #366 `eve/db/ds/util.cljc:239` — `#?(:clj (defmacro if+`
- #367 `eve/db/ds/util.cljc:384` — `#?(:clje (defmacro if+`
- #368 `eve/db/ds/util.cljc:403` — `#?(:clj (defmacro cond+ [& clauses]`
- #369 `eve/db/ds/util.cljc:431` — `#?(:clje (defmacro cond+ [& clauses]`
- #370 `eve/db/ds/util.cljc:439` — `#?(:clj (defmacro some-of`
- #371 `eve/db/ds/util.cljc:459` — `#?(:clje (defmacro some-of`
- #372 `eve/db/ds/util.cljc:468` — `#?(:cljr`
- #373 `eve/db/ds/util.cljc:477` — `#?(:cljs`
- #375 `eve/db/ds/util.cljc:491` — `#?(:clj`
