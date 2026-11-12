(ns ^:no-doc eve.db.ds.pull-parser
  #?(:cljs (:require-macros [eve.record :as erecord]))
  (:require
    [eve.core :as e]
    [eve.record :as erecord]
    #?(:cljs [eve.record])
    [eve.db.ds.built-ins :as built-ins]
    [eve.db.ds.db :as db]
    [eve.db.ds.util :as util]
    ;; PORT DELTA (F-CLJE-HOST-REFERS): util/if+ expansions emit
    ;; (volatile! nil) into the using ns; see eve.db.ds.util
    #?(:clje [eve.host :refer [volatile!]])))

;; PullAttr/PullPattern are Eve's slab eve.record types (the #56 record
;; migration): portable field values only — keywords, numbers, strings,
;; nested records. Two consequences:
;;
;; - Updates go through eve.core/assoc (e/assoc), not clojure.core/assoc:
;;   on SCI hosts clojure.core/assoc does not dispatch to a slab
;;   record's IAssociative (measured on bb 1.13.226 at the 383097e pin:
;;   ClassCastException "SciType cannot be cast to
;;   clojure.lang.Associative"), while e/assoc works on every host.
;;
;; - No functions in Eve memory: the :xform option is NOT stored as a
;;   fn. A symbol spec is stored as its string (:xform-spec) and
;;   resolved at pull time (xform-fn); a raw fn spec is registered in
;;   the host-side xform registry and only its id is stored
;;   (:xform-id) — the Conn-listener/FilteredDB-pred pattern. Neither
;;   field holds a fn, so parsed patterns stay storable in Eve memory
;;   (the DB pull-patterns cache) and durable across processes; a
;;   pattern whose fn is not registered in this process fails at pull
;;   time with a clear error.
(erecord/defrecord PullAttr [as default limit name pattern recursion-limit recursive? reverse? xform-spec xform-id multival? ref? component?])

(erecord/defrecord PullPattern [attrs first-attr last-attr reverse-attrs wildcard?])

(defonce ^:private xform-fns (atom {}))
(defonce ^:private xform-ids-by-fn (atom {}))
(defonce ^:private xform-ids (atom 0))

(defn register-xform!
  "Register a pull :xform fn host-side; returns its id. Idempotent per
   fn object: the same fn always gets the same id, so a pattern parsed
   twice (or an expected pattern built by registering the same fn)
   compares equal."
  [f]
  (or (get @xform-ids-by-fn f)
      (let [id (swap! xform-ids inc)]
        (swap! xform-fns assoc id f)
        (swap! xform-ids-by-fn assoc f id)
        id)))

(declare resolve-xform)

(defn xform-fn
  "The fn a PullAttr's :xform option denotes, resolved at pull time:
   registry id first, then the stored symbol spec, else identity."
  [attr]
  (cond
    (some? (:xform-id attr))
    (or (get @xform-fns (:xform-id attr))
      (throw (ex-info "Pull :xform function is not available in this process: functions are host-side call state and are not stored in Eve memory. Re-parse the pattern in a process where the fn is in scope."
               {:eve.db.ds/error :pull/xform-not-registered, :xform-id (:xform-id attr)})))

    (some? (:xform-spec attr))
    (resolve-xform (symbol (:xform-spec attr)))

    :else identity))

;; Construction law: the default patterns are parse data, constructed
;; in the explicit parse domain (eve.db.ds.db).
(def default-db-id-attr
  (db/call-in-parse-domain
    (fn [] (map->PullAttr {:name :db/id :as :db/id}))))

(def default-pattern-ref
  (db/call-in-parse-domain
    (fn [] (map->PullPattern {:attrs (list default-db-id-attr)}))))

(def default-pattern-component
  (db/call-in-parse-domain
    (fn [] (e/assoc default-pattern-ref :wildcard? true))))

(declare parse-pattern parse-pattern-impl parse-attr-spec)

; pattern             = [(attr-spec | map-spec | '* | "*")+]
; attr-spec           = attr-name | attr-expr | legacy-limit-expr | legacy-default-expr
; attr-name           = an edn keyword that names an attr
; attr-expr           = [attr-name attr-option+]
; map-spec            = {attr-spec (pattern | recursion-limit)}
; attr-option         = :as any-value | :limit multival-limit | :default any-value | :xform symbol
; recursion-limit     = positive-number | '...
; multival-limit      = positive-number | nil
; legacy-limit-expr   = [("limit" | 'limit) attr-spec multival-limit]
; legacy-default-expr = [("default" | 'default) attr-spec any-value]

(defn check [cond expected fragment]
  (when-not cond
    (throw (ex-info (str "Expected " expected ", got: " (pr-str fragment))
             {:error :parser/pull, :fragment fragment}))))

(defn parse-attr-name [db attr-spec]
  (let [reverse?   (db/reverse-ref? attr-spec)
        name       (if reverse? (db/reverse-ref attr-spec) attr-spec)
        ref?       (db/ref? db name)
        component? (db/component? db name)
        multival?  (db/multival? db name)]
    (map->PullAttr
      {:as         attr-spec
       :name       name
       :multival?  (when multival? true)
       :limit      (if multival? 1000 nil)
       :ref?       (when ref? true)
       :component? (when component? true)
       :pattern    (cond
                     (not ref?) nil
                     reverse?   default-pattern-ref
                     component? default-pattern-component
                     :else      default-pattern-ref)
       :reverse?   (when reverse?
                     (check ref? "reverse attribute having :db.type/ref" attr-spec)
                     true)})))

(defn- check-limit [db pull-attr limit]
  (check (or (and (number? limit) (pos? limit)) (nil? limit)) "(positive-number | nil)" limit)
  (check (db/multival? db (:name pull-attr)) "limit attribute having :db.cardinality/many" (:name pull-attr)))

(defn resolve-xform [sym-or-fn]
  (or
    (when (fn? sym-or-fn)
      sym-or-fn)
    (get built-ins/query-fns sym-or-fn)
    #?(:clj (when (namespace sym-or-fn)
              (when-some [v (requiring-resolve sym-or-fn)]
                @v)) :cljr (when (namespace sym-or-fn)
              (when-some [v (requiring-resolve sym-or-fn)]
                @v)))
    (util/raise "Can't resolve symbol " sym-or-fn {:error :parser/pull, :fragment sym-or-fn})))

(defn parse-attr-expr [db attr-spec]
  (when-some [pull-attr (parse-attr-spec db (first attr-spec))]
    (check (even? (count (next attr-spec))) "even number of opts" attr-spec)
    (reduce
      (fn [pull-attr [key value]]
        (case key
          :as      (e/assoc pull-attr :as value)
          :limit   (do
                     (check-limit db pull-attr value)
                     (e/assoc pull-attr :limit value))
          :default (e/assoc pull-attr :default value)
          :xform   (cond
                     ;; A raw fn is host-side call state: register it
                     ;; and store only the id (see the ns note).
                     (fn? value)
                     (e/assoc pull-attr :xform-id (register-xform! value))
                     ;; A symbol spec is validated now, stored as its
                     ;; string, and resolved at pull time (xform-fn).
                     (or (symbol? value) (string? value))
                     (do
                       (resolve-xform (if (string? value) (symbol value) value))
                       (e/assoc pull-attr :xform-spec (str value)))
                     :else
                     (check false ":xform to be a symbol or a fn" attr-spec))
          #_else   (check false "one of :as, :limit, :default, :xform" attr-spec)))
      pull-attr
      (partition 2 (next attr-spec)))))

(defn parse-legacy-limit-expr [db attr-spec]
  (let [expected "['limit attr-name (positive-number | nil)]"]
    (when (#{'limit "limit"} (first attr-spec))
      (check (= (count attr-spec) 3) expected attr-spec)
      (let [[_ attr limit] attr-spec
            pull-attr (parse-attr-spec db attr)]
        (check-limit db pull-attr limit)
        (e/assoc pull-attr :limit limit)))))

(defn parse-legacy-default-expr [db attr-spec]
  (let [expected "['default attr-name any-value]"]
    (when (#{'default "default"} (first attr-spec))
      (check (= (count attr-spec) 3) expected attr-spec)
      (let [[_ attr default] attr-spec
            pull-attr (parse-attr-spec db attr)]
        (e/assoc pull-attr :default default)))))

(defn parse-attr-spec [db attr-spec]
  (cond
    (or (keyword? attr-spec)
      (and (string? attr-spec)
        (not (#{"default" "limit"} attr-spec))))
    (parse-attr-name db attr-spec)

    (sequential? attr-spec)
    (or
      (parse-attr-expr db attr-spec)
      (parse-legacy-limit-expr db attr-spec)
      (parse-legacy-default-expr db attr-spec)
      (check false "[attr-name attr-option+] | ['limit attr-name (positive-num | nil)] | ['default attr-name any-val]" attr-spec))

    :else nil))

(defn parse-map-spec [db attr-spec pattern]
  (let [pull-attr (parse-attr-spec db attr-spec)]
    (check (some? pull-attr) "attr-name | attr-expr" attr-spec)
    (check (db/ref? db (:name pull-attr)) "attribute having :db.type/ref" attr-spec)
    (cond
      (or (= '... pattern) (= "..." pattern))
      ;; nested 3-arity assocs: eve.core/assoc's variadic arity exists
      ;; on the JVM adapter but not on the SCI read (Eve gap, banked).
      (e/assoc (e/assoc (e/assoc pull-attr :pattern nil) :recursive? true) :recursion-limit nil)

      (number? pattern)
      (do
        (check (pos? pattern) "(positive-num | ...)" {attr-spec pattern})
        (e/assoc (e/assoc (e/assoc pull-attr :pattern nil) :recursive? true) :recursion-limit pattern))

      :else
      (e/assoc pull-attr :pattern (parse-pattern db pattern)))))

(defn- index-of [pred xs]
  (some (fn [[x idx]] (when (pred x) idx)) (map vector xs (range))))

(defn- conj-attr [pull-pattern pull-attr]
  ;; PORT DELTA (#56): the accumulator is a plain host map, not the
  ;; PullPattern record — after the eve.record migration the record's
  ;; fields hold Eve collections, whose seq/conj semantics under SCI
  ;; hosts differ from host collections; vanilla's seq fns over the
  ;; accumulator (sort-by/some/conj) assume host data. The slab record
  ;; is built once, at the end of parse-pattern-impl.
  (let [pattern-attr (if (:reverse? pull-attr) :reverse-attrs :attrs)
        attrs        (get pull-pattern pattern-attr)]
    (if-some [idx (index-of #(= (:as %) (:as pull-attr)) attrs)]
      (assoc pull-pattern pattern-attr (assoc attrs idx pull-attr))
      (assoc pull-pattern pattern-attr (conj attrs pull-attr)))))

;; PORT DELTA (F-LPY-SPLICE): unhinted defn name (a missing :lpy arm on a
;; name splice would delete the defn on basilisp; a plain defn is fine).
(defn parse-pattern [db pattern]
  ;; Construction law: parse output is process-local compiled data,
  ;; constructed in the explicit parse domain (eve.db.ds.db) — the same
  ;; domain the parsed patterns are cached in.
  (db/call-in-parse-domain (fn [] (parse-pattern-impl db pattern))))

(defn- parse-pattern-impl [db pattern]
  (check (sequential? pattern) "pattern to be sequential?" pattern)
  (loop [pattern pattern
         ;; host-map accumulator (see conj-attr's PORT DELTA note); the
         ;; PullPattern slab record is built once in the finalization.
         result {:attrs [] :reverse-attrs [] :wildcard? nil}]
    (util/cond+
      (empty? pattern)
      (let [attrs       (:attrs result)
            db-id?      (fn [^PullAttr attr] (#{:db/id ":db/id"} (:name attr)))
            key-fn      (fn [^PullAttr attr]
                          (let [name (:name attr)]
                            (cond
                              (keyword? name) name
                              (= ":" (subs name 0 1)) (keyword (subs name 1))
                              :eles (keyword name))))
            attrs       (if (and
                              (:wildcard? result)
                              (not (some db-id? (:attrs result))))
                          (conj attrs default-db-id-attr)
                          attrs)
            attrs       (list* (sort-by key-fn attrs))
            datom-attrs (remove db-id? attrs)
            first-attr  (first datom-attrs)
            last-attr   (last datom-attrs)]
        (map->PullPattern
          {:attrs         attrs
           :first-attr    first-attr
           :last-attr     last-attr
           :reverse-attrs (list* (sort-by key-fn (:reverse-attrs result)))
           :wildcard?     (:wildcard? result)}))

      :let [attr-spec (first pattern)]

      (or (= '* attr-spec) (= "*" attr-spec) (= :* attr-spec))
      (recur (next pattern) (assoc result :wildcard? true))

      (map? attr-spec)
      (let [result' (reduce-kv
                      (fn [result attr-spec pattern]
                        (conj-attr result (parse-map-spec db attr-spec pattern)))
                      result
                      attr-spec)]
        (recur (next pattern) result'))
        
      :let [pull-attr (parse-attr-spec db attr-spec)]

      (nil? pull-attr)
      (check false "attr-name | attr-expr | map-spec | *" attr-spec)
      
      :else
      (recur (next pattern) (conj-attr result pull-attr)))))
