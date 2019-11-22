(ns org.parkerici.datomic.datalog.json-parser-test
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.data :as data]
            [clojure.pprint :as pp]
            [clojure.string :as str]
            [clojure.data.json :as json]
            [org.parkerici.datomic.datalog.json-parser :as sut]))


(defn json+edn-pairs
  "This helper functions gets all the edn and json queries in test/resources
  and provides a map representation of each example query pair.

  suffix allows filtering by '-q' or '-rules' prior to edn/json extension."
  [suffix]
  (let [json-files (->> (io/file "test/resources")
                        (file-seq)
                        (rest)  ; rest drops directory (first in file-seq)
                        (map str)
                        (filter #(.endsWith % (str suffix ".json"))))
        edn-files (map #(str/replace % ".json" ".edn") json-files)]
    (map
      (fn [jsonf ednf]
         {:file jsonf
          :json (-> jsonf slurp json/read-str)
          :edn (edn/read-string (slurp ednf))})
      json-files
      edn-files)))

(deftest exemplar-q-tests
  (doseq [{:keys [file json edn]} (json+edn-pairs "-q")]
    (testing (str "parsing:" file)
      (is (= edn (sut/parse-q json))
          (let [[json-only edn-only] (data/diff (sut/parse-q json) edn)]
            {:json-q-diff (with-out-str (pp/pprint json-only))
             :edn-q-diff (with-out-str (pp/pprint edn-only))})))))

(deftest exemplar-rule-tests
  (doseq [{:keys [file json edn]} (json+edn-pairs "-rules")]
    (testing (str "parsing:" file)
      (is (= edn (sut/parse-rules json))
          (let [[json-only edn-only] (data/diff (sut/parse-rules json) edn)]
            {:json-q-diff (with-out-str (pp/pprint json-only))
             :edn-q-diff (with-out-str (pp/pprint edn-only))})))))
(comment
  (run-tests *ns*))

(comment
  :test-parsing
  (def from-json (->> (io/file "test/resources/re-q.json")
                      (slurp)
                      (json/read-str)))
  (prn (sut/parse-q from-json)))
