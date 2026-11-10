;; SPDX-License-Identifier: MIT
(ns hive-fosfora.tools
  "The `fosfora` MCP tool: one tool, a `command` verb and its `params`.
   The verbs are the `commands` table; each runs one hive-fosfora.ops call."
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [hive-dsl.result :as r]
            [hive-fosfora.ops :as ops]
            [hive-fosfora.ports :as ports]))

(def commands
  "verb -> {:doc :params :run (fn [link params] Result)}. `params` is a
   string-keyed map. :params documents the keys for the tool description."
  (array-map
   "status"       {:doc "Connection status, without connecting." :params ""
                   :run (fn [l _] (r/ok (ports/status l)))}
   "state"        {:doc "Layers, the active layer's params, counts; full=true for the raw state."
                   :params "full?"
                   :run (fn [l p] (if (get p "full") (ops/state l) (ops/summary l)))}
   "audio"        {:doc "Latest audio features (bands, rms, kick, onset, beat, bpm)." :params ""
                   :run (fn [l _] (ops/audio l))}
   "effects"      {:doc "Loadable effects [{index name}]; query filters by name." :params "query?"
                   :run (fn [l p]
                          (let [q (some-> (get p "query") str str/lower-case)]
                            (r/bind (ops/effects l)
                                    #(r/ok (if (str/blank? q)
                                             %
                                             (filterv (fn [e] (str/includes? (str/lower-case (:name e)) q)) %))))))}
   "presets"      {:doc "Presets [{index name builtin}]." :params ""
                   :run (fn [l _] (ops/presets l))}
   "params"       {:doc "Params of a layer (default: active)." :params "layer?"
                   :run (fn [l p] (ops/params l (get p "layer")))}
   "set_param"    {:doc "Set a param, value normalized 0..1; on the active layer unless layer is given."
                   :params "param value layer?"
                   :run (fn [l p] (ops/set-param l (get p "layer") (get p "param") (get p "value")))}
   "load_effect"  {:doc "Load an effect on the active layer, by index or name." :params "effect"
                   :run (fn [l p] (ops/load-effect l (get p "effect")))}
   "load_preset"  {:doc "Load a preset by index or name." :params "preset"
                   :run (fn [l p] (ops/load-preset l (get p "preset")))}
   "select_layer" {:doc "Make a layer active." :params "layer"
                   :run (fn [l p] (ops/select-layer l (get p "layer")))}
   "opacity"      {:doc "Layer opacity 0..1." :params "layer value"
                   :run (fn [l p] (ops/opacity l (get p "layer") (get p "value")))}
   "blend"        {:doc "Layer blend mode number." :params "layer mode"
                   :run (fn [l p] (ops/blend l (get p "layer") (get p "mode")))}
   "enable"       {:doc "Turn a layer on or off." :params "layer on"
                   :run (fn [l p] (ops/enable l (get p "layer") (get p "on")))}
   "postprocess"  {:doc "Turn post-processing on or off." :params "on"
                   :run (fn [l p] (ops/postprocess l (get p "on")))}
   "trigger"      {:doc "Fire a trigger: next_effect prev_effect next_preset prev_preset next_layer prev_layer toggle_postprocess toggle_overlay scene_go_next scene_go_prev toggle_timeline tempo_half tempo_double tempo_tap."
                   :params "action"
                   :run (fn [l p] (ops/trigger l (get p "action")))}
   "data"         {:doc "Feed the binding bus: fields {name number} become <source>.<name>; send steadily, unbound values expire."
                   :params "source fields"
                   :run (fn [l p] (ops/data l (get p "source") (get p "fields")))}
   "send"         {:doc "Send a raw web-remote message object with a \"type\"." :params "message"
                   :run (fn [l p] (ops/raw l (get p "message")))}))

(defn- str-keys [m]
  (into {} (map (fn [[k v]] [(if (keyword? k) (name k) (str k)) v])) m))

(defn- verb [command]
  (some-> command str str/trim str/lower-case (str/replace "-" "_")))

(defn- text [s] {:content [{:type "text" :text s}]})

(defn render
  "An MCP result for Result `res`. Errors keep their qualified reason as a handle."
  [res]
  (if (r/err? res)
    (assoc (text (str (symbol (:error res))
                      (when-let [h (:hint res)] (str ": " h))
                      "\n" (json/write-str (dissoc res :error :hint))))
           :isError true)
    (text (json/write-str (:ok res)))))

(defn run
  "Run verb `command` with `params` against the link `link-fn` answers."
  [link-fn command params]
  (let [v (verb command)]
    (if-let [{:keys [run]} (get commands v)]
      (render (run (link-fn) (str-keys (or params {}))))
      (render (r/err :fosfora/unknown-command
                     {:command command :commands (vec (keys commands))})))))

(defn- description []
  (str "Live-control a running Fosfora (visuals) over its web remote. Commands: "
       (str/join "; " (map (fn [[k {:keys [doc params]}]]
                             (str k (when-not (str/blank? params) (str " (" params ")")) " - " doc))
                           commands))
       ". Values for params and opacity are normalized 0..1."))

(defn fosfora-tool
  "The `fosfora` tool over the link `link-fn` answers at each call."
  [link-fn]
  {:name        "fosfora"
   :description (description)
   :inputSchema {:type "object"
                 :additionalProperties false
                 :properties {"command" {:type "string" :enum (vec (keys commands))
                                         :description "The verb to run."}
                              "params"  {:type "object"
                                         :description "The verb's arguments, e.g. {\"param\": \"speed\", \"value\": 0.8}."}}
                 :required ["command"]}
   :annotations {:readOnlyHint false :destructiveHint false :idempotentHint false :openWorldHint true}
   :handler     (fn [params]
                  (let [p (str-keys params)]
                    (run link-fn (get p "command") (get p "params"))))})

(defn tools [link-fn] [(fosfora-tool link-fn)])
