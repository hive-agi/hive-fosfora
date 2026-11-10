;; SPDX-License-Identifier: MIT
(ns hive-fosfora.config-test
  (:require [clojure.test :refer [deftest is]]
            [hive-fosfora.config :as config]))

(deftest defaults-point-at-the-local-remote
  (is (= {:host "127.0.0.1" :port 9002 :key nil :timeout-ms 3000} (config/endpoint {} {})))
  (is (= "ws://127.0.0.1:9002/ws" (config/url (config/endpoint {} {})))))

(deftest env-then-overrides
  (let [env {"HIVE_FOSFORA_HOST" " 10.0.0.5 " "HIVE_FOSFORA_PORT" "9100"
             "FOSFORA_KEY" "bridgekey" "HIVE_FOSFORA_TIMEOUT_MS" "500"}]
    (is (= {:host "10.0.0.5" :port 9100 :key "bridgekey" :timeout-ms 500}
           (config/endpoint env {})))
    (is (= "hivekey" (:key (config/endpoint (assoc env "HIVE_FOSFORA_KEY" "hivekey") {})))
        "HIVE_FOSFORA_KEY wins over FOSFORA_KEY")
    (is (= 9200 (:port (config/endpoint env {:port 9200 :host nil :other 1}))))
    (is (= "10.0.0.5" (:host (config/endpoint env {:host nil}))) "nil overrides nothing")
    (is (not (contains? (config/endpoint env {:other 1}) :other)))))

(deftest blank-or-junk-env-is-ignored
  (is (= 9002 (:port (config/endpoint {"HIVE_FOSFORA_PORT" "abc"} {}))))
  (is (= "127.0.0.1" (:host (config/endpoint {"HIVE_FOSFORA_HOST" "  "} {})))))

(deftest the-key-rides-in-the-query-and-is-redacted-for-display
  (let [ep (config/endpoint {} {:key "a b&c"})]
    (is (= "ws://127.0.0.1:9002/ws?key=a+b%26c" (config/url ep)))
    (is (= "ws://127.0.0.1:9002/ws?key=***" (config/url (config/redacted ep))))))
