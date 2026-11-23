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

(defn- and-let-split
       "Split an (and ...) test's children at :let pairs, mirroring
   if+'s expansion: returns [binding-vector-nodes kept-test-nodes].
   The bindings scope over the later test clauses and the clause
   body, not over the else branch."
       [children]
       (loop [cs children, binds [], kept []]
             (if (seq cs)
                 (if (= :let (api/sexpr (first cs)))
                     (recur (nnext cs) (conj binds (second cs)) kept)
                     (recur (next cs) binds (conj kept (first cs))))
                 [binds kept])))

(defn- cond+-rewrite
       "cond+ clauses -> nested if/let/do/or, mirroring the macro's own
   expansion (util.cljc): :let binds for the remaining clauses,
   :do evaluates for effect, :some short-circuits on truthy,
   anything else is a test whose value if+ guards. A test that is
   an (and ...) containing :let pairs expands through if+, whose
   bindings scope over the rest of the test and the clause body."
       [clauses]
       (when (seq clauses)
             (let [[test expr & more] clauses]
                  (case (api/sexpr test)
                        :let (api/list-node [(api/token-node 'let) expr (cond+-rewrite more)])
                        :do (api/list-node [(api/token-node 'do) expr (cond+-rewrite more)])
                        :some (api/list-node [(api/token-node 'or) expr (cond+-rewrite more)])
                        (if (and (api/list-node? test)
                                 (= 'and (api/sexpr (first (:children test))))
                                 (some #(= :let (api/sexpr %)) (:children test)))
                            (let [[binds kept] (and-let-split (rest (:children test)))
                                  if-node (api/list-node [(api/token-node 'if)
                                                          (api/list-node (list* (api/token-node 'and) kept))
                                                          expr
                                                          (cond+-rewrite more)])]
                                 (reduce (fn [inner b] (api/list-node [(api/token-node 'let) b inner]))
                                         if-node (reverse binds)))
                            (api/list-node [(api/token-node 'if) test expr (cond+-rewrite more)]))))))

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

(defn defrecord-updatable
      "eve.db.ds.db/defrecord-updatable: (defrecord-updatable Tag
   [fields] impls...) expands per pass to a record type with
   updatable fields. The plain defrecord rewrite models the
   type, its factory fns and the impl specs."
      [{:keys [node]}]
      (let [[_ tag fields & specs] (:children node)
            new-node (api/list-node
                      (list* (api/token-node 'defrecord) tag fields specs))]
           {:node new-node}))

(defn db-fn
      "eve.db.ds.core/db-fn: (db-fn [params] body...) defines a host
   fn for :db/fn — the body is ordinary host code under a fn
   binding, so the plain fn rewrite is the faithful model."
      [{:keys [node]}]
      (let [[_ & rest] (:children node)
            new-node (api/list-node
                      (list* (api/token-node 'fn) rest))]
           {:node new-node}))

(defn sci-fn
      "eve.db.ds.core/sci-fn: (sci-fn [params] body...) builds a
   stored :db/fn value — the body is SCI source DATA, stored and
   evaluated later by the SCI interpreter; the host compiler
   never compiles it. The faithful lint model is a quoted fn."
      [{:keys [node]}]
      (let [[_ & rest] (:children node)
            fn-node (api/list-node (list* (api/token-node 'fn) rest))
            new-node (api/list-node [(api/token-node 'quote) fn-node])]
           {:node new-node}))

(defn defn+
      "eve.db.ds.db/defn+: (defn+ name & fdecl) expands to a defn of
   exactly that shape (on cljs preceded by a ^:declared defn with
   the same arglists). The plain defn rewrite is the faithful
   call-site model."
      [{:keys [node]}]
      (let [[_ & rest] (:children node)
            new-node (api/list-node
                      (list* (api/token-node 'defn) rest))]
           {:node new-node}))
