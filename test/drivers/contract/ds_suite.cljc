(ns contract.ds-suite
  "Runs DataScript's own upstream test suite (upstream-ds/test, tag
   1.8.1, UNCHANGED) against the port on the JVM seat. Per-namespace
   require + run with throwables caught per namespace, so one bad load
   does not end the row."
  (:require [eve.host] [eve.atom] [clojure.test :as t]))

(def suite-nss
  '[eve.db.ds.test.components eve.db.ds.test.conn eve.db.ds.test.core
    eve.db.ds.test.datafy eve.db.ds.test.db eve.db.ds.test.entity
    eve.db.ds.test.explode eve.db.ds.test.filter eve.db.ds.test.ident
    eve.db.ds.test.index eve.db.ds.test.issues eve.db.ds.test.listen
    eve.db.ds.test.lookup-refs eve.db.ds.test.lru eve.db.ds.test.parser
    eve.db.ds.test.parser-find eve.db.ds.test.parser-query
    eve.db.ds.test.parser-return-map eve.db.ds.test.parser-rules
    eve.db.ds.test.parser-where eve.db.ds.test.pull-api
    eve.db.ds.test.pull-parser eve.db.ds.test.query
    eve.db.ds.test.query-aggregates eve.db.ds.test.query-find-specs
    eve.db.ds.test.query-fns eve.db.ds.test.query-not
    eve.db.ds.test.query-or eve.db.ds.test.query-pull
    eve.db.ds.test.query-return-map eve.db.ds.test.query-rules
    eve.db.ds.test.query-v3 eve.db.ds.test.serialize
    eve.db.ds.test.storage eve.db.ds.test.transact
    eve.db.ds.test.tuples eve.db.ds.test.upsert
    eve.db.ds.test.validation])

(defn -main [& _]
  (doseq [ns-sym suite-nss]
    (try
      (require ns-sym)
      (let [res (t/run-tests ns-sym)]
        (println {:suite-ns ns-sym :result res}))
      (catch Throwable e
        (println {:suite-ns ns-sym :threw (str (type e) " " (.getMessage e))}))))
  (println {:suite :done})
  (System/exit 0))
