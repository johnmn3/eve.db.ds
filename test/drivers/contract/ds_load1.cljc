(ns contract.ds-load1
  "Load probe: requires eve.db.ds.test.serialize and prints the cause chain
   on failure; exits non-zero when the load fails (nbb: require is async)."
  (:require [eve.host] [eve.atom])
  #?(:lpy (:import sys)))

(defn- causes [e]
  (loop [c e depth 0]
    (when c (println {:cause depth :type (str (type c)) :msg (ex-message c)})
      (recur (ex-cause c) (inc depth)))))

(defn- exit! [code]
  #?(:clj (System/exit code)
     :cljs (js/process.exit code)
     :lpy (sys/exit code)
     :default (when-not (zero? code) (throw (ex-info "DS-LOAD1-FAILED" {})))))

(defn -main [& _]
  #?(:cljs (-> (js/Promise. (fn [res _] (res (require 'eve.db.ds.test.serialize))))
               (.then (fn [_] (println {:load :ok}) (exit! 0))
                      (fn [e] (causes e) (exit! 1))))
     :default (exit! (try (require 'eve.db.ds.test.serialize) (println {:load :ok}) 0
                       (catch #?(:clj Throwable :lpy python/Exception :default Exception) e
                         (causes e) 1)))))
