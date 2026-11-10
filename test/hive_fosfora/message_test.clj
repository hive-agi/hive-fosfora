;; SPDX-License-Identifier: MIT
(ns hive-fosfora.message-test
  (:require [clojure.test :refer [deftest is testing]]
            [hive-fosfora.message :as msg]))

(deftest messages-carry-the-web-remote-shape
  (is (= {:ok {:type "set_param" :name "speed" :value 0.75}} (msg/set-param :speed 0.75)))
  (is (= {:ok {:type "set_layer_param" :layer 1 :name "intensity" :value 0.5}}
         (msg/set-layer-param 1 "intensity" 0.5)))
  (is (= {:ok {:type "load_effect" :index 3}} (msg/load-effect 3)))
  (is (= {:ok {:type "load_effect" :index 3}} (msg/load-effect 3.0)) "a whole double from JSON")
  (is (= {:ok {:type "set_layer_opacity" :layer 0 :value 1.0}} (msg/opacity 0 1)))
  (is (= {:ok {:type "set_layer_blend" :layer 1 :value 3}} (msg/blend 1 3)))
  (is (= {:ok {:type "set_layer_enabled" :layer 0 :value false}} (msg/enable 0 false)))
  (is (= {:ok {:type "set_postprocess_enabled" :value true}} (msg/postprocess true)))
  (is (= {:ok {:type "data" :source "lfo" :fields {"a" 0.5 "b" 1.0}}}
         (msg/data :lfo {:a 0.5 "b" 1}))))

(deftest triggers-accept-kebab-or-snake-and-nothing-else
  (is (= "next_preset" (get-in (msg/trigger :next-preset) [:ok :action])))
  (is (= "tempo_tap" (get-in (msg/trigger " TEMPO_TAP ") [:ok :action])))
  (is (= :fosfora/invalid-arg (:error (msg/trigger :explode)))))

(deftest bad-arguments-name-the-argument
  (testing "values are normalized 0..1 and finite"
    (is (= :value (:arg (msg/set-param :speed 1.5))))
    (is (= :value (:arg (msg/set-param :speed Double/NaN))))
    (is (= :value (:arg (msg/set-param :speed "0.5")))))
  (testing "indices are non-negative integers"
    (is (= :index (:arg (msg/load-effect -1))))
    (is (= :index (:arg (msg/load-effect 1.5))))
    (is (= :layer (:arg (msg/opacity nil 0.5)))))
  (testing "names are not blank"
    (is (= :param (:arg (msg/set-param "" 0.5))))
    (is (= :param (:arg (msg/set-param nil 0.5)))))
  (testing "the first bad field wins, and the error is an invalid-arg Result"
    (let [e (msg/set-layer-param -1 "" 9)]
      (is (= :fosfora/invalid-arg (:error e)))
      (is (= :layer (:arg e)))))
  (is (= :on (:arg (msg/enable 0 "yes"))))
  (is (= :fields (:arg (msg/data "s" {}))))
  (is (= :fields (:arg (msg/data "s" {:a ##Inf})))))

(deftest raw-messages-must-name-a-type
  (is (= {:ok {:type "set_param" :name "x" :value 1}} (msg/raw {:type "set_param" :name "x" :value 1})))
  (is (= {:ok {"type" "trigger"}} (msg/raw {"type" "trigger"})))
  (is (= :message (:arg (msg/raw {:name "x"}))))
  (is (= :message (:arg (msg/raw "set_param")))))
