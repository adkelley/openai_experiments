(ns openai.audio
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [hato.client :as hc])
  (:import
   [java.net URI URLDecoder]
   [java.nio.charset StandardCharsets]))

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

(defn- body->string [body]
  (cond
    (string? body) body
    (instance? (Class/forName "[B") body)
    (String. ^bytes body StandardCharsets/UTF_8)
    :else
    (some-> body str)))

(defn- require-api-key []
  (when-not (seq openai-key)
    (throw (ex-info "OPENAI_API_KEY is not set." {}))))

(defn- url-string? [value]
  (and (string? value)
       (re-matches #"https?://.+" value)))

(defn- require-local-file [file-path]
  (let [file (io/file file-path)]
    (when-not (.exists file)
      (throw (ex-info "File does not exist."
                      {:file-path file-path})))
    file))

(defn- existing-local-file? [value]
  (and (string? value)
       (.exists (io/file value))))

(defn- decode-url-segment [segment]
  (URLDecoder/decode segment (.name StandardCharsets/UTF_8)))

(defn- filename-from-url [url]
  (let [path (.getPath (URI. url))
        segment (some-> path (str/split #"/") last decode-url-segment)]
    (when (seq segment)
      segment)))

(defn- suffix-from-filename [filename]
  (let [name (or filename "")
        index (.lastIndexOf name ".")]
    (if (pos? index)
      (subs name index)
      ".tmp")))

(defn- download-url-to-temp-file [url]
  (let [filename (filename-from-url url)
        temp-file (java.io.File/createTempFile "openai-audio-" (suffix-from-filename filename))]
    (try
      (let [{:keys [body status]} (hc/get url {:as :stream
                                               :throw-exceptions false})]
        (when-not (<= 200 status 299)
          (throw (ex-info "Audio download returned a non-success status."
                          {:status status
                           :url url})))
        (with-open [in body]
          (io/copy in temp-file)))
      temp-file
      (catch Exception e
        (throw (ex-info "Audio download failed."
                        {:url url
                         :error (.getMessage e)}
                        e))))))

(defn- multipart-value [value]
  (when (some? value)
    (str value)))

(defn- request-headers [content-type]
  (cond-> {"authorization" (format "Bearer %s" openai-key)}
    content-type (assoc "content-type" content-type)))

(defn- request-multipart [url multipart failure-message]
  (require-api-key)
  (let [headers (auth-headers)
        response
        (try
          (hc/post url {:headers headers
                        :multipart multipart
                        :throw-exceptions false})
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

(defn- slurp-text-file [file-path]
  (try
    (slurp (require-local-file file-path))
    (catch Exception e
      (throw (ex-info "Text file read failed."
                      {:file-path file-path
                       :error (.getMessage e)}
                      e)))))

(defn- fetch-text-url [url]
  (try
    (let [{:keys [body status]} (hc/get url {:throw-exceptions false})
          response-body (body->string body)]
      (when-not (<= 200 status 299)
        (throw (ex-info "Text URL fetch returned a non-success status."
                        {:status status
                         :url url})))
      (when-not (seq response-body)
        (throw (ex-info "Text URL response body was empty."
                        {:url url})))
      response-body)
    (catch clojure.lang.ExceptionInfo e
      (throw e))
    (catch Exception e
      (throw (ex-info "Text URL fetch failed."
                      {:url url
                       :error (.getMessage e)}
                      e)))))

(defn- audio-source->file [audio-source]
  (cond
    (not (string? audio-source))
    (throw (ex-info "Audio source must be a string."
                    {:audio-source audio-source}))

    (url-string? audio-source)
    {:file (download-url-to-temp-file audio-source)
     :delete-after? true}

    :else
    {:file (require-local-file audio-source)
     :delete-after? false}))

(defn- audio-result-text [response-body]
  (or (:text response-body)
      (throw (ex-info "OpenAI audio response did not include text."
                      {:body response-body}))))

(defn- speech-source->text [source]
  (cond
    (not (string? source))
    (throw (ex-info "Speech source must be a string."
                    {:source source}))

    (url-string? source)
    (fetch-text-url source)

    (existing-local-file? source)
    (slurp-text-file source)

    :else
    source))

(defn- output-file [output-path format]
  (if (seq output-path)
    (io/file output-path)
    (java.io.File/createTempFile "openai-speech-" (str "." format) (io/file "/tmp"))))

(defn- write-bytes! [file body]
  (let [bytes (cond
                (instance? (Class/forName "[B") body) body
                (string? body) (.getBytes ^String body StandardCharsets/UTF_8)
                :else nil)]
    (when-not (and bytes (pos? (alength ^bytes bytes)))
      (throw (ex-info "OpenAI speech response body was empty."
                      {})))
    (try
      (with-open [out (io/output-stream file)]
        (.write out ^bytes bytes))
      (.getPath file)
      (catch Exception e
        (throw (ex-info "Speech output write failed."
                        {:output-path (.getPath file)
                         :error (.getMessage e)}
                        e))))))

(defn- request-speech [source opts]
  (require-api-key)
  (let [request-opts (merge {:model "gpt-4o-mini-tts"
                             :voice "alloy"
                             :format "mp3"}
                            opts)
        input-text (speech-source->text source)
        payload (cond-> {:model (:model request-opts)
                         :input input-text
                         :voice (:voice request-opts)
                         :response_format (:format request-opts)}
                  (:instructions request-opts)
                  (assoc :instructions (:instructions request-opts)))
        headers (request-headers "application/json")
        response
        (try
          (hc/post "https://api.openai.com/v1/audio/speech"
                   {:headers headers
                    :body (json/encode payload)
                    :as :byte-array
                    :throw-exceptions false})
          (catch Exception e
            (throw (ex-info "OpenAI audio speech request failed."
                            {:error (.getMessage e)}
                            e))))
        {:keys [body status]} response]
    (when-not (<= 200 status 299)
      (throw (ex-info "OpenAI audio speech request failed. returned a non-success status."
                      {:status status
                       :body (parse-json-body (body->string body))
                       :response {:status status
                                  :body (body->string body)
                                  :opts {:headers (redact-headers headers)}}})))
    (write-bytes! (output-file (:output-path request-opts)
                               (:format request-opts))
                  body)))

(defn- request-audio [endpoint audio-source opts default-model failure-message]
  (let [{:keys [file delete-after?]} (audio-source->file audio-source)
        request-opts (merge {:model default-model} opts)
        multipart (->> [[:file file]
                        [:model (:model request-opts)]
                        [:prompt (:prompt request-opts)]
                        [:language (:language request-opts)]
                        [:temperature (:temperature request-opts)]]
                       (keep (fn [[field value]]
                               (when (some? value)
                                 {:name (name field)
                                  :content (if (= :file field)
                                             value
                                             (multipart-value value))})))
                       vec)]
    (try
      (-> (request-multipart endpoint multipart failure-message)
          audio-result-text)
      (finally
        (when delete-after?
          (.delete file))))))

(defn transcribe-audio
  ([audio-source]
   (transcribe-audio audio-source {}))
  ([audio-source opts]
   (when-not (map? opts)
     (throw (ex-info "Options must be a map." {})))
   (request-audio "https://api.openai.com/v1/audio/transcriptions"
                  audio-source
                  opts
                  "gpt-4o-mini-transcribe"
                  "OpenAI audio transcription request failed.")))

(defn translate-audio
  ([audio-source]
   (translate-audio audio-source {}))
  ([audio-source opts]
   (when-not (map? opts)
     (throw (ex-info "Options must be a map." {})))
   (request-audio "https://api.openai.com/v1/audio/translations"
                  audio-source
                  opts
                  "whisper-1"
                  "OpenAI audio translation request failed.")))

(defn tts
  ([source]
   (tts source {}))
  ([source opts]
   (when-not (map? opts)
     (throw (ex-info "Options must be a map." {})))
   (request-speech source opts)))

(def speak-audio tts)
