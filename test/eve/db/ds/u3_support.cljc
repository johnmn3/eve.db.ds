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
    [clojure.test :as t]
    [eve.db.ds.core :as d]))

(defn all-datoms [db]
  (into #{} (map (juxt :e :a :v)) (d/datoms db :eavt)))

(defn entity-map [db eid]
  (into {:db/id eid} (d/entity db eid)))

;; upstream test.core's thrown-msg? assert-expr, verbatim
#?(:clj
   (defmethod t/assert-expr 'thrown-msg? [msg form]
     (let [[_ match & body] form]
       `(try ~@body
             (t/do-report {:type :fail, :message ~msg, :expected '~form, :actual nil})
             (catch Throwable e#
               (let [m# (.getMessage e#)]
                 (if (= ~match m#)
                   (t/do-report {:type :pass, :message ~msg, :expected '~form, :actual e#})
                   (t/do-report {:type :fail, :message ~msg, :expected '~form, :actual e#})))
               e#))))
   :cljs
   (defmethod t/assert-expr 'thrown-msg? [msg form]
     (let [[_ match & body] form]
       `(try ~@body
             (t/do-report {:type :fail, :message ~msg, :expected '~form, :actual nil})
             (catch :default e#
               (let [m# (.-message e#)]
                 (if (= ~match m#)
                   (t/do-report {:type :pass, :message ~msg, :expected '~form, :actual e#})
                   (t/do-report {:type :fail, :message ~msg, :expected '~form, :actual e#})))
               e#)))))
