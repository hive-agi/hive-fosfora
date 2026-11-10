;; SPDX-License-Identifier: MIT
(ns hive-fosfora.ports
  "The one port to a running Fosfora. The WebSocket adapter lives in
   hive-fosfora.transport.ws; tests pass records of their own.")

(defprotocol IFosforaLink
  (send-message! [link msg]
    "Send message map `msg` as one JSON text frame, connecting first when
     needed. Answers (r/ok msg) or (r/err reason {...}); never throws.")
  (cache [link wait-ms]
    "The snapshot cache ({:state :audio :state-at :audio-at}), connecting
     first when needed and waiting up to `wait-ms` for the first state.
     Answers (r/ok cache) or (r/err reason {...}); never throws.")
  (status [link]
    "{:url :connected? :has-state? :state-at :audio-at :error}, without connecting.")
  (close! [link]
    "Close the connection. Idempotent; returns nil."))
