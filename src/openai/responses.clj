(ns openai.responses
  (:require
   [cheshire.core :as json]
   [hato.client :as hc]
   [openai.files :as files]))

(def openai-key (System/getenv "OPENAI_API_KEY"))

(defn- redact-headers [headers]
  (cond-> headers
    (contains? headers "authorization")
    (assoc "authorization" "[REDACTED]")))

(defn- request-headers []
  {"content-type" "application/json"
   "authorization" (format "Bearer %s" openai-key)})

(defn- parse-json-body [body]
  (when (seq body)
    (json/decode body keyword)))

(defn- output-text [response-body]
  (some (fn [node]
          (when (map? node)
            (:text node)))
        (tree-seq coll? seq (:output response-body))))

(defn- post-responses-request [payload]
  (when-not (seq openai-key)
    (throw (ex-info "OPENAI_API_KEY is not set." {})))

  (let [headers (request-headers)
        response
        (try
          (hc/post "https://api.openai.com/v1/responses"
                   {:headers headers
                    :body (json/encode payload)
                    :throw-exceptions false})
          (catch Exception e
            (throw (ex-info "OpenAI request failed."
                            {:error (.getMessage e)}
                            e))))
        {:keys [body status]}
        response
        parsed-body
        (parse-json-body body)]
    (when-not (<= 200 status 299)
      (throw (ex-info "OpenAI request returned a non-success status."
                      {:status status
                       :body parsed-body
                       :response {:status status
                                  :body body
                                  :opts {:headers (redact-headers headers)}}})))
    (when-not parsed-body
      (throw (ex-info "OpenAI response body was empty or could not be decoded."
                      {:status status
                       :body body
                       :response {:status status
                                  :body body
                                  :opts {:headers (redact-headers headers)}}})))
    parsed-body))

(defn request-text [input]
  (when-not (string? input)
    (throw (ex-info "Input must be a string." {})))

  (let [response-body
        (post-responses-request
         {:model "gpt-5.4"
          :input input})]
    (or (output-text response-body)
        (throw (ex-info "OpenAI response did not include output content."
                        {:body response-body})))))

(defn request-websearch [input]
  (when-not (string? input)
    (throw (ex-info "Input must be a string." {})))

  (let [response-body
        (post-responses-request
         {:model "gpt-5.4"
          :input input
          :tools [{:type "web_search_preview"}]})]
    (or (output-text response-body)
        (throw (ex-info "OpenAI web search response did not include output content."
                        {:body response-body})))))

(defn file-input-by-id [input-text file-id]
  (when-not (string? input-text)
    (throw (ex-info "Input must be a string." {})))

  (when-not (string? file-id)
    (throw (ex-info "File id must be a string." {})))

  (let [response-body
        (post-responses-request
         {:model "gpt-5.4"
          :input [{:role "user"
                   :content [{:type "input_text"
                              :text input-text}
                             {:type "input_file"
                              :file_id file-id}]}]})]

    (or (output-text response-body)
        (throw (ex-info "OpenAI file input response did not include output content."
                        {:body response-body})))))

(defn file-input [input-text file-path]
  (let [file-id (files/upload-file file-path)]
    (file-input-by-id input-text file-id)))
