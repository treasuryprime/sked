(ns sked.postgres-test
  (:require
   [clojure.test :refer [deftest is]]
   [malli.dev.pretty :as m.pretty]
   [malli.instrument :as m.inst]
   [next.jdbc :as jdbc]
   [sked.core :as sked]
   [sked.cron :as cron]
   [sked.postgres :as pg]
   [sked.sql :as sql]))

(m.inst/instrument! {:report (m.pretty/thrower)})

(defn read-scheduler
  [tx id]
  (-> {:select :*
       :from :sked.scheduler
       :where [:= :id id]}
      (pg/execute-1! tx)))

(defn set-cron-created-at
  [tx cron]
  ;; the cron won't start until the next minute after `:created-at`,
  ;; so we set that to 1970 to prevent the test from waiting up to 60s
  (-> {:update :sked.cron
       :set {:created-at java.time.Instant/EPOCH}
       :where [:= :id (:id cron)]
       :returning :*}
      (pg/execute-1! tx)
      pg/normalize-cron))

(deftest full-test
  (jdbc/with-transaction [tx {:host (or (System/getenv "DB_HOST") "localhost")
                              :user (or (System/getenv "DB_USER")
                                        (System/getProperty "user.name"))
                              :password (System/getenv "DB_PASSWORD")
                              :dbname (or (System/getenv "DB_NAME") "sked")
                              :dbtype "postgres"}
                             {:rollback-only true}]
    (-> {:delete-from :sked.event}
        (pg/execute-1! tx))
    (-> {:delete-from :sked.cron}
        (pg/execute-1! tx))
    ;; poll faster, to keep the test fast
    (with-redefs [cron/sleep-until-next-minute #(Thread/sleep 10)]
      (let [handler-call (promise)
            sked (pg/->Sked tx #(deliver handler-call %&))
            scheduler (pg/scheduler-create sked)
            cron (->> {:schedule "* * * * *"
                       :time-zone "America/Los_Angeles"}
                      (pg/cron-create sked)
                      (set-cron-created-at tx))
            thread (sked/start! sked (:id scheduler))]
        (is (:active (read-scheduler tx (:id scheduler))))
        (let [[cron_ event] (deref handler-call 1000 ::timeout)]
          (is (= cron_ cron))
          (is (= (:id scheduler) (:scheduler-id event)))
          (is (= (:id cron) (:cron-id event))))
        (pg/scheduler-stop sked (:id scheduler))
        (is (not (:active (read-scheduler tx (:id scheduler)))))
        (is (= ::sked/done @thread))))))
