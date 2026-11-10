;; SPDX-License-Identifier: MIT
(ns hive-fosfora.tools-test
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [hive-dsl.result :as r]
            [hive-fosfora.stub :as stub]
            [hive-fosfora.tools :as tools]))

(defn- call [link command params]
  ((:handler (tools/fosfora-tool (constantly link))) {"command" command "params" params}))

(defn- body [res] (json/read-str (get-in res [:content 0 :text]) :key-fn keyword))

(deftest one-tool-whose-enum-is-the-command-table
  (let [[t & more] (tools/tools (constantly (stub/link)))]
    (is (nil? more))
    (is (= "fosfora" (:name t)))
    (is (= (vec (keys tools/commands)) (get-in t [:inputSchema :properties "command" :enum])))
    (is (every? #(str/includes? (:description t) %) (keys tools/commands)))))

(deftest names-become-indices-before-sending
  (let [l (stub/link)]
    (is (not (:isError (call l "load_effect" {"effect" "plasma"}))))
    (is (not (:isError (call l "load-preset" {"preset" "drop"}))) "kebab verbs work too")
    (is (= [{:type "load_effect" :index 2} {:type "load_preset" :index 1}] (stub/sent l)))))

(deftest set-param-targets-the-active-layer-unless-a-layer-is-given
  (let [l (stub/link)]
    (call l "set_param" {"param" "speed" "value" 0.8})
    (call l "set_param" {"param" "speed" "value" 0.2 "layer" 0})
    (is (= [{:type "set_param" :name "speed" :value 0.8}
            {:type "set_layer_param" :layer 0 :name "speed" :value 0.2}]
           (stub/sent l)))))

(deftest reads-answer-json
  (let [l (stub/link {:state stub/sample-state :audio {:rms 0.4}})]
    (is (= 1 (:active_layer (body (call l "state" {})))))
    (is (= "Aurora" (-> (call l "state" {"full" true}) body :effects first :name)))
    (is (= {:rms 0.4} (body (call l "audio" nil))))
    (is (= ["Aurora" "Aurora Borealis"] (mapv :name (body (call l "effects" {"query" "AUR"})))))
    (is (= ["speed"] (mapv :name (body (call l "params" {})))))))

(deftest errors-keep-a-qualified-reason
  (testing "unknown verb lists the verbs"
    (let [res (call (stub/link) "explode" {})]
      (is (:isError res))
      (is (str/starts-with? (get-in res [:content 0 :text]) "fosfora/unknown-command"))))
  (testing "bad argument names the argument"
    (let [res (call (stub/link) "set_param" {"param" "speed" "value" 7})]
      (is (:isError res))
      (is (str/includes? (get-in res [:content 0 :text]) "\"arg\":\"value\""))))
  (testing "ambiguous name lists candidates and sends nothing"
    (let [l (stub/link) res (call l "load_effect" {"effect" "aur"})]
      (is (str/starts-with? (get-in res [:content 0 :text]) "fosfora/ambiguous"))
      (is (empty? (stub/sent l)))))
  (testing "no state yet"
    (is (str/starts-with? (get-in (call (stub/link {}) "state" {}) [:content 0 :text])
                          "fosfora/no-state")))
  (testing "transport failures carry their hint"
    (let [l (stub/failing (r/err :fosfora/not-listening {:hint "Start Fosfora."}))
          res (call l "trigger" {"action" "next_preset"})]
      (is (:isError res))
      (is (str/starts-with? (get-in res [:content 0 :text]) "fosfora/not-listening: Start Fosfora.")))))
