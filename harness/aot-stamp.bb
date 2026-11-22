#!/usr/bin/env bb
;; aot-stamp.bb — compute a cohost AOT basis stamp + artifact dir with
;; cohost.aot's own functions (the contract is the code: basis-stamp /
;; artifact-dir from $COHOST_DIR/src/cohost/aot.cljc, unmodified).
;;
;;   COHOST_DIR=<cohost clone> bb harness/aot-stamp.bb <sister> <runtime> <extra> <file>...
;;
;; Prints two lines: the stamp, then the artifact dir
;; (<cache-root>/aot/<sister>/<stamp>). The file set is content-hashed
;; by basis-stamp in sorted path order; mtimes never enter.
(require '[babashka.fs :as fs])

(let [cohost-dir (or (System/getenv "COHOST_DIR")
                     (throw (ex-info "COHOST_DIR not set" {})))
      src (str (fs/file cohost-dir "src" "cohost" "aot.cljc"))]
  (load-file src)
  (let [[sister runtime extra & files] *command-line-args*
        stamp ((resolve 'cohost.aot/basis-stamp)
               {:sister  (keyword sister)
                :runtime runtime
                :files   (set files)
                :extra   extra})]
    (println stamp)
    (println ((resolve 'cohost.aot/artifact-dir) (keyword sister) stamp))))
