(ns user
  (:require
   [malli.core :as m]
   [malli.dev :as m.dev]
   [malli.dev.pretty :as m.dev.pretty]
   [malli.error :as m.err]
   [sked.atom :as atom]
   [sked.core :as sked]
   [sked.postgres :as pg]))

(defn atom! []
  (let [sked (atom/->Sked (atom {}))
        scheduler (atom/scheduler-create sked)]
    (sked/start! sked (:id scheduler))
    sked))

(defn atom-cron [sked]
  (atom/cron-create sked {:fn println
                          :args [1 2 3]
                          :schedule "* * * * *"}))

(defn pg! [handler]
  (let [sked (pg/->Sked {:dbtype "postgres" :dbname "sked"}
                        handler)
        scheduler (pg/scheduler-create sked)]
    (sked/start! sked (:id scheduler))
    sked))

(defn pg-cron [sked]
  (pg/cron-create sked {:schedule "* * * * *"}))

(m.dev/start! {:report (m.dev.pretty/thrower)})
