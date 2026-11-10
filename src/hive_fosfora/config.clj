;; SPDX-License-Identifier: MIT
(ns hive-fosfora.config
  "Where Fosfora's web remote listens. Pure: the environment comes in as a map.

   HIVE_FOSFORA_HOST        default 127.0.0.1
   HIVE_FOSFORA_PORT        default 9002
   HIVE_FOSFORA_KEY         access key (Setup > Control > Web remote); also read
                            from FOSFORA_KEY, as Fosfora's bridges do. Not needed
                            on the same machine.
   HIVE_FOSFORA_TIMEOUT_MS  connect and send timeout, default 3000"
  (:require [clojure.string :as str])
  (:import (java.net URLEncoder)
           (java.nio.charset StandardCharsets)))

(def defaults {:host "127.0.0.1" :port 9002 :key nil :timeout-ms 3000})

(def override-keys [:host :port :key :timeout-ms])

(defn- present [s] (when-not (str/blank? s) (str/trim s)))

(defn- int-of [s] (some-> (present s) parse-long))

(defn endpoint
  "The endpoint read from `env` (a map shaped like System/getenv), with the
   non-nil keys of `overrides` winning."
  [env overrides]
  (let [g #(get env %)
        from-env (cond-> {}
                   (present (g "HIVE_FOSFORA_HOST")) (assoc :host (present (g "HIVE_FOSFORA_HOST")))
                   (int-of (g "HIVE_FOSFORA_PORT")) (assoc :port (int-of (g "HIVE_FOSFORA_PORT")))
                   (present (g "FOSFORA_KEY")) (assoc :key (present (g "FOSFORA_KEY")))
                   (present (g "HIVE_FOSFORA_KEY")) (assoc :key (present (g "HIVE_FOSFORA_KEY")))
                   (int-of (g "HIVE_FOSFORA_TIMEOUT_MS")) (assoc :timeout-ms (int-of (g "HIVE_FOSFORA_TIMEOUT_MS"))))
        explicit (into {} (filter (comp some? val)) (select-keys overrides override-keys))]
    (merge defaults from-env explicit)))

(defn url
  "The WebSocket URL for `endpoint`, carrying the key when there is one."
  [{:keys [host port key]}]
  (str "ws://" host ":" port "/ws"
       (when-not (str/blank? key)
         (str "?key=" (URLEncoder/encode ^String key StandardCharsets/UTF_8)))))

(defn redacted
  "`endpoint` with the key hidden, for logs and tool output."
  [endpoint]
  (cond-> endpoint (:key endpoint) (assoc :key "***")))
