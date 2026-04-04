(ns openai.responses
  (:require
   [cheshire.core :as json]
   [hato.client :as hc]))

(def openai-key (System/getenv "OPENAI_API_KEY"))

(defn- redact-headers [headers]
  (cond-> headers
    (contains? headers "authorization")
    (assoc "authorization" "[REDACTED]")))

(defn llm-request [input]
  (when-not (seq openai-key)
    (throw (ex-info "OPENAI_API_KEY is not set." {})))

  (let [request-headers {"content-type" "application/json"
                         "authorization" (format "Bearer %s" openai-key)}

        response
        (try
          (hc/post "https://api.openai.com/v1/responses"
                   {:headers request-headers
                    :body (json/encode
                           {:model "gpt-5.4"
                            :input input})})
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
    (or (get-in parsed-body [:output 0 :content 0 :text])
        (throw (ex-info "OpenAI response did not include output content."
                        {:status status
                         :headers headers
                         :body parsed-body})))))
