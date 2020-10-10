(ns mypage-engine.core
  (:require [clojure.test :refer [is]]
            [clojure.java.io :as io]
            [clojure.java.io :refer [resource]]
            [clojure.data.json :refer [read-json write-str]]
            [clojure.string :as str]
            [clojure.edn :as edn])
  (:import (java.time ZoneId Instant ZonedDateTime)
           (java.time.format DateTimeFormatter)))

(defn uuid [] (str (java.util.UUID/randomUUID)))

(defn print-exit
  [msg]
  (println msg)
  (System/exit 0))

(defn throw-error
  "Throws an error"
  [msg]
  (throw (Exception. msg)))

(defn now
  "Return the current time in the given time zone as string"
  {:test (fn []
           (is (= (now :zone "Europe/Stockholm") (now :zone "Europe/Stockholm"))))}
  [& {zone :zone :or {zone "Europe/Stockholm"}}]
  (let [zone-id (ZoneId/of zone)]
    (.format (ZonedDateTime/ofInstant (Instant/now) zone-id) (DateTimeFormatter/ofPattern "yyyy-MM-dd-HH:mm"))))

(defn list-files-in-directory
  "Lists files in a directory"
  [root]
  (map (fn [file] file) (.list (io/file root))))

(defn directory-exists?
  "Check if directory exists"
  {:test (fn []
           (is (= (directory-exists? "src")
                  true))
           (is (= (directory-exists? "srcc")
                  false)))}
  [root]
  (.isDirectory (io/file root)))

(meta #'directory-exists?)

(defn exists?
  "Check if a file exists"
  [file]
  (.exists (io/file file)))

(defn create-directory!
  "Create directory if it does not exists"
  [root]
  (if-not (directory-exists? root)
    (.mkdir (io/file root))
    (throw-error (str "Directory: " root " already exists!"))))

(defn data->file!
  "Write a post to disk"
  [root file-name data]
  (let [post-path (str root "/" file-name)]
    (if-not (exists? post-path)
      (spit (str post-path ".json") data)
      (throw-error (str "Autch! post already exists " post-path)))))

(defn replace-space-with-dash
  {:test (fn []
           (is (= (replace-space-with-dash "This is my post") "This-is-my-post"))
           (is (= (replace-space-with-dash nil) nil))
           (is (= (replace-space-with-dash "This is my post ") "This-is-my-post"))
           (is (= (replace-space-with-dash " This is my post ") "This-is-my-post"))
           (is (= (replace-space-with-dash "notstring") "notstring")))}
  [str]
  (when-not (nil? str)
    (str/replace (str/trim str) #" " "-")))

(defn timestamp-with-str
  "Creates a filename with a timestamp"
  {:test (fn [] (is (= (timestamp-with-str "This is my title") (str (now) "-This is my title"))))}
  [string-title]
  (str (now) "-" string-title))

(defn parse-query-string
  "Parse an http query string into a clojure map"
  [query-string]
  (->> (str/split query-string #"&")
       (map #(str/split % #"="))
       (map (fn [[k v]] [(keyword k) v]))
       (into {})))

(defn body->map
  "Casts the request body http raw string to a clojure map"
  [{:keys [body] :as request}]
  (if (nil? body)
    request
    (assoc request :body (read-json (slurp body)))))

(defn body->str
  "Convert a any type of the body to a string"
  [{:keys [body] :as request}]
  (if (or (nil? body) (not (string? body)))
    request
    (update-in request [:body] (fn [body] (str (write-str body))))))

(defn read-edn
  [file]
  (edn/read-string (slurp file)))
