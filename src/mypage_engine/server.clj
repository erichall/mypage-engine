(ns mypage-engine.server
  (:require
    [org.httpkit.server :refer [run-server]]
    [taoensso.timbre :as log]
    [mypage-engine.api_v1 :refer [app]]
    ))

(defonce server-atom (atom nil))

(defn stop-server!
  []
  (when-not (nil? (deref server-atom))
    ((deref server-atom) :timeout 100)
    (reset! server-atom nil)))

(defn handler
  [state-atom config-atom request]
  (app {:state-atom  state-atom
        :request     request
        :config-atom config-atom}))

(defn start-server!
  [{:keys [state-atom config-atom]}]
  (when (nil? (deref server-atom))
    (do
      (log/info (str "Starting server: " (get @config-atom :server-ip) ":" (get @config-atom :server-port)))
      (reset! server-atom (run-server (fn [request]
                                        (handler state-atom config-atom request))
                                      {:port (get @config-atom :server-port)
                                       :ip   (get @config-atom :server-ip)})))))

