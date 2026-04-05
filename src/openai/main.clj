(ns openai.main
  (:require
   [openai.completions :as completions]
   [openai.responses :as responses])
  (:gen-class))

(defn- run-completions []
  (let [messages [{:role "system"
                   :content "You are a helpful assistant."}
                  {:role "user"
                   :content "What is the clojure programming language?"}]]
    (completions/request-text messages)))

(def default-responses-input
  "Write a one-sentence description of bedtime story")

(defn- run-responses [input]
  (responses/request-text input))

(defn -main [& args]
  (let [api-name (first args)
        responses-input (or (second args)
                            default-responses-input)
        result (case api-name
                 "completions" (run-completions)
                 "responses" (run-responses responses-input)
                 (throw (ex-info "Unknown API. Use 'completions' or 'responses'."
                                 {:api api-name
                                  :valid-apis ["completions" "responses"]})))]
    (prn result)))
