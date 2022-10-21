(ns sked.core
  (:require
   [cambium.codec :as log.codec]
   [cambium.core :as log]
   [cambium.logback.json.flat-layout :as log.flat]
   [malli.instrument :as m.inst]
   [sked.cron :as cron]
   [sked.protocol :as proto]
   [sked.schema :as schema]))

(defn- run-one
  [sked scheduler-id cron date]
  (when-let [event (proto/event-try-create sked scheduler-id (:id cron) date)]
    (log/with-logging-context {[:sked :event] (select-keys event [:id])}
      (log/debug "Handling event")
      (proto/event-handle sked cron event))))

(defn- run-active
  [sked scheduler-id]
  ;; shuffling ensures fairness
  (let [crons (shuffle (proto/cron-list-active sked))]
    (-> {[:sked :count] (count crons)}
        (log/debug "Running active crons"))
    (doseq [cron crons]
      (log/with-logging-context {[:sked :cron] (select-keys cron [:id])}
        (try
          (some->> (cron/event-seq
                    (:schedule cron)
                    (:created-at cron)
                    (:time-zone cron))
                   first
                   (run-one sked scheduler-id cron))
          (catch Throwable e
            (log/error e "Failed to run cron")))))))

(defn- run
  [sked scheduler-id]
  (run-active sked scheduler-id)
  (cron/sleep-until-next-minute)
  (when (proto/scheduler-heartbeat sked scheduler-id)
    (recur sked scheduler-id)))

(defn start!
  "Starts a scheduler future and returns it.
   The future will continue to run until `sked.protocol/scheduler-heartbeat`
   returns false, at which point the future will yield the value `::done`."
  {:malli/schema [:=>
                  [:cat schema/sked :any]
                  [:fn future?]]}
  [sked scheduler-id]
  (future
    (try
      (log.flat/set-decoder! log.codec/destringify-val)
      (log/with-logging-context {[:sked :scheduler] {:id scheduler-id}}
        (log/info "Starting up")
        (run sked scheduler-id)
        (log/info "Shutting down"))
      (catch Throwable e
        (log/fatal {} e "Scheduler died")
        (throw e)))
    ::done))

(m.inst/collect!)
