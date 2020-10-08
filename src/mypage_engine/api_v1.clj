(ns mypage-engine.api_v1
  (:require [clojure.data.json :refer [read-json write-str write-str]]
            [taoensso.timbre :as log]
            [org.httpkit.server :refer [send! with-channel on-close on-receive]]
            [mypage-engine.websocket :as ws]))

(defn header
  [response name value]
  (assoc-in response [:headers name] (str value)))

(defn set-default-headers-middleware
  [handler]
  (fn [request & args]
    (-> (handler request)
        (header "X-app" "mypage-engine-v1")
        (header "Content-Type" "text/json; charset=UTF-8")
        (header "Access-Control-Allow-Headers" "Accept,Content-Type, text/json"))))

(defn app-routes
  [{:keys [request] :as args}]

  (log/info request)

  (condp = (:uri request)

    "/api/v1/" (assoc request :body "<h1>Hello world!</h1>")
    "/api/ws/" (ws/ws-handler args)

    ;; else
    (assoc {:status 404 :headers {}} :body "<div align= \"center \"><h4>Ooops... 404</h4><h1>¯ \\_ (ツ) _ / ¯</h1></div>")

    ))

(def app
  (-> app-routes
      set-default-headers-middleware))


