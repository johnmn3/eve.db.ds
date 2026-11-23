(ns contract.one-db-aot
    "AOT entry for contract.one-db (the write-only job over eve.db.ds.db
   alone): the completion first, the job, exit through eve.process/exit!."
    (:require [eve.host-completion] [eve.process :as proc] [contract.one-db]))

;; Host driver for clj-family seats (clj/jank/lpy/bb), never
;; compiled as CLJS: kondo's cljs pass reads the host/:default
;; arms and cannot resolve host names (System, Throwable, future,
;; jank classes). Per-form ignore for that pass artifact.
#_{:clj-kondo/ignore [:unresolved-symbol]}
(defn -main [& args]
      (let [code (try (apply contract.one-db/-main args) 0
                      (catch jank.runtime.object_ref e
                             (println (pr-str {:mark :ONE-DB-ERROR :error (ex-message e)})) 1))]
           (proc/exit! code)))
