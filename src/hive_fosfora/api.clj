;; SPDX-License-Identifier: MIT
(ns hive-fosfora.api
  "Live-code a running Fosfora from a REPL, over its web remote.

     (require '[hive-fosfora.api :as f])
     (f/summary)                    ; layers, active params, counts
     (f/set-param! :speed 0.8)      ; normalized 0..1, active layer
     (f/load-effect! \"aurora\")      ; index or name
     (f/trigger! :next-preset)
     (f/data! \"lfo\" {:a 0.5})        ; binding bus: lfo.a

   Values for params and opacity are normalized 0..1, as the web remote
   sends them. Every call throws ex-info carrying the error Result on
   failure. Inside hive-mcp this shares the link the `fosfora` MCP tool uses."
  (:require [hive-dsl.result :as r]
            [hive-fosfora.config :as config]
            [hive-fosfora.ops :as ops]
            [hive-fosfora.ports :as ports]
            [hive-fosfora.transport.ws :as ws]))

(def ^:dynamic *link*
  "When bound, the link every call in its scope uses (e.g. a test double)."
  nil)

(defonce ^:private installed (atom nil))

(defn install!
  "Make `link` the shared link, closing the one it replaces. Returns `link`."
  [link]
  (let [[old _] (reset-vals! installed link)]
    (when (and old (not (identical? old link)))
      (ports/close! old))
    link))

(defn uninstall!
  "Close and forget the shared link. Returns nil."
  []
  (let [[old _] (reset-vals! installed nil)]
    (when old (ports/close! old))
    nil))

(defn link
  "The link in use: *link* when bound, else the shared one, created from the
   environment on first use."
  []
  (or *link*
      @installed
      (swap! installed #(or % (ws/link (config/endpoint (System/getenv) {}))))))

(defn status
  "Connection status, without connecting."
  []
  (ports/status (link)))

(defn connect!
  "Point the shared link at another endpoint, e.g. {:host \"10.0.0.5\" :key \"...\"},
   and connect. Returns the status."
  ([] (connect! {}))
  ([overrides]
   (let [l (install! (ws/link (config/endpoint (System/getenv) overrides)))]
     (ports/cache l 0)
     (ports/status l))))

(defn disconnect! [] (uninstall!))

(defn- unwrap [res]
  (if (r/err? res)
    (throw (ex-info (str (symbol (:error res))
                         (when-let [h (:hint res)] (str ": " h)))
                    res))
    (:ok res)))

(defn state "The latest full state." [] (unwrap (ops/state (link))))

(defn audio "The latest audio features (bands, rms, kick, onset, beat, bpm...)." []
  (unwrap (ops/audio (link))))

(defn summary "Layers, the active layer's params, counts." [] (unwrap (ops/summary (link))))

(defn effects "[{:index :name}] of every loadable effect." [] (unwrap (ops/effects (link))))

(defn presets "[{:index :name :builtin}]." [] (unwrap (ops/presets (link))))

(defn params
  "Params of the active layer, or of layer `layer`."
  ([] (params nil))
  ([layer] (unwrap (ops/params (link) layer))))

(defn send!
  "Send a raw web-remote message, e.g. {:type \"set_param\" :name \"speed\" :value 0.5}."
  [m]
  (unwrap (ops/raw (link) m)))

(defn set-param! "Set `param` on the active layer; `v` is 0..1." [param v]
  (unwrap (ops/set-param (link) nil param v)))

(defn set-layer-param! "Set `param` on layer `layer`; `v` is 0..1." [layer param v]
  (unwrap (ops/set-param (link) layer param v)))

(defn load-effect! "Load an effect by index or name on the active layer." [pick]
  (unwrap (ops/load-effect (link) pick)))

(defn load-preset! "Load a preset by index or name." [pick]
  (unwrap (ops/load-preset (link) pick)))

(defn select-layer! [i] (unwrap (ops/select-layer (link) i)))

(defn opacity! "Opacity of layer `layer`, 0..1." [layer v] (unwrap (ops/opacity (link) layer v)))

(defn blend! "Blend mode number `mode` on layer `layer`." [layer mode]
  (unwrap (ops/blend (link) layer mode)))

(defn enable! [layer on?] (unwrap (ops/enable (link) layer on?)))

(defn postprocess! [on?] (unwrap (ops/postprocess (link) on?)))

(defn trigger!
  "Fire a trigger: :next-effect :prev-effect :next-preset :prev-preset
   :next-layer :prev-layer :toggle-postprocess :toggle-overlay
   :scene-go-next :scene-go-prev :toggle-timeline :tempo-half :tempo-double :tempo-tap."
  [action]
  (unwrap (ops/trigger (link) action)))

(defn data!
  "Feed the binding bus: (data! \"lfo\" {:a 0.5}) sets lfo.a. Unbound values
   expire after a few seconds, so send steadily."
  [source fields]
  (unwrap (ops/data (link) source fields)))
