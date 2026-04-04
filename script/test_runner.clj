(ns test-runner
  (:require [clojure.java.shell :as sh]))

(defn run []
  (let [{:keys [exit out err]} (sh/sh "clojure" "-M:test" "-m" "openai.test-runner")]
    (when (seq out)
      (print out))
    (when (seq err)
      (binding [*out* *err*]
        (print err)))
    (when-not (zero? exit)
      (throw (ex-info "Tests failed." {:exit exit})))))
