(ns mypage-engine.main
  (:require [mypage-engine.server :refer [start-server! stop-server!]]
            [mypage-engine.websocket :as ws]
            [mypage-engine.io-handler :refer [exists? read-posts-from-disk]]
            [mypage-engine.core :refer [print-exit]]
            [mypage-engine.security :refer [initialize-secrets]]
            [taoensso.timbre :as t]
            [clojure.edn :as edn]
            [taoensso.timbre.appenders.core :as appenders]
            [taoensso.timbre.appenders.3rd-party.rotor :as rotor]
            [nrepl.server :refer [start-server stop-server]])
  (:gen-class))


(defonce repl-server-atom (atom nil))
(defonce state-atom (atom nil))
(defonce config-atom (atom nil))

(defn config
  [prop]
  (get-in (deref config-atom) (if (vector? prop) prop [prop])))

(defn configure-logs!
  []
  (t/merge-config! {:appenders {:spit  (appenders/spit-appender {:fname (config :log-file-name)})
                                :rotor (rotor/rotor-appender {:path    (config :log-file-name)
                                                              :backlog 20})}}))
(def initial-state
  {:misc  {:header {:title    "Eric Hallström"
                    :subtitle "Software Engineer"}}
   :pages {:front-page nil
           :resume     nil
           :posts      nil
           :portfolio  nil
           :login      nil
           }})

(when (nil? (deref state-atom))
  (add-watch state-atom
             :game-loop
             (fn [_ _ old-value new-value]))
  (reset! state-atom initial-state))

(defn stop-repl!
  []
  (when-not (nil? (deref repl-server-atom))
    ((deref repl-server-atom) :timeout 100)
    (reset! repl-server-atom nil)))

(defn start-repl!
  []
  (when (nil? (deref repl-server-atom))
    (t/info
      (str
        "Starting repl at "
        (config :repl-ip)
        ":"
        (config :repl-port)))
    (reset! repl-server-atom (start-server
                               :bind (config :repl-ip)
                               :port (config :repl-port)
                               :greeting (fn [x] (println (str "Hello" x)))
                               :greeting-fn (fn [x] (println (str "Welcome! " x)))))))

(defn restart-repl!
  []
  (when (nil? (deref repl-server-atom))
    (start-repl!)))

(defn -main
  [& args]
  (t/info "Engine is loading.....")

  (let [conf (apply hash-map args)
        config-file (get conf "--config")]
    (if (and (nil? config-file) (not (exists? config-file)))
      (print-exit (str "Unable to find config-file: " config-file " provide it with --config <file-name>.edn"))
      (let [conf (edn/read-string (slurp config-file))]
        (reset! config-atom conf))))

  (initialize-secrets (deref config-atom))

  (when-not (= (config :env) :prod)
    (configure-logs!)
    (restart-repl!)
    (ws/initialize-ping-clients {:delay (* 1000 (config :ping-clients-delay-sec))}))

  (start-server! {:state-atom state-atom :config-atom config-atom}))

(comment

  (t/set-level! :fatal)

  (do
    (reset! config-atom (-> "config.edn" slurp edn/read-string))

    (reset! state-atom initial-state)
    (initialize-secrets (deref config-atom))

    (start-server! {:state-atom state-atom :config-atom config-atom})
    )

  (stop-server!)

  (ws/initialize-ping-clients {:delay (* 1000 10)})

  (ws/broadcast! {:data {:state (deref state-atom)}})

  (totp/generate-key "Eric Hallstrom" "hallstrom.eric@gmail.com")
  )

