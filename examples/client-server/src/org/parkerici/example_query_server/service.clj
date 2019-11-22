(ns org.parkerici.example-query-server.service
  (:require [datomic.client.api :as d]
            [io.pedestal.http :as http]
            [clojure.data.json :as json]
            [clojure.walk :as walk]
            [org.parkerici.example-query-server.db.query :as query]
            [io.pedestal.http.route :as route])
  (:import java.time.ZoneId
   java.util.Date
   java.time.format.DateTimeFormatter))

(defn- inst->str
  "Encode date into string.

  Note: uses java.time functionality, should be thread safe unlike java.util.Date"
  [i]
  (.format (.withZone DateTimeFormatter/ISO_OFFSET_DATE_TIME
                      (ZoneId/systemDefault))
           (.toInstant ^Date i)))

(defn- prep-json-out
  "Encode keywords as ':keyword' in result, and encode instants in standardized
  strings. Other data-type specific coding may be necessary depending on client/
  result consumer context."
  [result-tuples]
  (walk/postwalk
    (fn [v]
      (cond
        (keyword? v) (str v)
        (inst? v) (inst->str v)
        (uuid? v) (str v)
        :else v))
    result-tuples))


(defn serialize-q-results
  "Serializes output to JSON

  Note: for large query results/better client architecture, recommend serializing
  results to s3, GCP, etc. rather than JSON in body of request (only used to keep
  demo app simple)"
  [q-results]
  ;; Note encode-kws here happens _before_ json deserialization so that keywords are
  ;; coerced into preferred string representation in Clojure data, then those strings
  ;; are coerced into JSON as string literals.
  (json/write-str
    {"query_result" (prep-json-out q-results)}))


(defn query
  [request]
  (if-let [db-name (get-in request [:path-params :db-name])]
    (let [body (:body request)
          q-arg-map (merge
                      (query/handle-query-map body)
                      {:db-name db-name})
          q-result (query/q->result-or-errors q-arg-map)]
      {:status 200
       :headers {"Content-Type" "application/json"}
       :body (serialize-q-results q-result)})
    {:status 400}))

(def routes
  (route/expand-routes
    #{["/query/:db-name" :post query :route-name :query-dbname]}))

(defn create-server [{:keys [host port dev]}]
  (let [server-port (if port
                      (Integer/parseInt port)
                      8890)
        server-host (or host "localhost")]
    (http/create-server
      {::http/join? (not dev)
       ::http/host server-host
       ::http/routes routes
       ::http/type   :jetty
       ::http/port   server-port})))

(defn -main [& [host port]]
  (http/start (create-server {:port port
                              :host host})))

(comment
  (def server
    (create-server {:port "8988"
                    :host "localhost"
                    :dev true})))
