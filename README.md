# hive-fosfora

Live-code a running [Fosfora](https://github.com/kevinraymond/fosfora) from Clojure, from outside Fosfora.

hive-fosfora talks to Fosfora's web remote, the same WebSocket its phone control page uses. It adds nothing to Fosfora: no build feature and no dependency. It is a hive IAddon (the `fosfora` MCP tool) and a plain Clojure library (the `hive-fosfora.api` namespace for a REPL).

## Connect

Fosfora's web remote is on by default at port 9002 (Setup > Control > Web remote). On the same machine nothing else is needed. From another machine, turn on LAN there and give hive-fosfora the access key.

| Variable | Default | |
|---|---|---|
| `HIVE_FOSFORA_HOST` | `127.0.0.1` | |
| `HIVE_FOSFORA_PORT` | `9002` | |
| `HIVE_FOSFORA_KEY` | none | Access key; `FOSFORA_KEY` works too, as for Fosfora's bridges. Not needed on loopback. |
| `HIVE_FOSFORA_TIMEOUT_MS` | `3000` | Connect and send timeout. |

## From a REPL

```clojure
(require '[hive-fosfora.api :as f])

(f/summary)                  ; layers, the active layer's params, counts
(f/effects)                  ; [{:index :name}]
(f/load-effect! "aurora")    ; index or name (exact, then unique substring)
(f/set-param! :speed 0.8)    ; normalized 0..1, on the active layer
(f/set-layer-param! 0 :intensity 0.3)
(f/opacity! 1 0.5)
(f/trigger! :next-preset)
(f/data! "lfo" {:a 0.5})     ; binding bus: lfo.a; send steadily
(f/audio)                    ; bands, rms, kick, onset, beat, bpm
(f/connect! {:host "10.0.0.5" :key "..."})
```

Values for params and opacity are normalized 0..1, exactly as the web remote sends them; Fosfora scales each to its own range. A failing call throws `ex-info` whose data is the error, with a qualified reason such as `:fosfora/not-listening`, `:fosfora/ambiguous` or `:fosfora/invalid-arg`.

Inside hive-mcp, connect CIDER, Calva or Conjure to hive's nREPL and require the same namespace: the REPL and the MCP tool share one connection.

## The `fosfora` MCP tool

One tool, a `command` and its `params`: `status`, `state`, `audio`, `effects`, `presets`, `params`, `set_param`, `load_effect`, `load_preset`, `select_layer`, `opacity`, `blend`, `enable`, `postprocess`, `trigger`, `data`, `send`.

```json
{"command": "load_effect", "params": {"effect": "plasma"}}
{"command": "set_param",   "params": {"param": "speed", "value": 0.8}}
```

## What it cannot do

The web remote cannot open local files, on purpose, so neither can this. Loading images or videos as media layers is left to Fosfora's own UI.

## Development

```
clojure -M:test          # kaocha
clojure -M:test:nrepl    # a REPL with the tests on the path
```

The WebSocket adapter is tested against a small fake web remote on a free port (`test/hive_fosfora/fake_remote.clj`). Everything else runs on record doubles.

## License

MIT
