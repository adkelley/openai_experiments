(ns openai.completions-test
  (:require
   [cheshire.core :as json]
   [clojure.test :refer [deftest is]]
   [hato.client :as hc]
   [openai.completions :as completions]
   [openai.error :as error]))

(def sample-messages
  [{:role "user"
    :content "Hello"}])

(deftest redact-headers-redacts-authorization
  (is (= {"authorization" "[REDACTED]"
          "content-type" "application/json"}
         (error/redact-headers
          {"authorization" "Bearer secret"
           "content-type" "application/json"})))
  (is (= {"content-type" "application/json"}
         (error/redact-headers
          {"content-type" "application/json"})))
  (is (nil? (error/redact-headers nil))))

(deftest llm-request-completions-returns-content-on-success
  (with-redefs [completions/openai-key "test-key"
                hc/post (fn [_url opts]
                          (is (= "Bearer test-key"
                                 (get-in opts [:headers "authorization"])))
                          {:status 200
                           :headers {"content-type" "application/json"}
                           :body (json/encode
                                  {:choices [{:message {:content "Hi there"}}]})})]
    (is (= "Hi there"
           (completions/llm-request sample-messages)))))

(deftest llm-request-completions-throws-when-api-key-is-missing
  (with-redefs [completions/openai-key nil]
    (let [ex (try
               (completions/llm-request sample-messages)
               (catch clojure.lang.ExceptionInfo e
                 e))]
      (is (instance? clojure.lang.ExceptionInfo ex))
      (is (= "OPENAI_API_KEY is not set."
             (ex-message ex))))))

(deftest llm-request-completions-wraps-http-client-exceptions
  (with-redefs [completions/openai-key "test-key"
                hc/post (fn [& _]
                          (throw (Exception. "boom")))]
    (let [ex (try
               (completions/llm-request sample-messages)
               (catch clojure.lang.ExceptionInfo e
                 e))]
      (is (= "OpenAI request failed."
             (ex-message ex)))
      (is (= {:error "boom"}
             (ex-data ex)))
      (is (= "boom"
             (some-> ex ex-cause .getMessage))))))

(deftest llm-request-completions-throws-on-rate-limit-and-redacts-auth
  (with-redefs [completions/openai-key "test-key"
                hc/post (fn [& _]
                          {:status 429
                           :headers {"content-type" "application/json"}
                           :body (json/encode
                                  {:error {:message "You exceeded your current quota."}})})]
    (let [ex (try
               (completions/llm-request sample-messages)
               (catch clojure.lang.ExceptionInfo e
                 e))]
      (is (= "OpenAI returned status 429. Check or update your OpenAI billing and usage limits."
             (ex-message ex)))
      (is (= 429
             (get-in (ex-data ex) [:response :status])))
      (is (= "[REDACTED]"
             (get-in (ex-data ex) [:response :opts :headers "authorization"])))
      (is (= "You exceeded your current quota."
             (get-in (ex-data ex) [:body :error :message]))))))

(deftest llm-request-completions-throws-on-empty-body
  (with-redefs [completions/openai-key "test-key"
                hc/post (fn [& _]
                          {:status 200
                           :headers {"content-type" "application/json"}
                           :body ""})]
    (let [ex (try
               (completions/llm-request sample-messages)
               (catch clojure.lang.ExceptionInfo e
                 e))]
      (is (= "OpenAI response body was empty or could not be decoded."
             (ex-message ex)))
      (is (= ""
             (get-in (ex-data ex) [:response :body]))))))

(deftest llm-request-completions-throws-when-content-is-missing
  (with-redefs [completions/openai-key "test-key"
                hc/post (fn [& _]
                          {:status 200
                           :headers {"content-type" "application/json"}
                           :body (json/encode {:choices [{:message {}}]})})]
    (let [ex (try
               (completions/llm-request sample-messages)
               (catch clojure.lang.ExceptionInfo e
                 e))]
      (is (= "OpenAI response did not include assistant content."
             (ex-message ex)))
      (is (= {:choices [{:message {}}]}
             (:body (ex-data ex)))))))
