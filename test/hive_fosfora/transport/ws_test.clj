;; SPDX-License-Identifier: MIT
(ns hive-fosfora.transport.ws-test
  "The real JDK adapter against hive-fosfora.fake-remote on a free port."
  (:require [clojure.data.json :as json]
            [clojure.test :refer [deftest is]]
            [hive-dsl.result :as r]
            [hive-fosfora.fake-remote :as fake]
            [hive-fosfora.ports :as ports]
            [hive-fosfora.transport.ws :as ws])
  (:import (java.net InetAddress ServerSocket)))

(defn- closed-port []
  (with-open [s (ServerSocket. 0 1 (InetAddress/getLoopbackAddress))]
    (.getLocalPort s)))

(defn- await-received [record n]
  (loop [i 0]
    (let [got (:received @record)]
      (if (or (>= (count got) n) (> i 100))
        got
        (do (Thread/sleep 20) (recur (inc i)))))))

(deftest state-comes-in-and-commands-go-out
  (let [{:keys [port record stop]}
        (fake/start! [(json/write-str {:type "state" :active_layer 0 :layers [] :effects []})
                      (json/write-str {:type "audio" :rms 0.5})])
        link (ws/link {:host "127.0.0.1" :port port :key nil :timeout-ms 2000})]
    (try
      (let [c (ports/cache link 2000)]
        (is (r/ok? c))
        (is (= {:active_layer 0 :layers [] :effects []} (:state (:ok c)))))
      (is (= {:ok {:type "trigger" :action "next_preset"}}
             (ports/send-message! link {:type "trigger" :action "next_preset"})))
      (is (= [{:type "trigger" :action "next_preset"}]
             (mapv #(json/read-str % :key-fn keyword) (await-received record 1))))
      (is (= "GET /ws HTTP/1.1" (:request @record)))
      (is (not (contains? (:headers @record) "origin"))
          "no Origin header, so Fosfora's loopback gate needs no key")
      (is (:connected? (ports/status link)))
      (finally
        (ports/close! link)
        (stop)))
    (is (not (:connected? (ports/status link))))))

(deftest the-key-goes-in-the-request-target
  (let [{:keys [port record stop]} (fake/start! [])
        link (ws/link {:host "127.0.0.1" :port port :key "s3cret" :timeout-ms 2000})]
    (try
      (ports/cache link 0)
      (is (= "GET /ws?key=s3cret HTTP/1.1" (:request @record)))
      (is (= "ws://127.0.0.1:" (subs (:url (ports/status link)) 0 15)))
      (is (re-find #"key=\*\*\*$" (:url (ports/status link))) "status never shows the key")
      (finally (ports/close! link) (stop)))))

(deftest nothing-listening-is-a-typed-error-not-a-throw
  (let [link (ws/link {:host "127.0.0.1" :port (closed-port) :key nil :timeout-ms 1000})
        res (ports/send-message! link {:type "trigger" :action "next_preset"})]
    (is (= :fosfora/not-listening (:error res)))
    (is (string? (:hint res)))
    (is (= :fosfora/not-listening (:error (ports/status link))))))
