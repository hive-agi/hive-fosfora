;; SPDX-License-Identifier: MIT
(ns hive-fosfora.snapshot-test
  (:require [clojure.test :refer [deftest is testing]]
            [hive-fosfora.snapshot :as snap]
            [hive-fosfora.stub :as stub]))

(deftest decode-reads-json-objects-only
  (is (= {:type "audio" :rms 0.2} (snap/decode "{\"type\":\"audio\",\"rms\":0.2}")))
  (is (nil? (snap/decode "not json")))
  (is (nil? (snap/decode "[1,2]"))))

(deftest fold-keeps-the-latest-state-and-audio
  (let [c (-> {}
              (snap/fold {:type "state" :active_layer 0 :layers []} 10)
              (snap/fold {:type "audio" :rms 0.3} 20)
              (snap/fold {:type "active_layer" :index 2} 30)
              (snap/fold {:type "effect_loaded"} 40))]
    (is (= {:active_layer 2 :layers []} (:state c)))
    (is (= {:rms 0.3} (:audio c)))
    (is (= 10 (:state-at c)))
    (is (= 20 (:audio-at c))))
  (testing "an active_layer before any state is ignored"
    (is (= {} (snap/fold {} {:type "active_layer" :index 1} 0)))))

(deftest names-resolve-exactly-then-by-unique-substring
  (let [effects (:effects stub/sample-state)]
    (is (= {:ok 0} (snap/resolve-index :effect effects "aurora")) "exact beats substring")
    (is (= {:ok 2} (snap/resolve-index :effect effects :plas)))
    (is (= {:ok 1} (snap/resolve-index :effect effects 1)))
    (is (= :fosfora/ambiguous (:error (snap/resolve-index :effect effects "aur"))))
    (is (= ["Aurora" "Aurora Borealis"] (:candidates (snap/resolve-index :effect effects "aur"))))
    (is (= :fosfora/not-found (:error (snap/resolve-index :effect effects "nope"))))
    (is (= :fosfora/not-found (:error (snap/resolve-index :effect effects 9))))
    (is (= :fosfora/not-found (:error (snap/resolve-index :effect effects ""))))))

(deftest summary-shows-the-active-layer-params
  (let [s (snap/summary stub/sample-state)]
    (is (= 1 (:active_layer s)))
    (is (= 3 (:effects s)))
    (is (= [{:name "speed" :type "float" :value 0.5}] (:params s)))
    (is (= ["Base" "Top"] (mapv :name (:layers s)))))
  (is (nil? (snap/summary nil))))
