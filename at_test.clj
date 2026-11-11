(ns at-test
  (:refer-clojure :exclude [seqable? update])
  (:require [eve.core :as e]
            [eve.deftype :refer [deftype]]))

(deftype AutoTempid [id]
  Object
  (toString [this]
    (str "#eve.db.ds/AutoTempid [" id "]"))
  IPrintWithWriter
  (-pr-writer [this writer opts]
    (e/-write writer (str "#eve.db.ds/AutoTempid [" (.-id this) "]"))))

(println :AT-OK (pr-str (at-test.AutoTempid. 7)))
