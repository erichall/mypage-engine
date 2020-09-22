(ns mypage-engine.security
  (:require
    [buddy.sign.jwt :as jwt]
    [buddy.core.keys :as keys]
    [clojure.java.io :as io]
    [clj-time.core :as time]
    [mypage-engine.core :refer [get-config]]
    ))

(def config (get-config))
(def cred-path (:credentials config))

(println "CRED :: " cred-path)

(def has-credentials? (.exists (io/file cred-path)))

(when (not has-credentials?)
  (print "\n\n  NO PATH TO KEYS FOUND - ")
  (println cred-path)
  (println "\n\n")
  )

(def secrets
  {:auth-data   (if has-credentials?
                  (clojure.edn/read-string (slurp (str cred-path "/auth-data")))
                  "")
   :private-key (if has-credentials? (keys/private-key (str cred-path "/ecprivkey.pem")) "")
   :public-key  (if has-credentials? (keys/public-key (str cred-path "/ecpubkey.pem")) "")
   :jwt-options (if has-credentials? (clojure.edn/read-string (slurp (str cred-path "/jwt-options"))) "")})

(defn decrypt-token
  "throws if invalid token.."
  [token]
  (jwt/unsign token (:public-key secrets) (:jwt-options secrets)))

(defn valid-token?
  "Validate a jwt token"
  [token]
  (try
    (when (decrypt-token token)
      true)
    (catch Exception e
      (println (str "Invalid token - " e))
      false)))

(defn authenticate
  [{:keys [username password]}]
  (let [valid? (some-> (:auth-data secrets)
                       (get (keyword username))
                       (= password))]
    (if valid?
      (let [claims {:user (keyword username)
                    :exp  (time/plus (time/now) (time/seconds 3600))}
            token (jwt/sign claims (:private-key secrets) (:jwt-options secrets))]
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

(comment
  (println secrets)

  (println cred-path)
  )
