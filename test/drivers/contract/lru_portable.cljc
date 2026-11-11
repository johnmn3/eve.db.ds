(ns contract.lru-portable
  (:require [eve.db.ds.lru :as lru]))

(defn -main [& _]
  (let [empty (lru/lru 2)
        first (assoc empty :a 1)
        full (assoc first :b 2)
        refreshed (assoc full :a 1)
        evicted (assoc refreshed :c 3)]
    (assert (= :missing (get empty :a :missing)))
    (assert (= 1 (get first :a)))
    (assert (contains? first :a))
    (assert (= [:a 1] (vec (find first :a))))
    (assert (= 1 (get evicted :a)))
    (assert (= :missing (get evicted :b :missing)))
    (assert (= 3 (get evicted :c))))
  (let [cache (lru/cache 2)
        calls (atom 0)
        compute #(swap! calls inc)]
    (assert (= 1 (lru/-get cache :a compute)))
    (assert (= 1 (lru/-get cache :a compute)))
    (assert (= 1 @calls)))
  (println "LRU-PORTABLE-OK"))
