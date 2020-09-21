(ns mypage-engine.core
  (:require [ysera.test :refer [is= is error?]]
            [clojure.java.io :as io]
            [clojure.data.json :refer [read-json write-str]]
            [clojure.string :as str])
  (:import (java.time ZoneId Instant ZonedDateTime)
           (java.time.format DateTimeFormatter)))

(defn uuid [] (str (java.util.UUID/randomUUID)))

(defn portfolio-item
  [{:keys [title text image link created tags]}]
  {:title   title
   :text    text
   :image   image
   :link    link
   :created created
   :tags    tags})

(defn post
  [{:keys [text id title caption thumbnail category author created last-edit]}]
  {:text      text
   :id        id
   :title     title
   :caption   caption
   :thumbnail thumbnail
   :category  category
   :author    author
   :created   created
   :last-edit last-edit})

(defn throw-error
  "Throws an error"
  [msg]
  (throw (Exception. msg)))

(defn now
  "Return the current time in the given time zone as string"
  {:test (fn []
           (is= (now :zone "Europe/Stockholm")
                (now :zone "Europe/Stockholm")))}
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
           (is= (directory-exists? "src")
                true)
           (is= (directory-exists? "srcc")
                false))}
  [root]
  (.isDirectory (io/file root)))

(defn file-exists?
  "Check if a file exists"
  {:test (fn []
           (is= (file-exists? "src/core.clj")
                true))}
  [file]
  (.exists (io/file file)))

(defn create-directory!
  "Create directory if it does not exists"
  {:test (fn []
           (error? (create-directory! "src")))}
  [root]
  (if-not (directory-exists? root)
    (.mkdir (io/file root))
    (throw-error (str "Directory: " root " already exists!"))))

(defn data->file!
  "Write a post to disk"
  {:test (fn []
           (error? (data->file! "src" "core.clj" "This should not work!")))}
  [root file-name data]
  (let [post-path (str root "/" file-name)]
    (if-not (file-exists? post-path)
      (spit (str post-path ".json") data)
      (throw-error (str "Autch! post already exists " post-path)))))

(defn replace-space-with-dash
  {:test (fn []
           (is= (replace-space-with-dash "This is my post") "This-is-my-post")
           (is= (replace-space-with-dash nil) nil)
           (is= (replace-space-with-dash "This is my post ") "This-is-my-post")
           (is= (replace-space-with-dash " This is my post ") "This-is-my-post")
           (is= (replace-space-with-dash "notstring") "notstring"))}
  [str]
  (if (= str nil)
    nil
    (-> (str/trim str)
        (str/replace #" " "-"))))

(defn timestamp-with-str
  "Creates a filename with a timestamp"
  {:test (fn [] (is= (timestamp-with-str "This is my title") (str (now) "-This is my title")))}
  [string-title]
  (str (now) "-" string-title))

(defn timestamp-with-str-and-uuid
  "Joins the string-title with '-' and creates a string of timeNow-joinedTitle-uuid"
  [string-title]
  (str (now) "-" (replace-space-with-dash string-title) "-" (uuid)))

(defn create-portfolio-item
  "Creates a portfolio item with the defined JSON format."
  {:test (fn []
           (is= (create-portfolio-item {:title "title"
                                        :text  "text"
                                        :image "image"
                                        :link  "link"
                                        :tags  ["tag1" "tag2"]})
                {:title   "title"
                 :text    "text"
                 :image   "image"
                 :link    "link"
                 :created (now)
                 :tags    ["tag1" "tag2"]}))}
  [{:keys [title text image link created tags]}]
  (portfolio-item {:title   title
                   :text    text
                   :image   image
                   :link    link
                   :created (or created (now))
                   :tags    tags}))

(defn create-post
  "Creates a post with the defined JSON format."
  {:test (fn []
           (let [blog-post (post {:text      "Mumbo jumbo"
                                  :title     "My title!"
                                  :caption   "This is caption"
                                  :thumbnail "www.eee.com"
                                  :category  "cheese"
                                  :author    "Jumbo"
                                  :created   "2019-01-02"
                                  :id        "id"
                                  :last-edit "2019-02-03"})]
             (is= (create-post {:text      "Mumbo jumbo"
                                :title     "My title!"
                                :id        "id"
                                :author    "Jumbo"
                                :created   "2019-01-02"
                                :last-edit "2019-02-03"
                                :caption   "This is caption"
                                :thumbnail "www.eee.com"
                                :category  "cheese"})
                  blog-post)))}
  [{:keys [text title caption thumbnail category author created last-edit id]}]
  (post {:text      text
         :id        (or id (uuid))
         :title     title
         :caption   caption
         :thumbnail thumbnail
         :category  category
         :author    author
         :created   (or created (now))
         :last-edit (or last-edit (now))}))

(defn read-files
  "Read files from disk"
  {:test (fn []
           (is= (->> (read-files (:posts-root-mocks config) (list-files-in-directory (:posts-root-mocks config)))
                     (map :text))
                '("Mumbo jumbo" "Mumbo jumbo" "Mumbo jumbo")))}
  [root file-names]
  (map (fn [file-name] (clojure.edn/read-string (slurp (str root file-name)))) file-names))


(defn get-posts-by-title
  "Find all posts with the given title."
  {:test (fn []
           (is= (map :text (get-posts-by-title "This is the title" (:posts-root-mocks config)))
                '("Mumbo jumbo" "Mumbo jumbo"))
           (is= (map :text (get-posts-by-title "another Title" (:posts-root-mocks config)))
                '("Mumbo jumbo"))
           (is= (map :text (get-posts-by-title "Can't find me" (:posts-root-mocks config)))
                '()))}
  [title root]
  (as-> (filter (fn [file] (.contains file title)) (list-files-in-directory root)) $
        (read-files root $)))

(defn get-all-posts
  "Get all posts from a given root"
  {:test (fn []
           (is= (map :text (get-all-posts (:posts-root-mocks config)))
                '("Mumbo jumbo" "Mumbo jumbo" "Mumbo jumbo")))}
  [root]
  (read-files root (list-files-in-directory root)))

(defn get-portfolio
  "Get all items in your portfolio"
  [root]
  (read-files root (list-files-in-directory root)))

(defn parse-query-string
  "Parse an http query string into a clojure map"
  [query-string]
  (->> (str/split query-string #"&")
       (map #(str/split % #"="))
       (map (fn [[k v]] [(keyword k) v]))
       (into {})))

(defn find-post-by-title
  "Find a post by title, takes the first find if multiple posts with same title"
  {:test (fn []
           (is= (find-post-by-title "a" [{:title "a"} {:title "b"} {:title "c"}]) {:title "a"})
           (is= (find-post-by-title "d" [{:title "a"} {:title "b"} {:title "c"}]) nil)
           (is= (find-post-by-title "a" [{:title "a"} {:title "a"} {:title "a"}]) {:title "a"}))}
  ([post-title] (find-post-by-title post-title (get-all-posts (:posts-root config))))
  ([post-title posts]
   (-> (filter (fn [{:keys [title]}] (= (replace-space-with-dash title) post-title)) posts)
       first)))

(defn find-post-by-id
  "Finds a post from id, takes the first one if multiple posts with same id"
  {:test (fn []
           (is= (find-post-by-id 123 [{:id 9} {:id 123} {:id 1} {:id 3}]) {:id 123})
           (is= (find-post-by-id 123 [{:id 123} {:id 123} {:id 1} {:id 3}]) {:id 123})
           (is= (find-post-by-id 123 []) nil)
           (is= (find-post-by-id 2 [{:id 123}]) nil))}
  ([post-id]
   (find-post-by-id post-id (get-all-posts (:posts-root config))))
  ([post-id posts]
   (-> (filter (fn [{:keys [id]}] (= id post-id)) posts)
       first)))

(defn body->map
  "Casts the request body http raw string to a clojure map"
  [{:keys [body] :as request}]
  (if (nil? body)
    request
    (assoc request :body (read-json (slurp body)))))

(defn body->str
  "Convert a any type of the body to a string"
  [{:keys [body] :as request}]
  (if (or (nil? body) (= (not (string? body))))
    request
    (update-in request [:body] (fn [body] (str (write-str body))))))

(defn get-post-title-from-query-string
  "Get the ?post-title=title from a http query string"
  [request]
  (-> (parse-query-string (:query-string request))
      (get :post-title)))

(defn allow-any
  [& _]
  true)

(def config (delay (load-file (.getFile (io/resource "config.clj")))))
(defn get-config
  []
  @(force config))

(comment
  (create-directory! (:posts-root config))
  (create-directory! (:posts-root-mocks config))

  (create-directory! (:portfolio-root config))

  (let [title "Yet another postsis"]
    (data->file! (:posts-root config)
                 (timestamp-with-str-and-uuid title)
                 (create-post {:text      "# Tjenare"
                               :title     title
                               :created   "2018-05-02"
                               :author    "Cool"
                               :caption   "This is caption"
                               :thumbnail "www.eee.com"
                               :category  "cheese"})))

  (let [title "We can make berry jam"]
    (data->file! (:portfolio-root config)
                 (timestamp-with-str title)
                 (create-portfolio-item {:title title
                                         :text  "Now the jam is in the jar with the jam lid on."
                                         :image "portfolio-assets/berrys.jpg"
                                         :link  "www.myprojecthadcom"
                                         :tags  ["Rasp"]})))

  (mapv str (filter #(.isFile %) (file-seq (clojure.java.io/file "./resources/portfolio/")))))

