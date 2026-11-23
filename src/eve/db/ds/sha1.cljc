(ns ^:no-doc eve.db.ds.sha1
  "SHA-1 (FIPS 180-4) over a string's UTF-8 bytes, written in portable
   Clojure: for :clj-reading hosts with no JDK MessageDigest provider
   (jolt, measured: java.security.MessageDigest is unprovided). The JVM
   keeps MessageDigest; both answer the same 40 hex digits, so a db-fn's
   content-addressed var name is the same on every host.")

(defn- utf8
  "The UTF-8 bytes (0..255 ints) of s, surrogate pairs joined."
  [s]
  (let [n (count s)]
    (loop [i 0 out (transient [])]
      (if (>= i n)
        (persistent! out)
        (let [c (int (nth s i))
              hi? (and (>= c 0xD800) (<= c 0xDBFF) (< (inc i) n))
              c2 (when hi? (int (nth s (inc i))))
              [cp step] (if (and hi? (>= c2 0xDC00) (<= c2 0xDFFF))
                          [(+ 0x10000 (bit-shift-left (- c 0xD800) 10) (- c2 0xDC00)) 2]
                          [c 1])]
          (recur (+ i step)
                 (cond
                   (< cp 0x80) (conj! out cp)
                   (< cp 0x800) (-> out (conj! (bit-or 0xC0 (bit-shift-right cp 6)))
                                    (conj! (bit-or 0x80 (bit-and cp 0x3F))))
                   (< cp 0x10000) (-> out (conj! (bit-or 0xE0 (bit-shift-right cp 12)))
                                      (conj! (bit-or 0x80 (bit-and (bit-shift-right cp 6) 0x3F)))
                                      (conj! (bit-or 0x80 (bit-and cp 0x3F))))
                   :else (-> out (conj! (bit-or 0xF0 (bit-shift-right cp 18)))
                             (conj! (bit-or 0x80 (bit-and (bit-shift-right cp 12) 0x3F)))
                             (conj! (bit-or 0x80 (bit-and (bit-shift-right cp 6) 0x3F)))
                             (conj! (bit-or 0x80 (bit-and cp 0x3F)))))))))))

(def ^:private mask 0xFFFFFFFF)
(defn- add32 [& xs] (bit-and (reduce + 0 xs) mask))
(defn- rotl [x n] (bit-and (bit-or (bit-shift-left x n) (bit-shift-right x (- 32 n))) mask))

(defn- pad [bs]
  (let [len (count bs)
        bits (* 8 len)
        zeros (mod (- 55 len) 64)]
    (-> bs (conj 0x80) (into (repeat zeros 0))
        (into (map (fn [k] (bit-and (bit-shift-right bits (* 8 (- 7 k))) 0xFF)) (range 8))))))

(defn- block-words [bs off]
  (mapv (fn [j] (let [o (+ off (* 4 j))]
                  (bit-or (bit-shift-left (nth bs o) 24) (bit-shift-left (nth bs (+ o 1)) 16)
                          (bit-shift-left (nth bs (+ o 2)) 8) (nth bs (+ o 3)))))
        (range 16)))

(defn- schedule [w16]
  (loop [w w16]
    (if (= 80 (count w))
      w
      (let [t (count w)]
        (recur (conj w (rotl (bit-xor (nth w (- t 3)) (nth w (- t 8)) (nth w (- t 14)) (nth w (- t 16))) 1)))))))

(defn- compress [[h0 h1 h2 h3 h4] w]
  (loop [t 0 a h0 b h1 c h2 d h3 e h4]
    (if (= t 80)
      [(add32 h0 a) (add32 h1 b) (add32 h2 c) (add32 h3 d) (add32 h4 e)]
      (let [[f k] (cond (< t 20) [(bit-or (bit-and b c) (bit-and (bit-xor b mask) d)) 0x5A827999]
                        (< t 40) [(bit-xor b c d) 0x6ED9EBA1]
                        (< t 60) [(bit-or (bit-and b c) (bit-and b d) (bit-and c d)) 0x8F1BBCDC]
                        :else [(bit-xor b c d) 0xCA62C1D6])
            tmp (add32 (rotl a 5) f e k (nth w t))]
        (recur (inc t) tmp a (rotl b 30) c d)))))

(defn- hex8 [x]
  (apply str (map (fn [k] (nth "0123456789abcdef" (bit-and (bit-shift-right x (* 4 (- 7 k))) 0xF))) (range 8))))

(defn sha1-hex
  "The SHA-1 digest of s's UTF-8 bytes as 40 lowercase hex digits."
  [s]
  (let [m (pad (utf8 s))
        h (reduce (fn [h off] (compress h (schedule (block-words m off))))
                  [0x67452301 0xEFCDAB89 0x98BADCFE 0x10325476 0xC3D2E1F0]
                  (range 0 (count m) 64))]
    (apply str (map hex8 h))))
