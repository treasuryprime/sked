(ns sked.postgres
  "Postgres-backed Sked implementation."
  (:require
   [cambium.core :as log]
   [honey.sql :as sql]
   [malli.instrument :as m.inst]
   [next.jdbc :as jdbc]
   [next.jdbc.date-time] ; extends `SettableParameter` automatically
   [next.jdbc.prepare :as jdbc.prep]
   [next.jdbc.result-set :as jdbc.rs]
   [sked.protocol :as proto]
   [sked.schema :as schema])
  (:import
   (java.sql ResultSet ResultSetMetaData Timestamp)))

;;;
;;; Utilities
;;;

(defn column-reader
  [^ResultSet rs ^ResultSetMetaData _ ^Integer i]
  (when-let [value (.getObject rs i)]
    (cond-> value
      (instance? Timestamp value)
      .toInstant)))

(defn execute!
  {:malli/schema [:=>
                  [:cat :map :any]
                  [:sequential :map]]}
  [m conn]
  (let [sql (sql/format m)]
    (log/trace {:sql sql, :sql-map m} "Querying database")
    (jdbc/execute! conn (sql/format m) {:builder-fn (jdbc.rs/as-maps-adapter
                                                     jdbc.rs/as-unqualified-kebab-maps
                                                     column-reader)})))

(defn execute-1!
  {:malli/schema [:=>
                  [:cat :map :any]
                  [:maybe [:map]]]}
  [m conn]
  (first (execute! m conn)))

(defn normalize-cron
  "Sked expects `java.time.ZoneId` objects instead of string time zone IDs."
  [cron]
  (update cron :time-zone #(java.time.ZoneId/of %)))

;;;
;;; `Sked` backend
;;;

(defn scheduler-create
  {:malli/schema [:=>
                  [:cat schema/sked]
                  :map]}
  [sked]
  (-> {:insert-into :sked.scheduler
       :values [{:hostname (.getHostName (java.net.InetAddress/getLocalHost))}]
       :returning :*}
      (execute-1! (.db sked))))

(defn scheduler-stop
  {:malli/schema [:=>
                  [:cat schema/sked :int]
                  :boolean]}
  [sked id]
  (-> {:update :sked.scheduler
       :set {:active false}
       :where [:and [:= :id id]
                    [:= :active true]]
       :returning :id}
      (execute-1! (.db sked))
      boolean))

(defn cron-create
  {:malli/schema [:=>
                  [:cat schema/sked [:map
                                     [:schedule :string]
                                     [:time-zone {:optional true} :string]]]
                  :map]}
  [sked cron]
  (-> {:insert-into :sked.cron
       :values [cron]
       :returning :*}
      (execute-1! (.db sked))
      normalize-cron))

(defn cron-stop
  {:malli/schema [:=>
                  [:cat schema/sked :int]
                  :boolean]}
  [sked id]
  (-> {:update :sked.cron
       :set {:active false}
       :where [:and [:= :id id]
                    [:= :active true]]
       :returning :id}
      (execute-1! (.db sked))
      boolean))

(defrecord Sked [db handler]
  proto/Sked

  (scheduler-heartbeat
    [sked id]
    (-> {:update [:sked.scheduler]
         :set {:last-active-at [:now]}
         :where [:= :id id]
         :returning :active}
        (execute-1! (.db sked))
        (or (throw (ex-info "Scheduler heartbeat failed" {})))
        :active))

  (cron-list-active
    [sked]
    (-> {:select :*
         :from :sked.cron
         :where [:= :active true]}
        (execute! (.db sked))
        (->> (mapv normalize-cron))))

  (event-try-create
    [sked scheduler-id cron-id date]
    (-> {:insert-into :sked.event
         :values [{:scheduler-id scheduler-id
                   :cron-id cron-id
                   :date date}]
         :on-conflict []
         :do-nothing []
         :returning :*}
        (execute-1! (.db sked))))

  (event-handle
    [sked cron event]
    ((.handler sked) cron event)))

(m.inst/collect!)
