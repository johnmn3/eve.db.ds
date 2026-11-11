# Suite load gaps (2026-11-11 ET)

Known baseline failures are expected in the next isolated run, not new
regressions. They still count as errors; none are hidden or made passes.
Producer attribution is provisional where noted; no Eve defect is proven.
No upstream assertion bodies changed. No further HashBeef retry was attempted.

| Host | Namespace | Producer | Exact error / context |
|---|---|---|---|
| JVM | storage | eve.db.ds src | `Unable to resolve var: storage/tail-addr` (CompilerException at storage.clj:247:5) |
| bb | query-v3 | eve.db.ds src | `Unable to resolve symbol: clojure.lang.ITransientAssociative` |
| bb | serialize | test harness | `Could not locate jsonista/core.bb, jsonista/core.clj or jsonista/core.cljc on classpath.` |
| bb | storage | eve.db.ds src | `Unable to resolve var: storage/tail-addr` |
| basilisp | db | test harness | `error occurred during inlining`: CLJ/CLJS-only HashBeef declaration; missing map->HashBeef; exact exception below |
| basilisp | parser | test harness | `error occurred during macroexpansion`: exception-type assertion imports/aliases lack :lpy wiring; exact exception below |
| basilisp | parser-rules | test harness | `error occurred during macroexpansion`: exception-type assertion imports/aliases lack :lpy wiring; exact exception below |
| basilisp | parser-where | test harness | `error occurred during macroexpansion`: exception-type assertion imports/aliases lack :lpy wiring; exact exception below |
| basilisp | pull-api | test harness | `error occurred during macroexpansion`: clojure.edn/read-string dependency in upstream test; exact exception below |
| basilisp | query | test harness | `error occurred during macroexpansion`: exception-type assertion imports/aliases lack :lpy wiring; exact exception below |
| basilisp | query-fns | test harness | `error occurred during macroexpansion`: exception-type assertion imports/aliases lack :lpy wiring; exact exception below |
| basilisp | query-or | test harness | `error occurred during macroexpansion`: exception-type assertion imports/aliases lack :lpy wiring; exact exception below |
| basilisp | query-v3 | test harness | `error occurred during macroexpansion`: exception-type assertion imports/aliases lack :lpy wiring; exact exception below |
| basilisp | serialize | test harness | `error occurred during macroexpansion`: exception-type assertion imports/aliases lack :lpy wiring; exact exception below |
| basilisp | tuples | test harness | `error occurred during macroexpansion`: exception-type assertion imports/aliases lack :lpy wiring; exact exception below |
| basilisp | validation | test harness | `error occurred during macroexpansion`: exception-type assertion imports/aliases lack :lpy wiring; exact exception below |
| basilisp | upsert | test harness | `error occurred during macroexpansion`: exception-type assertion imports/aliases lack :lpy wiring; exact exception below |
| basilisp | datafy | eve.db.ds src; Eve/type-translation boundary unconfirmed | `error occurred during macroexpansion`: datafy.cljc extends cp/Datafiable on Entity; exact exception below |

## Exact Basilisp exceptions

Verbatim from `harness-lpy-v5.log`. Load-only executed zero test vars.

### eve.db.ds.test.db

```text
CompilerException(msg='error occurred during inlining', phase=<CompilerPhase.INLINING: :inlining>, filename='/workspace/ds-wt/eve.db.ds/test/upstream-ds/test/eve/db/ds/test/db.cljc', form=(hash (map->HashBeef {:x :ignored})), lisp_ast=None, py_ast=None)
```

### eve.db.ds.test.parser

```text
CompilerException(msg='error occurred during macroexpansion', phase=<CompilerPhase.MACROEXPANSION: :macroexpansion>, filename='/workspace/ds-wt/eve.db.ds/test/upstream-ds/test/eve/db/ds/test/parser.cljc', form=(basilisp.test/is (thrown-with-msg? ExceptionInfo #"Cannot parse binding" (dp/parse-binding :key)) (basilisp.core/str "Test failure: " (basilisp.core/pr-str (quote (thrown-with-msg? ExceptionInfo #"Cannot parse binding" (dp/parse-binding :key)))))), lisp_ast=None, py_ast=None)
```

### eve.db.ds.test.parser-rules

```text
CompilerException(msg='error occurred during macroexpansion', phase=<CompilerPhase.MACROEXPANSION: :macroexpansion>, filename='/workspace/ds-wt/eve.db.ds/test/upstream-ds/test/eve/db/ds/test/parser_rules.cljc', form=(basilisp.test/is (thrown-with-msg? ExceptionInfo #"Cannot parse rule-vars" (dp/parse-rules (quote [[(rule) [_]]]))) (basilisp.core/str "Test failure: " (basilisp.core/pr-str (quote (thrown-with-msg? ExceptionInfo #"Cannot parse rule-vars" (dp/parse-rules (quote [[(rule) [_]]]))))))), lisp_ast=None, py_ast=None)
```

### eve.db.ds.test.parser-where

```text
CompilerException(msg='error occurred during macroexpansion', phase=<CompilerPhase.MACROEXPANSION: :macroexpansion>, filename='/workspace/ds-wt/eve.db.ds/test/upstream-ds/test/eve/db/ds/test/parser_where.cljc', form=(basilisp.test/is (thrown-with-msg? ExceptionInfo #"Pattern could not be empty" (dp/parse-clause (quote []))) (basilisp.core/str "Test failure: " (basilisp.core/pr-str (quote (thrown-with-msg? ExceptionInfo #"Pattern could not be empty" (dp/parse-clause (quote []))))))), lisp_ast=None, py_ast=None)
```

### eve.db.ds.test.pull-api

```text
CompilerException(msg='error occurred during macroexpansion', phase=<CompilerPhase.MACROEXPANSION: :macroexpansion>, filename='/workspace/ds-wt/eve.db.ds/test/upstream-ds/test/eve/db/ds/test/pull_api.cljc', form=(basilisp.core/-> (d/from-serializable (clojure.edn/read-string (pr-str (d/serializable (basilisp.core/deref *test-db)))))), lisp_ast=None, py_ast=None)
```

### eve.db.ds.test.query

```text
CompilerException(msg='error occurred during macroexpansion', phase=<CompilerPhase.MACROEXPANSION: :macroexpansion>, filename='/workspace/ds-wt/eve.db.ds/test/upstream-ds/test/eve/db/ds/test/query.cljc', form=(basilisp.test/is (thrown-with-msg? ExceptionInfo #"Cannot bind value :a to tuple \[\?a \?b\]" (d/q (quote [:find ?a ?b :in [?a ?b]]) :a)) (basilisp.core/str "Test failure: " (basilisp.core/pr-str (quote (thrown-with-msg? ExceptionInfo #"Cannot bind value :a to tuple \[\?a \?b\]" (d/q (quote [:find ?a ?b :in [?a ?b]]) :a)))))), lisp_ast=None, py_ast=None)
```

### eve.db.ds.test.query-fns

```text
CompilerException(msg='error occurred during macroexpansion', phase=<CompilerPhase.MACROEXPANSION: :macroexpansion>, filename='/workspace/ds-wt/eve.db.ds/test/upstream-ds/test/eve/db/ds/test/query_fns.cljc', form=(basilisp.test/is (thrown-with-msg? ExceptionInfo #"get-else: nil default value is not supported" (d/q (quote [:find ?e ?height :where [?e :age] [(get-else $ ?e :height nil) ?height]]) db)) (basilisp.core/str "Test failure: " (basilisp.core/pr-str (quote (thrown-with-msg? ExceptionInfo #"get-else: nil default value is not supported" (d/q (quote [:find ?e ?height :where [?e :age] [(get-else $ ?e :height nil) ?height]]) db)))))), lisp_ast=None, py_ast=None)
```

### eve.db.ds.test.query-or

```text
CompilerException(msg='error occurred during macroexpansion', phase=<CompilerPhase.MACROEXPANSION: :macroexpansion>, filename='/workspace/ds-wt/eve.db.ds/test/upstream-ds/test/eve/db/ds/test/query_or.cljc', form=(basilisp.test/is (thrown-with-msg? ExceptionInfo #"All clauses in 'or' must use same set of free vars, had \[#\{\?e\} #\{(\?a \?e|\?e \?a)\}\] in \(or \[\?e :name _\] \[\?e :age \?a\]\)" (d/q (quote [:find ?e :where (or [?e :name _] [?e :age ?a])]) (basilisp.core/deref *test-db))) (basilisp.core/str "Test failure: " (basilisp.core/pr-str (quote (thrown-with-msg? ExceptionInfo #"All clauses in 'or' must use same set of free vars, had \[#\{\?e\} #\{(\?a \?e|\?e \?a)\}\] in \(or \[\?e :name _\] \[\?e :age \?a\]\)" (d/q (quote [:find ?e :where (or [?e :name _] [?e :age ?a])]) (basilisp.core/deref *test-db))))))), lisp_ast=None, py_ast=None)
```

### eve.db.ds.test.query-v3

```text
CompilerException(msg='error occurred during macroexpansion', phase=<CompilerPhase.MACROEXPANSION: :macroexpansion>, filename='/workspace/ds-wt/eve.db.ds/test/upstream-ds/test/eve/db/ds/test/query_v3.cljc', form=(basilisp.test/is (thrown-with-msg? ExceptionInfo #"Wrong number of arguments for bindings \[\$ \?a\], 2 required, 1 provided" (apply dq/q (quote [:find ?a :in $ ?a]) [0])) (basilisp.core/str "Test failure: " (basilisp.core/pr-str (quote (thrown-with-msg? ExceptionInfo #"Wrong number of arguments for bindings \[\$ \?a\], 2 required, 1 provided" (apply dq/q (quote [:find ?a :in $ ?a]) [0])))))), lisp_ast=None, py_ast=None)
```

### eve.db.ds.test.serialize

```text
CompilerException(msg='error occurred during macroexpansion', phase=<CompilerPhase.MACROEXPANSION: :macroexpansion>, filename='/workspace/ds-wt/eve.db.ds/test/upstream-ds/test/eve/db/ds/test/serialize.cljc', form=(basilisp.test/is (thrown-with-msg? ExceptionInfo #"init-db expects list of Datoms, got " (d/init-db [[:add -1 :name "Ivan"] {:add -1, :age 35}] schema)) (basilisp.core/str "Test failure: " (basilisp.core/pr-str (quote (thrown-with-msg? ExceptionInfo #"init-db expects list of Datoms, got " (d/init-db [[:add -1 :name "Ivan"] {:add -1, :age 35}] schema)))))), lisp_ast=None, py_ast=None)
```

### eve.db.ds.test.tuples

```text
CompilerException(msg='error occurred during macroexpansion', phase=<CompilerPhase.MACROEXPANSION: :macroexpansion>, filename='/workspace/ds-wt/eve.db.ds/test/upstream-ds/test/eve/db/ds/test/tuples.cljc', form=(basilisp.test/is (thrown-with-msg? ExceptionInfo #"Cannot add .* because of unique constraint: .*" (d/transact! conn [[:db/add 1 :a "A"]])) (basilisp.core/str "Test failure: " (basilisp.core/pr-str (quote (thrown-with-msg? ExceptionInfo #"Cannot add .* because of unique constraint: .*" (d/transact! conn [[:db/add 1 :a "A"]])))))), lisp_ast=None, py_ast=None)
```

### eve.db.ds.test.validation

```text
CompilerException(msg='error occurred during macroexpansion', phase=<CompilerPhase.MACROEXPANSION: :macroexpansion>, filename='/workspace/ds-wt/eve.db.ds/test/upstream-ds/test/eve/db/ds/test/validation.cljc', form=(basilisp.test/is (thrown-with-msg? Throwable #"Expected number, string or lookup ref for :db/id" (d/db-with db [{:name "Ivan", :db/id #""}])) (basilisp.core/str "Test failure: " (basilisp.core/pr-str (quote (thrown-with-msg? Throwable #"Expected number, string or lookup ref for :db/id" (d/db-with db [{:name "Ivan", :db/id #""}])))))), lisp_ast=None, py_ast=None)
```

### eve.db.ds.test.upsert

```text
CompilerException(msg='error occurred during macroexpansion', phase=<CompilerPhase.MACROEXPANSION: :macroexpansion>, filename='/workspace/ds-wt/eve.db.ds/test/upstream-ds/test/eve/db/ds/test/upsert.cljc', form=(basilisp.test/is (thrown-with-msg? Throwable #"Conflicting upsert: \[:name "Ivan"\] resolves to 1, but entity already has :db/id 2" (d/with db [{:age 36, :name "Ivan", :db/id 2}])) (basilisp.core/str "Test failure: " (basilisp.core/pr-str (quote (thrown-with-msg? Throwable #"Conflicting upsert: \[:name "Ivan"\] resolves to 1, but entity already has :db/id 2" (d/with db [{:age 36, :name "Ivan", :db/id 2}])))))), lisp_ast=None, py_ast=None)
```

### eve.db.ds.test.datafy

```text
CompilerException(msg='error occurred during macroexpansion', phase=<CompilerPhase.MACROEXPANSION: :macroexpansion>, filename='/workspace/ds-wt/eve.db.ds/src/eve/db/ds/datafy.cljc', form=(extend-protocol cp/Datafiable eve.db.ds.impl.entity.Entity (datafy [this] (let [db (.-db this) ref-attrs (:db.type/ref (:rschema db)) ref-rattrs (set (map db/reverse-ref ref-attrs)) pull-pattern (into ["*"] ref-rattrs)] (navize-pulled-entity db (dp/pull db pull-pattern (:db/id this)))))), lisp_ast=None, py_ast=None)
```

## Load counts and producer follow-up

JVM 37/38; bb 35/38; nbb 37/37; Basilisp 23/37. No full suite ran locally.
The Basilisp package anchor prevents the eager upstream test.cljc aggregator
from poisoning other namespaces. The command was:

```
flock /workspace/ds-wt/one-eve-workers/test.lock python3 scripts/ds_suite.py lpy --load-only --timeout 300 --out /workspace/ds-wt/one-eve-workers/harness-lpy-v5
```

Source/API fixes: storage/tail-addr compatibility, bb query-v3 transient
interface portability, Basilisp datafy extension/class dispatch (determine
whether the remaining fault is in source or Eve's type translation).
Harness fixes: bb-native JSON test dependency policy; Basilisp HashBeef fixture,
exception-type imports/aliases, pull-api edn dependency. Do not infer a library
runtime regression from a test namespace that cannot compile.
