(ns sked.sql
  (:require
   [jsonista.core :as json]
   [cambium.core :as log]
   [next.jdbc :as jdbc]
   [next.jdbc.date-time] ; for java.time.Instant support
   [next.jdbc.prepare :as jdbc.prep]
   [next.jdbc.result-set :as jdbc.rs]
   [honey.sql :as sql])
  (:import
   (java.sql PreparedStatement)
   (org.postgresql.util PGobject)))

(defn execute!
  [m conn]
  (let [sql (sql/format m)]
    (log/trace {:sql sql, :sql-map m} "Querying database")
    (jdbc/execute! conn (sql/format m) {:builder-fn jdbc.rs/as-unqualified-kebab-maps})))

(defn execute-1!
  [m conn]
  (first (execute! m conn)))

;;;
;;; Postgres JSON support, copied from:
;;; https://cljdoc.org/d/seancorfield/next.jdbc/1.2.659/doc/getting-started/tips-tricks#working-with-json-and-jsonb
;;;

(def mapper (json/object-mapper {:decode-key-fn keyword}))
(def ->json json/write-value-as-string)
(def <-json #(json/read-value % mapper))

(defn ->pgobject
  "Transforms Clojure data to a PGobject that contains the data as
  JSON. PGObject type defaults to `jsonb` but can be changed via
  metadata key `:pgtype`"
  [x]
  (let [pgtype (:pgtype (meta x) "jsonb")]
    (doto (PGobject.)
      (.setType pgtype)
      (.setValue (->json x)))))

(defn <-pgobject
  "Transform PGobject containing `json` or `jsonb` value to Clojure
  data."
  [^org.postgresql.util.PGobject v]
  (let [type (.getType v)
        value (.getValue v)]
    (if (#{"jsonb" "json"} type)
      (when value
        (with-meta (<-json value) {:pgtype type}))
      value)))

(extend-protocol jdbc.prep/SettableParameter
  clojure.lang.IPersistentMap
  (set-parameter [m ^PreparedStatement s i]
    (.setObject s i (->pgobject m)))

  clojure.lang.IPersistentVector
  (set-parameter [v ^PreparedStatement s i]
    (.setObject s i (->pgobject v))))

(extend-protocol jdbc.rs/ReadableColumn
  org.postgresql.util.PGobject
  (read-column-by-label [^org.postgresql.util.PGobject v _]
    (<-pgobject v))
  (read-column-by-index [^org.postgresql.util.PGobject v _2 _3]
    (<-pgobject v)))
