# Sked

[![cljdoc badge](https://cljdoc.org/badge/treasuryprime/sked)](https://cljdoc.org/d/treasuryprime/sked)
[![Clojars Project](https://img.shields.io/clojars/v/treasuryprime/sked.svg)](https://clojars.org/treasuryprime/sked)

Sked is a simple scheduler based on Cron syntax. It does not concern itself with running jobs, only with generating events and passing them to your handler function at the right time. It is up to you to process these events as you wish.

## Backends

Sked supports pluggable storage backends, via the protocol [`sked.protocol/Sked`](src/sked/protocol.clj). Two implementations are provided:

### [`sked.atom`](src/sked/atom.clj)

In-memory store, backed by an atom.

```clojure
(require '[sked.core :as sked]
         '[sked.atom :as atom])

(def sked (atom/->Sked (atom {})))
(def scheduler (atom/scheduler-create sked))

(sked/start! sked (:id scheduler))

(def cron (atom/cron-create sked {:fn println
                                  :args [:hello "world"]
                                  :schedule "* * * * *"}))

(Thread/sleep 60000) ; observe log messages

(atom/scheduler-stop sked (:id scheduler))
```

### [`sked.postgres`](src/sked/postgres.clj)

Postgres data store, based on the schema in [`resources/sked.sql`](resources/sked.sql).

```clojure
(require '[sked.core :as sked]
         '[sked.postgres :as pg])

(defn handler
  [cron event]
  (println "Handling event:" event))

(def sked (pg/->Sked {:dbtype "postgres" :dbname "sked"} handler))
(def scheduler (pg/scheduler-create sked))

(sked/start! sked (:id scheduler))

(def cron (pg/cron-create sked {:schedule "* * * * *"}))

(Thread/sleep 60000) ; observe log messages

(pg/scheduler-stop sked (:id scheduler))
```

`pg/->Sked` requires you to provide an event handler, which takes the event that is firing and its cron. You can do whatever you want with these events, such as:

- Resolving the event name as a Clojure function var and calling it
- Submitting a task to a message queue, such as RabbitMQ or Amazon SQS

The handler is also where you can store additional context, for example by inserting a row into an association table that ties Sked events to their outcomes in your task runner.
