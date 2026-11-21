(ns eve.db.ds.db-fn
  "Stored transaction functions (:db/fn) — John's rulings,
   2026-11-20: the feature stays, with two invocation kinds, both
   tested (18:34 ET); and the default is the host kind (20:49 ET:
   \"Let's default to host fns and let you opt into sci fns\") —
   eve.db.ds.core/db-fn builds a :resolve fn (its expansion defines
   and registers a host fn and stores its name); the SCI kind is
   the opt-in, via eve.db.ds.core/sci-fn or the function map form
   below. See DB-FN-STATUS.md and GUIDE §5.

   A stored db fn is DATA. It lives in the DB as the value of a
   :db/fn datom, so it must be a legal Eve value — a plain map of
   keywords, symbols, strings and vectors. A host fn is NOT an Eve
   value (\"no functions in Eve memory\", ONE-EVE #56): at the old
   pin, transacting one committed and then threw the flat-chunk
   unwritable error while the tx-report was being delivered
   (GAP-U3-5). Host fns are now rejected BEFORE commit
   (see validate-tx-data!, called by eve.db.ds.conn before its
   swap!), and this namespace's invoke validates what a db fn
   returns the same way, so no path can smuggle a fn into a DB.

   The two kinds:

   {:db.fn/kind :sci
    :db.fn/lang :clojure
    :db.fn/params [db name]        ; a vector of symbols
    :db.fn/code   \"(inc ...)\"}    ; the fn BODY, as a string

   {:db.fn/kind :resolve
    :db.fn/sym  my.app/tx-fns}     ; a fully-qualified symbol

   The :sci shape is Datomic's function shape ({:lang :params
   :code}, code as data) with a :db.fn/kind discriminator added.
   Provenance note: the vendored DataScript 1.8.1 baseline stores
   host fns directly and has no sourcified form — the sourcified
   shape is adopted from Datomic because a host fn cannot be an
   Eve value at all.

   Invocation happens inside eve.db.ds.db/transact-tx-data, at the
   two upstream call sites ([:db.fn/call f & args] and the
   [:ident & args] form for an ident with a :db/fn datom). The fn
   receives the transaction's current DB value and its args, and
   returns tx-data, exactly as upstream.

   :sci fns are evaluated by SCI (org.babashka/sci) in a sandbox:
   clojure.core's safe surface as SCI provides it, plus the DB read
   API registered by eve.db.ds.core (see register-sci-surface!):
   entity, entid, q, datoms, seek-datoms, pull, pull-many —
   available unqualified, as d/<name>, and as
   eve.db.ds.core/<name>. The evaluated fn is cached per stored
   map on the host side; the stored map itself stays pure data.

   :resolve fns are looked up by symbol: first in this namespace's
   registry (register-fn! — the portable path, both hosts), then,
   on the JVM only, by requiring-resolve. An unresolvable symbol
   throws :eve.db.ds/db-fn-unresolved at transact time. The usual
   way in is eve.db.ds.core/db-fn (the default): its expansion
   defines the body as a var in the caller's namespace under a
   content-addressed name (db-fn-<hash> of the caller's ns and
   the printed form) and registers it, so the registration runs
   wherever the defining form is evaluated — on ClojureScript,
   where there is no requiring-resolve, that registration is the
   path.

   SCI loading: SCI is an opt-in dependency — only the :sci kind
   needs it — so on the :clj family sci.core is NOT a load-time
   require of this namespace. It is resolved lazily, on first use
   of the :sci kind, via requiring-resolve (see load-sci!). Hosts
   that cannot load sci.core at all (jank: sci.core's ns form
   pulls clojure.tools.reader.reader-types, a .clj-only module)
   still load the whole library and use every other feature;
   invoking the :sci kind there throws
   :eve.db.ds/db-fn-sci-unavailable, naming the dependency.
   ClojureScript has no runtime module loading, so there
   sci.core remains a compile-time require of this namespace."
  #?(:cljs (:require-macros [eve.db.ds.util :as util]))
  (:require
    [eve.db.ds.util :as util]
    #?(:cljs [sci.core :as sci])))

;; ---------------------------------------------------------------------------
;; Stored shapes

(defn sci-fn
  "The stored representation of a SCI db fn: params is a vector of
   symbols ([db & args]), code is the fn body as a string (one or
   more forms). Pure data — safe to transact as a :db/fn value.
   Built by eve.db.ds.core/sci-fn (the opt-in macro) and by
   function below."
  [params code]
  {:pre [(vector? params) (every? symbol? params) (string? code)]}
  {:db.fn/kind   :sci
   :db.fn/lang   :clojure
   :db.fn/params (vec params)
   :db.fn/code   code})

(defn resolve-fn
  "The stored representation of a resolve db fn: sym is a
   fully-qualified symbol naming a host fn of [db & args] that
   returns tx-data. Pure data — safe to transact as a :db/fn
   value. The named fn is found through the registry (both hosts)
   or, on the JVM, requiring-resolve (see register-fn!)."
  [sym]
  {:pre [(symbol? sym) (some? (namespace sym))]}
  {:db.fn/kind :resolve
   :db.fn/sym  sym})

(defn function
  "The explicit map form (Datomic's function shape): normalizes
   {:lang :clojure, :params [db & args], :code <body>} to a stored
   SCI db fn. :code may be a string (the body) or a single form,
   which is sourcified with pr-str. This is the explicit map path;
   eve.db.ds.core/sci-fn is the sourcifying macro."
  [{:keys [lang params code]}]
  (when-not (contains? #{:clojure "clojure" nil} lang)
    (util/raise "db fn :lang must be :clojure, got " (pr-str lang)
      {:error :eve.db.ds/db-fn-malformed :lang lang}))
  (when-not (and (vector? params) (every? symbol? params))
    (util/raise "db fn :params must be a vector of symbols, got " (pr-str params)
      {:error :eve.db.ds/db-fn-malformed :params params}))
  (when-not (or (string? code) (sequential? code) (symbol? code))
    (util/raise "db fn :code must be a string or a form, got " (pr-str code)
      {:error :eve.db.ds/db-fn-malformed :code code}))
  (sci-fn params (if (string? code) code (pr-str code))))

(defn stored-fn?
  "True if x is a well-formed stored db fn (either kind)."
  [x]
  (and (map? x)
    (case (:db.fn/kind x)
      :sci     (and (vector? (:db.fn/params x))
                 (every? symbol? (:db.fn/params x))
                 (string? (:db.fn/code x)))
      :resolve (and (symbol? (:db.fn/sym x))
                 (some? (namespace (:db.fn/sym x))))
      false)))

(defn invocable?
  "True if x can be invoked as a transaction function: a stored
   db fn, or a host fn (host fns can appear at the [:db.fn/call f]
   call site and in DBs built outside transact!, e.g. by init-db;
   they just cannot be STORED through transact!/with)."
  [x]
  (or (fn? x) (stored-fn? x)))

;; ---------------------------------------------------------------------------
;; Pre-commit validation: no functions in Eve memory, enforced
;; before the swap (conn) and on db-fn results (invoke).

(defn- host-fn-error! []
  (util/raise
    (str "Cannot store a host function as :db/fn: a function is not an Eve value, "
      "so it cannot live in a DB. Store the fn as data instead: "
      "(eve.db.ds.core/db-fn [db & args] ...) for a host fn (the default — it "
      "defines and registers the fn and stores its name), "
      "(eve.db.ds.core/sci-fn [db & args] ...) or (eve.db.ds.core/function {...}) "
      "for a SCI fn whose source travels with the DB, "
      "(eve.db.ds.core/fn-ref 'my.ns/fn) for a resolved fn by name, or the explicit "
      "stored maps {:db.fn/kind :resolve, :db.fn/sym my.ns/fn} / "
      "{:db.fn/kind :sci, :db.fn/params [...], :db.fn/code \"...\"}. "
      "See GUIDE §5, \"Transaction functions\".")
    {:error :eve.db.ds/db-fn-host-fn}))

(defn- fn-value-error! [attr]
  (util/raise
    (str "Cannot store a function as the value of " attr
      ": functions are not Eve values and cannot live in a DB "
      "(see eve.db.ds.db-fn).")
    {:error :eve.db.ds/fn-not-storable :attr attr}))

(defn- malformed-error! [v]
  (util/raise
    (str "Malformed stored db fn " (pr-str v)
      ": expected {:db.fn/kind :sci, :db.fn/params [db & args], :db.fn/code \"<body>\"} "
      "or {:db.fn/kind :resolve, :db.fn/sym my.ns/fn}. "
      "Build one with eve.db.ds.core/db-fn, fn-ref or function.")
    {:error :eve.db.ds/db-fn-malformed :value v}))

(defn- check-value!
  "Check one value that is about to be stored under attr. Recurses
   into maps and collections: a fn anywhere inside a stored value
   is still a fn in Eve memory."
  [attr v]
  (cond
    (fn? v)
    (if (= :db/fn attr)
      (host-fn-error!)
      (fn-value-error! attr))

    (and (= :db/fn attr) (map? v) (contains? v :db.fn/kind))
    (when-not (stored-fn? v)
      (malformed-error! v))

    (map? v)
    (doseq [[a v'] v]
      (check-value! a v'))

    (or (sequential? v) (set? v))
    (doseq [v' v]
      (check-value! attr v'))

    :else nil))

(defn validate-tx-data!
  "Reject, before anything is committed, any tx-data that would
   store a function (or a malformed stored db fn) in the DB.
   Walks entity maps, [:db/add e a v] / retract / cas forms and
   Datoms. [:db.fn/call f & args] is an invocation, not storage:
   its fn position and args are not checked. Everything else is
   left exactly as upstream leaves it."
  [tx-data]
  (doseq [entity tx-data]
    (cond
      (map? entity)
      (doseq [[a v] entity]
        (check-value! a v))

      (sequential? entity)
      (let [[op _ a v nv] entity]
        (cond
          (= op :db.fn/call) nil
          (contains? #{:db/add :db/retract} op)
          (check-value! a v)
          (contains? #{:db.fn/cas :db/cas} op)
          (do (check-value! a v) (check-value! a nv))
          :else nil))

      ;; a Datom (not a map, not sequential): check its value
      (and (some? entity) (fn? (:v entity)))
      (check-value! (:a entity) (:v entity))

      :else nil))
  tx-data)

;; ---------------------------------------------------------------------------
;; The resolve registry (portable, both hosts)

;; Symbol -> host fn, for :resolve db fns. Apps populate it with
;; register-fn!; a DB naming a symbol stays pure data and travels
;; without the fn — the receiving process must register (or, on
;; the JVM, be able to requiring-resolve) the same symbol.
(defonce fn-registry (atom {}))

(defn register-fn!
  "Register host fn f under fully-qualified symbol sym for
   :resolve db fns. Returns sym."
  [sym f]
  {:pre [(symbol? sym) (some? (namespace sym)) (fn? f)]}
  (swap! fn-registry assoc sym f)
  sym)

(defn unregister-fn!
  "Remove sym from the :resolve registry. Returns sym."
  [sym]
  (swap! fn-registry dissoc sym)
  sym)

(defn- resolve-stored [stored]
  (let [sym (:db.fn/sym stored)]
    (or (get @fn-registry sym)
      #?(:clj (try (requiring-resolve sym)
                   (catch #?(:clj Throwable :cljs :default) _ nil)))
      (util/raise
        (str "Cannot resolve db fn " sym
          ": it is not in the eve.db.ds registry and "
          #?(:clj "cannot be requiring-resolve'd"
             :cljs "ClojureScript has no requiring-resolve")
          ". Register it with (eve.db.ds.core/register-fn! '" sym " f).")
        {:error :eve.db.ds/db-fn-unresolved :sym sym}))))

;; ---------------------------------------------------------------------------
;; SCI: surface, context, compiled-fn cache

;; SCI loading on the :clj family is lazy (see the ns docstring):
;; sci.core's init/eval-string* are resolved via requiring-resolve
;; on first use of the :sci kind and cached here. On cljs the ns
;; form's compile-time require provides them directly.
#?(:clj
   (defonce ^:private sci-fns (atom nil)))

#?(:clj
   (defn- load-sci!
     "Resolve sci.core's init and eval-string* (:clj family),
      caching them on success. When this host cannot load
      sci.core, throw a ds error naming the opt-in dependency —
      the failure surfaces where the :sci kind is exercised,
      never at library load."
     []
     (or @sci-fns
       (let [attempt (try {:init         (requiring-resolve 'sci.core/init)
                           :eval-string* (requiring-resolve 'sci.core/eval-string*)}
                          (catch Throwable e {:load-error e}))]
         (if (and (:init attempt) (:eval-string* attempt))
           (reset! sci-fns attempt)
           (util/raise
             (str "The :sci kind of stored :db/fn is opt-in and needs the SCI "
               "interpreter (org.babashka/sci, namespace sci.core), which this "
               "host could not load"
               (when-let [e (:load-error attempt)]
                 (str ": " (ex-message e)))
               ". Everything else in eve.db.ds — including the default host-fn "
               "kind (eve.db.ds.core/db-fn) and :resolve fns — works without it.")
             {:error :eve.db.ds/db-fn-sci-unavailable
              :cause (some-> (:load-error attempt) ex-message)}))))))

(defn- sci-init [opts]
  #?(:cljs (sci/init opts)
     :clj  ((:init (load-sci!)) opts)))

(defn- sci-eval-string* [ctx form]
  #?(:cljs (sci/eval-string* ctx form)
     :clj  ((:eval-string* (load-sci!)) ctx form)))

;; Symbol -> host fn: the DB read API a SCI db fn may call.
;; eve.db.ds.core registers its public read fns here when it
;; loads (register-sci-surface!); until then the surface is
;; empty and SCI db fns can only use clojure.core.
(defonce sci-surface (atom {}))

(defonce ^:private sci-ctx-cache (atom nil))
(defonce ^:private sci-compiled  (atom {}))

(defn register-sci-surface!
  "Merge {symbol fn} into the SCI db-fn surface and invalidate
   the cached SCI context and compiled fns. Called by
   eve.db.ds.core on load; apps may add their own read helpers."
  [m]
  (swap! sci-surface merge m)
  (reset! sci-ctx-cache nil)
  (reset! sci-compiled {})
  m)

(defn- build-sci-ctx []
  (let [surface @sci-surface]
    (sci-init
      {:namespaces {'user           surface
                    'eve.db.ds.core surface
                    'd              surface}})))

(defn- sci-ctx []
  (or @sci-ctx-cache
    (reset! sci-ctx-cache (build-sci-ctx))))

(defn- unwrap-sci-error
  "A SCI fn that throws an ex-info surfaces a :sci/error wrapper
   whose message already matches the original; rethrow the
   original so callers see the fn's own exception, data included
   (measured against sci 0.15.59: wrapper message = user message,
   cause = the user's ex-info)."
  [e]
  (if (and (= :sci/error (:type (ex-data e)))
        (some? (ex-cause e)))
    (ex-cause e)
    e))

(defn- compile-sci [stored]
  (or (get @sci-compiled stored)
    (let [form (str "(fn " (pr-str (:db.fn/params stored)) " "
                 (:db.fn/code stored) ")")
          f    (try (sci-eval-string* (sci-ctx) form)
                    (catch #?(:clj Throwable :cljs :default) e
                      (throw (unwrap-sci-error e))))]
      (swap! sci-compiled assoc stored f)
      f)))

;; ---------------------------------------------------------------------------
;; Invocation

(defn invoke
  "Invoke a transaction function value against db with args,
   returning tx-data. stored may be a host fn ([:db.fn/call f]
   call site), a :sci stored map, or a :resolve stored map.
   The result is validated with the same rule as tx-data: a db
   fn may not return a function to be stored, either."
  [db stored args]
  (let [result
        (cond
          (fn? stored)
          (apply stored db args)

          (= :sci (:db.fn/kind stored))
          (try (apply (compile-sci stored) db args)
               (catch #?(:clj Throwable :cljs :default) e
                 (throw (unwrap-sci-error e))))

          (= :resolve (:db.fn/kind stored))
          (apply (resolve-stored stored) db args)

          :else
          (util/raise "Not an invocable transaction function: " (pr-str stored)
            {:error :eve.db.ds/db-fn-not-invocable :value stored}))]
    (when (sequential? result)
      (validate-tx-data! result))
    result))
