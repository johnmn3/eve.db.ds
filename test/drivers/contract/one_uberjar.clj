(ns contract.one-uberjar
  "The JVM uberjar entry for the DataScript shootout contract — the JVM
   twin of contract.one-aot. The AOT entry itself cannot be the JVM
   compilation unit: its catch names jank.runtime.object_ref and it
   exits through eve.process/exit!, both jank-only. Here the shape is
   the same — eve.host-completion first (the JVM completion), then the
   contract — over the identical body (contract.one/-main: a persistent
   conn on the given directory, one transact!, count the datoms). The
   exit is the JVM's own: flush, then System/exit, whose shutdown hooks
   are where Eve registers the domain close (eve.process/on-process-exit!
   — the JVM process adapter has no exit!)."
  (:require [eve.host-completion]
            [contract.one])
  (:gen-class))

(defn -main [& args]
  (let [code (try (apply contract.one/-main args)
                  0
                  (catch Throwable e
                    (println (pr-str {:mark :ONE-UBERJAR-ERROR
                                      :error (ex-message e)
                                      :data (ex-data e)}))
                    1))]
    (flush)
    (System/exit code)))
