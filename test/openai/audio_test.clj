(ns openai.audio-test
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is]]
   [hato.client :as hc]
   [openai.audio :as audio]))

(deftest transcribe-audio-uses-default-model-and-returns-text
  (with-redefs [audio/openai-key "test-key"
                hc/post (fn [url opts]
                          (is (= "https://api.openai.com/v1/audio/transcriptions"
                                 url))
                          (is (= "Bearer test-key"
                                 (get-in opts [:headers "authorization"])))
                          (is (= "gpt-4o-mini-transcribe"
                                 (get-in opts [:multipart 1 :content])))
                          (is (= "README.md"
                                 (some-> (get-in opts [:multipart 0 :content])
                                         .getName)))
                          {:status 200
                           :body (json/encode {:text "transcribed text"})})]
    (is (= "transcribed text"
           (audio/transcribe-audio "README.md")))))

(deftest translate-audio-uses-default-model-and-returns-text
  (with-redefs [audio/openai-key "test-key"
                hc/post (fn [url opts]
                          (is (= "https://api.openai.com/v1/audio/translations"
                                 url))
                          (is (= "whisper-1"
                                 (get-in opts [:multipart 1 :content])))
                          {:status 200
                           :body (json/encode {:text "translated text"})})]
    (is (= "translated text"
           (audio/translate-audio "README.md")))))

(deftest transcribe-audio-preserves-explicit-model-and-options
  (with-redefs [audio/openai-key "test-key"
                hc/post (fn [_url opts]
                          (is (= [{:name "file"
                                   :content (get-in opts [:multipart 0 :content])}
                                  {:name "model"
                                   :content "gpt-4o-transcribe"}
                                  {:name "prompt"
                                   :content "hello"}
                                  {:name "language"
                                   :content "en"}
                                  {:name "temperature"
                                   :content "0.25"}]
                                 (:multipart opts)))
                          {:status 200
                           :body (json/encode {:text "custom"})})]
    (is (= "custom"
           (audio/transcribe-audio "README.md"
                                   {:model "gpt-4o-transcribe"
                                    :prompt "hello"
                                    :language "en"
                                    :temperature 0.25})))))

(deftest transcribe-audio-downloads-remote-url-before-upload
  (let [uploaded-file (atom nil)]
    (with-redefs [audio/openai-key "test-key"
                  hc/get (fn [url opts]
                           (is (= "https://example.com/audio/sample.wav" url))
                           (is (= :stream (:as opts)))
                           {:status 200
                            :body (java.io.ByteArrayInputStream.
                                   (.getBytes "wave-data" "UTF-8"))})
                  hc/post (fn [_url opts]
                            (reset! uploaded-file (get-in opts [:multipart 0 :content]))
                            (is (.exists ^java.io.File @uploaded-file))
                            (is (.endsWith (.getName ^java.io.File @uploaded-file) ".wav"))
                            {:status 200
                             :body (json/encode {:text "downloaded"})})]
      (is (= "downloaded"
             (audio/transcribe-audio "https://example.com/audio/sample.wav")))
      (is (false? (.exists ^java.io.File @uploaded-file))))))

(deftest transcribe-audio-wraps-download-failures
  (with-redefs [audio/openai-key "test-key"
                hc/get (fn [& _]
                         (throw (Exception. "boom")))]
    (let [ex (try
               (audio/transcribe-audio "https://example.com/audio/sample.wav")
               (catch clojure.lang.ExceptionInfo e
                 e))]
      (is (= "Audio download failed."
             (ex-message ex)))
      (is (= "https://example.com/audio/sample.wav"
             (:url (ex-data ex))))
      (is (= "boom"
             (some-> ex ex-cause .getMessage))))))

(deftest transcribe-audio-throws-when-api-key-is-missing
  (with-redefs [audio/openai-key nil]
    (let [ex (try
               (audio/transcribe-audio "README.md")
               (catch clojure.lang.ExceptionInfo e
                 e))]
      (is (= "OPENAI_API_KEY is not set."
             (ex-message ex))))))

(deftest transcribe-audio-throws-on-non-success-status-and-redacts-auth
  (with-redefs [audio/openai-key "test-key"
                hc/post (fn [& _]
                          {:status 401
                           :body (json/encode {:error {:message "Unauthorized"}})})]
    (let [ex (try
               (audio/transcribe-audio "README.md")
               (catch clojure.lang.ExceptionInfo e
                 e))]
      (is (= "OpenAI audio transcription request failed. returned a non-success status."
             (ex-message ex)))
      (is (= 401
             (get-in (ex-data ex) [:response :status])))
      (is (= "[REDACTED]"
             (get-in (ex-data ex) [:response :opts :headers "authorization"])))
      (is (= "Unauthorized"
             (get-in (ex-data ex) [:body :error :message]))))))

(deftest transcribe-audio-throws-on-empty-body
  (with-redefs [audio/openai-key "test-key"
                hc/post (fn [& _]
                          {:status 200
                           :body ""})]
    (let [ex (try
               (audio/transcribe-audio "README.md")
               (catch clojure.lang.ExceptionInfo e
                 e))]
      (is (= "OpenAI audio transcription request failed. response body was empty or could not be decoded."
             (ex-message ex)))
      (is (= ""
             (get-in (ex-data ex) [:response :body]))))))

(deftest transcribe-audio-throws-when-text-is-missing
  (with-redefs [audio/openai-key "test-key"
                hc/post (fn [& _]
                          {:status 200
                           :body (json/encode {:segments []})})]
    (let [ex (try
               (audio/transcribe-audio "README.md")
               (catch clojure.lang.ExceptionInfo e
                 e))]
      (is (= "OpenAI audio response did not include text."
             (ex-message ex))))))

(deftest tts-uses-defaults-for-literal-text
  (let [written-path (atom nil)]
    (with-redefs [audio/openai-key "test-key"
                  hc/post (fn [url opts]
                            (let [payload (json/decode (:body opts) keyword)]
                              (is (= "https://api.openai.com/v1/audio/speech" url))
                              (is (= "Bearer test-key"
                                     (get-in opts [:headers "authorization"])))
                              (is (= {:model "gpt-4o-mini-tts"
                                      :input "Hello world"
                                      :voice "alloy"
                                      :response_format "mp3"}
                                     payload))
                              {:status 200
                               :body (.getBytes "AUDIO" "UTF-8")}))]
      (reset! written-path (audio/tts "Hello world"))
      (is (.exists (io/file @written-path)))
      (is (= "AUDIO" (slurp @written-path)))
      (is (.startsWith @written-path "/tmp/"))
      (is (.endsWith @written-path ".mp3"))
      (.delete (io/file @written-path)))))

(deftest tts-reads-local-file-content
  (let [input-file (java.io.File/createTempFile "openai-speech-input-" ".txt")
        output-file (java.io.File/createTempFile "openai-speech-output-" ".wav")]
    (spit input-file "Read me aloud")
    (with-redefs [audio/openai-key "test-key"
                  hc/post (fn [_url opts]
                            (is (= {:model "gpt-4o-mini-tts"
                                    :input "Read me aloud"
                                    :voice "nova"
                                    :response_format "wav"}
                                   (json/decode (:body opts) keyword)))
                            {:status 200
                             :body (.getBytes "WAVE" "UTF-8")})]
      (is (= (.getPath output-file)
             (audio/tts (.getPath input-file)
                        {:voice "nova"
                         :format "wav"
                         :output-path (.getPath output-file)})))
      (is (= "WAVE" (slurp output-file))))
    (.delete input-file)
    (.delete output-file)))

(deftest tts-fetches-remote-text-content
  (let [written-path (atom nil)]
    (with-redefs [audio/openai-key "test-key"
                  hc/get (fn [url _opts]
                           (is (= "https://example.com/script.txt" url))
                           {:status 200
                            :body "Remote script"})
                  hc/post (fn [_url opts]
                            (is (= {:model "gpt-4o-mini-tts"
                                    :input "Remote script"
                                    :voice "alloy"
                                    :response_format "mp3"
                                    :instructions "Speak slowly"}
                                   (json/decode (:body opts) keyword)))
                            {:status 200
                             :body (.getBytes "REMOTE" "UTF-8")})]
      (reset! written-path
              (audio/tts "https://example.com/script.txt"
                         {:instructions "Speak slowly"}))
      (is (= "REMOTE" (slurp @written-path)))
      (is (.startsWith @written-path "/tmp/"))
      (.delete (io/file @written-path)))))

(deftest tts-falls-back-to-literal-text-for-missing-file-path
  (let [written-path (atom nil)]
    (with-redefs [audio/openai-key "test-key"
                  hc/post (fn [_url opts]
                            (is (= "notes/missing.txt"
                                   (:input (json/decode (:body opts) keyword))))
                            {:status 200
                             :body (.getBytes "TEXT" "UTF-8")})]
      (reset! written-path (audio/tts "notes/missing.txt"))
      (is (= "TEXT" (slurp @written-path)))
      (is (.startsWith @written-path "/tmp/"))
      (.delete (io/file @written-path)))))

(deftest tts-wraps-remote-text-failures
  (with-redefs [audio/openai-key "test-key"
                hc/get (fn [& _]
                         (throw (Exception. "boom")))]
    (let [ex (try
               (audio/tts "https://example.com/script.txt")
               (catch clojure.lang.ExceptionInfo e
                 e))]
      (is (= "Text URL fetch failed."
             (ex-message ex)))
      (is (= "https://example.com/script.txt"
             (:url (ex-data ex))))
      (is (= "boom"
             (some-> ex ex-cause .getMessage))))))

(deftest tts-throws-when-api-key-is-missing
  (with-redefs [audio/openai-key nil]
    (let [ex (try
               (audio/tts "Hello world")
               (catch clojure.lang.ExceptionInfo e
                 e))]
      (is (= "OPENAI_API_KEY is not set."
             (ex-message ex))))))

(deftest tts-throws-on-non-success-status-and-redacts-auth
  (with-redefs [audio/openai-key "test-key"
                hc/post (fn [& _]
                          {:status 401
                           :body (.getBytes (json/encode {:error {:message "Unauthorized"}})
                                            "UTF-8")})]
    (let [ex (try
               (audio/tts "Hello world")
               (catch clojure.lang.ExceptionInfo e
                 e))]
      (is (= "OpenAI audio speech request failed. returned a non-success status."
             (ex-message ex)))
      (is (= 401
             (get-in (ex-data ex) [:response :status])))
      (is (= "[REDACTED]"
             (get-in (ex-data ex) [:response :opts :headers "authorization"])))
      (is (= "Unauthorized"
             (get-in (ex-data ex) [:body :error :message]))))))

(deftest tts-throws-on-empty-body
  (with-redefs [audio/openai-key "test-key"
                hc/post (fn [& _]
                          {:status 200
                           :body (byte-array 0)})]
    (let [ex (try
               (audio/tts "Hello world")
               (catch clojure.lang.ExceptionInfo e
                 e))]
      (is (= "OpenAI speech response body was empty."
             (ex-message ex))))))

(deftest tts-wraps-output-write-failures
  (with-redefs [audio/openai-key "test-key"
                hc/post (fn [& _]
                          {:status 200
                           :body (.getBytes "AUDIO" "UTF-8")})]
    (let [ex (try
               (audio/tts "Hello world"
                          {:output-path "/missing-dir/output.mp3"})
               (catch clojure.lang.ExceptionInfo e
                 e))]
      (is (= "Speech output write failed."
             (ex-message ex)))
      (is (= "/missing-dir/output.mp3"
             (:output-path (ex-data ex)))))))

(deftest speak-audio-remains-an-alias-for-tts
  (with-redefs [audio/request-speech (fn [source opts]
                                       {:source source
                                        :opts opts})]
    (is (= {:source "Hello world"
            :opts {}}
           (audio/speak-audio "Hello world")))
    (is (= {:source "Hello world"
            :opts {:voice "nova"}}
           (audio/speak-audio "Hello world" {:voice "nova"})))))
