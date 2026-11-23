(ns eve.db.ds.datafy
    (:require
     [clojure.core.protocols :as p]
     [eve.db.ds.pull-api :as dp]
     [eve.db.ds.db :as db]
     [eve.db.ds.impl.entity :as e]))

(declare datafy-entity-seq)

(defn- navize-pulled-entity [db-val pulled-entity]
       (let [ref-attrs (:db.type/ref (:rschema db-val))
             ref-rattrs (set (map db/reverse-ref ref-attrs))
             many-attrs (:db.cardinality/many (:rschema db-val))]
            (with-meta pulled-entity
                       {`p/nav (fn [_coll k v]
                                   (cond
                                    (or (and (many-attrs k) (ref-attrs k))
                                        (ref-rattrs k))
                                    (datafy-entity-seq db-val v)
                                    (ref-attrs k)
                                    (e/entity db-val (:db/id v))
                                    :else v))})))

(defn- navize-pulled-entity-seq [db-val entities]
       (with-meta entities
                  {`p/nav (fn [_coll _k v]
                              (e/entity db-val (:db/id v)))}))

(defn- datafy-entity-seq [db-val entities]
       (with-meta entities
                  {`p/datafy (fn [entities] (navize-pulled-entity-seq db-val entities))}))

(extend-protocol p/Datafiable
                 eve.db.ds.impl.entity.Entity
                 (datafy [this]
                         (let [db (.-db this)
                               ref-attrs (:db.type/ref (:rschema db))
                               ref-rattrs (set (map db/reverse-ref ref-attrs))
                               pull-pattern (into ["*"] ref-rattrs)]
                              (navize-pulled-entity db (dp/pull db pull-pattern (:db/id this))))))
