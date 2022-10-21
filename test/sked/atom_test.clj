(ns sked.atom-test
  (:require
   [clojure.test :refer [deftest is]]
   [malli.dev.pretty :as m.pretty]
   [malli.instrument :as m.inst]
   [sked.core :as sked]
   [sked.cron :as cron]
   [sked.atom :as atom]))

(m.inst/instrument! {:report (m.pretty/thrower)})

(deftest full-test
  ;; poll faster, to keep the test fast
  (with-redefs [cron/sleep-until-next-minute #(Thread/sleep 10)]
    (let [sked (sked.atom/->Sked (atom {}))
          scheduler (sked.atom/scheduler-create sked)
          payload (promise)
          handler #(deliver payload %&)
          cron (atom/cron-create sked {:fn handler
                                       :args [:hello "world"]
                                       :schedule "* * * * *"
                                       :time-zone "Europe/London"})
          thread (sked/start! sked (:id scheduler))]
      (is (= cron (-> sked :atom deref (get-in [:crons (:id cron)]))))
      ;; the cron won't start until the next minute after `:created-at`,
      ;; so we set that to 1970 to prevent the test from waiting up to 60s
      (swap! (:atom sked) assoc-in [:crons (:id cron) :created-at] java.time.Instant/EPOCH)
      (is (= 1 (-> sked :atom deref :schedulers count)))
      (is (= [:hello "world"] (deref payload 1000 ::timeout)))
      (is (= 1 (-> sked :atom deref :events count)))
      (atom/scheduler-stop sked (:id scheduler))
      (is (= 0 (-> sked :atom deref :schedulers count)))
      (is (= ::sked/done @thread)))))
