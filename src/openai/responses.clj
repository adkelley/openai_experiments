(ns openai.responses
  (:require
   [cheshire.core :as json]
   [hato.client :as hc]))

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
  (or (:output_text response-body)
      (some (fn [node]
              (when (map? node)
                (:text node)))
            (tree-seq coll? seq (:output response-body)))))

(defn- find-by-type [items type-name]
  (some #(when (= type-name (:type %)) %) items))

(defn- decode-by-type-selector [response-body selector]
  (let [{:keys [output-type content-type field]} selector
        output-item (find-by-type (:output response-body) output-type)
        content-item (find-by-type (:content output-item) content-type)
        selected-field (or field :text)]
    (get content-item selected-field)))

(defn- decode-response-body [response-body selector]
  (cond
    (nil? selector)
    (output-text response-body)

    (and (map? selector)
         (string? (:output-type selector))
         (string? (:content-type selector))
         (or (nil? (:field selector))
             (keyword? (:field selector))))
    (decode-by-type-selector response-body selector)

    (keyword? selector)
    (get response-body selector)

    (and (vector? selector)
         (seq selector)
         (every? #(or (keyword? %) (integer? %)) selector))
    (get-in response-body selector)

    :else
    (throw (ex-info "Expected nil, a keyword, a vector path, or a type selector map."
                    {:selector selector}))))

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

(defn request-response [encoder selector]
  (when-not (map? encoder)
    (throw (ex-info "Encoder must be a map." {})))

  (let [payload (update encoder :model #(or % "gpt-5-mini"))
        response-body (post-responses-request payload)
        result (decode-response-body response-body selector)]
    (if (nil? result)
      (throw (ex-info "OpenAI response did not include requested content."
                      {:selector selector
                       :body response-body}))
      result)))
