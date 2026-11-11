(ns eve.db.ds.test
  (:require
    [clojure.test :as t :refer [is are deftest testing]]
    #?(:clj [clojure.java.shell :as sh])
    eve.db.ds.test.core
   
    eve.db.ds.test.components
    eve.db.ds.test.conn
    eve.db.ds.test.db
    eve.db.ds.test.entity
    eve.db.ds.test.explode
    eve.db.ds.test.filter
    eve.db.ds.test.ident
    eve.db.ds.test.index
    eve.db.ds.test.listen
    eve.db.ds.test.lookup-refs
    eve.db.ds.test.lru
    eve.db.ds.test.parser
    eve.db.ds.test.parser-find
    eve.db.ds.test.parser-return-map
    eve.db.ds.test.parser-rules
    eve.db.ds.test.parser-query
    eve.db.ds.test.parser-where
    eve.db.ds.test.pull-api
    eve.db.ds.test.pull-parser
    eve.db.ds.test.query
    eve.db.ds.test.query-aggregates
    eve.db.ds.test.query-find-specs
    eve.db.ds.test.query-fns
    eve.db.ds.test.query-not
    eve.db.ds.test.query-or
    eve.db.ds.test.query-pull
    eve.db.ds.test.query-return-map
    eve.db.ds.test.query-rules
    eve.db.ds.test.query-v3
    eve.db.ds.test.serialize
    #?(:clj eve.db.ds.test.storage)
    eve.db.ds.test.transact
    eve.db.ds.test.tuples
    eve.db.ds.test.validation
    eve.db.ds.test.upsert
    eve.db.ds.test.issues
    eve.db.ds.test.datafy))

(defn ^:export test-clj []
  (eve.db.ds.test.core/wrap-res #(t/run-all-tests #"eve\.db\.ds\..*")))

(defn ^:export test-cljs []
  (eve.db.ds.test.core/wrap-res #(t/run-all-tests #"eve\.db\.ds\..*")))

#?(:clj
   (defn test-node [& args]
     (let [res (apply sh/sh "node" "test_node.js" args)]
       (println (:out res))
       (binding [*out* *err*]
         (println (:err res)))
       (System/exit (:exit res)))))
