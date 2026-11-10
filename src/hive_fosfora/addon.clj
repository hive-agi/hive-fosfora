;; SPDX-License-Identifier: MIT
(ns hive-fosfora.addon
  "The IAddon: the `fosfora` MCP tool over the link hive-fosfora.api shares.
   initialize! opens no connection, so a closed Fosfora never blocks the mount."
  (:require [hive-addon.protocol :as addon]
            [hive-fosfora.api :as api]
            [hive-fosfora.config :as config]
            [hive-fosfora.ports :as ports]
            [hive-fosfora.tools :as tools]
            [hive-fosfora.transport.ws :as ws]))

(def owner "hive.fosfora")

(defrecord FosforaAddon [state config]
  addon/IAddon
  (addon-id [_] owner)
  (addon-type [_] :external)
  (capabilities [_] #{:tools :health-reporting})
  (initialize! [_ cfg]
    (if (:ready? @state)
      {:success? true :already-initialized? true :errors []}
      (let [ep (config/endpoint (System/getenv) (merge config cfg))]
        (api/install! (ws/link ep))
        (reset! state {:ready? true :endpoint ep})
        {:success? true :errors [] :metadata {:url (config/url (config/redacted ep))}})))
  (shutdown! [_]
    (when (:ready? @state) (api/uninstall!))
    (reset! state {:ready? false})
    nil)
  (tools [_]
    (if (:ready? @state) (tools/tools api/link) []))
  (schema-extensions [_] {})
  (health [_]
    (if-not (:ready? @state)
      {:status :down :details {:reason :not-initialized}}
      (let [res (ports/cache (api/link) 0)
            st  (ports/status (api/link))]
        (if (:error res)
          {:status :degraded
           :details (assoc st :reason (:error res) :hint (:hint res))}
          {:status :ok :details st}))))
  (excluded-tools [_] #{})
  (hooks [_] {}))

(defn addon-ctor
  "The manifest's init-fn."
  [config]
  (->FosforaAddon (atom {:ready? false}) (or config {})))
