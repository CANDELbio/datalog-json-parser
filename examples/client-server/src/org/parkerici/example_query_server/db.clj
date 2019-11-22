(ns org.parkerici.example-query-server.db
  (:require [datomic.client.api :as d]))

(defn ps-access-key []
  (or (System/getenv "PS_ACCESS_KEY")
      "dev"))

(defn ps-secret []
  (or (System/getenv "PS_SECRET")
      "dev"))

(defn ps-endpoint []
  (or (System/getenv "PS_ENDPOINT")
      "localhost:4338"))

(defn client-arg-map []
  {:server-type :peer-server
   :access-key (ps-access-key)
   :secret (ps-secret)
   :endpoint (ps-endpoint)
   :validate-hostnames false})

(defn connect-to
  [db-name]
  (let [cl-arg-map (client-arg-map)
        client (d/client cl-arg-map)]
    (d/connect client {:db-name db-name})))

(comment
  (def conn (connect-to "candel")))
