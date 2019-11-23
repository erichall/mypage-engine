(ns mypage-engine.graphql
  (:require
    [clojure.edn :as edn]
    [clojure.test :refer [is]]
    [clojure.core.async :refer [chan >!! <! pub sub put! go-loop go]]
    [clojure.data.json :refer [read-json write-str write-str]]
    [clojure.java.shell :refer [sh]]
    [org.httpkit.server :refer [send!]]

    [com.walmartlabs.lacinia :as l :refer [execute]]
    [com.walmartlabs.lacinia.util :refer [attach-resolvers attach-streamers]]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.lacinia.executor :as e]
    [com.walmartlabs.lacinia.parser :as parser]
    [com.walmartlabs.lacinia.constants :as constants]
    [com.walmartlabs.lacinia.resolve :as resolve]
    [com.walmartlabs.lacinia.executor :as executor])
  (:import (clojure.lang ExceptionInfo)))

(def db-atom (atom nil))

(def initial-state
  {:about-me-obj  {:name         "Eric"
                   :age          28
                   :location     "Sweden Stockholm"
                   :experience   "5 years"
                   :career_level "Junior"
                   :website_url  "www.ww.com"
                   :email        "hal@com.se"
                   :degree       "Msc. Computer Science"}

   :about-me-list [{:key "name" :value "Eric"}
                   {:key "age" :value "28"}
                   {:key "location" :value "Stockholm"}
                   {:key "career level" :value "5"}
                   {:key "website" :value "Junior"}
                   {:key "email" :value "www"}
                   {:key "degree" :value "eric@eric.se"}
                   {:key "add" :value "cool!"}]
   :logs          {:severity "default"
                   :message  "classy msg"}
   :posts         {:the-post-id-for-1 {:id    :the-post-id-for-1
                                       :title "This is an awesome post"
                                       :votes 1}}
   })

(defn is-gq-operation
  "Don't care about fragments atm ?!"
  [key]
  (or
    (= key :subscription)
    (= key :query)
    (= key :mutation)))

(defn get-query-keyword
  [query]
  (-> (keys query)
      first))

(defn parse-gql-query-string
  {:test (fn []
           (is (= (parse-gql-query-string {:mutation [:logs {:severity "FROM CLIENT ARGS"}
                                                      [:message :severity]]})
                  "mutation {logs (severity:\"FROM CLIENT ARGS\") {message severity}}"
                  )))}
  [query]
  (cond
    (keyword? query) (name query)

    (and (map? query) (-> (get-query-keyword query)
                          is-gq-operation))
    (let [key (get-query-keyword query)]
      (str (name key) " " (parse-gql-query-string (get query key))))

    (map? query) (str
                   \(
                   (clojure.string/join
                     \,
                     (map (fn [[k v]]
                            (let [v (if (keyword? v) (name v) v)]
                              (str (name k) ":\"" (str v) \")
                              ))
                          query))
                   \))

    (vector? query)
    (str \{
         (clojure.string/join
           \space
           (map parse-gql-query-string query))
         \}
         )

    :else (throw (str " Unable to parse query " query))))

(parse-gql-query-string {:mutation [:logs {:severity "FROM CLIENT ARGS"}
                                    [:message :severity]]})

(when (nil? (deref db-atom))
  (reset! db-atom initial-state))

(def output-chan (chan))
(def input-chan (chan))
(def our-pub (pub input-chan :msg-type))

;(>!! input-chan {:msg-type :greeting :text "Hi!!!!!!!!!!"})
(sub our-pub :greeting output-chan)

(defn get-about-me [a b c] (get-in (deref db-atom) [:about-me-obj]))
(defn get-about-me-factlist [a b c] (get-in (deref db-atom) [:about-me-list]))
(defn get-logs [_ _ _] (get-in (deref db-atom) [:logs]))

(defn get-post [context arguments value]
  (println "The arguments you gave: " arguments " together with a value: " value)
  (get-in (deref db-atom) [:posts :the-post-id-for-1])
  )

(defn stream-logs
  [context args source-stream]
  (let [trigger-event (:trigger-event context)
        channel-id (:channel-id context)]

    ;(clojure.pprint/pprint context)

    (trigger-event {:name :subscribe
                    :data {:subscription :stream-logs
                           :channel-id   channel-id}}))

  ;; TODO consider args from client here
  (source-stream {:name :subscription-initialized
                  :data {:message  " THIS IS WOOT BRO"
                         :severity "REALLY SERIUS"
                         }})
  (fn []
    ;; cleanup her
    nil))

(defn votes
  [context args source-stream]
  (let [trigger-event (:trigger-event context)
        channel-id (:channel-id context)]

    (trigger-event {:name :subscribe
                    :data {:subscription :votes
                           :channel-id   channel-id}}))

  (fn []
    ;; cleanup her
    nil))


(defn mutate-logs
  [context args c]
  (let [mutated-msg {:severity (:severity args)
                     :message  (:message args)}
        trigger-event (:trigger-event context)]

    (swap! db-atom (fn [state] (assoc state :logs mutated-msg)))

    (trigger-event {:name :broadcast-subscription
                    :data {:name :mutate-logs
                           :data mutated-msg}})

    mutated-msg
    ))


(defn vote-on-post
  [context args c]
  (let [vote (:vote args)
        post-id (:id args)
        trigger-event (:trigger-event context)]

    (condp = vote
      :UP (swap! db-atom (fn [state] (update-in state [:posts post-id :votes] inc)))
      :DOWN (swap! db-atom (fn [state] (update-in state [:posts post-id :votes] dec)))

      (println "Silent fail, unable to find post id"))

    (trigger-event {:name :broadcast-subscription
                    :data {:name :votes
                           :data (get-in (deref db-atom) [:posts post-id :votes])}})

    (get-in (deref db-atom) [:posts post-id :votes])))

;(def a-schema
;  (->
;    "./resources/graphql_schema.edn"
;    slurp
;    edn/read-string
;    (attach-streamers {
;                       :stream-logs stream-logs
;                       :votes       votes
;                       })
;    (attach-resolvers {:get-about-me          get-about-me
;                       :get-about-me-factlist get-about-me-factlist
;                       :mutate-logs           mutate-logs
;                       :get-logs              get-logs
;
;                       :get-post              get-post
;                       :vote-on-post          vote-on-post
;                       })
;    schema/compile))

;(defn execute-subscription!
;  [{:keys [channel id query vars trigger-event] :as d}]
;  (let [query (parse-gql-query-string query)
;        [prepared-query errors] (try
;                                  [(-> a-schema
;                                       (parser/parse-query query)
;                                       (parser/prepare-with-query-variables vars))]
;                                  (catch ExceptionInfo e
;                                    [nil e]))
;        source-stream-fn (fn [{:keys [data name]}]
;                           (send! channel (str {:name name
;                                                :data (-> (assoc data :id id)
;                                                          (assoc :query (:query d)))})))]
;
;    (println "Prepared query" query)
;    (if (some? errors)
;      (send! channel (str "errors dude " (ex-data errors)))
;      (let [cleanup-fn (executor/invoke-streamer {constants/parsed-query-key prepared-query
;                                                  :trigger-event             trigger-event
;                                                  :channel-id                id}
;                                                 source-stream-fn)]
;        (println "call cleanup fn return" (cleanup-fn))))))
;
;(defn execute-query
;  [{:keys [gq-query-string args]}]
;  (let [
;        query (parse-gql-query-string gq-query-string)
;        result (execute a-schema query args {:trigger-event (:trigger-event args)
;                                             :id            (:id args)})
;        ]
;    (println "QUERY" query)
;    result
;    ))


(comment
  (execute a-schema "query { aboutme { name age career_level}}" nil nil)
  (execute a-schema "query { aboutme_list { key value} }" nil nil)
  (execute a-schema "query { logs { message severity} }" nil nil)

  (execute a-schema "mutation { change_logs (severity:\"DUDE sev\", message: \"MUTATED\") {message}}" {:a 2} nil)

  (execute-query {:query "mutation { change_logs (severity:\"DUDE sev\", message: \"MUTATED\") {message}}"
                  :args  {:trigger-event (fn [a] (println "hmm" a))}})

  (deref db-atom)

  (execute a-schema "subscription { logs { message }}" nil nil)

  (->
    (assoc-in {} {:subscription [:a [:b [:c]]]} "response")
    (assoc-in {:subscription [:a [:b [:d [:e]]]]} "response1")
    (assoc-in {:query [:a [:b [:d [:e]]]]} {:respone "response"
                                            :calling true})
    (get-in {:query [:a [:b [:d [:e]]]]})
    )

  )



