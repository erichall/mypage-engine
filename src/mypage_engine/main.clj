(ns mypage-engine.main
  (:require [mypage-engine.server :refer [start-server! stop-server!]]
            [mypage-engine.websocket :as ws]
            [mypage-engine.core :refer [exists?
                                        print-exit]]
            [taoensso.timbre :as t]
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
  {:content   {
               :front-page {:intro "Hi! I'm Eric."
                            :about "I work as a software engineer at TietoEvry. I received my Masters in Computer Science from the Royal Institute of Technology in Sweden. Apart from coding and tinkering with electronics I'm interested in baking, brewing and cooking."}
               :resume     {:text "Hello this is my resume."}
               :about-me   {:text "I'm a developer."}
               :posts      {:text "Here is my thoughts about things."}
               :portfolio  {:text "Here is what I've done."}
               :header     {:title    "Eric Hallström"
                            :subtitle "Software Engineer"}
               }
   :posts     {
               :a {:points 2 :title "How is it to work as a developer" :created "2020-02-03" :content "boboo" :id "a" :author "Eric Hallström"}
               :b {:points 10 :title "When consulting fails" :created "2020-04-03" :content "To be or not to be in here" :id "b" :author "Eric Hallström"}
               :c {:points -20 :title "Are we there yet?" :created "2020-05-03" :content "What can we do about stuff" :id "c" :author "Eric Hallström"}
               }
   :portfolio {:my-awesome-tool {:text "cool stuff"}}
   })

(when (nil? (deref state-atom))
  (add-watch state-atom
             :game-loop
             (fn [_ _ old-value new-value]
               (ws/broadcast! {:data {:state new-value}})))
  (reset! state-atom initial-state))

(defn stop-repl!
  []
  (when-not (nil? (deref repl-server-atom))
    ((deref repl-server-atom) :timeout 100)
    (reset! repl-server-atom nil)))

(defn start-repl!
  []
  (when (nil? (deref repl-server-atom))
    (do
      (t/info (str "Starting repl at " (config :repl-ip) ":" (config :repl-port)))
      (reset! repl-server-atom (start-server
                                 :bind (config :repl-ip)
                                 :port (config :repl-port)
                                 :greeting (fn [x] (println (str "Hello" x)))
                                 :greeting-fn (fn [x] (println (str "Welcome! " x))))))))

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
      (let [conf (-> (slurp config-file) clojure.edn/read-string)]
        (reset! config-atom conf))))

  (configure-logs!)
  (restart-repl!)                                           ;; TODO not start this in production :)
  (start-server! {:state-atom state-atom :config-atom config-atom})
  (ws/initialize-ping-clients {:delay (* 1000 (config :ping-clients-delay-sec))})) ;; 30 sec

(comment

  (reset! config-atom (-> (slurp "config.edn" clojure.edn/read-string)))

  (start-server! {:state-atom state-atom :config-atom config-atom})
  (stop-server!)

  (ws/initialize-ping-clients {:delay (* 1000 10)})

  (deref state-atom)

  (reset! state-atom initial-state)

  (ws/broadcast! {:data {:state (deref state-atom)}})

  (swap! state-atom assoc-in [:content :front-page :text] "This is awesome")

  (totp/generate-key "Eric Hallstrom" "hallstrom.eric@gmail.com")
  )

