(ns openai.files-test
  (:require
   [cheshire.core :as json]
   [clojure.test :refer [deftest is]]
   [hato.client :as hc]
   [openai.files :as files]))

(deftest create-file-returns-response-body-on-success
  (with-redefs [files/openai-key "test-key"
                hc/post (fn [_url opts]
                          (is (= "Bearer test-key"
                                 (get-in opts [:headers "authorization"])))
                          (is (= "user_data"
                                 (get-in opts [:multipart 0 :content])))
                          (is (= "README.md"
                                 (some-> (get-in opts [:multipart 1 :content])
                                         .getName)))
                          {:status 200
                           :body (json/encode {:id "file_123"
                                               :object "file"})})]
    (is (= {:id "file_123"
            :object "file"}
           (files/create-file "README.md")))))

(deftest upload-file-returns-file-id
  (with-redefs [files/create-file (fn [_] {:id "file_abc"})]
    (is (= "file_abc"
           (files/upload-file "README.md")))))

(deftest list-files-passes-query-params
  (with-redefs [files/openai-key "test-key"
                hc/get (fn [_url opts]
                         (is (= {:purpose "user_data"
                                 :limit 5
                                 :order "asc"
                                 :after "file_1"}
                                (:query-params opts)))
                         {:status 200
                          :body (json/encode {:data [{:id "file_1"}]})})]
    (is (= {:data [{:id "file_1"}]}
           (files/list-files {:purpose "user_data"
                              :limit 5
                              :order "asc"
                              :after "file_1"})))))

(deftest retrieve-file-returns-file-metadata
  (with-redefs [files/openai-key "test-key"
                hc/get (fn [url _opts]
                         (is (= "https://api.openai.com/v1/files/file_123"
                                url))
                         {:status 200
                          :body (json/encode {:id "file_123"
                                              :filename "README.md"})})]
    (is (= {:id "file_123"
            :filename "README.md"}
           (files/retrieve-file "file_123")))))

(deftest retrieve-file-content-returns-raw-content
  (with-redefs [files/openai-key "test-key"
                hc/get (fn [url opts]
                         (is (= "https://api.openai.com/v1/files/file_123/content"
                                url))
                         (is (= :stream (:as opts)))
                         {:status 200
                          :body (java.io.ByteArrayInputStream.
                                 (.getBytes "hello" "UTF-8"))})]
    (is (= "hello"
           (files/retrieve-file-content "file_123")))))
