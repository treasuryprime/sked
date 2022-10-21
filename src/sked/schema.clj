(ns sked.schema
  (:require
   [malli.core :as m]
   [malli.experimental.time :as m.time]
   [malli.experimental.time.generator]
   [malli.registry :as m.reg]
   [sked.protocol]))

(m.reg/set-default-registry!
  (m.reg/composite-registry
    (m/default-schemas)
    (m.time/schemas)))

(def instance
  (m/-simple-schema
   {:type ::instance
    :compile (fn [_ [cls] _]
               {:pred #(instance? cls %)
                :min 1
                :max 1
                :type-properties {:error/message (str "Must be instance of: " (.getName cls))}})}))

(def sked
  (m/schema [instance sked.protocol.Sked]))

(def cron
  (m/schema
   [:map
    [:schedule :string]
    [:time-zone :time/zone-id]
    [:created-at :time/instant]]))

(def event
  (m/schema
   [:map
    [:scheduler-id :any]
    [:cron-id :any]
    [:date :time/instant]
    [:created-at :time/instant]]))
