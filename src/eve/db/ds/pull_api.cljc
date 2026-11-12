(ns ^:no-doc eve.db.ds.pull-api
  (:require
    [clojure.string :as str]
    [eve.db.ds.pull-parser :as dpp]
    ;; PORT DELTA (F-NBB-MACROS round): as in eve.db.ds.conn — no
    ;; :cljs type refer; types are named qualified at their uses
    [eve.db.ds.db :as db]
    [eve.db.ds.lru :as lru]
    [eve.db.ds.util :as util]
    ;; PORT DELTA (F-CLJE-HOST-REFERS): see eve.db.ds.util
    #?(:clje [eve.host :refer [transient persistent! conj! assoc! volatile!]])))

(declare pull-impl attrs-frame ref-frame ->ReverseAttrsFrame)

;; PORT DELTA (F-BB-PULL-SEQ): the :clj arms call ISeq methods by
;; interop (.first/.next/.cons). Under bb those compile to
;; reflective invocations, and bb's native image has no reflection
;; registration for clojure.lang.ISeq.first/next/cons -- every pull
;; dies with MissingReflectionRegistrationError (RESULTS-BB
;; wall 2). The :bb arms use the core fns, like :clje already
;; does: same methods, reached through SCI's own dispatch instead
;; of reflection.
;; PORT DELTA (F-JOLT-PULL-SEQ, fix-for-release C2): the :clj/:cljr
;; arms use the core fns too. jolt reads :clj, and its ISeq interop
;; on a clojure.lang.ArraySeq — what (sort-by ...) answers there,
;; and so the attrs a pull pattern walks — throws "No matching field
;; found: first/next for class clojure.lang.ArraySeq", so every pull
;; died on jolt; core first/next work on it. On the JVM the core
;; fns call the same ISeq methods (RT.first/RT.next on an ISeq are
;; .first/.next, and RT.conj on an ISeq is .cons), so the values are
;; unchanged there.
;; Portable clojure.core first/next/cons (covers jolt ArraySeq and basilisp;
;; host type hints and per-host ISeq methods were reader branches).
(defn- first-seq [xs]
  (first xs))

(defn- next-seq [xs]
  (next xs))

(defn- conj-seq [xs x]
  (if (nil? xs)
    (list x)
    (cons x xs)))

(defn- assoc-some! [m k v]
  (if (nil? v) m (assoc! m k v)))

(defn- conj-some! [xs v]
  (if (nil? v) xs (conj! xs v)))

;; ----------------------------------------------------------------------------
;; Pull execution state (the #56 record migration): the Context and the
;; frames are call-local host state, never Eve values. The Context
;; carries the :visitor fn — a call argument; the frames are the pull
;; interpreter's stack: they hold host transients (acc), datoms (which
;; belong to the DB's own domain, not to any frame), and parsed
;; patterns. Storing any of this in an Eve value would put functions
;; and foreign-domain datoms in Eve memory, which the
;; no-functions-in-Eve-memory ruling forbids; so they are plain host
;; maps with :kind dispatch, replacing the def-eve-record frame types
;; and the IFrame protocol. acc stays a host transient inside the host
;; map for the same reason: it accumulates one call's output and is
;; never an Eve value.

(defn- ->Context [db visitor]
  {:db db :visitor visitor})

(defn visit [context pattern e a v]
  (when-some [visitor (:visitor context)]
    (visitor pattern e a v)))

(defn- apply-xform [attr v]
  ((dpp/xform-fn attr) v))

(defn- attr-str [attr]
  (or (:as attr) (:name attr)))

(defn- ->ResultFrame [value datoms]
  {:kind :result :value value :datoms datoms})

(defn- ->MultivalAttrFrame [acc attr datoms]
  {:kind :multival-attr :acc acc :attr attr :datoms datoms})

(defn- ->MultivalRefAttrFrame [seen recursion-limits acc pattern attr datoms]
  {:kind :multival-ref-attr :seen seen :recursion-limits recursion-limits
   :acc acc :pattern pattern :attr attr :datoms datoms})

(defn- ->AttrsFrame [seen recursion-limits acc pattern attr attrs datoms id]
  {:kind :attrs :seen seen :recursion-limits recursion-limits :acc acc
   :pattern pattern :attr attr :attrs attrs :datoms datoms :id id})

(defn- ->ReverseAttrsFrame [seen recursion-limits acc pattern attr attrs id]
  {:kind :reverse-attrs :seen seen :recursion-limits recursion-limits
   :acc acc :pattern pattern :attr attr :attrs attrs :id id})

(declare frame-run frame-merge ref-frame)

(defn- frame-str [frame]
  (case (:kind frame)
    :result (str "ResultFrame<value=" (:value frame) ">")
    :multival-attr (str "MultivalAttrFrame<attr=" (attr-str (:attr frame)) ">")
    :multival-ref-attr (str "MultivalAttrFrame<attr=" (attr-str (:attr frame)) ">")
    :attrs (str "AttrsFrame<id=" (:id frame) ", attr=" (attr-str (:attr frame))
             ", attrs=" (str/join " " (map attr-str (:attrs frame))) ">")
    :reverse-attrs (str "ReverseAttrsFrame<id=" (:id frame) ", attr=" (attr-str (:attr frame))
                     ", attrs=" (str/join " " (map attr-str (:attrs frame))) ">")
    (str "Frame<" (:kind frame) ">")))

(defn- multival-attr-run [{:keys [acc attr datoms]}]
  (loop [acc acc
         datoms datoms]
    (util/cond+
      :let [datom (first-seq datoms)]

      (or (nil? datom) (not= (.-a datom) (:name attr)))
      [(->ResultFrame (not-empty (persistent! acc)) (or datoms ()))]

      ; got limit, skip rest of the datoms
      (and (:limit attr) (>= (count acc) (:limit attr)))
      (loop [datoms datoms]
        (let [datom (first-seq datoms)]
          (if (or (nil? datom) (not= (.-a datom) (:name attr)))
            [(->ResultFrame (persistent! acc) (or datoms ()))]
            (recur (next-seq datoms)))))

      :else
      (recur (conj! acc (.-v datom)) (next-seq datoms)))))

(defn- multival-ref-merge [{:keys [seen recursion-limits acc pattern attr datoms]} result]
  (->MultivalRefAttrFrame
    seen
    recursion-limits
    (conj-some! acc (:value result))
    pattern
    attr
    (next-seq datoms)))

(defn- multival-ref-run [{:keys [seen recursion-limits acc pattern attr datoms] :as this} context]
  (util/cond+
    :let [datom (first-seq datoms)]

    (or (nil? datom) (not= (.-a datom) (:name attr)))
    [(->ResultFrame (not-empty (persistent! acc)) (or datoms ()))]

    ; got limit, skip rest of the datoms
    (and (:limit attr) (>= (count acc) (:limit attr)))
    (loop [datoms datoms]
      (let [datom (first-seq datoms)]
        (if (or (nil? datom) (not= (.-a datom) (:name attr)))
          [(->ResultFrame (persistent! acc) (or datoms ()))]
          (recur (next-seq datoms)))))

    :let [id (if (:reverse? attr) (.-e datom) (.-v datom))]

    :else
    [this (ref-frame context seen recursion-limits pattern attr id)]))

(defn- attrs-merge [{:keys [seen recursion-limits acc pattern attr attrs datoms id]} result]
  (->AttrsFrame
    seen
    recursion-limits
    (assoc-some! acc (:as attr) (apply-xform attr (:value result)))
    pattern
    (first-seq attrs)
    (next-seq attrs)
    (not-empty (or (:datoms result) (next-seq datoms)))
    id))

(defn- attrs-run [{:keys [seen recursion-limits pattern id]} context acc attr attrs datoms]
  (loop [acc    acc
         attr   attr
         attrs  attrs
         datoms datoms]
    (util/cond+
      ;; exit
      (and (nil? datoms) (nil? attr))
      [(->ReverseAttrsFrame seen recursion-limits acc pattern (first-seq (:reverse-attrs pattern)) (next-seq (:reverse-attrs pattern)) id)]

      ;; :db/id
      (and (some? attr) (= :db/id (:name attr)))
      (recur (assoc! acc (:as attr) (apply-xform attr id)) (first-seq attrs) (next-seq attrs) datoms)

      :let [datom (first-seq datoms)
            cmp          (when (and datom attr)
                           (compare (:name attr) (.-a datom)))
            attr-ahead?  (or (nil? attr) (and cmp (pos? cmp)))
            datom-ahead? (or (nil? datom) (and cmp (neg? cmp)))]

      ;; wildcard
      (and (:wildcard? pattern) (some? datom) attr-ahead?)
      (let [datom-attr (lru/-get
                         db/pull-attrs-cache
                         (.-a datom)
                         ;; parse work: construct in the parse domain like
                         ;; every other parse-pattern output — the attr
                         ;; embeds the parse-domain default patterns, and
                         ;; eve.record refuses cross-domain storage.
                         #(db/call-in-parse-domain
                            (fn [] (dpp/parse-attr-name (:db context) (.-a datom)))))]
        (recur acc datom-attr (when attr (conj-seq attrs attr)) datoms))

      ;; advance datom
      attr-ahead?
      (recur acc attr attrs (next-seq datoms))

      :do (visit context :db.pull/attr id (:name attr) nil)

      ;; advance attr
      (and datom-ahead? (nil? attr))
      (recur acc (first-seq attrs) (next-seq attrs) datoms)

      ;; default
      (and datom-ahead? (some? (:default attr)))
      (recur (assoc! acc (:as attr) (:default attr)) (first-seq attrs) (next-seq attrs) datoms)

      ;; xform
      datom-ahead?
      (if-some [value (apply-xform attr nil)]
        (recur (assoc! acc (:as attr) value) (first-seq attrs) (next-seq attrs) datoms)
        (recur acc (first-seq attrs) (next-seq attrs) datoms))

      ;; matching attr
      (and (:multival? attr) (:ref? attr))
      [(->AttrsFrame seen recursion-limits acc pattern attr attrs datoms id)
       (->MultivalRefAttrFrame seen recursion-limits (transient []) pattern attr datoms)]

      (:multival? attr)
      [(->AttrsFrame seen recursion-limits acc pattern attr attrs datoms id)
       (->MultivalAttrFrame (transient []) attr datoms)]

      (:ref? attr)
      [(->AttrsFrame seen recursion-limits acc pattern attr attrs datoms id)
       (ref-frame context seen recursion-limits pattern attr (.-v datom))]

      :else
      (recur
        (assoc! acc (:as attr) (apply-xform attr (.-v datom)))
        (first-seq attrs)
        (next-seq attrs)
        (next-seq datoms)))))

(defn- reverse-attrs-merge [{:keys [seen recursion-limits acc pattern attr attrs id]} result]
  (->ReverseAttrsFrame
    seen
    recursion-limits
    (assoc-some! acc (:as attr) (apply-xform attr (:value result)))
    pattern
    (first-seq attrs)
    (next-seq attrs)
    id))

(defn- reverse-attrs-run [{:keys [seen recursion-limits pattern id]} context acc attr attrs]
  (loop [acc   acc
         attr  attr
         attrs attrs]
    (util/cond+
      (nil? attr)
      [(->ResultFrame (not-empty (persistent! acc)) nil)]

      :let [name   (:name attr)
            db     (:db context)
            ;; Concrete-DB avet slice and FilteredDB -search are the
            ;; same ISearch pattern [_ a v]; always go through the protocol.
            datoms (db/-search db [nil name id])]

      :do (visit context :db.pull/reverse nil name id)

      (and (empty? datoms) (some? (:default attr)))
      (recur (assoc! acc (:as attr) (:default attr)) (first-seq attrs) (next-seq attrs))

      (empty? datoms)
      (recur acc (first-seq attrs) (next-seq attrs))

      (:component? attr)
      [(->ReverseAttrsFrame seen recursion-limits acc pattern attr attrs id)
       (ref-frame context seen recursion-limits pattern attr (.-e (first-seq datoms)))]

      :else
      [(->ReverseAttrsFrame seen recursion-limits acc pattern attr attrs id)
       (->MultivalRefAttrFrame seen recursion-limits (transient []) pattern attr datoms)])))

(defn- frame-run [frame context]
  (case (:kind frame)
    :multival-attr (multival-attr-run frame)
    :multival-ref-attr (multival-ref-run frame context)
    :attrs (attrs-run frame context (:acc frame) (:attr frame) (:attrs frame) (:datoms frame))
    :reverse-attrs (reverse-attrs-run frame context (:acc frame) (:attr frame) (:attrs frame))
    (throw (ex-info (str "Frame does not implement -run: " (frame-str frame)) {:frame frame}))))

(defn- frame-merge [frame result]
  (case (:kind frame)
    :multival-ref-attr (multival-ref-merge frame result)
    :attrs (attrs-merge frame result)
    :reverse-attrs (reverse-attrs-merge frame result)
    (throw (ex-info (str "Frame does not implement -merge: " (frame-str frame)) {:frame frame}))))
(defn- auto-expanding? [attr]
  (or
    (:recursive? attr)
    (and
      (:component? attr)
      (:wildcard? (:pattern attr)))))

(defn ref-frame [context seen recursion-limits pattern attr id]
  (util/cond+
    (not (auto-expanding? attr))
    (attrs-frame context seen recursion-limits (:pattern attr) id)

    (seen id)
    (->ResultFrame {:db/id id} nil)

    :let [lim (recursion-limits attr)]

    (and lim (<= lim 0))
    (->ResultFrame nil nil)

    :let [seen' (conj seen id)
          recursion-limits' (cond
                              lim                        (update recursion-limits attr dec)
                              (:recursion-limit attr)    (assoc recursion-limits attr (dec (:recursion-limit attr)))
                              :else                      recursion-limits)]

    :else
    (attrs-frame context seen' recursion-limits' (if (:recursive? attr) pattern (:pattern attr)) id)))

(defn attrs-frame [context seen recursion-limits pattern id]
  (let [db (:db context)
        datoms (util/cond+
                 ;; Wildcard / range slices on a concrete DB are the
                 ;; ISearch / IIndexAccess paths already used for
                 ;; FilteredDB; always go through the protocol.
                 (:wildcard? pattern)
                 (db/-search db [id])

                 (nil? (:first-attr pattern))
                 nil

                 :let [from (:name (:first-attr pattern))
                       to   (:name (:last-attr pattern))]

                 :else
                 (->> (db/-seek-datoms db :eavt id from nil nil)
                   (take-while
                     (fn [d]
                       (and
                         (= (.-e d) id)
                         (<= (compare (.-a d) to) 0))))))]
    (when (:wildcard? pattern)
      (visit context :db.pull/wildcard id nil nil))
    (->AttrsFrame
      seen
      recursion-limits
      (transient {})
      pattern
      (first-seq (:attrs pattern))
      (next-seq (:attrs pattern))
      datoms
      id)))

(defn pull-impl [parsed-opts id]
  (let [{context :context
         pattern :pattern} parsed-opts]
    ;; Construction law: the frames and the result maps are constructed
    ;; in the pulled DB's own domain.
    (db/call-in-db-domain (:db context)
      (fn []
        (when-some [eid (db/entid (:db context) id)]
          (loop [stack (list (attrs-frame context #{} {} pattern eid))]
            (util/cond+
              :let [last   (first-seq stack)
                    stack' (next-seq stack)]

              (not= :result (:kind last))
              (recur (reduce conj-seq stack' (frame-run last context)))

              (nil? stack')
              (:value last)

              :let [penultimate (first-seq stack')
                    stack''     (next-seq stack')]

              :else
              (recur (conj-seq stack'' (frame-merge penultimate last))))))))))

(defn parse-opts
  ([db pattern] (parse-opts db pattern nil))
  ([db pattern {:keys [visitor]}]
   {:pattern (lru/-get db/pull-patterns-cache pattern #(dpp/parse-pattern db pattern))
    :context (->Context db visitor)}))

(defn pull
  "Supported opts:

   :visitor a fn of 4 arguments, will be called for every entity/attribute pull touches

   (:db.pull/attr     e   a   nil) - when pulling a normal attribute, no matter if it has value or not
   (:db.pull/wildcard e   nil nil) - when pulling every attribute on an entity
   (:db.pull/reverse  nil a   v  ) - when pulling reverse attribute"
  ([db pattern id] (pull db pattern id {}))
  ([db pattern id opts]
   {:pre [(db/db? db)]}
   (let [parsed-opts (parse-opts db pattern opts)]
     (pull-impl parsed-opts id))))

(defn pull-many
  ([db pattern ids] (pull-many db pattern ids {}))
  ([db pattern ids opts]
   {:pre [(db/db? db)]}
   (let [parsed-opts (parse-opts db pattern opts)]
     (mapv #(pull-impl parsed-opts %) ids))))
