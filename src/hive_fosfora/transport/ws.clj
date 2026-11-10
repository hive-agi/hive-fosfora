;; SPDX-License-Identifier: MIT
(ns hive-fosfora.transport.ws
  "The only namespace that touches the network: a JDK WebSocket to Fosfora's
   web remote. It sends no Origin header, so a loopback client needs no key."
  (:require [clojure.data.json :as json]
            [hive-dsl.result :as r]
            [hive-fosfora.config :as config]
            [hive-fosfora.ports :as ports]
            [hive-fosfora.snapshot :as snap])
  (:import (java.net ConnectException URI)
           (java.net.http HttpClient HttpTimeoutException WebSocket WebSocket$Listener
                          WebSocketHandshakeException)
           (java.time Duration)
           (java.util.concurrent TimeUnit TimeoutException)))

(defn- listener
  "Folds every complete text frame into `cache`; tracks close and error."
  [cache]
  (let [buf (StringBuilder.)]
    (reify WebSocket$Listener
      (onOpen [_ ws]
        (.request ^WebSocket ws 1))
      (onText [_ ws data last?]
        (.append buf ^CharSequence data)
        (when last?
          (let [text (.toString buf)]
            (.setLength buf 0)
            (when-let [m (snap/decode text)]
              (swap! cache snap/fold m (System/currentTimeMillis)))))
        (.request ^WebSocket ws 1)
        nil)
      (onBinary [_ ws _ _]
        (.request ^WebSocket ws 1)
        nil)
      (onClose [_ _ code reason]
        (swap! cache assoc :connected? false :closed {:code code :reason reason})
        nil)
      (onError [_ _ t]
        (swap! cache assoc :connected? false :error (str t))))))

(defn- causes [^Throwable t]
  (take-while some? (iterate #(.getCause ^Throwable %) t)))

(defn- failure
  "The Result error for exception `t` while talking to `url`."
  [t url]
  (let [cs (causes t)
        of (fn [cls] (some #(when (instance? cls %) %) cs))]
    (if-let [^WebSocketHandshakeException h (of WebSocketHandshakeException)]
      (r/err :fosfora/refused
             {:url url :status (.statusCode (.getResponse h))
              :hint (str "Fosfora refused the upgrade. From another machine, turn on LAN in "
                         "Setup > Control > Web remote and set HIVE_FOSFORA_KEY to its access key.")})
      (cond
        (of ConnectException)
        (r/err :fosfora/not-listening
               {:url url :hint "Start Fosfora with the web remote on (Setup > Control > Web remote; port 9002 by default)."})
        (or (of TimeoutException) (of HttpTimeoutException))
        (r/err :fosfora/timeout {:url url})
        :else
        (r/err :fosfora/transport-error {:url url :message (str t)})))))

(defn- open? [^WebSocket ws]
  (boolean (and ws (not (.isOutputClosed ws)) (not (.isInputClosed ws)))))

(defn- connect [endpoint cache]
  (let [ms (Duration/ofMillis (:timeout-ms endpoint))]
    (-> (HttpClient/newBuilder)
        (.connectTimeout ms)
        (.build)
        (.newWebSocketBuilder)
        (.connectTimeout ms)
        (.buildAsync (URI/create (config/url endpoint)) (listener cache))
        (.get (:timeout-ms endpoint) TimeUnit/MILLISECONDS))))

(defn- ensure-open!
  "The open WebSocket, connecting when there is none. Answers a Result."
  [{:keys [endpoint cache conn lock]}]
  (locking lock
    (let [ws @conn]
      (if (open? ws)
        (r/ok ws)
        (let [url (config/url (config/redacted endpoint))
              res (try
                    (r/ok (connect endpoint cache))
                    (catch Exception e (failure e url)))]
          (if (r/ok? res)
            (do (reset! conn (:ok res))
                (swap! cache assoc :connected? true :error nil :closed nil))
            (swap! cache assoc :connected? false :error (:error res)))
          res)))))

(defn- send-text! [{:keys [endpoint lock]} ^WebSocket ws msg]
  (locking lock
    (try
      (.get (.sendText ws ^CharSequence (json/write-str msg) true)
            (:timeout-ms endpoint) TimeUnit/MILLISECONDS)
      (r/ok msg)
      (catch Exception e
        (failure e (config/url (config/redacted endpoint)))))))

(defn- await-state
  "The cache once it holds a state, or as it is after `wait-ms`."
  [cache wait-ms]
  (let [deadline (+ (System/currentTimeMillis) wait-ms)]
    (loop []
      (let [c @cache]
        (if (or (:state c) (>= (System/currentTimeMillis) deadline))
          c
          (do (Thread/sleep 20) (recur)))))))

(defrecord WsLink [endpoint cache conn lock]
  ports/IFosforaLink
  (send-message! [this msg]
    (r/bind (ensure-open! this) #(send-text! this % msg)))
  (cache [this wait-ms]
    (r/bind (ensure-open! this) (fn [_] (r/ok (await-state cache wait-ms)))))
  (status [_]
    (let [c @cache]
      {:url        (config/url (config/redacted endpoint))
       :connected? (open? @conn)
       :has-state? (some? (:state c))
       :state-at   (:state-at c)
       :audio-at   (:audio-at c)
       :error      (:error c)
       :closed     (:closed c)}))
  (close! [_]
    (locking lock
      (when-let [^WebSocket ws @conn]
        (reset! conn nil)
        (when (open? ws)
          (.sendClose ws WebSocket/NORMAL_CLOSURE "")))
      (swap! cache assoc :connected? false))
    nil))

(defn link
  "A link to the endpoint (see hive-fosfora.config). Connects on first use."
  [endpoint]
  (->WsLink endpoint (atom {}) (atom nil) (Object.)))
