;; Problem-1 control: an Eve atom holding a 200-entry map, N swap!s of 2-key assocs.
;; Usage (from the eve.db.ds root, on an Eve tree):
;;   clojure -Sdeps '{:deps {eve/eve {:local/root "<eve>"}}}' -M test/drivers/cliff/probe0_eve.clj [N] [heap|disk]
;; Prints used blocks per bitmap class (0-5) every N/10 swaps. A leak is a class that grows by ~1 block a swap
;; (eve dcba2ee, in-process: class 0 at 10,341 blocks after 10,000 swaps); fixed, every class cycles with no drift.
;; EVE_READ_END=commit reads in C (no view pins): the count is then the reclaim alone, not the GC's cadence.
(require '[eve.atom :as ea] '[eve.slab-io :as slab] '[eve.deftype-proto.alloc :as alloc] '[eve.mem :as mem])
(def N (Long/parseLong (or (first *command-line-args*) "10000")))
(def mode (or (second *command-line-args*) "heap"))
(def kws (mapv #(keyword (str "k" %)) (range 200)))
(def init (zipmap kws (range 200)))
(def a (if (= mode "disk")
           (let [b (str (java.nio.file.Files/createTempDirectory "eve-probe0-" (make-array java.nio.file.attribute.FileAttribute 0)))]
                (ea/persistent-atom-domain b)
                (ea/persistent-atom {:id :probe/main :persistent b} init))
           (ea/atom ::probe init)))
(defn ctx [] (slab/domain-ctx (alloc/domain-root (ea/atom-sio a))))
(defn used []
      (let [c (ctx) rs (.-regions c)]
           (vec (for [i (range 6)]
                     (if-let [r (aget rs i)]
                             (- (mem/-load-i32 r 8) (mem/-load-i32 r 12)) 0)))))
(def sizes [32 64 128 256 512 1024])
(defn kb [u] (/ (reduce + (map * u sizes)) 1024.0))
(def u0 (used))
(println "mode" mode "start used/class" u0 (format "%.1f KB" (kb u0)))
(def t0 (System/nanoTime))
(dotimes [i N]
         (swap! a assoc (kws (mod i 200)) i (kws (mod (+ i 101) 200)) (- i))
         (when (zero? (mod (inc i) (quot N 10)))
               (let [u (used)]
                    (println (format "swap %6d used/class %s  %.1f KB  delta %.1f KB  (%.3f KB/swap)"
                                     (inc i) u (kb u) (- (kb u) (kb u0)) (/ (- (kb u) (kb u0)) (inc i)))))))
(println "count" (count @a) "sum" (reduce + (vals @a)) "secs" (/ (- (System/nanoTime) t0) 1e9))
(shutdown-agents)
(System/exit 0)
