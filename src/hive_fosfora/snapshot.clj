;; SPDX-License-Identifier: MIT
(ns hive-fosfora.snapshot
  "What Fosfora pushes, as values: decode a text frame, fold it into the
   link's cache, and answer questions about the cached state. Pure.

   Cache shape: {:state <last state msg> :audio <last audio msg>
                 :state-at ms :audio-at ms}. Keys inside :state and :audio
   are keywords named as Fosfora names them (snake_case)."
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [hive-dsl.result :as r]))

(def ^:private read-json
  (r/rescue-fn #(json/read-str % :key-fn keyword)))

(defn decode
  "A text frame as a map with keyword keys, or nil when it is not a JSON object."
  [text]
  (let [m (read-json text)]
    (when (map? m) m)))

(defn fold
  "The cache after message `m` arrived at `now-ms`. Unknown types change nothing."
  [cache m now-ms]
  (case (:type m)
    "state"        (assoc cache :state (dissoc m :type) :state-at now-ms)
    "audio"        (assoc cache :audio (dissoc m :type) :audio-at now-ms)
    "active_layer" (if (:state cache)
                     (assoc-in cache [:state :active_layer] (:index m))
                     cache)
    cache))

(defn layer
  "Layer `i` of `state`, or the active layer when `i` is nil."
  [state i]
  (let [i (if (nil? i) (:active_layer state) i)]
    (some #(when (= i (:index %)) %) (:layers state))))

(defn resolve-index
  "The index of the item in `items` ([{:index :name}]) that `pick` names.
   `pick` is an index, or a name matched exactly (ignoring case), then as a
   unique substring. Answers (r/ok index), or a :fosfora/not-found or
   :fosfora/ambiguous error listing candidates."
  [kind items pick]
  (if (number? pick)
    (if (some #(== pick (:index %)) items)
      (r/ok (long pick))
      (r/err :fosfora/not-found {:kind kind :pick pick :count (count items)}))
    (let [want  (str/lower-case (str (if (keyword? pick) (name pick) pick)))
          named (fn [it] (str/lower-case (str (:name it))))
          exact (filter #(= want (named %)) items)
          part  (filter #(str/includes? (named %) want) items)]
      (cond
        (str/blank? want)  (r/err :fosfora/not-found {:kind kind :pick pick})
        (seq exact)        (r/ok (:index (first exact)))
        (= 1 (count part)) (r/ok (:index (first part)))
        (seq part)         (r/err :fosfora/ambiguous {:kind kind :pick pick
                                                      :candidates (mapv :name (take 12 part))})
        :else              (r/err :fosfora/not-found {:kind kind :pick pick})))))

(defn summary
  "A compact view of `state`: counts, layers, and the active layer's params."
  [state]
  (when state
    (let [active (layer state nil)]
      {:active_layer   (:active_layer state)
       :current_preset (:current_preset state)
       :postprocess    (:postprocess_enabled state)
       :effects        (count (:effects state))
       :presets        (count (:presets state))
       :layers         (mapv #(select-keys % [:index :name :effect_name :blend_name
                                              :opacity :enabled :locked])
                             (:layers state))
       :params         (mapv #(select-keys % [:name :type :value :live]) (:params active))
       :streams        (:streams state)})))
