(ns mypage-engine.api_v1
  (:require [clojure.data.json :refer [read-json write-str write-str]]

            [org.httpkit.server :refer [send! with-channel on-close on-receive]]

            [mypage-engine.websocket :as ws]
            [mypage-engine.events :refer [handle-event!]]

            [mypage-engine.core :refer [
                                        allow-any
                                        body->map
                                        body->str
                                        config
                                        create-post
                                        data->file!
                                        find-post-by-id
                                        find-post-by-title
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

(defn get-posts-handler [_ & args] (assoc-in ok [:body :posts] (get-all-posts (:posts-root config))))
(defn get-portfolio-handler [_ & args] (assoc-in ok [:body :portfolio] (get-portfolio (:portfolio-root config))))

(defn get-post-by-title
  [request & args]
  (let [post-title (get-post-title-from-query-string request)]
    (assoc-in ok [:body :post] (find-post-by-title post-title))))

(defn get-post-by-id
  [request & args]
  (let [post (find-post-by-id (-> (:query-string request)
                                  parse-query-string
                                  (get :post-id)))]
    (if (nil? post)
      (assoc-in no-content [:body :message] "Unable to find post")
      (assoc-in ok [:body :post] post))))

(defn create-post-from-request
  [request & args]
  (let [body (:body request)]
    (if-not (true? request)                                 ;; REFACTORED TODO
      unauthorized
      (let [new-post (create-post {:text      (:markdown body)
                                   :title     (:title body)
                                   :caption   (:caption body)
                                   :category  (:category body)
                                   :thumbnail (:thumbnail body)
                                   :author    (:author body)})
            post-file (timestamp-with-str-and-uuid (:title body))]
        (do
          (data->file! (:posts-root config) post-file new-post)
          (->
            (assoc-in ok [:body :post] new-post)
            (assoc-in [:body :post-file] post-file)))))))

(defn hello-world
  [request & args]
  (assoc request :body "<h1>Hello world!</h1>"))

(defn four-o-four
  [& _]
  (assoc not-found :body "<div align= \"center \"><h4>Ooops... 404</h4><h1>¯ \\_ (ツ) _ / ¯</h1></div>"))

(def default-handler
  {:handler four-o-four
   :auth-fn allow-any})

(defn graphql-test
  [request & args]
  {:status  200
   :headers {"Content-Type" "application/json"}
   :body    (let [body (clojure.edn/read-string (slurp (get request :body)))
                  trigger-event (:trigger-event (first args))
                  result (trigger-event {:name :execute-query
                                         :data {:gq-query-string (:query body)
                                                :args            {:id            (:id body)
                                                                  :trigger-event trigger-event}}})]

              (write-str result)                            ;; json for this, since this is an ordered/map, unable to str this..?!
              )})

(defn uri-handler-get
  [request]
  (println "Request:: " request)
  (condp = (:uri request)
    "/api/v1/" {:handler hello-world
                :auth-fn allow-any}

    "/api/ws/" {:handler ws/ws-handler
                :auth-fn allow-any}

    default-handler
    ))

(defn uri-handler-post [request])

(defn uri-handler
  [{:keys [request-method] :as request}]
  (condp = request-method
    :get (uri-handler-get request)
    :post (uri-handler-post request)
    default-handler
    ))

(defn app-routes
  [{:keys [state-atom request]}]
  (let [uri-handler (uri-handler request)
        authorized? ((:auth-fn uri-handler) request)
        handler (:handler uri-handler)]
    (if authorized?
      (handler request {:trigger-event handle-event!
                        :state-atom    state-atom})
      unauthorized)))

(def app
  (as-> app-routes $
        (set-default-headers-middleware $)
        ))


