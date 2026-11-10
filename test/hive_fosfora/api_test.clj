;; SPDX-License-Identifier: MIT
(ns hive-fosfora.api-test
  (:require [clojure.test :refer [deftest is]]
            [hive-fosfora.api :as f]
            [hive-fosfora.stub :as stub]))

(deftest the-repl-vocabulary-sends-web-remote-messages
  (let [l (stub/link)]
    (binding [f/*link* l]
      (f/set-param! :speed 0.8)
      (f/set-layer-param! 0 :intensity 0.5)
      (f/load-effect! "plasma")
      (f/load-preset! 0)
      (f/select-layer! 1)
      (f/opacity! 0 0.25)
      (f/blend! 1 3)
      (f/enable! 0 false)
      (f/postprocess! true)
      (f/trigger! :next-preset)
      (f/data! "lfo" {:a 0.5})
      (f/send! {:type "schema" :source "lfo" :fields {}}))
    (is (= ["set_param" "set_layer_param" "load_effect" "load_preset" "select_layer"
            "set_layer_opacity" "set_layer_blend" "set_layer_enabled"
            "set_postprocess_enabled" "trigger" "data" "schema"]
           (mapv :type (stub/sent l))))))

(deftest reads-come-from-the-cache
  (binding [f/*link* (stub/link {:state stub/sample-state :audio {:bpm 128}})]
    (is (= stub/sample-state (f/state)))
    (is (= {:bpm 128} (f/audio)))
    (is (= "Plasma" (:name (last (f/effects)))))
    (is (= ["speed"] (mapv :name (f/params))))
    (is (= [] (f/params 0)))))

(deftest failures-throw-the-error-result
  (binding [f/*link* (stub/link)]
    (let [e (try (f/set-param! :speed 3) nil (catch clojure.lang.ExceptionInfo e e))]
      (is (= :fosfora/invalid-arg (:error (ex-data e))))
      (is (= :value (:arg (ex-data e)))))
    (let [e (try (f/load-effect! "aur") nil (catch clojure.lang.ExceptionInfo e e))]
      (is (= :fosfora/ambiguous (:error (ex-data e)))))))
