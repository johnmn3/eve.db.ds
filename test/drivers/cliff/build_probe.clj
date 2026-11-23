(ns cliff.build-probe
    "A Logseq-shaped build on the Eve-atom conn, measured per page (2026-11-20 ET).

   Problem 2's driver (the static-eve seat's buildcheck) is not in this repository; this is a stand-in of the same
   shape for discriminating the build's DECELERATION, not for its digest: pages (:block/name), blocks with
   :block/page, :block/parent and :block/left chains (nested to depth 3), :block/refs to hub-skewed pages,
   :block/tags, TODO markers. One transact! per block, as an editor commits.

   Per page it prints the wall time, the datom count, the Eve domain's used slab (bitmap classes 0-5) and the JVM's
   used heap after a GC every 10 pages.

     clojure -M:test -m cliff.build-probe PAGES [BLOCKS-PER-PAGE]
"
    (:require [eve.db.ds.core :as ds]
              [eve.atom :as ea]
              [eve.slab-io :as slab]
              [eve.mem :as mem]
              [eve.deftype-proto.alloc :as alloc]
              [eve.deftype-proto.data :as d]
              [eve.retire :as retire]))

(def schema
     {:block/uuid    {:db/unique :db.unique/identity}
      :block/name    {:db/unique :db.unique/identity}
      :block/page    {:db/valueType :db.type/ref}
      :block/parent  {:db/valueType :db.type/ref}
      :block/left    {:db/valueType :db.type/ref}
      :block/refs    {:db/valueType :db.type/ref :db/cardinality :db.cardinality/many}
      :block/tags    {:db/valueType :db.type/ref :db/cardinality :db.cardinality/many}})

(defn- used-kb [conn]
       (let [rs (.-regions (slab/domain-ctx (alloc/domain-root (ea/atom-sio (.-eve-atom conn)))))]
            (/ (reduce + (for [i (range 6)]
                              (if-let [r (aget ^objects rs i)]
                                      (* (bit-shift-left 32 i) (- (mem/-load-i32 r 8) (mem/-load-i32 r 12)))
                                      0)))
               1024.0)))

(defn- heap-mb [gc?]
       (when gc? (System/gc))
       (let [rt (Runtime/getRuntime)] (/ (- (.totalMemory rt) (.freeMemory rt)) 1048576.0)))

(defn- block-bytes [off]
       (let [ci (alloc/decode-class-idx off)] (if (< ci 6) (bit-shift-left 32 ci) 0)))

(defn census
      "What the conn's atom holds: every block its root reaches (eve.retire/children), by kind — a value header by its
   type byte, a node by its kind — with bytes (bitmap classes; class 6 counted, not sized); an eve value array's
   slots by how they hold their value (2 a pointer, 4 an encoded block: flat EVE bytes) and an encoded block by its
   EVE tag; and the domain's used slab against the reached bytes (allocated, not reached)."
      [conn]
      (let [a    (.-eve-atom conn)
            ds   (.-domain-state a)
            sio  (:sio ds)
            root (mem/-load-i32 (:root-r ds) (d/atom-slot-offset (.-atom-slot-idx a) d/ATOM_SLOT_PTR_OFFSET))]
           (loop [stack [[:val root]] seen #{} acc {}]
                 (if (empty? stack)
                     (assoc acc :reached-kb (/ (reduce + (map block-bytes seen)) 1024.0) :used-kb (used-kb conn))
                     (let [[k o] (peek stack) stack (pop stack)]
                          (if (or (== o -1) (contains? seen o))
                              (recur stack seen acc)
                              (let [label (if (= k :val) (format "val-0x%02x" (slab/read-u8 sio o 0)) (if (vector? k) (first k) k))
                                    acc (-> acc (update-in [:blocks label] (fnil inc 0)) (update-in [:kb label] (fnil + 0) (/ (block-bytes o) 1024.0)))
                                    acc (if (and (= k :val) (== 0x1F (slab/read-u8 sio o 0)))
                                            (let [n (slab/read-i32 sio o 4)]
                                                 (reduce (fn [acc i] (update-in acc [:slots (slab/read-u8 sio o (+ 8 i))] (fnil inc 0))) acc (range n)))
                                            acc)
                                    acc (if (= k :vblock)
                                            (update-in acc [:vblock-tag (format "0x%02x" (slab/read-u8 sio o 6))] (fnil inc 0))
                                            acc)]
                                   (recur (into stack (map (fn [[_ ck c]] [ck c])) (retire/children sio k o)) (conj seen o) acc))))))))

(defn- page-txs
       "The transactions that build page p (deterministic in p): the page, then its blocks one by one."
       [p bpp rng]
       (let [pname (str "page-" p)
             hub   (fn [] (str "page-" (let [r (.nextDouble ^java.util.Random rng)]
                                      ;; hub-skewed: half the refs land on the first 5 pages
                                            (if (< r 0.5) (.nextInt ^java.util.Random rng (min 5 (inc p)))
                                                (.nextInt ^java.util.Random rng (inc p))))))]
            (cons [{:block/name pname :block/uuid (str "u-" pname)}]
                  (loop [b 0 out [] stack []]   ;; stack: [uuid-at-depth ...]; left sibling tracked per depth
                        (if (>= b bpp)
                            out
                            (let [depth (min (count stack) (.nextInt ^java.util.Random rng 3))
                                  stack (subvec stack 0 depth)
                                  uuid  (str "u-" p "-" b)
                                  parent (if (zero? depth) [:block/name pname] [:block/uuid (peek stack)])
                                  refs  (vec (distinct (repeatedly (.nextInt ^java.util.Random rng 3) hub)))
                                  tx    (cond-> {:block/uuid uuid
                                                 :block/page [:block/name pname]
                                                 :block/parent parent
                                                 :block/content (str "block " b " of " pname)}
                                                (seq refs) (assoc :block/refs (mapv (fn [n] [:block/name n]) refs))
                                                (zero? (mod b 7)) (assoc :block/marker "TODO")
                                                (zero? (mod b 5)) (assoc :block/tags [[:block/name (hub)]]))
                    ;; refs to pages that may not exist yet: create them first in the same tx
                                  pre   (mapv (fn [n] {:block/name n}) refs)
                                  tx    (if (pos? b) (assoc tx :block/left [:block/uuid (str "u-" p "-" (dec b))]) tx)]
                                 (recur (inc b) (conj out (conj pre tx)) (conj stack uuid))))))))

(defn -main [& args]
      (let [pages (Long/parseLong (or (first args) "20"))
            bpp   (Long/parseLong (or (second args) "30"))
            conn  (ds/create-conn schema)
            rng   (java.util.Random. 42)
            t-all (System/nanoTime)]
           (println "pages" pages "blocks/page" bpp)
           (dotimes [p pages]
                    (let [t0 (System/nanoTime)]
                         (doseq [tx (page-txs p bpp rng)]
          ;; a tag page must exist before its lookup ref resolves
                                (let [tags (keep #(some-> (:block/tags %) first second) tx)]
                                     (ds/transact! conn (into (mapv (fn [n] {:block/name n}) tags) tx))))
                         (let [secs (/ (- (System/nanoTime) t0) 1e9)
                               n    (count (ds/datoms (ds/db conn) :eavt))]
                              (println (format "page %4d  %7.3f s  datoms %7d  slab %9.1f KB  heap %7.1f MB"
                                               (inc p) secs n (used-kb conn) (heap-mb (zero? (mod (inc p) 10)))))
                              (when (and (System/getenv "CENSUS") (zero? (mod (inc p) (Long/parseLong (System/getenv "CENSUS")))))
                                    (println "  census" (pr-str (census conn))))
                              (flush))))
           (println "total" (/ (- (System/nanoTime) t-all) 1e9) "s")
           (shutdown-agents)
           (System/exit 0)))
