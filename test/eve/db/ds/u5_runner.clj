(ns eve.db.ds.u5-runner
  "U5 runner: serialize + persistence slices (JVM).
   Run: clojure -Sdeps '{:paths [\"src\" \"test\"]}' -M:ds-suite -m eve.db.ds.u5-runner

   Named, not silent — what is NOT in this runner:
   - upstream test-pr-read / test-init-db / test-max-eid-from-refs:
     not ported (DB/Datom print-read and init-db equivalence are
     U2/U3 surfaces; see U5-STATUS.md).
   - close-conn! on a :persistent conn: throws inside Eve at pin
     323bfe3 (GAP-U5-1); the persistence slice proves reopen by
     adopt and by fresh process instead.
   - the cliff/Logseq digests: the generator is not in this repo;
     the synthetic digest route is used (U5-STATUS.md)."
  (:require
    [clojure.test :as t]
    [eve.db.ds.test.serialize]
    [eve.db.ds.test.persistence]))

(defn -main []
  (println "U5 parked/excluded: upstream print-read + init-db deftests"
    "(U2/U3 surfaces); persistent close-conn! (GAP-U5-1); cliff anchors"
    "(generator not in repo — synthetic digests instead).")
  (let [{:keys [fail error] :as res}
        (t/run-tests 'eve.db.ds.test.serialize
          'eve.db.ds.test.persistence)]
    (println "U5-RESULT" res)
    (System/exit (if (zero? (+ fail error)) 0 1))))
