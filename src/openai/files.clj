(ns openai.files
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [hato.client :as hc]))

(def openai-key (System/getenv "OPENAI_API_KEY"))

(defn- redact-headers [headers]
  (cond-> headers
    (contains? headers "authorization")
    (assoc "authorization" "[REDACTED]")))

(defn- auth-headers []
  {"authorization" (format "Bearer %s" openai-key)})

(defn- parse-json-body [body]
  (when (seq body)
    (json/decode body keyword)))

(defn- require-api-key []
  (when-not (seq openai-key)
    (throw (ex-info "OPENAI_API_KEY is not set." {}))))

(defn- require-file [file-path]
  (let [file (io/file file-path)]
    (when-not (.exists file)
      (throw (ex-info "File does not exist."
                      {:file-path file-path})))
    file))

(defn- request-json [request-fn url opts failure-message]
  (require-api-key)
  (let [headers (merge (auth-headers) (:headers opts))
        response
        (try
          (request-fn url (assoc opts :headers headers :throw-exceptions false))
          (catch Exception e
            (throw (ex-info failure-message
                            {:error (.getMessage e)}
                            e))))
        {:keys [body status]}
        response
        parsed-body (parse-json-body body)]
    (when-not (<= 200 status 299)
      (throw (ex-info (str failure-message " returned a non-success status.")
                      {:status status
                       :body parsed-body
                       :response {:status status
                                  :body body
                                  :opts {:headers (redact-headers headers)}}})))
    (when-not parsed-body
      (throw (ex-info (str failure-message " response body was empty or could not be decoded.")
                      {:status status
                       :body body
                       :response {:status status
                                  :body body
                                  :opts {:headers (redact-headers headers)}}})))
    parsed-body))

(defn create-file
  ([file-path]
   (create-file file-path "user_data"))
  ([file-path purpose]
   (when-not (string? file-path)
     (throw (ex-info "File path must be a string." {})))
   (when-not (string? purpose)
     (throw (ex-info "Purpose must be a string." {})))
   (let [file (require-file file-path)]
     (request-json hc/post
                   "https://api.openai.com/v1/files"
                   {:multipart [{:name "purpose"
                                 :content purpose}
                                {:name "file"
                                 :content file}]}
                   "OpenAI file upload failed."))))

(defn upload-file [file-path]
  (let [response-body (create-file file-path)
        file-id (:id response-body)]
    (or file-id
        (throw (ex-info "OpenAI file upload response did not include a file id."
                        {:body response-body})))))

(defn list-files
  ([] (list-files {}))
  ([{:keys [after limit order purpose]}]
   (let [query-params (cond-> {}
                        after (assoc :after after)
                        limit (assoc :limit limit)
                        order (assoc :order order)
                        purpose (assoc :purpose purpose))]
     (request-json hc/get
                   "https://api.openai.com/v1/files"
                   (cond-> {}
                     (seq query-params) (assoc :query-params query-params))
                   "OpenAI file list request failed."))))

(defn retrieve-file [file-id]
  (when-not (string? file-id)
    (throw (ex-info "File id must be a string." {})))
  (request-json hc/get
                (str "https://api.openai.com/v1/files/" file-id)
                {}
                "OpenAI file retrieve request failed."))

(defn retrieve-file-content [file-id]
  (when-not (string? file-id)
    (throw (ex-info "File id must be a string." {})))
  (require-api-key)
  (let [headers (auth-headers)
        response
        (try
          (hc/get (str "https://api.openai.com/v1/files/" file-id "/content")
                  {:headers headers
                   :as :stream
                   :throw-exceptions false})
          (catch Exception e
            (throw (ex-info "OpenAI file content request failed."
                            {:error (.getMessage e)}
                            e))))
        {:keys [body status]}
        response
        response-body (some-> body slurp)]
    (when-not (<= 200 status 299)
      (throw (ex-info "OpenAI file content request returned a non-success status."
                      {:status status
                       :body (parse-json-body response-body)
                       :response {:status status
                                  :body response-body
                                  :opts {:headers (redact-headers headers)}}})))
    response-body))
