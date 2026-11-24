(ns contract.ds-bench-clock
  "contract.ds-bench's clock and timing macro, in a namespace of their own
   so every host can expand the macro: shadow-cljs does not expand a .cljc
   namespace's macros inside that same namespace (\"Can't take value of
   macro\"), it does from a consumer that names them in :refer-macros. The
   JVM arm's clock and the macro's expansion are the same as before the
   split; other hosts read the clocks contract.ds-runtime reads."
  #?(:cljs (:require-macros [contract.ds-bench-clock]))
  (:require [eve.clock :as clock]))

(defn now-ms []
  #?(:jank (double (clock/current-time-millis))
     :clj (double (/ (System/nanoTime) 1e6))
     :cljs (js/performance.now)
     :default (double (clock/current-time-millis))))

#?(:cljs nil
   :default
   (defmacro timed-ms [& body]
     `(let [t0# (now-ms) r# (do ~@body)] [(- (now-ms) t0#) r#])))
