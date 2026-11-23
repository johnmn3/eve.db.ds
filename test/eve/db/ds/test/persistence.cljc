(ns eve.db.ds.test.persistence
    "U5's persistence slice: a :persistent conn's DB survives, an
   in-memory conn's does not.

   Close/reopen at Eve pin 323bfe3 has one measured caveat
   (GAP-U5-1, U5-STATUS.md): close-conn! on a persistent conn
   throws inside Eve's close-atom-domain!, so the proofs use the
   two close shapes that work and matter — reopen-by-adopt in
   process (the GUIDE §8 contract: a new create-conn on the same
   directory reads the same database back) and process exit +
   fresh process (the real restart shape, via a child JVM)."
    (:require
     ;; edn and str are used only inside #?(:clj ...) arms below;
     ;; kondo's :cljs pass cannot see them (dual-pass false
     ;; positive, measured at recount3).
     #_{:clj-kondo/ignore [:unused-namespace]}
     [clojure.edn :as edn]
     #_{:clj-kondo/ignore [:unused-namespace]}
     [clojure.string :as str]
     [clojure.test :as t :refer [is deftest]]
     [eve.db.ds.core :as d]
     [eve.db.ds.u5-support :as u5s])
    #?(:clj
       (:import
        [java.io File]
        [java.nio.file Files]
        [java.nio.file.attribute FileAttribute])))

#?(:clj
   (defn- temp-dir []
          (let [base (File. "target/u5-persist")]
               (.mkdirs base)
               (str (Files/createTempDirectory
                     (.toPath base) "db-"
                     (into-array FileAttribute []))))))

#?(:clj
   (defn- delete-recursively [path]
          (let [f (File. path)]
               (when (.exists f)
                     (doseq [child (reverse (file-seq f))]
                            (.delete ^File child))))))

#?(:clj
   (defn- run-child [mode dir]
          (let [java (str (System/getProperty "java.home")
                          File/separator "bin" File/separator "java")
                pb   (ProcessBuilder.
                      [java "-cp" (System/getProperty "java.class.path")
                       "clojure.main" "-m" "eve.db.ds.u5-persist-child"
                       mode dir])
                _    (.redirectErrorStream pb true)
                proc (.start pb)
                out  (slurp (.getInputStream proc))
                done (.waitFor proc 240 java.util.concurrent.TimeUnit/SECONDS)]
               (when-not done
                         (.destroyForcibly proc)
                         (throw (ex-info "u5 persist child timed out" {:mode mode :dir dir})))
               {:exit (.exitValue proc) :out out})))

(deftest test-persistent-reopen-adopt
         #?(:clj
            (let [dir (temp-dir)]
                 (try
                  (let [conn (d/create-conn u5s/gen-schema {:persistent dir})]
                       (d/transact! conn (u5s/gen-rows 40))
                       (let [before (u5s/content-via-conn (d/db conn))
                             conn2  (d/create-conn u5s/gen-schema {:persistent dir})
                             after  (u5s/content-via-core (d/db conn2))]
                            (is (= before after))
                            (is (= "page-7" (:page/name (d/entity (d/db conn2) 7))))
                            (is (= {:idx 7 :tags [:t :k2]}
                                   (:page/attach (d/entity (d/db conn2) 7))))))
                  (finally
                   (delete-recursively dir))))
            :cljs (is true "JVM-only: :persistent dirs are a host facility")))

(deftest test-persistent-cross-process
         #?(:clj
            (let [dir (temp-dir)]
                 (try
         ;; child process writes and exits (no close-conn!)
                  (let [{:keys [exit out]} (run-child "write" dir)]
                       (is (zero? exit) out)
                       (is (re-find #":u5-child-wrote 17\d" out) out))
         ;; this process opens the same dir: content must equal an
         ;; in-memory DB built from the same generator — the disk
         ;; path graded against a non-disk path
                  (let [mem-conn (d/create-conn u5s/gen-schema)]
                       (d/transact! mem-conn (u5s/gen-rows 40))
                       (let [expected (u5s/content-via-conn (d/db mem-conn))
                             conn     (d/create-conn u5s/gen-schema {:persistent dir})]
                            (is (= expected (u5s/content-via-core (d/db conn))))
             ;; write more here; a fresh child must read it back
                            (d/transact! conn [[:db/add 41 :page/name "page-41"]])
                            (let [{:keys [exit out]} (run-child "digest" dir)
                                  child-digest (edn/read-string
                                                (last (str/split-lines out)))]
                                 (is (zero? exit) out)
                                 (is (= (u5s/digest (d/db conn)) child-digest)))))
                  (finally
                   (delete-recursively dir))))
            :cljs (is true "JVM-only: child JVMs")))

(deftest test-in-memory-does-not-persist
         (let [conn (d/create-conn u5s/gen-schema)]
              (d/transact! conn (u5s/gen-rows 5))
              (is (pos? (count (d/datoms (d/db conn) :eavt))))
              (d/close-conn! conn)
              (is (= :eve.db.ds/conn-closed
                     (try (d/transact! conn [[:db/add 100 :page/name "x"]])
                          :no-throw
                          #?(:clj  (catch Throwable e (:error (ex-data e)))
                             :cljs (catch :default e (:error (ex-data e)))))))
    ;; a fresh in-memory conn starts empty: nothing persisted
              (let [conn2 (d/create-conn u5s/gen-schema)]
                   (is (= 0 (count (d/datoms (d/db conn2) :eavt)))))))
