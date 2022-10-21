(ns sked.cron
  (:import
   (java.time Instant LocalDate ZoneId ZonedDateTime)
   (java.time.temporal ChronoUnit)
   (com.cronutils.model CronType)
   (com.cronutils.model.definition CronDefinitionBuilder)
   (com.cronutils.model.time ExecutionTime)
   (com.cronutils.parser CronParser)))

(def ^CronParser unix-cron-parser
  (CronParser.
   (CronDefinitionBuilder/instanceDefinitionFor CronType/UNIX)))

(defn next-minute
  []
  (-> (Instant/now)
      (.truncatedTo ChronoUnit/MINUTES)
      (.plus 1 ChronoUnit/MINUTES)))

(defn sleep-until-next-minute
  []
  (let [next-minute (next-minute)]
    ;; use `(not (.isAfter))` instead of `(.isBefore)` to ensure we don't run
    ;; exactly on the minute, because our intervals are half-open: `[start, end)`
    (while (not (.isAfter (Instant/now) next-minute))
      (-> (Instant/now)
          (.until next-minute ChronoUnit/MILLIS)
          (* 0.75) ; sleep less than we think we need to, to simulate higher-res sleep
          (+ (rand-int 10)) ; add jitter
          (max 1) ; always sleep at least 1 ms
          long
          Thread/sleep))))

(defn on-or-after?
  [^Instant dt1 ^Instant dt2]
  (not (.isBefore dt1 dt2)))

(defn daily-events
  "Reverse-chronological sequence of event Instants falling on `date`."
  [cron ^LocalDate date tz]
  (let [sod (.. date
                (atStartOfDay tz)
                ;; hack: force `getExecutionDates` to work on
                ;; `[start, end)` rather than `(start, end]`
                (minusNanos 1))
        next-sod (.plusDays sod 1)]
    (->> (.getExecutionDates (ExecutionTime/forCron cron) sod next-sod)
         (map (fn [^ZonedDateTime date]
                (.toInstant date))))))

(defn event-seq
  "Lazy, reverse-chronological sequence of event Instants in `[start, now)`."
  ([schedule start tz]
   (event-seq schedule start tz (Instant/now)))
  ([^String schedule, ^java.sql.Timestamp start, ^ZoneId tz, ^Instant now]
   (let [cron (.parse unix-cron-parser schedule)
         today (.toLocalDate (.atZone now tz))]
     (sequence (comp (mapcat #(reverse (daily-events cron (.minusDays today %) tz)))
                     (drop-while #(on-or-after? % now))
                     (take-while #(on-or-after? % start)))
               (range)))))
