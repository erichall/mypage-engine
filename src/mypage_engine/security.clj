(ns mypage-engine.security
  (:require
    [buddy.sign.jwt :as jwt]
    [buddy.core.keys :as keys]
    [clojure.java.io :as io]
    [taoensso.timbre :as log]
    [clj-time.core :as time]
    [mypage-engine.core :refer [read-edn]]))

(defonce secrets-atom (atom nil))

(defn decrypt-token
  "throws if invalid token.."
  [token]
  (jwt/unsign token (:public-key @secrets-atom) (:jwt-options @secrets-atom)))

(defn valid-token?
  "Validate a jwt token"
  [token]
  (try
    (when (decrypt-token token)
      true)
    (catch Exception e
      (log/error (str "Invalid token - " e))
      false)))

(defn authenticate
  [{:keys [username password]}]
  (log/info "Authentication - " username password)
  (let [valid? (some-> (:auth-data @secrets-atom)
                       (get (keyword username))
                       (= password))]
    (if valid?
      (let [claims {:user (keyword username)
                    :exp  (time/plus (time/now) (time/seconds (:token-time-sec @secrets-atom)))}
            token (jwt/sign claims (:private-key @secrets-atom) (:jwt-options @secrets-atom))]
        {:event-name :authenticate-success
         :data       {:token token}})
      {:event-name :authenticate-fail
       :data       nil})))

(defn is-authenticated?
  [{:keys [token page]}]
  (if (valid-token? token)
    {:event-name :is-authenticated
     :data       {:token token
                  :page  page}}
    {:event-name :not-authenticated
     :data       nil}))

(defn initialize-secrets
  [{:keys [auth-data private-key public-key jwt-options token-time-sec]}]
  (reset! secrets-atom {:auth-data      (read-edn auth-data)
                        :private-key    (keys/private-key private-key)
                        :public-key     (keys/public-key public-key)
                        :jwt-options    (read-edn jwt-options)
                        :token-time-sec token-time-sec}))
