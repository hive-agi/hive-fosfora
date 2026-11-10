;; SPDX-License-Identifier: MIT
(ns hive-fosfora.addon-test
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [hive-addon.protocol :as addon]
            [hive-fosfora.addon :as sut])
  (:import (java.net InetAddress ServerSocket)))

(defn- manifest []
  (edn/read-string (slurp (io/resource "META-INF/hive-addons/hive-fosfora.edn"))))

(deftest the-manifest-names-a-constructor-that-builds-this-addon
  (let [m (manifest)
        ctor (requiring-resolve (symbol (:addon/init-ns m) (:addon/init-fn m)))
        a (ctor (:addon/config m))]
    (is (= "hive.fosfora" (:addon/id m) (addon/addon-id a)))
    (is (= (:addon/type m) (addon/addon-type a)))
    (is (= (:addon/capabilities m) (addon/capabilities a)))))

(deftest the-manifest-declares-where-it-may-run
  (is (= {:requires #{:display}} (:addon/placement (manifest)))
      "it drives an app on the operator's screen, so it runs where the display is"))

(deftest lifecycle-is-idempotent-and-never-needs-fosfora-running
  (let [port (with-open [s (ServerSocket. 0 1 (InetAddress/getLoopbackAddress))] (.getLocalPort s))
        a (sut/addon-ctor {})]
    (try
      (is (= [] (addon/tools a)) "no tools before initialize!")
      (is (:success? (addon/initialize! a {:port port :timeout-ms 500})))
      (is (:already-initialized? (addon/initialize! a {})))
      (is (= ["fosfora"] (mapv :name (addon/tools a))))
      (let [h (addon/health a)]
        (is (= :degraded (:status h)) "a closed Fosfora degrades, it is not down")
        (is (= :fosfora/not-listening (get-in h [:details :reason]))))
      (finally
        (is (nil? (addon/shutdown! a)))
        (is (nil? (addon/shutdown! a)))))
    (is (= :down (:status (addon/health a))))))
