(ns eve.db.ds.u5-persist-child
    "Child-JVM half of U5's cross-process persistence proof.

   Modes:
     write  <dir> — build the synthetic DB (u5-support/gen-rows)
                    into a :persistent conn at dir, print
                    :u5-child-wrote <count>, exit WITHOUT
                    close-conn! (process exit is the close at
                    this Eve pin — GAP-U5-1).
     digest <dir> — open dir with create-conn, print the content
                    digest (pr-str of u5-support/digest) and exit."
    (:require
     [eve.db.ds.core :as d]
     [eve.db.ds.u5-support :as u5s]))

(defn -main [mode dir]
      (case mode
            "write"
            (let [conn (d/create-conn u5s/gen-schema {:persistent dir})]
                 (d/transact! conn (u5s/gen-rows 40))
                 (println :u5-child-wrote (count (d/datoms (d/db conn) :eavt)))
                 (flush))

            "digest"
            (let [conn (d/create-conn u5s/gen-schema {:persistent dir})]
                 (println (pr-str (u5s/digest (d/db conn))))
                 (flush)))
      (System/exit 0))
