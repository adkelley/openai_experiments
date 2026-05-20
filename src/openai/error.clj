(ns openai.error
  (:require
   [cheshire.core :as json]))

(defn redact-headers [headers]
  (cond-> headers
    (contains? headers "authorization")
    (assoc "authorization" "[REDACTED]")))

(defn parse-json-body [body]
  (when (seq body)
    (json/decode body keyword)))

(defn status-message [status]
  (case status
    429 "OpenAI returned status 429. Check or update your OpenAI billing and usage limits."
    401 "OpenAI returned status 401. Check that OPENAI_API_KEY is set and valid."
    403 "OpenAI returned status 403. Your API key does not have access to this resource."
    (str "OpenAI returned status " status ".")))

(defn throw-response-error [response request-headers]
  (let [{:keys [status body headers]} response
        parsed-body (parse-json-body body)]

    (throw
     (ex-info (status-message status)
              {:status status
               :headers headers
               :body parsed-body
               :response {:status status
                          :headers headers
                          :body body
                          :opts {:headers (redact-headers request-headers)}}}))))
