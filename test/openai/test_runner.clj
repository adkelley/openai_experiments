(ns openai.test-runner
  (:require [clojure.test :as t]
            [openai.audio-test]
            [openai.completions-test]
            [openai.files-test]
            [openai.responses-test]))

(defn -main [& _args]
  (let [{:keys [fail error]} (t/run-tests 'openai.audio-test
                                          'openai.completions-test
                                          'openai.files-test
                                          'openai.responses-test)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
