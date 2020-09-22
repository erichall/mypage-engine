(ns mypage-engine.server
  (:require
    [org.httpkit.server :refer [run-server]]
    [clj-totp.core :as totp]
    [mypage-engine.api_v1 :refer [app]]
    ))

(def server-port 8885)
(def server-ip "0.0.0.0")

(defonce server-atom (atom nil))

(defn stop-server!
  []
  (when-not (nil? (deref server-atom))
    ((deref server-atom) :timeout 100)
    (reset! server-atom nil)))

(defn handler
  [state-atom request]
  (app {:state-atom state-atom :request request}))

(defn start-server!
  [state-atom]
  (when (nil? (deref server-atom))
    (do
      (println (str "Starting server: " server-ip ":" server-port))
      (reset! server-atom (run-server (fn [request]
                                        (handler state-atom request))
                                      {:port server-port :ip server-ip})))))

(comment
  (start-server! state-atom)
  (stop-server!)

  (totp/generate-key "Eric Hallstrom" "hallstrom.eric@gmail.com")
  (totp/valid-code? "" 926280)
  )

