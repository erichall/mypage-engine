(ns mypage-engine.api_v1
  (:require [clojure.data.json :refer [read-json write-str write-str]]

            [taoensso.timbre :as log]

            [org.httpkit.server :refer [send! with-channel on-close on-receive]]

            [mypage-engine.websocket :as ws]
            [mypage-engine.events :refer [handle-event!]]

            [mypage-engine.core :refer [
                                        allow-any
                                        body->map
                                        body->str
                                        create-post
                                        data->file!
                                        get-all-posts
                                        get-portfolio
                                        get-post-title-from-query-string
                                        parse-query-string
                                        replace-space-with-dash
                                        timestamp-with-str
                                        timestamp-with-str-and-uuid]]))


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
(def bad-request (response 400))
(def not-found (response 404))
(def unauthorized (response 401 {} {:message "unauthorized"}))

(defn set-default-headers-middleware
  [handler]
  (fn [request & args]
    (-> (handler request)
        (header "X-app" "mypage-engine-v1")
        (header "Content-Type" "text/json; charset=UTF-8")
        (header "Access-Control-Allow-Headers" "Accept,Content-Type, text/json"))))

(defn hello-world
  [request & args]
  (assoc request :body "<h1>Hello world!</h1>"))

(defn four-o-four
  [& _]
  (assoc not-found :body "<div align= \"center \"><h4>Ooops... 404</h4><h1>¯ \\_ (ツ) _ / ¯</h1></div>"))

(def default-handler
  {:handler four-o-four
   :auth-fn allow-any})

(defn uri-handler-get
  [request]

  (condp = (:uri request)
    "/api/v1/" {:handler hello-world
                :auth-fn allow-any}

    "/api/ws/" {:handler ws/ws-handler
                :auth-fn allow-any}

    default-handler
    ))

(defn uri-handler-post [request] nil)

(defn uri-handler
  [{:keys [request-method] :as request}]
  (condp = request-method
    :get (uri-handler-get request)
    :post (uri-handler-post request)
    default-handler
    ))

(defn app-routes
  [{:keys [state-atom request]}]

  (log/info request)

  (let [uri-handler (uri-handler request)
        authorized? ((:auth-fn uri-handler) request)
        handler (:handler uri-handler)]
    (if authorized?
      (do
        (log/info "Authroized request - " request " - state - " (deref state-atom))
        (handler request {:trigger-event handle-event!
                          :state-atom    state-atom}))
      unauthorized)))

(def app
  (as-> app-routes $
        (set-default-headers-middleware $)
        ))


