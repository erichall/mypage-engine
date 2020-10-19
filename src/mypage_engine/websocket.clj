(ns mypage-engine.websocket
  (:require [clojure.data.json :refer [read-json write-str write-str]]
            [org.httpkit.server :refer [close send! with-channel on-close on-receive]]
            [clojure.set :refer [map-invert]]
            [taoensso.timbre :as log]
            [mypage-engine.core :refer [uuid]]
            [clojure.edn :as edn]
            [clojure.string :refer [split]]
            [mypage-engine.security :refer [authenticate is-authenticated?]]
            [mypage-engine.io-handler :as ioh]
            [clojure.spec.alpha :as s]))

(defonce sockets-atom (atom {}))

(s/def ::title string?)
(s/def ::content string?)
(s/def ::date-created string?)
(s/def ::author string?)
(s/def ::votes int?)
(s/def ::comments string?)
(s/def ::published? boolean?)
(s/def ::id string?)
(s/def ::post (s/keys :req-un [::title ::content ::date-created ::author ::votes ::comments ::published? ::id]))

(s/explain-data ::post {:title        "heej"
                        :content      "poo"
                        :date-created "2020-01-01"
                        :author       "eric"
                        :votes        2
                        :comments     "ba"
                        :published?   false
                        :id           (uuid)})

(defn post-template
  [extras]
  (merge {
          :title        ""
          :date-created (mypage-engine.core/now)
          :author       ""
          :points       0
          :comments     ""
          :published?   false
          :id           (uuid)
          :content      ""
          } extras))

(defn create-socket
  [{:keys [channel]}]
  {:channel channel
   :id      (uuid)})

(defn get-channel
  [{:keys [id]}]
  (get-in (deref sockets-atom) [id :channel]))

(defn broadcast!
  [{:keys [data event-name]}]
  (doall (map (fn [[id {:keys [channel]}]]
                (send! channel (str {:event-name (or event-name :re-hydrate)
                                     :data       data})))
              (deref sockets-atom))))

(defn connect!
  [channel state]
  (let [socket (create-socket {:channel channel})
        id (:id socket)]

    (swap! sockets-atom assoc id socket)

    (send! channel (str {:event-name :connected
                         :data       {:id    id
                                      :state state}}))

    (broadcast! {:data       {:visitors (-> (deref sockets-atom) keys count)}
                 :event-name :page-count})))

(defn disconnect!
  [channel status]
  (log/info "disconnect! " status)
  (let [id-to-be-removed (->> (map-invert (deref sockets-atom))
                              (filterv (fn [[socket]] (= channel (:channel socket))))
                              flatten
                              first
                              :id)]
    (log/info " removing id:: " id-to-be-removed)
    (send! channel (str {:event-name :connection-closed}))
    (swap! sockets-atom dissoc id-to-be-removed)
    (broadcast! {:data       {:visitors (-> (deref sockets-atom) keys count)}
                 :event-name :page-count})))

(defn get-id-from-request
  [request]
  (-> (get request :query-string)
      (split #"=")
      second))

(defn handler
  [state-atom config-atom channel args]
  (let [{:keys [event-name data]} (edn/read-string args)]

    (condp = event-name

      :page-selected (send! channel (str (is-authenticated? data)))

      :post-template (send! channel (str {:event-name :post-template
                                          :data       {:template (post-template {})}}))

      :create-post (let [error-or-post? (try (ioh/create-post! (deref config-atom) data)
                                             (catch AssertionError e
                                               (.getMessage e)))
                         response (if (string? error-or-post?)
                                    {:status :error
                                     :error  error-or-post?}
                                    {:status :success
                                     :post   error-or-post?})]
                     (send! channel (str {:event-name :post-created
                                          :data       response})))

      :login (send! channel (str (authenticate data)))

      :vote-down (ioh/vote! :down @config-atom (:id data))
      :vote-up (ioh/vote! :up @config-atom (:id data))

      :pong (log/info "Pong!")

      :front-page-facts (send! channel (str {:event-name :front-page-facts
                                             :data       {:path [:pages :front-page]
                                                          :fact {:intro "Hi! I'm Eric."
                                                                 :about "I work as a software engineer at TietoEvry. I received my Masters in Computer Science from the Royal Institute of Technology in Sweden. Apart from coding and tinkering with electronics I'm interested in baking, brewing and cooking."}}}))
      :resume-facts (send! channel (str {:event-name :resume-page-facts
                                         :data       {:path [:pages :resume]
                                                      :fact {}}}))

      :posts-facts (send! channel (str {:event-name :posts-page-facts
                                        :data       {:path [:pages :posts]
                                                     :fact {:title "Posts"
                                                            :intro "Some writing."
                                                            :posts (ioh/read-posts-from-disk @state-atom @config-atom)}}}))

      :post-facts (let [post-or-error (try (ioh/get-post-by :name @config-atom (:slug data))
                                           (catch AssertionError _ (str "No post named " (:slug data) "...")))]
                    (send! channel (str {:event-name :post-page-facts
                                         :data       {:path [:pages :post]
                                                      :fact {:title "Post"
                                                             :intro "This is a post"
                                                             :post  (when post-or-error post-or-error)
                                                             :error (when (string? post-or-error) post-or-error)}}})))

      :portfolio-facts (send! channel (str {:event-name :portfolio-page-facts
                                            :data       {:path [:pages :portfolio]
                                                         :fact {}}}))

      :login-facts (send! channel (str {:event-name :login-page-facts
                                        :data       {:path [:pages :login]
                                                     :fact {}}}))

      ; else
      (log/info (str "No matching clause " event-name " type: " (type event-name)))
      ))
  )

;; https://gist.github.com/viperscape/8529476 handle dead clients
;; or handle through nginx?
(defn ws-handler
  [{:keys [state-atom request config-atom]}]
  (with-channel request channel
                (connect! channel (deref state-atom))
                (on-close channel (fn [status]
                                    (disconnect! channel status)))
                (on-receive channel (fn [data]
                                      (handler state-atom config-atom channel data)))))

(defn initialize-ping-clients
  [{:keys [delay] :or {delay (* 1000 10)}}]
  (future (loop []
            (Thread/sleep delay)
            (broadcast! {:data       {:event-name :ping}
                         :event-name :ping})
            (recur))))

(comment

  (-> (deref sockets-atom)
      vals
      first
      :channel
      close)
  (reset! sockets-atom {})
  )
