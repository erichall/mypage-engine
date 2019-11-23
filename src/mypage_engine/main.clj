(ns mypage-engine.main
  (:require [mypage-engine.server :refer [start-server! stop-server!]]
            [mypage-engine.websocket :as ws]
            [nrepl.server :refer [start-server stop-server]])
  (:gen-class))

(def repl-port 5557)
(def repl-ip "0.0.0.0")

(defonce repl-server-atom (atom nil))
(defonce state-atom (atom nil))

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
               (ws/broadcast! {:data {:state new-value}})
               ))
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
      (println (str "Starting repl at " repl-ip ":" repl-port))
      (reset! repl-server-atom (start-server
                                 :bind repl-ip
                                 :port repl-port
                                 :greeting (fn [x] (println (str "Hello" x)))
                                 :greeting-fn (fn [x] (println (str "Welcome! " x))))))))

(defn restart-repl!
  []
  (when (nil? (deref repl-server-atom))
    (start-repl!)))

(defn -main
  [& args]
  (println "Engine is loading.....")
  (restart-repl!)                                           ;; TODO not start this in production :)
  (start-server! state-atom))

(comment
  (start-server! state-atom)
  (stop-server!)

  (deref state-atom)



  (reset! state-atom initial-state)
  (swap! state-atom assoc-in [:content :front-page :text] "CHANGE FROM THE BACKEND!!!!")
  (swap! state-atom assoc-in [:content :header :title] "ERIC Hallllllls")

  (ws/broadcast! {:data {:state (deref state-atom)}})

  (swap! state-atom assoc-in [:content :front-page :text] "This is awesome")

  (totp/generate-key "Eric Hallstrom" "hallstrom.eric@gmail.com")
  (totp/valid-code? "3VDZV3ZWTN2ILIHZ" 926280)
  )

