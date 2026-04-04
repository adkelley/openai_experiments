(ns openai.main-test
  (:require
   [clojure.test :refer [deftest is]]
   [openai.completions :as completions]
   [openai.main :as main]
   [openai.responses :as responses]))

(deftest main-routes-to-completions
  (with-redefs [completions/llm-request (fn [_] "completions result")]
    (is (= "\"completions result\"\n"
           (with-out-str
             (main/-main "completions"))))))

(deftest main-routes-to-responses
  (with-redefs [responses/llm-request (fn [input]
                                        (is (= main/default-responses-input
                                               input))
                                        "responses result")]
    (is (= "\"responses result\"\n"
           (with-out-str
             (main/-main "responses"))))))

(deftest main-routes-to-responses-with-custom-input
  (with-redefs [responses/llm-request (fn [input]
                                        (is (= "custom prompt"
                                               input))
                                        "responses result")]
    (is (= "\"responses result\"\n"
           (with-out-str
             (main/-main "responses" "custom prompt"))))))

(deftest main-throws-on-unknown-api
  (let [ex (try
             (main/-main "images")
             (catch clojure.lang.ExceptionInfo e
               e))]
    (is (= "Unknown API. Use 'completions' or 'responses'."
           (ex-message ex)))
    (is (= {:api "images"
            :valid-apis ["completions" "responses"]}
           (ex-data ex)))))
