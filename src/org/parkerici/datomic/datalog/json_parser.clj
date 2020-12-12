(ns org.parkerici.datomic.datalog.json-parser
  (:require [clojure.spec.alpha :as s]
            [clojure.walk :as walk]))

;; Not (yet) supported:
;; -- any where expression not in white list
;; -- custom aggregates definitions
;; -- string literals that start with ":" or "?"
;; -- generic Clojure fn or Java method calls not in whitelist

(set! *warn-on-reflection* true)

(def aggregates
  {"count" ['count ::aggregate]
   "count-distinct" ['count-distinct ::aggregate]
   "distinct" ['distinct ::aggregate]
   "sum" ['sum ::aggregate]
   "median" ['median ::aggregate]
   "avg" ['avg ::aggregate]
   "variance" ['variance ::aggregate]
   "stddev" ['stddev ::aggregate]
   "max" ['max ::aggregate]
   "min" ['min ::aggregate]
   "rand" ['rand ::aggregate]
   "sample" ['sample ::aggregate]})

(def aggregate-whitelist
  (into #{} (keys aggregates)))

(s/def ::aggregate-fn
  (into #{} (map symbol aggregate-whitelist)))

(s/def ::aggregate
  (s/cat :fn ::aggregate-fn :args (s/+ any?)))

(s/def ::raw-aggregate-expr
  (s/cat :fn aggregate-whitelist :args (s/+ any?)))

(s/def ::pull-literal
  #{"pull"})

;; don't need structural, conditional parsing to disambiguate
;; literals in pull patterns
(s/def ::pattern
  coll?)

(s/def ::raw-pull-expr
  (s/cat :pull ::pull-literal
         :var symbol?
         :pattern ::pattern))

(s/def ::resolvable-find-elem
  (s/or :pull-expr ::raw-pull-expr
        :aggr-expr ::raw-aggregate-expr))

(s/def ::fn-arg
  (complement coll?))

(s/def ::fn-expr
  (s/cat :fn symbol? :args (s/+ ::fn-arg)))

(def where-expressions
  {">" ['> ::fn-expr]
   "<" ['< ::fn-expr]
   ">=" ['>= ::fn-expr]
   "<=" ['<= ::fn-expr]
   "=" ['= ::fn-expr]
   "!=" ['!= ::fn-expr]
   "+" ['+ ::fn-expr]
   "-" ['- ::fn-expr]
   "*" ['* ::fn-expr]
   "/" ['/ ::fn-expr]
   "str" ['str ::fn-expr]
   "re-pattern" ['re-pattern ::fn-expr]
   "re-find" ['re-find ::fn-expr]
   "tuple" ['tuple ::fn-expr]
   "untuple" ['untuple ::fn-expr]
   "get-else" ['get-else ::fn-expr]
   "get-some" ['get-some ::fn-expr]
   "missing?" ['missing? ::fn-expr]
   "ground" ['ground ::fn-expr]})


(s/def ::where-expr-whitelist
  (into #{} (keys where-expressions)))

(s/def ::expr-str
  (s/and string? (s/or :where ::where-expr-whitelist
                       :find ::find-expr-whitelist)))

(s/def ::var
  (s/and symbol?
         #(.startsWith (str %) "?")))

(s/def ::vars (s/and sequential?
                     (s/coll-of ::var)))
(s/def ::binding (s/or :var ::var
                       :vars ::vars))


(s/def ::expression-clause
  (s/cat :expr vector? :binding (s/? ::binding)))


(s/def ::query-primitive
  (s/or
     :var ::var
     :underscore #{"_"}
     :constant (complement (or ::var #{"_"}))))

(s/def ::data-pattern
  (s/cat :data-elems (s/+ ::query-primitive)))

(def clause-types
  {"or" 'or
   "or-join" 'or-join
   "not" 'not
   "not-join" 'not-join
   "and" 'and})

(s/def ::clause-type
  (into #{} (keys clause-types)))

(s/def ::alternative-clause
  (s/cat :clause-type ::clause-type
         :vars (s/* ::var)
         :clauses (s/+ ::clause)))

(defn unqualified-symbol-str?
  [v]
  (let [v-sym (try
                (symbol v)
                (catch Exception e
                  ::s/invalid))]
    (when-not (= v-sym ::s/invalid)
      (and (symbol? v-sym)
           (not (namespace v-sym))))))


(s/def ::rule-name unqualified-symbol-str?)

(s/def ::rule-expr
  (s/cat :rule-name ::rule-name
         :rule-args (s/+ ::query-primitive)))


(s/def ::clause
  (s/or :expression ::expression-clause
        :alternative-clause ::alternative-clause
        :rule-expr ::rule-expr
        :data-pattern ::data-pattern))


;; -- lightweight query lexer --
(defn throw-if-whitespace!
  [s]
  (when-let [whitespace (re-find #"\s" s)]
    (println "Found whitespace")
    (throw (ex-info (str "Invalid query: symbol or keyword string '"
                         s
                         "' contained whitespace.")
                    {::bad-string s
                     ::whitespace-chars whitespace}))))


(defn coerce-symbol [s]
  (throw-if-whitespace! s)
  (symbol s))

(defn coerce-kw [s]
  (throw-if-whitespace! s)
  (read-string s))

(def q-lex
  [[#"\?.+" coerce-symbol]
   [#"\:.+" coerce-kw]
   [#"\$.*" coerce-symbol]
   [#"\_" coerce-symbol]
   [#"\%" coerce-symbol]
   [#"\.\.\." coerce-symbol]])

(defn str-parse [s]
  (let [matches (keep (fn [[regex parse-fn]]
                        (when (re-matches regex s)
                          (parse-fn s)))
                      q-lex)]
    (if (seq matches)
      (first matches)
      s)))

(str-parse "_yeah")


(defn resolve-aggregate [aggregate]
  (let [aggr-fn-str (first aggregate)
        [aggr-fn aggr-spec] (get aggregates aggr-fn-str)
        aggr-w-fn (conj (rest aggregate) aggr-fn)]
    (if (s/valid? aggr-spec aggr-w-fn)
      aggr-w-fn
      (throw (ex-info "Invalid aggregate in :find of query."
                      {:aggregate aggregate
                       :aggregate-fn aggr-fn
                       :explain-data (s/explain-data aggr-spec aggr-w-fn)})))))


(defn parse-pull-pattern
  [pull-pattern]
  (walk/postwalk
    ;; anon fn here as substitution map for replace returns (\*) instead of '* for symbol.
    (fn [v]
      (cond
        (#{"*"} v) '*
        (#{"..."} v) '...
        :else v))
    pull-pattern))

(defn resolve-pull
  "Return pull expression with pull symbol literal instead of string."
  [[_ pull-var pull-pattern]]
  (list 'pull pull-var (parse-pull-pattern pull-pattern)))

(defn resolve-find-elems
  [find-rel]
  (mapv (fn [find-elem]
          (cond
            (s/valid? ::raw-aggregate-expr find-elem) (resolve-aggregate find-elem)
            (s/valid? ::raw-pull-expr find-elem) (resolve-pull find-elem)
            :else find-elem))
        find-rel))

(defn resolve-where-expression [clause]
  (let [[expr binds] clause
        [f expr-spec] (get where-expressions (first expr))
        expr-w-fn (conj (rest expr) f)]
    (if (s/valid? expr-spec expr-w-fn)
      ;; drops binds portion if nil
      (into [] (remove nil? [expr-w-fn binds]))
      (throw (ex-info "Invalid :where expression clause in query"
               {:clause clause
                :expression-fn f
                :explain-data (s/explain-data expr-spec expr)})))))

(declare resolve-where-clauses)

(defn resolve-alternative-clause [clause]
  (let [conformed (s/conform ::alternative-clause clause)]
    (if (= conformed ::s/invalid)
      (throw (ex-info "[or,not,and]?(-join) clause of invalid form."
               {:clause clause
                :explain-data (s/explain-data ::alternative-clause clause)}))
      (let [{:keys [clause-type vars clauses]} conformed
            clause-symbol (get clause-types clause-type)
            resolved-clauses (resolve-where-clauses
                               (map (comp vec (partial s/unform ::clause)) clauses))]
        (vec (if vars
               (concat [clause-symbol (second clause)] resolved-clauses)
               (concat [clause-symbol] resolved-clauses)))))))

(defn resolve-rule-expr [[rule-name & rule-args]]
  (cons (symbol rule-name) rule-args))


(defn resolve-where-clauses [where-clauses]
  (mapv (fn [clause]
          (cond
            (s/valid? ::alternative-clause clause) (resolve-alternative-clause clause)
            (s/valid? ::expression-clause clause) (resolve-where-expression clause)
            (s/valid? ::rule-expr clause) (resolve-rule-expr clause)
            :else clause))
        where-clauses))

(defn parse-json-tree
  "First pass parse from json str to EDN (for parsing that does not need structural context
  to be completed). Takes a json query form as clojure data and returns edn substitutions where
  they can be made."
  [q-form]
  (clojure.walk/postwalk
                 (fn [v]
                   (if (string? v)
                     (str-parse v)
                     v))
                 q-form))

(defn parse-q
  [q-form]
  (let [as-edn (parse-json-tree q-form)
        in-clause (:in as-edn)
        where-expressions (seq (filter (partial s/valid? ::clause) (:where as-edn)))
        resolvable-find-elems (seq (filter (partial s/valid? ::resolvable-find-elem) (:find as-edn)))]
    (cond-> as-edn
       in-clause (assoc :in in-clause)
       where-expressions (assoc :where (resolve-where-clauses (:where as-edn)))
       resolvable-find-elems (assoc :find (resolve-find-elems (:find as-edn))))))

;; -- rule parsing --
(s/def ::rule-vars
  (s/alt :flat (s/+ ::var)
         :some-nested (s/cat :nested (s/coll-of ::var)
                             :maybe-flat (s/* ::var))))

(s/def ::rule-head
  (s/cat :rule-name ::rule-name
         :rule-vars ::rule-vars))

(s/def ::rule-def
  (s/cat :rule-head (s/spec ::rule-head)
         :rule-clauses (s/+ ::clause)))

(s/def ::rule
  (s/cat :rule-defs (s/+ (s/spec ::rule-def))))

(defn parse-rules
  [rule-form]
  (let [partially-parsed (parse-json-tree rule-form)]
    (if (s/valid? ::rule partially-parsed)
      (vec (for [[[rule-name & vars] & clauses] partially-parsed]
             (vec (concat [(cons (symbol rule-name) vars)] clauses))))
      (throw (ex-info "Rule definition is not valid."
               {:rule rule-form
                :explain-data (s/explain-data ::rule rule-form)})))))
