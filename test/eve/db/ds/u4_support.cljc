(ns eve.db.ds.u4-support
  "U4 slice fixtures. The vendored baseline does not load through
   eve.db.ds.core / eve.db.ds.conn (both require the deleted
   eve.db.ds.storage), so slices build DB values with db.cljc's own
   engine — the exact calls baseline conn/db-with makes — and exercise
   U4's namespaces (query, pull-api, impl.entity, datafy) directly.
   This is the single re-point site for DB construction when U2/U3 land."
  (:require
    [clojure.test :as t]
    [eve.db.ds.db :as db]))

(defn empty-db
  ([schema] (db/empty-db schema {}))
  ([] (db/empty-db nil {})))

(defn with
  "Baseline conn/with, verbatim shape: a TxReport from db/transact-tx-data."
  [db tx-data]
  (db/transact-tx-data (db/->TxReport db db [] {} nil) tx-data))

(defn db-with
  "Baseline conn/db-with: (:db-after (with db tx-data))."
  [db tx-data]
  (:db-after (with db tx-data)))

(defn no-namespace-maps [f]
  (binding [*print-namespace-maps* false]
    (f)))

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
            e#)))))
