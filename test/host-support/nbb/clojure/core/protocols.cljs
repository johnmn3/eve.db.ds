(ns clojure.core.protocols
  "Clojure datafy/nav protocols missing from nbb 1.6.214.
   Default implementations honor the metadata hooks used by DataScript.")
(defprotocol Datafiable (datafy [o]))
(defprotocol Navigable (nav [coll k v]))
(extend-protocol Datafiable
  default (datafy [o] (if-let [f ('clojure.core.protocols/datafy (meta o))] (f o) o)))
(extend-protocol Navigable
  default (nav [coll k v] (if-let [f ('clojure.core.protocols/nav (meta coll))] (f coll k v) v)))
