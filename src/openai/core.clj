(ns openai.core
  (:require
   [hato.client :as hc]
   [cheshire.core :as json])
  (:gen-class))

(def openai-key (System/getenv "OPENAI_API_KEY"))

(defn- redact-headers [headers]
  (cond-> headers
    (contains? headers "authorization")
    (assoc "authorization" "[REDACTED]")))

(defn llm-request-completions [messages]
  (when-not (seq openai-key)
    (throw (ex-info "OPENAI_API_KEY is not set." {})))
  (let [request-headers {"content-type" "application/json"
                         "authorization" (format "Bearer %s" openai-key)}
        response
        (try
          (hc/post "https://api.openai.com/v1/chat/completions"
                   {:headers request-headers
                    :body (json/encode
                           {:model "gpt-4"
                            :messages messages})})
          (catch Exception e
            (throw (ex-info "OpenAI request failed."
                            {:error (.getMessage e)}
                            e))))
        {:keys [body headers status]}
        response
        parsed-body
        (when (seq body)
          (json/decode body keyword))]
    (when-not (<= 200 status 299)
      (throw (ex-info "OpenAI request returned a non-success status."
                      {:status status
                       :headers headers
                       :body parsed-body
                       :response {:status status
                                  :headers headers
                                  :body body
                                  :opts {:headers (redact-headers request-headers)}}})))
    (when-not parsed-body
      (throw (ex-info "OpenAI response body was empty or could not be decoded."
                      {:status status
                       :headers headers
                       :body body
                       :response {:status status
                                  :headers headers
                                  :body body
                                  :opts {:headers (redact-headers request-headers)}}})))
    (or (get-in parsed-body [:choices 0 :message :content])
        (throw (ex-info "OpenAI response did not include assistant content."
                        {:status status
                         :headers headers
                         :body parsed-body})))))

(defn -main [& _args]
  (let [messages [{:role "system"
                   :content "You are a helpful assistant."}
                  {:role "user"
                   :content "What is the clojure programming language?"}]]
    (prn
     (llm-request-completions messages))))
