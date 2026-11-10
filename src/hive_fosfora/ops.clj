;; SPDX-License-Identifier: MIT
(ns hive-fosfora.ops
  "Every operation on a running Fosfora, once, over an IFosforaLink. Each
   answers a Result. hive-fosfora.api unwraps them for a REPL;
   hive-fosfora.tools renders them for MCP."
  (:require [hive-dsl.result :as r]
            [hive-fosfora.message :as msg]
            [hive-fosfora.ports :as ports]
            [hive-fosfora.snapshot :as snap]))

(def state-wait-ms
  "How long a read waits for the first state after connecting."
  1500)

(defn send-built
  "Send a message built by hive-fosfora.message (a Result)."
  [link built]
  (r/bind built #(ports/send-message! link %)))

(defn state
  "The latest full state, or :fosfora/no-state before the first one."
  [link]
  (r/bind (ports/cache link state-wait-ms)
          (fn [c]
            (if-let [s (:state c)]
              (r/ok s)
              (r/err :fosfora/no-state
                     {:hint "Connected, but Fosfora has sent no state yet. Is a window open and rendering?"})))))

(defn audio
  "The latest audio features, or :fosfora/no-audio before the first ones."
  [link]
  (r/bind (ports/cache link state-wait-ms)
          (fn [c]
            (if-let [a (:audio c)]
              (r/ok a)
              (r/err :fosfora/no-audio {:hint "No audio features yet: is an audio input selected?"})))))

(defn summary [link] (r/bind (state link) #(r/ok (snap/summary %))))

(defn effects [link] (r/bind (state link) #(r/ok (vec (:effects %)))))

(defn presets [link] (r/bind (state link) #(r/ok (vec (:presets %)))))

(defn params
  "The params of layer `layer`, or of the active layer when nil."
  [link layer]
  (r/bind (state link)
          (fn [s]
            (if-let [l (snap/layer s layer)]
              (r/ok (vec (:params l)))
              (r/err :fosfora/not-found {:kind :layer :pick layer})))))

(defn- pick-index
  "An index from `pick`: a number as is, a name resolved against the state."
  [link kind k pick]
  (if (number? pick)
    (r/ok pick)
    (r/bind (state link) #(snap/resolve-index kind (get % k) pick))))

(defn set-param
  "Set `param` to normalized `v` on layer `layer`, or on the active layer when nil."
  [link layer param v]
  (send-built link (if (nil? layer)
                     (msg/set-param param v)
                     (msg/set-layer-param layer param v))))

(defn load-effect
  "Load the effect `pick` names (index or name) on the active layer."
  [link pick]
  (r/bind (pick-index link :effect :effects pick) #(send-built link (msg/load-effect %))))

(defn load-preset
  "Load the preset `pick` names (index or name)."
  [link pick]
  (r/bind (pick-index link :preset :presets pick) #(send-built link (msg/load-preset %))))

(defn select-layer [link i] (send-built link (msg/select-layer i)))

(defn opacity [link layer v] (send-built link (msg/opacity layer v)))

(defn blend [link layer mode] (send-built link (msg/blend layer mode)))

(defn enable [link layer on?] (send-built link (msg/enable layer on?)))

(defn postprocess [link on?] (send-built link (msg/postprocess on?)))

(defn trigger [link action] (send-built link (msg/trigger action)))

(defn data [link source fields] (send-built link (msg/data source fields)))

(defn raw [link m] (send-built link (msg/raw m)))
