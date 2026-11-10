;; SPDX-License-Identifier: MIT
(ns hive-fosfora.fake-remote
  "A minimal stand-in for Fosfora's web remote, on a free loopback port: it
   completes one WebSocket upgrade, pushes the given text frames, and records
   the request and every text frame the client sends."
  (:import (java.io DataInputStream InputStream OutputStream)
           (java.net InetAddress ServerSocket Socket SocketException SocketTimeoutException)
           (java.nio.charset StandardCharsets)
           (java.security MessageDigest)
           (java.util Base64)))

(defn- read-line* [^InputStream in]
  (let [sb (StringBuilder.)]
    (loop []
      (let [b (.read in)]
        (cond
          (neg? b) (str sb)
          (= b 10) (let [s (str sb)] (if (.endsWith s "\r") (subs s 0 (dec (count s))) s))
          :else (do (.append sb (char b)) (recur)))))))

(defn- accept-key [k]
  (let [d (.digest (MessageDigest/getInstance "SHA-1")
                   (.getBytes (str k "258EAFA5-E914-47DA-95CA-C5AB0DC85B11") StandardCharsets/US_ASCII))]
    (.encodeToString (Base64/getEncoder) d)))

(defn- write-frame! [^OutputStream out opcode ^bytes payload]
  (let [n (alength payload)]
    (.write out (bit-or 0x80 opcode))
    (cond
      (< n 126) (.write out n)
      :else (do (.write out 126) (.write out (bit-shift-right n 8)) (.write out (bit-and n 0xff))))
    (.write out payload)
    (.flush out)))

(defn- read-frame
  "[opcode payload-bytes] of the next client frame (always masked)."
  [^DataInputStream in]
  (let [b0 (.readUnsignedByte in)
        b1 (.readUnsignedByte in)
        n0 (bit-and b1 0x7f)
        n  (case n0 126 (.readUnsignedShort in) 127 (.readLong in) n0)
        mask (byte-array 4)
        _ (.readFully in mask)
        data (byte-array n)]
    (.readFully in data)
    (dotimes [i n]
      (aset-byte data i (unchecked-byte (bit-xor (aget data i) (aget mask (mod i 4))))))
    [(bit-and b0 0x0f) data]))

(defn- serve [^Socket s frames record]
  (with-open [s s]
    (.setSoTimeout s 5000)
    (let [in (DataInputStream. (.getInputStream s))
          out (.getOutputStream s)
          request (read-line* in)
          headers (loop [h {}]
                    (let [line (read-line* in)]
                      (if (empty? line)
                        h
                        (let [i (.indexOf line ":")]
                          (recur (assoc h (.toLowerCase (subs line 0 i)) (.trim (subs line (inc i)))))))))]
      (swap! record assoc :request request :headers headers)
      (.write out (.getBytes (str "HTTP/1.1 101 Switching Protocols\r\n"
                                  "Upgrade: websocket\r\nConnection: Upgrade\r\n"
                                  "Sec-WebSocket-Accept: " (accept-key (get headers "sec-websocket-key")) "\r\n\r\n")
                             StandardCharsets/US_ASCII))
      (doseq [f frames] (write-frame! out 1 (.getBytes ^String f StandardCharsets/UTF_8)))
      (loop []
        (let [[op data] (read-frame in)]
          (case (int op)
            1 (do (swap! record update :received (fnil conj []) (String. ^bytes data StandardCharsets/UTF_8))
                  (recur))
            8 (write-frame! out 8 (byte-array 0))
            (recur)))))))

(defn start!
  "Serve one connection on a free loopback port, pushing `frames` (JSON
   strings) after the upgrade. Returns {:port :record (atom) :stop (fn)}."
  [frames]
  (let [ss (ServerSocket. 0 1 (InetAddress/getLoopbackAddress))
        record (atom {})
        t (doto (Thread. (fn []
                           (try
                             (serve (.accept ss) frames record)
                             (catch SocketException e (swap! record assoc :ended (str e)))
                             (catch SocketTimeoutException e (swap! record assoc :ended (str e)))
                             (catch java.io.EOFException e (swap! record assoc :ended (str e))))))
            (.setDaemon true)
            (.start))]
    {:port (.getLocalPort ss)
     :record record
     :stop (fn [] (.close ss) (.join t 2000))}))
