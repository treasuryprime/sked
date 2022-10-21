(ns sked.atom
  "Atom-backed Sked implementation."
  (:require
   [malli.instrument :as m.inst]
   [sked.protocol :as proto]
   [sked.schema :as schema]))

(defn scheduler-create
  {:malli/schema [:=>
                  [:cat schema/sked]
                  :map]}
  [sked]
  (let [id (random-uuid)
        scheduler {:id id}]
    (swap! (.atom sked) assoc-in [:schedulers id] scheduler)
    scheduler))

(defn scheduler-stop
  {:malli/schema [:=>
                  [:cat schema/sked :uuid]
                  :boolean]}
  [sked id]
  (-> (swap-vals! (.atom sked) update :schedulers dissoc id)
      (get-in [0 :schedulers id])
      boolean))

(defn cron-create
  {:malli/schema [:=>
                  [:cat schema/sked [:map
                                     [:fn fn?]
                                     [:args [:sequential :any]]
                                     [:schedule :string]
                                     [:time-zone {:optional true} :string]]]
                  :map]}
  [sked {f :fn, :keys [args schedule time-zone]
         :or {time-zone "UTC"}}]
  (let [cron {:id (random-uuid)
              :fn f
              :args args
              :schedule schedule
              :time-zone (java.time.ZoneId/of time-zone)
              :created-at (java.time.Instant/now)}]
    (swap! (.atom sked) assoc-in [:crons (:id cron)] cron)
    cron))

(defn cron-stop
  {:malli/schema [:=>
                  [:cat schema/sked :uuid]
                  :boolean]}
  [sked cron-id]
  (-> (swap-vals! (.atom sked) update :crons dissoc cron-id)
      (get-in [0 :crons cron-id])
      boolean))

(defrecord Sked [atom]
  proto/Sked
  (scheduler-heartbeat
    [sked scheduler-id]
    (get-in (swap! (.atom sked)
                   (fn [a]
                     (cond-> a
                       (get-in a [:schedulers scheduler-id])
                       (assoc-in [:schedulers scheduler-id :last-active-at] (java.time.Instant/now)))))
            [:schedulers scheduler-id]))
  (cron-list-active
    [sked]
    (-> sked .atom deref :crons vals vec))
  (event-try-create
    [sked scheduler-id cron-id date]
    (let [id (random-uuid)
          event {:id id}
          [old] (swap-vals! (.atom sked) update-in [:events cron-id date] #(or % event))]
      (when-not (get-in old [:events cron-id date])
        event)))
  (event-handle
    [sked cron event]
    (apply (:fn cron) (:args cron))))

(m.inst/collect!)
