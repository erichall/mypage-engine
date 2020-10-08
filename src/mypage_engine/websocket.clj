(ns mypage-engine.websocket
  (:require [clojure.data.json :refer [read-json write-str write-str]]
            [org.httpkit.server :refer [close send! with-channel on-close on-receive]]
            [clojure.set :refer [map-invert]]
            [taoensso.timbre :as log]
            [mypage-engine.core :refer [uuid]]
            [mypage-engine.security :refer [authenticate is-authenticated?]]
            ))

(defonce sockets-atom (atom {}))

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
      (clojure.string/split #"=")
      second))

(defn handler
  [state-atom channel args]
  (let [{:keys [event-name data]} (clojure.edn/read-string args)]

    (condp = event-name

      :page-selected (send! channel (str (is-authenticated? data)))

      :create-post (log/info (str "We should create a new post..." data))

      :login (send! channel (str (authenticate data)))

      :vote-down (swap! state-atom assoc-in [:posts (keyword (:id data)) :points] (dec (get-in (deref state-atom) [:posts (keyword (:id data)) :points])))
      :vote-up (swap! state-atom assoc-in [:posts (keyword (:id data)) :points] (inc (get-in (deref state-atom) [:posts (keyword (:id data)) :points])))

      :pong (log/info "Pong!")

      ; else
      (log/error "No matching clause " event-name)
      ))
  )

;; https://gist.github.com/viperscape/8529476 handle dead clients
;; or handle through nginx?
(defn ws-handler
  [{:keys [state-atom request]}]
  (with-channel request channel
                (connect! channel (deref state-atom))
                (on-close channel (fn [status]
                                    (disconnect! channel status)))
                (on-receive channel (fn [data]
                                      (handler state-atom channel data)))))

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
