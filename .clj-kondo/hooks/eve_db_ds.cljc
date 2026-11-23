(ns hooks.eve-db-ds
  "clj-kondo analyze-call hooks teaching kondo this repo's own
   defining macros. Without them the macro bodies are opaque to
   kondo: record fields read as unresolved symbols, protocol specs
   read as calls, and recur inside cond+ reads as non-tail.
   Each rewrite below is the macro's own expansion shape, reduced
   to the part call sites need modeled."
  (:require [clj-kondo.hooks-api :as api]))

(defn deftrecord
  "eve.db.ds.parser/deftrecord: (deftrecord Tag [fields] specs...)
   expands to defrecord with a default ITraversable impl prepended.
   The default impl is internal to the expansion; rewriting to the
   plain defrecord form models fields, factories and specs."
  [{:keys [node]}]
  (let [[_ tag fields & specs] (:children node)
        new-node (api/list-node
                   (list* (api/token-node 'defrecord) tag fields specs))]
    {:node new-node}))

(defn defcomp
  "eve.db.ds.db/defcomp: (defcomp name ^long [a b] body...) defines
   a comparator type with a 2-arg invoke. For lint, the defn shape
   is the faithful call-site model: a 2-arg fn whose body closes
   over nothing but its args."
  [{:keys [node]}]
  (let [[_ name-node args-node & body] (:children node)
        new-node (api/list-node
                   (list* (api/token-node 'defn) name-node args-node body))]
    {:node new-node}))

(defn- cond+-rewrite
  "cond+ clauses -> nested if/let/do/or, mirroring the macro's own
   expansion (util.cljc): :let binds for the remaining clauses,
   :do evaluates for effect, :some short-circuits on truthy,
   anything else is a test whose value if+ guards."
  [clauses]
  (when (seq clauses)
    (let [[test expr & more] clauses]
      (case (api/sexpr test)
        :let (api/list-node [(api/token-node 'let) expr (cond+-rewrite more)])
        :do (api/list-node [(api/token-node 'do) expr (cond+-rewrite more)])
        :some (api/list-node [(api/token-node 'or) expr (cond+-rewrite more)])
        (api/list-node [(api/token-node 'if) test expr (cond+-rewrite more)])))))

(defn cond+
  "eve.db.ds.util/cond+ — see cond+-rewrite."
  [{:keys [node]}]
  {:node (cond+-rewrite (rest (:children node)))})

(defn if+
  "eve.db.ds.util/if+: (if+ (and c1 :let [b v] c2) then else) —
   the :let bindings are visible to later and-clauses and to the
   then branch. Model: bind them around the if (they are not
   visible in else in the real expansion; a lint model that also
   sees them there can only under-report, and the test gate is the
   backstop). Non-and conditions rewrite to a plain if."
  [{:keys [node]}]
  (let [[_ cond-node then-node else-node] (:children node)
        cs (api/sexpr cond-node)
        new-node
        (if (and (seq? cs) (= 'and (first cs)) (some #{:let} (rest cs)))
          (let [children (rest (:children cond-node))
                pairs (partition 2 children)
                binds (vec (mapcat (fn [[t v]] (when (= :let (api/sexpr t))
                                                 (:children v)))
                                   pairs))
                kept (remove (fn [[t _]] (= :let (api/sexpr t))) pairs)
                and-node (api/list-node
                           (list* (api/token-node 'and)
                                  (mapcat identity kept)))]
            (api/list-node [(api/token-node 'let)
                            (api/vector-node binds)
                            (api/list-node [(api/token-node 'if)
                                            and-node then-node else-node])]))
          (api/list-node [(api/token-node 'if) cond-node then-node else-node]))]
    {:node new-node}))
