(ns contract.one
    "The shootout's one-datum entry, on the rewrite's public API.

   The pre-rewrite version stored (d/empty-db) as an mmap atom's root
   and db-with'd it there. In the rewrite (DS-IN-EVE-PLAN v2 §4)
   empty-db returns a handle into a private conn — a host map holding
   a host Eve atom — and packing it into a persistent domain is
   refused by Eve's serializer (eve.deftype-proto.serialize/
   refuse-reference!), so the old shape cannot complete a single run
   at this pin. This version measures the same thing the operator's
   definition names — cold process, put one datum on a disk-backed
   DataScript DB — through the shipped API: a persistent conn on the
   given directory, one transact!, count the datoms."
    (:require [eve.db.ds.core :as d]))

(defn -main [& [dir]]
      (let [conn (d/create-conn nil {:persistent dir})]
           (d/transact! conn [{:db/id 1 :name "one" :age 1}])
           (println (pr-str {:mark :ONE-DONE :datoms (count (d/datoms (d/db conn) :eavt))}))
           (flush)))
