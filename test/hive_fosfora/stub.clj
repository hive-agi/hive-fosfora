;; SPDX-License-Identifier: MIT
(ns hive-fosfora.stub
  "Test doubles for IFosforaLink. Records, never with-redefs."
  (:require [hive-dsl.result :as r]
            [hive-fosfora.ports :as ports]))

(defrecord StubLink [cache-value sent failure]
  ports/IFosforaLink
  (send-message! [_ msg]
    (if failure
      failure
      (do (swap! sent conj msg) (r/ok msg))))
  (cache [_ _]
    (or failure (r/ok cache-value)))
  (status [_]
    {:url "ws://stub/ws" :connected? (nil? failure) :has-state? (some? (:state cache-value))})
  (close! [_] nil))

(def sample-state
  {:effects [{:index 0 :name "Aurora"} {:index 1 :name "Aurora Borealis"} {:index 2 :name "Plasma"}]
   :presets [{:index 0 :name "Warm Up" :builtin true} {:index 1 :name "Drop" :builtin false}]
   :active_layer 1
   :layers [{:index 0 :name "Base" :effect_name "Plasma" :params []}
            {:index 1 :name "Top" :effect_name "Aurora"
             :params [{:name "speed" :type "float" :value 0.5 :min 0.0 :max 2.0}]}]
   :postprocess_enabled true
   :streams []})

(defn link
  "A link whose cache holds `cache-value` (default: a state) and records sends."
  ([] (link {:state sample-state}))
  ([cache-value] (->StubLink cache-value (atom []) nil)))

(defn failing
  "A link whose every call answers the error Result `failure`."
  [failure]
  (->StubLink {} (atom []) failure))

(defn sent [l] @(:sent l))
