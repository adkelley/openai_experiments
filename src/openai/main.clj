(ns openai.main
  (:require
   [openai.completions :as completions])
  (:gen-class))

(defn -main [& _args]
  (let [messages [{:role "system"
                   :content "You are a helpful assistant."}
                  {:role "user"
                   :content "What is the clojure programming language?"}]]
    (prn
     (completions/llm-request-completions messages))))
