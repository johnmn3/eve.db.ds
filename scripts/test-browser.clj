;; scripts/test-browser.sh [laws|bset] — a DS suite in a real browser.
;;
;; Modeled on Eve's scripts/test-browser.clj (bb test:browser), adapted to this repo:
;; builds the page with shadow-cljs (the :ds-browser build in shadow-cljs.edn, at :simple with no
;; output wrapper, so the kernel a worker importScripts keeps its names global) on a classpath of
;; Eve's :browser roots (hosts.edn in the Eve tree at the deps.edn pin) + this repo's src, test and
;; test/browser — serves it cross-origin isolated (COOP + COEP: SharedArrayBuffer needs it) on
;; 127.0.0.1, opens it in headless Chromium and waits for the page to POST its result to /result.
;; Exits 0 when the suite is green, 1 otherwise (a red suite still prints its full summary).
;;
;;   DS_CHROME (or EVE_CHROME): the Chromium binary; default: a Playwright-installed Chromium from
;;   the per-OS cache (~/.cache/ms-playwright on Linux). DS_BROWSER_TIMEOUT_S (default 900).
;;   The Eve checkout for the pin is resolved from the tools.deps gitlibs cache; a missing checkout
;;   is materialized with `clojure -P` (and, failing that, located on the resolved classpath).
(require '[babashka.fs :as fs]
         '[babashka.process :as p]
         '[clojure.edn :as edn]
         '[clojure.string :as str])

(def root (str (fs/absolutize ".")))
(def suite (or (first *command-line-args*) "laws"))
(when-not (#{"laws" "bset" "u2" "u3" "u4" "u5"} suite)
  (println "test-browser: unknown suite" (pr-str suite) "— expected laws, bset, u2, u3, u4 or u5")
  (System/exit 2))

(def out (str root "/target/ds-browser"))
(def public (str out "/public"))

(defn- step [& xs] (apply println "test-browser:" xs) (flush))

;; --- the Eve tree at the deps.edn pin, and its :browser roots -------------------------------

(def pin (get-in (edn/read-string (slurp (str root "/deps.edn"))) [:deps 'eve/eve :git/sha]))
(step "eve pin" pin)

(defn- eve-dir-candidate [gitlibs]
  (let [d (str gitlibs "/libs/eve/eve/" pin)]
    (when (fs/exists? (str d "/hosts.edn")) d)))

(def eve-dir
  (or (eve-dir-candidate (or (System/getenv "GITLIBS") (str (System/getProperty "user.home") "/.gitlibs")))
      (do (step "eve checkout not in gitlibs; materializing with clojure -P")
          (p/shell {:dir root :continue true :out :string :err :string} "clojure" "-P")
          (eve-dir-candidate (or (System/getenv "GITLIBS") (str (System/getProperty "user.home") "/.gitlibs"))))
      (let [cp (:out (p/shell {:dir root :continue true :out :string :err :string} "clojure" "-Spath"))
            hit (first (filter #(str/ends-with? % "/src")
                               (str/split (str/trim cp) #":")))]
        (when (and hit (fs/exists? (str (fs/parent hit) "/hosts.edn")))
          (str (fs/parent hit))))
      (throw (ex-info (str "test-browser: cannot locate the Eve checkout for pin " pin
                           " — run clojure -P once, or set GITLIBS") {}))))
(step "eve tree" eve-dir)

;; src + the hosts.edn :browser row, absolute — the same expansion Eve's `bb classpath browser`
;; prints (verified identical at this pin).
(def eve-roots
  (mapv (fn [r] (str eve-dir "/" r))
        (into ["src"] (:browser (edn/read-string (slurp (str eve-dir "/hosts.edn")))))))

;; --- build the page -------------------------------------------------------------------------

(fs/create-dirs public)
(spit (str public "/index.html")
      (str "<!doctype html><meta charset=utf-8><title>eve.db.ds browser tests</title>"
           "<script>window.DS_SUITE=\"" suite "\"</script>"
           "<script src=\"/js/main.js\"></script>"))

(step "building the page (shadow-cljs release ds-browser, :simple)")
(let [sdeps (pr-str {:paths (into eve-roots ["src" "test" "test/browser"])
                     :deps {'thheller/shadow-cljs {:mvn/version "3.3.6"}}})
      r (p/shell {:dir root :continue true :out :string :err :string}
                 "clojure" "-Sdeps" sdeps "-M" "-m" "shadow.cljs.devtools.cli" "release" "ds-browser")]
  (spit (str out "/build.log") (str (:out r) "\n" (:err r)))
  (when-not (zero? (:exit r))
    (println (:out r) (:err r))
    (step "build FAILED — see" (str out "/build.log"))
    (System/exit 1)))

;; --- serve cross-origin isolated, drive headless Chromium -----------------------------------

(def result (promise))

(defn- serve [req]
  (let [uri (:uri req)
        isolated {"Cross-Origin-Opener-Policy" "same-origin" "Cross-Origin-Embedder-Policy" "require-corp"
                  "Cross-Origin-Resource-Policy" "same-origin" "Cache-Control" "no-store"}]
    (cond
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

(defn- find-chrome
  "DS_CHROME, then EVE_CHROME, then a Playwright-installed Chromium under the per-OS cache dir."
  []
  (or (System/getenv "DS_CHROME")
      (System/getenv "EVE_CHROME")
      (let [mac? (str/includes? (str/lower-case (System/getProperty "os.name")) "mac")
            ;; user.home and $HOME can differ (this fleet's exec runs as root with
            ;; HOME=/home/hatch, and the Playwright cache lives under $HOME) — search both.
            homes (distinct [(System/getProperty "user.home") (System/getenv "HOME")])
            caches (map (fn [home] (if mac?
                                     (str home "/Library/Caches/ms-playwright")
                                     (str home "/.cache/ms-playwright")))
                        homes)
            full (if mac?
                   ["chromium-*/chrome-mac*/Chromium.app/Contents/MacOS/Chromium"]
                   ["chromium-*/chrome*/chrome"])
            shell (if mac?
                    ["chromium_headless_shell-*/chrome-mac*/headless_shell"]
                    ["chromium_headless_shell-*/chrome*/chrome-headless-shell"
                     "chromium_headless_shell-*/chrome*/headless_shell"])
            newest (fn [pats] (last (sort (map str (mapcat (fn [cache] (mapcat #(fs/glob cache %) pats)) caches)))))]
        (or (newest full)
            (newest shell)))))

;; stale profiles from this harness's earlier runs only (prefix ds-chrome-)
(doseq [d (fs/glob (fs/temp-dir) "ds-chrome-*")]
  (try (fs/delete-tree d) (catch Exception _ nil)))

(require '[org.httpkit.server :as http])
(def stop (http/run-server serve {:ip "127.0.0.1" :port 0 :legacy-return-value? false}))
(def port (http/server-port stop))
(def profile (str (fs/create-temp-dir {:prefix "ds-chrome-"})))
(step (str "serving on 127.0.0.1:" port "; opening headless Chromium for suite " suite))
(def chrome (p/process {:out :string :err :string}
                       (or (find-chrome)
                           (throw (ex-info "test-browser: no Chromium found — set DS_CHROME or run: npx playwright install chromium" {})))
                       "--headless=new" "--no-sandbox" "--disable-gpu" "--no-proxy-server" "--no-first-run"
                       "--enable-logging=stderr" "--v=0"
                       (str "--user-data-dir=" profile) (str "http://127.0.0.1:" port "/index.html")))

(def timeout-s (parse-long (or (System/getenv "DS_BROWSER_TIMEOUT_S") "900")))
(def got (deref result (* 1000 timeout-s) nil))
(p/destroy-tree chrome)
(deref chrome 10000 nil)
(http/server-stop! stop)
(try (fs/delete-tree profile) (catch Exception _ nil))

(let [log (str (:err (deref chrome 1000 nil)))]
  ;; Chromium's log, the page's and its workers' console lines among it
  (spit (str out "/chrome-" suite ".log") log)
  (when (or (not got) (System/getenv "DS_BROWSER_CONSOLE"))
    (doseq [l (remove #(re-find #":(ERROR|WARNING):(?!CONSOLE)" %) (str/split-lines log))] (println "  log:" l))))

(if-not got
  (do (step (str "no result from the page within " timeout-s " s — see " out "/chrome-" suite ".log"))
      (System/exit 1))
  (let [r (edn/read-string got)]
    (spit (str out "/result-" suite ".edn") (pr-str r))
    (step "result" (pr-str r))
    (if (and (= :suite (:op r))
             (zero? (+ (get-in r [:summary :fail] 0) (get-in r [:summary :error] 0)))
             (pos? (get-in r [:summary :test] 0)))
      (System/exit 0)
      (System/exit 1))))
