(ns org.parkerici.example-query-server.service-test
  (:require [clojure.java.io :as io]
            [io.pedestal.http :as http]
            [io.pedestal.test :as test]
            [org.parkerici.example-query-server.db.query :as query]
            [org.parkerici.example-query-server.service :as sut]))

(defonce test-server (atom nil))

(defn as-io-stream [s]
  (io/input-stream (.getBytes s)))

(defn start-test-server []
  (reset! test-server
          (http/start (sut/create-server {:dev true}))))

(defn stop-test-server []
  (http/stop @test-server))

(defn reset-test-server []
  (when @test-server
    (stop-test-server))
  (start-test-server))

(defn api-test
  [& args]
  (apply test/response-for (:io.pedestal.http/service-fn @test-server) args))

(def POST (partial api-test :post))

(comment
  (reset-test-server)

  (query/q->result-or-errors
    (merge (query/handle-query-map (as-io-stream (slurp (io/resource "example-q.json"))))
           {:db-name "candel"}))

  (db/connect-to "candel")

  (POST "/query/candel"
        :body (slurp (io/resource "example-q.json"))
        :headers {"Content-Type" "application/json"}))
