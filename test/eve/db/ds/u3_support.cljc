(ns eve.db.ds.u3-support
    "Support for U3's ported slices (conn / transact / listen / upsert).

   The upstream slices require eve.db.ds.test.core (tdc) for three
   helpers and the thrown-msg? assert-expr. test.core cannot be used
   as-is in the port: it sits in the gated upstream tree and pulls
   the whole upstream test surface. The helpers the four slices use
   are ported here, against the handle model:

   - all-datoms: [e a v] triples of a DB handle (upstream took a DB
     value; d/datoms resolves handles and delivers host Datoms).
   - entity-map: an entity of a DB handle as a plain map.
   - thrown-msg?: upstream's assert-expr, unchanged."
    (:require
     #_{:clj-kondo/ignore [:unused-namespace]} [clojure.test :as t]
     [eve.db.ds.core :as d])
    #?(:cljs (:require-macros [eve.db.ds.test-macros])))

(defn all-datoms [db]
      (into #{} (map (juxt :e :a :v)) (d/datoms db :eavt)))

(defn entity-map [db eid]
      (into {:db/id eid} (d/entity db eid)))

;; upstream test.core's thrown-msg? assert-expr, verbatim (JVM arm).
;; The cljs registration lives in eve.db.ds.test-macros (a macro
;; namespace): cljs.test/assert-expr is compile-time only in modern
;; ClojureScript, and a runtime :cljs defmethod here compiled to an
;; add-method call on undefined that killed the browser page at
;; load (U6, 2026-11-20). This ns pulls the macro file in via
;; :require-macros (see the ns form) so the method is registered
;; before test namespaces' `is` forms macroexpand.
#?(:clj
   (defmethod t/assert-expr 'thrown-msg? [msg form]
              (let [[_ match & body] form]
                   `(try
                     (let [res# (do ~@body)] res#)
                     (t/do-report {:type :fail, :message ~msg, :expected '~form, :actual nil})
                     (catch
                      Throwable
                      e#
                      (let [m# (.getMessage e#)]
                           (if (= ~match m#)
                               (t/do-report {:type :pass, :message ~msg, :expected '~form, :actual e#})
                               (t/do-report {:type :fail, :message ~msg, :expected '~form, :actual e#})))
                      e#)))))
