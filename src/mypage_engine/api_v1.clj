(ns mypage-engine.api_v1
  (:require [clojure.data.json :refer [read-json write-str write-str]]
            [taoensso.timbre :as log]
            [org.httpkit.server :refer [send! with-channel on-close on-receive]]
            [mypage-engine.websocket :as ws]))

(defn header
  [response name value]
  (assoc-in response [:headers name] (str value)))

(defn response
  ([] (response 200 {} {}))
  ([status] (response status {} {}))
  ([status headers] (response status headers {}))
  ([status headers body]
   {:status  status
    :headers headers
    :body    body}))

(def ok (response 200))
(def no-content (response 204))
(def not-found (response 404))

(defn set-default-headers-middleware
  [handler]
  (fn [request & args]
    (-> (handler request)
        (header "X-app" "mypage-engine-v1")
        (header "Content-Type" "text/json; charset=UTF-8")
        (header "Access-Control-Allow-Headers" "Accept,Content-Type, text/json"))))

(defn hello-world
  [{:keys [request]}]
  (assoc request :body "<h1>Hello world!</h1>"))

(defn four-o-four
  [_]
  (assoc not-found :body "<div align= \"center \"><h4>Ooops... 404</h4><h1>¯ \\_ (ツ) _ / ¯</h1></div>"))

(defn app-routes
  [{:keys [state-atom config-atom request] :as args}]

  (log/info request)

  (condp = (:uri request)

    "/api/v1/" (hello-world args)
    "/api/ws/" (ws/ws-handler args)

    ;; else
    (four-o-four args)

    ))

(def app
  (->> app-routes
       set-default-headers-middleware))


