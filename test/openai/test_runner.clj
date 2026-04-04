(ns openai.test-runner
  (:require [clojure.test :as t]
            [openai.completions-test]))

(defn -main [& _args]
  (let [{:keys [fail error]} (t/run-tests 'openai.completions-test)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
