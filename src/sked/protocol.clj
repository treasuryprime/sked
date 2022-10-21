(ns sked.protocol)

(defprotocol Sked
  (scheduler-heartbeat
    [sked scheduler-id]
    "Hearbeat the scheduler. Return false to signal the scheduler to terminate.")
  (cron-list-active
    [sked]
    "List all active crons.")
  (event-try-create
    [sked scheduler-id cron-id date]
    "Try to create an event, returning nil if the event already exists.")
  (event-handle
    [sked cron event]
    "Handle an event."))
