(ns openai.responses-test
  (:require
   [cheshire.core :as json]
   [clojure.test :refer [deftest is testing]]
   [hato.client :as hc]
   [openai.responses :as responses]))

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

(deftest request-response-uses-default-model-and-default-selector
  (with-redefs [responses/openai-key "test-key"
                hc/post (fn [_url opts]
                          (is (= "Bearer test-key"
                                 (get-in opts [:headers "authorization"])))
                          (is (= {:model "gpt-5-mini"
                                  :input "Hello"}
                                 (json/decode (:body opts) keyword)))
                          {:status 200
                           :body (json/encode
                                  {:output [{:type "reasoning"
                                             :summary []}
                                            {:type "message"
                                             :content [{:type "output_text"
                                                        :text "Hi there"}]}]})})]
    (is (= "Hi there"
           (responses/request-response {:input "Hello"} nil)))))

(deftest request-response-preserves-explicit-model
  (with-redefs [responses/openai-key "test-key"
                hc/post (fn [_url opts]
                          (is (= {:model "gpt-5.4"
                                  :input "Hello"}
                                 (json/decode (:body opts) keyword)))
                          {:status 200
                           :body (json/encode
                                  {:output [{:type "message"
                                             :content [{:type "output_text"
                                                        :text "Hi there"}]}]})})]
    (is (= "Hi there"
           (responses/request-response {:model "gpt-5.4"
                                        :input "Hello"}
                                       nil)))))

(deftest request-response-supports-type-selector-map
  (with-redefs [responses/openai-key "test-key"
                hc/post (fn [& _]
                          {:status 200
                           :body (json/encode
                                  {:output [{:type "reasoning"
                                             :summary []}
                                            {:type "message"
                                             :content [{:type "output_text"
                                                        :text "Paris"
                                                        :annotations []}]}]})})]
    (is (= "Paris"
           (responses/request-response {:input "What is the capital of France?"}
                                       {:output-type "message"
                                        :content-type "output_text"})))
    (is (= []
           (responses/request-response {:input "What is the capital of France?"}
                                       {:output-type "message"
                                        :content-type "output_text"
                                        :field :annotations})))))

(deftest request-response-supports-vector-path-selector
  (with-redefs [responses/openai-key "test-key"
                hc/post (fn [& _]
                          {:status 200
                           :body (json/encode
                                  {:output [{:type "reasoning"
                                             :summary []}
                                            {:type "message"
                                             :content [{:type "output_text"
                                                        :text "Paris"}]}]})})]
    (is (= "Paris"
           (responses/request-response {:input "What is the capital of France?"}
                                       [:output 1 :content 0 :text])))))

(deftest request-response-throws-when-api-key-is-missing
  (with-redefs [responses/openai-key nil]
    (let [ex (try
               (responses/request-response {:input "Hello"} nil)
               (catch clojure.lang.ExceptionInfo e
                 e))]
      (is (= "OPENAI_API_KEY is not set."
             (ex-message ex))))))

(deftest request-response-wraps-http-client-exceptions
  (with-redefs [responses/openai-key "test-key"
                hc/post (fn [& _]
                          (throw (Exception. "boom")))]
    (let [ex (try
               (responses/request-response {:input "Hello"} nil)
               (catch clojure.lang.ExceptionInfo e
                 e))]
      (is (= "OpenAI request failed."
             (ex-message ex)))
      (is (= {:error "boom"}
             (ex-data ex)))
      (is (= "boom"
             (some-> ex ex-cause .getMessage))))))

(deftest request-response-throws-on-non-success-status-and-redacts-auth
  (with-redefs [responses/openai-key "test-key"
                hc/post (fn [& _]
                          {:status 401
                           :body (json/encode {:error {:message "Unauthorized"}})})]
    (let [ex (try
               (responses/request-response {:input "Hello"} nil)
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

(deftest request-response-throws-on-empty-body
  (with-redefs [responses/openai-key "test-key"
                hc/post (fn [& _]
                          {:status 200
                           :body ""})]
    (let [ex (try
               (responses/request-response {:input "Hello"} nil)
               (catch clojure.lang.ExceptionInfo e
                 e))]
      (is (= "OpenAI response body was empty or could not be decoded."
             (ex-message ex)))
      (is (= ""
             (get-in (ex-data ex) [:response :body]))))))

(deftest request-response-throws-when-selector-finds-nothing
  (with-redefs [responses/openai-key "test-key"
                hc/post (fn [& _]
                          {:status 200
                           :body (json/encode
                                  {:output [{:type "reasoning"
                                             :summary []}]})})]
    (let [ex (try
               (responses/request-response {:input "Hello"}
                                           {:output-type "message"
                                            :content-type "output_text"})
               (catch clojure.lang.ExceptionInfo e
                 e))]
      (is (= "OpenAI response did not include requested content."
             (ex-message ex)))
      (is (= {:output-type "message"
              :content-type "output_text"}
             (:selector (ex-data ex)))))))

(deftest request-response-throws-on-invalid-selector
  (testing "empty map is not a valid selector"
    (let [ex (try
               (#'openai.responses/decode-response-body {} {})
               (catch clojure.lang.ExceptionInfo e
                 e))]
      (is (= "Expected nil, a keyword, a vector path, or a type selector map."
             (ex-message ex)))
      (is (= {}
             (:selector (ex-data ex)))))))
