(ns build
  "Build-time only: the JVM uberjar for the shootout's contract entry.
   Invoked by harness/run-jvm-aot.sh (ensure) with tools.build on the
   classpath as a build-time dep — it is NOT in the library's deps.edn
   paths and nothing here ships in the jar.

   The jar is one self-contained artifact: every namespace of the
   contract's reachable graph AOT-compiled with direct linking, the
   dependency jars (clojure, sci) copied in, data_readers.clj at the
   root, and NO Eve/ds/lib sources — a namespace missed by the compile
   is a runtime failure in the jar, never a source load. The timed run
   is exactly: java -jar one.jar <domain>."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.build.api :as b]))

;; The compile's transitive walk only reaches namespaces loaded while
;; compiling. These are reached at RUNTIME, by requiring-resolve or a
;; runtime require, so they are force-compiled. Derived by grepping
;; Eve (src + the :jvm adapter roots) and this repo for
;; requiring-resolve / runtime-require targets (2026-11-22):
;;   eve.flat.freeze/transfer/chunk/stream — eve.atom's flat deref path
;;     (the class-cache leg's AOT_LAZY set in harness/run-jvm.sh);
;;   eve.view — eve.atom resolves eve.view/wrap for wrapped values;
;;   the rest of the eve.flat.* family — eve.flat's delegate lanes and
;;     eve.flat.sister's per-name chunk-var resolves dispatch into
;;     check/column/container/image/io/place/sio/source/transit by
;;     symbol, and eve.codec resolves eve.flat.freeze/frozen-value;
;;   eve.wire — adapters/thread/jvm's eve.thread-host resolves
;;     eve.wire/encode|decode|data? for thread messages;
;;   eve.deftype.registry — statically required too, but eve.numeric
;;     also (require 'eve.deftype.registry) at runtime; listed so the
;;     jar's set is explicit.
;; sci.core is deliberately NOT compiled: db-fn's :sci kind resolves it
;; by requiring-resolve at runtime and it loads from the sci jar's own
;; .cljc sources, which the uber copies wholesale — same as the
;; class-cache leg, where sci is never AOT-compiled either.
(def forced-namespaces
  '[contract.one-uberjar
    eve.flat.freeze eve.flat.transfer eve.flat.chunk eve.flat.stream
    eve.view eve.wire eve.deftype.registry
    eve.flat.build eve.flat.check eve.flat.column eve.flat.container
    eve.flat.contract eve.flat.image eve.flat.io eve.flat.place
    eve.flat.sio eve.flat.source eve.flat.transit])

(defn uber
  "Compile the graph and assemble one.jar.
   :cp        colon-joined compile classpath (source roots + jars);
              the source roots come first, so the Eve tree the caller
              named is the one compiled (classpath first-wins).
   :class-dir scratch dir for the compiled classes.
   :out       the jar path to write.
   :ds-src    this repo's src root (data_readers.clj's home)."
  [{:keys [cp class-dir out ds-src]}]
  (let [roots (str/split cp #":")
        jars (filterv #(str/ends-with? % ".jar") roots)
        dirs (filterv #(not (str/ends-with? % ".jar")) roots)
        ;; synthetic lib entries, one per jar, for the hand-built bases
        jar-libs (into {}
                       (map-indexed (fn [i jar]
                                      [(symbol (str "uberjar.lib/lib-" i))
                                       {:paths [jar]}]))
                       jars)
        compile-basis {:libs jar-libs :classpath-roots roots}]
    (b/delete {:path class-dir})
    (b/compile-clj {:basis compile-basis
                    :src-dirs dirs
                    :class-dir class-dir
                    :ns-compile forced-namespaces
                    :compiler-options {:direct-linking true}})
    ;; The one source file that must ship: Clojure reads data_readers
    ;; from the classpath root at runtime (the ds Datom/DB readers).
    (b/copy-file {:src (str ds-src "/data_readers.clj")
                  :target (str class-dir "/data_readers.clj")})
    (b/uber {:class-dir class-dir
             :uber-file out
             :basis {:libs jar-libs :classpath-roots [class-dir]}
             :main 'contract.one-uberjar
             :manifest {"Main-Class" "contract.one_uberjar"}})
    (println "UBERJAR-BUILT" out)))
