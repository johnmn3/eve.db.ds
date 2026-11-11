(ns contract.native-sqrt
  (:require [eve.db.ds.built-ins :as built-ins]
            [eve.encoding :as enc]))

(defn- bits [x]
  (mapv #(bit-and 255 %) (seq (enc/f64-le-bytes x))))

(defn -main [& _]
  (let [sqrt (ns-resolve 'eve.db.ds.built-ins 'sqrt)
        variance (get built-ins/aggregates 'variance)
        stddev (get built-ins/aggregates 'stddev)
        sqrt-two [205 59 127 102 158 160 246 63]
        sqrt-variance [216 92 139 155 150 114 5 64]]
    ;; Literal little-endian IEEE-754 bytes, independently checked against
    ;; JVM Math/sqrt: 2.0 and the variance 7.1875 of [1 2 4 8].
    (assert (= sqrt-two (bits (sqrt 2.0))))
    (assert (= 7.1875 (variance [1.0 2.0 4.0 8.0])))
    (assert (= sqrt-variance (bits (stddev [1.0 2.0 4.0 8.0]))))
    #?(:clj (do (assert (= sqrt-two (bits (Math/sqrt 2.0))))
                (assert (= sqrt-variance (bits (Math/sqrt 7.1875)))))))
  (println "NATIVE-SQRT-BITS-OK"))
