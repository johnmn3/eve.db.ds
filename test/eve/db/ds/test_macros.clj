(ns eve.db.ds.test-macros
    "Macro-time registration of the thrown-msg? assert-expr for
   ClojureScript (U6 browser finding, 2026-11-20).

   In modern ClojureScript, cljs.test/assert-expr is a COMPILE-TIME
   multimethod (cljs/test.cljc, dispatched from the `is` macro at
   macroexpansion): there is no runtime assert-expr var to add a
   method to. A runtime (defmethod t/assert-expr ...) in a :cljs
   arm compiles to an add-method call on undefined and kills the
   page at load — the u2/u3/u4 browser suites all died there before
   running a single test. Methods for cljs must be registered from
   a Clojure (macro) namespace, as here; the support namespaces
   pull this file in via :require-macros so the method exists
   before any test namespace's `is` forms macroexpand.

   The body is the supports' former :cljs arm verbatim in shape,
   at the macro signature [menv msg form]; the JVM registration
   stays in the supports' :clj arms (clojure.test/assert-expr IS
   a runtime multimethod there)."
    (:require [cljs.test :as t]))

(defmethod t/assert-expr 'thrown-msg? [_menv msg form]
           (let [[_ match & body] form]
                `(try ~@body
                      (t/do-report {:type :fail, :message ~msg, :expected '~form, :actual nil})
                      (catch :default e#
                             (let [m# (.-message e#)]
                                  (if (= ~match m#)
                                      (t/do-report {:type :pass, :message ~msg, :expected '~form, :actual e#})
                                      (t/do-report {:type :fail, :message ~msg, :expected '~form, :actual e#})))
                             e#))))
