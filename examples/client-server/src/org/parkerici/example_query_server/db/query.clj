(ns org.parkerici.example-query-server.db.query
  (:require [datomic.client.api :as d]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [org.parkerici.datomic.datalog.json-parser :as datalog-parser]
            [org.parkerici.example-query-server.db :as db]
            [clojure.data.json :as json]))


(defn read-qmap-json
  [input-stream]
  (some-> input-stream
          io/reader
          (slurp)
          (json/read-str)
          ;; only kw-ify top level keys of query map
          ;; i.e., query->:query, args->:args, timeout->:timeout
          ((fn [m]
             (->> (for [[k v] m]
                    [(keyword k) v])
                  (into {}))))))

(defn handle-query-map
  "Parses just the query portion of the query arg map (the query arg map
  contains :args, :timeout, etc."
  [q-map]
  (let [deserialized-q-map (read-qmap-json q-map)
        parsed-q (datalog-parser/parse-q (:query deserialized-q-map))]
    (assoc deserialized-q-map :query parsed-q)))


(defn rule-arg-index
  "Returns the position of the rules arg, %, if any, in the query.

  Note: Query grammar is actually a little strange here: % can appear more
  than once but always just as % or does not bind correctly?"
  [{:keys [in]}]
  (let [inds (keep-indexed (fn [ind v]
                             (when (or (= v '%))
                               ind))
                           in)]
    (when (seq inds)
      (first inds))))

(defn q->result-or-errors
  [{:keys [db-name query args timeout]}]
  (let [conn (db/connect-to db-name)
        db (d/db conn)
        rule-index (rule-arg-index query)
        ;; note: arg parsing can throw
        parsed-args (if-not rule-index
                      args
                      ;; we omit '$ data source in user passed args, so to align with
                      ;; passed args vs. query args we drop 0th index from :in clause
                      (let [adj-ind (dec rule-index)]
                        (concat
                          (take adj-ind args)
                          [(datalog-parser/parse-rules (nth args (dec rule-index)))]
                          (drop (inc adj-ind) args))))
        q-result (d/q {:query query
                       :args    (cons db parsed-args)
                       :timeout (or timeout 10000)})]
    q-result))
