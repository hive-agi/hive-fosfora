;; SPDX-License-Identifier: MIT
(ns hive-fosfora.message
  "Constructors for the messages Fosfora's web remote accepts.

   Every constructor is pure and answers a Result: (r/ok msg) with a map
   ready for JSON, or (r/err :fosfora/invalid-arg {:arg :value :expected}).
   Values for params and opacity are normalized 0..1, as the remote sends
   them; Fosfora scales them to each param's own range."
  (:require [clojure.string :as str]
            [hive-dsl.result :as r]))

(def trigger-actions
  "The trigger actions Fosfora accepts; anything else it drops silently."
  #{"next_effect" "prev_effect" "toggle_postprocess" "toggle_overlay"
    "next_preset" "prev_preset" "next_layer" "prev_layer"
    "scene_go_next" "scene_go_prev" "toggle_timeline"
    "tempo_half" "tempo_double" "tempo_tap"})

(defn- invalid [arg value expected]
  (r/err :fosfora/invalid-arg {:arg arg :value value :expected expected}))

(defn- finite? [x]
  (and (number? x) (Double/isFinite (double x))))

(defn- unit [arg v]
  (if (and (finite? v) (<= 0.0 (double v) 1.0))
    (r/ok (double v))
    (invalid arg v "a number in 0..1")))

(defn- index [arg v]
  (if (and (finite? v) (>= v 0) (== v (Math/floor (double v))))
    (r/ok (long v))
    (invalid arg v "a non-negative integer")))

(defn- bool [arg v]
  (if (boolean? v)
    (r/ok v)
    (invalid arg v "true or false")))

(defn- label
  "A param or source name: keyword or string, not blank."
  [arg v]
  (let [s (cond (keyword? v) (name v) (string? v) v)]
    (if (str/blank? s)
      (invalid arg v "a non-blank name (string or keyword)")
      (r/ok s))))

(defn- action [v]
  (let [s (some-> (cond (keyword? v) (name v) (string? v) v)
                  str/trim str/lower-case (str/replace "-" "_"))]
    (if (contains? trigger-actions s)
      (r/ok s)
      (invalid :action v (str "one of " (str/join ", " (sort trigger-actions)))))))

(defn- fields-map [v]
  (if (and (map? v) (seq v)
           (every? (fn [[k x]] (and (or (keyword? k) (string? k)) (finite? x))) v))
    (r/ok (into (sorted-map) (map (fn [[k x]] [(name k) (double x)])) v))
    (invalid :fields v "a non-empty map of field name to finite number")))

(defn- message
  "Assemble a message of `type` from checked fields, or the first error."
  [type checked]
  (reduce-kv (fn [acc k res]
               (if (r/err? res)
                 (reduced res)
                 (update acc :ok assoc k (:ok res))))
             (r/ok {:type type})
             checked))

(defn set-param
  "Set `param` on the active layer to normalized `v`."
  [param v]
  (message "set_param" (array-map :name (label :param param) :value (unit :value v))))

(defn set-layer-param
  "Set `param` on layer `layer` to normalized `v`."
  [layer param v]
  (message "set_layer_param" (array-map :layer (index :layer layer)
                                        :name (label :param param)
                                        :value (unit :value v))))

(defn load-effect
  "Load effect number `i` (an index into the state's effects) on the active layer."
  [i]
  (message "load_effect" {:index (index :index i)}))

(defn select-layer
  "Make layer `i` the active layer."
  [i]
  (message "select_layer" {:index (index :index i)}))

(defn load-preset
  "Load preset number `i`."
  [i]
  (message "load_preset" {:index (index :index i)}))

(defn opacity
  "Opacity of layer `layer`, 0..1."
  [layer v]
  (message "set_layer_opacity" (array-map :layer (index :layer layer) :value (unit :value v))))

(defn blend
  "Blend mode number `mode` on layer `layer`."
  [layer mode]
  (message "set_layer_blend" (array-map :layer (index :layer layer) :value (index :mode mode))))

(defn enable
  "Turn layer `layer` on or off."
  [layer on?]
  (message "set_layer_enabled" (array-map :layer (index :layer layer) :value (bool :on on?))))

(defn postprocess
  "Turn post-processing on or off."
  [on?]
  (message "set_postprocess_enabled" {:value (bool :on on?)}))

(defn trigger
  "Fire a trigger. `a` may be kebab or snake case: :next-preset, \"next_preset\"."
  [a]
  (message "trigger" {:action (action a)}))

(defn data
  "Feed the binding bus: each field becomes \"<source>.<field>\". Fosfora
   expires an unbound value after a few seconds, so send it steadily."
  [source fields]
  (message "data" (array-map :source (label :source source) :fields (fields-map fields))))

(defn raw
  "Pass a hand-built message through, after checking it names a type."
  [m]
  (let [t (when (map? m) (or (get m :type) (get m "type")))]
    (if (and (string? t) (not (str/blank? t)))
      (r/ok m)
      (invalid :message m "a map with a string \"type\""))))
