;; scripts/ds_matrix_browser.clj — the browser column of the README host table.
;;
;;   bb scripts/ds_matrix_browser.clj build <eve-tree> <out-dir>
;;       builds the :ds-matrix-browser page (contract.ds-matrix-browser) on Eve's
;;       :browser roots (hosts.edn in <eve-tree>) + this repo's src, lib, lib-ds-deps
;;       and test/drivers, into <out-dir>/public (eve-sw.js beside it)
;;   bb scripts/ds_matrix_browser.clj run <out-dir> <bench|runtime> <opfs-dir> [arg...]
;;       one fresh process: serves the page cross-origin isolated on 127.0.0.1, opens
;;       it in headless Chromium on a fresh profile (so a fresh OPFS), and prints each
;;       row the page POSTs as it arrives. It stops itself 8 s before CELL_TIMEOUT
;;       (default 180) and exits 124 then, Chromium and its profile cleaned up.
;;
;; Called by scripts/ds_matrix.sh (browser). DS_CHROME / EVE_CHROME name the Chromium binary;
;; default: a Playwright-installed Chromium, then /opt/google/chrome/chrome.
(require '[babashka.fs :as fs]
         '[babashka.process :as p]
         '[clojure.edn :as edn]
         '[clojure.string :as str]
         '[org.httpkit.server :as http])

(def root (str (fs/absolutize (fs/parent (fs/parent *file*)))))
(def cmd (first *command-line-args*))

(defn- die [& xs] (binding [*out* *err*] (apply println "ds_matrix_browser:" xs)) (System/exit 2))

(defn build [eve out]
  (let [eve (str (fs/absolutize eve))
        public (str out "/public")
        roots (mapv #(str eve "/" %) (into ["src"] (:browser (edn/read-string (slurp (str eve "/hosts.edn"))))))
        sdeps (pr-str {:paths (into roots (mapv #(str root "/" %) ["src" "lib" "lib-ds-deps" "test/drivers"]))
                       :deps {'thheller/shadow-cljs {:mvn/version "3.3.6"}}})]
    (fs/create-dirs public)
    (spit (str public "/index.html")
          "<!doctype html><meta charset=utf-8><title>eve.db.ds host table: browser</title><script src=\"/js/main.js\"></script>")
    (fs/copy (str eve "/adapters/sw/browser/eve-sw.js") (str public "/eve-sw.js") {:replace-existing true})
    (let [r (p/shell {:dir root :continue true :out :string :err :string}
                     "clojure" "-Sdeps" sdeps "-M" "-m" "shadow.cljs.devtools.cli" "release" "ds-matrix-browser"
                     "--config-merge" (pr-str {:output-dir (str public "/js")}))]
      (spit (str out "/build.log") (str (:out r) "\n" (:err r)))
      (when-not (zero? (:exit r)) (println (:out r) (:err r)) (die "build FAILED, see" (str out "/build.log"))))
    (println "built" (str public "/js/main.js"))))

(defn- find-chrome []
  (or (System/getenv "DS_CHROME") (System/getenv "EVE_CHROME")
      (let [cache (str (System/getProperty "user.home") "/.cache/ms-playwright")]
        (last (sort (map str (concat (fs/glob cache "chromium-*/chrome*/chrome")
                                     (fs/glob cache "chromium_headless_shell-*/chrome*/chrome-headless-shell"))))))
      (when (fs/exists? "/opt/google/chrome/chrome") "/opt/google/chrome/chrome")
      (die "no Chromium found: set DS_CHROME")))

(defn run [out mode dir args]
  (let [public (str out "/public")
        _ (when-not (fs/exists? (str public "/js/main.js")) (die "no page: build first," public))
        cap (parse-long (or (System/getenv "CELL_TIMEOUT") "180"))
        result (promise)
        isolated {"Cross-Origin-Opener-Policy" "same-origin" "Cross-Origin-Embedder-Policy" "require-corp"
                  "Cross-Origin-Resource-Policy" "same-origin" "Cache-Control" "no-store"}
        serve (fn [req]
                (let [uri (:uri req)]
                  (cond
                    (and (= :post (:request-method req)) (= "/row" uri))
                    (do (println (slurp (:body req))) (flush) {:status 200 :headers isolated :body "ok"})
                    (and (= :post (:request-method req)) (= "/result" uri))
                    (do (deliver result (slurp (:body req))) {:status 200 :headers isolated :body "ok"})
                    :else
                    (let [f (fs/file public (subs (if (= "/" uri) "/index.html" uri) 1))]
                      (if (and (fs/regular-file? f) (str/starts-with? (str (fs/canonicalize f)) (str (fs/canonicalize public))))
                        {:status 200
                         :headers (assoc isolated "Content-Type" (cond (str/ends-with? uri ".js") "text/javascript"
                                                                       (str/ends-with? uri ".html") "text/html"
                                                                       :else "text/plain"))
                         :body f}
                        {:status 404 :headers isolated :body "not found"})))))
        stop (http/run-server serve {:ip "127.0.0.1" :port 0 :legacy-return-value? false})
        port (http/server-port stop)
        profile (str (fs/create-temp-dir {:prefix "ds-matrix-chrome-"}))
        url (str "http://127.0.0.1:" port "/index.html?mode=" mode "&dir=" dir "&args=" (str/join "," args))
        chrome (p/process {:out :string :err :string} (find-chrome)
                          "--headless=new" "--no-sandbox" "--disable-gpu" "--no-proxy-server" "--no-first-run"
                          "--disable-extensions" "--enable-logging=stderr" "--v=0"
                          (str "--user-data-dir=" profile) url)
        got (deref result (* 1000 (max 10 (- cap 8))) nil)]
    (p/destroy-tree chrome)
    (deref chrome 5000 nil)
    (http/server-stop! stop)
    (try (fs/delete-tree profile) (catch Exception _ nil))
    (spit (str out "/chrome-last.log") (str (:err (deref chrome 1000 nil))))
    (cond (nil? got) (do (println (pr-str {:timed-out-s (- cap 8)})) (System/exit 124))
          (str/includes? got ":died") (do (println got) (System/exit 1))
          :else (System/exit 0))))

(case cmd
  "build" (let [[_ eve out] *command-line-args*] (build eve out))
  "run" (let [[_ out mode dir & args] *command-line-args*] (run out mode dir args))
  (die "usage: build <eve-tree> <out-dir> | run <out-dir> <bench|runtime> <opfs-dir> [arg...]"))
