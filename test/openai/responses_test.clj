(ns openai.responses-test
  (:require
   [cheshire.core :as json]
   [clojure.test :refer [deftest is]]
   [hato.client :as hc]
   [openai.responses :as responses]))

(def sample-input
  "Hello")

(deftest redact-headers-redacts-authorization
  (is (= {"authorization" "[REDACTED]"
          "content-type" "application/json"}
         (#'openai.responses/redact-headers
          {"authorization" "Bearer secret"
           "content-type" "application/json"})))
  (is (= {"content-type" "application/json"}
         (#'openai.responses/redact-headers
          {"content-type" "application/json"})))
  (is (nil? (#'openai.responses/redact-headers nil))))

(deftest request--returns-output-text-on-success
  (with-redefs [responses/openai-key "test-key"
                hc/post (fn [_url opts]
                          (is (= "Bearer test-key"
                                 (get-in opts [:headers "authorization"])))
                          (is (= {:model "gpt-5.4"
                                  :input sample-input}
                                 (json/decode (:body opts) keyword)))
                          {:status 200
                           :headers {"content-type" "application/json"}
                           :body (json/encode
                                  {:output [{:content [{:text "Hi there"}]}]})})]
    (is (= "Hi there"
           (responses/request-text sample-input)))))

(deftest request--throws-when-api-key-is-missing
  (with-redefs [responses/openai-key nil]
    (let [ex (try
               (responses/request-text sample-input)
               (catch clojure.lang.ExceptionInfo e
                 e))]
      (is (instance? clojure.lang.ExceptionInfo ex))
      (is (= "OPENAI_API_KEY is not set."
             (ex-message ex))))))

(deftest request--wraps-http-client-exceptions
  (with-redefs [responses/openai-key "test-key"
                hc/post (fn [& _]
                          (throw (Exception. "boom")))]
    (let [ex (try
               (responses/request-text sample-input)
               (catch clojure.lang.ExceptionInfo e
                 e))]
      (is (= "OpenAI request failed."
             (ex-message ex)))
      (is (= {:error "boom"}
             (ex-data ex)))
      (is (= "boom"
             (some-> ex ex-cause .getMessage))))))

(deftest request--throws-on-non-success-status-and-redacts-auth
  (with-redefs [responses/openai-key "test-key"
                hc/post (fn [& _]
                          {:status 401
                           :headers {"content-type" "application/json"}
                           :body (json/encode {:error {:message "Unauthorized"}})})]
    (let [ex (try
               (responses/request-text sample-input)
               (catch clojure.lang.ExceptionInfo e
                 e))]
      (is (= "OpenAI request returned a non-success status."
             (ex-message ex)))
      (is (= 401
             (get-in (ex-data ex) [:response :status])))
      (is (= "[REDACTED]"
             (get-in (ex-data ex) [:response :opts :headers "authorization"])))
      (is (= "Unauthorized"
             (get-in (ex-data ex) [:body :error :message]))))))

(deftest request--throws-on-empty-body
  (with-redefs [responses/openai-key "test-key"
                hc/post (fn [& _]
                          {:status 200
                           :headers {"content-type" "application/json"}
                           :body ""})]
    (let [ex (try
               (responses/request-text sample-input)
               (catch clojure.lang.ExceptionInfo e
                 e))]
      (is (= "OpenAI response body was empty or could not be decoded."
             (ex-message ex)))
      (is (= ""
             (get-in (ex-data ex) [:response :body]))))))

(deftest request--throws-when-output-content-is-missing
  (with-redefs [responses/openai-key "test-key"
                hc/post (fn [& _]
                          {:status 200
                           :headers {"content-type" "application/json"}
                           :body (json/encode {:output [{:content [{}]}]})})]
    (let [ex (try
               (responses/request-text sample-input)
               (catch clojure.lang.ExceptionInfo e
                 e))]
      (is (= "OpenAI response did not include output content."
             (ex-message ex)))
      (is (= {:output [{:content [{}]}]}
             (:body (ex-data ex)))))))
