(ns mypage-engine.io-handler
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clj-time.coerce :as timec]
            [mypage-engine.core :as core]
            [clojure.core.specs.alpha :as s]
            [clojure.test :refer [is]]
            [clojure.edn :as edn]))

(defn not-empty?
  [x]
  (boolean (not-empty x)))

(defn throw-error
  "Throws an error"
  [msg]
  (throw (Exception. msg)))

(defn list-files-in-directory
  "Lists files in a directory"
  [root]
  (let [files (file-seq (io/file root))]
    (reduce (fn [acc f]
              (if (.isDirectory f)
                acc
                (conj acc (.getAbsolutePath f)))) [] files)))

(defn get-file-names-in-directory
  [root]
  (let [files (file-seq (io/file root))]
    (->> (filter (fn [f] (.isFile f)) files)
         (map (fn [f] (.getName f))))))

(defn exists?
  "Check if a file exists"
  [file]
  (.exists (io/file file)))

(defn dir-exists?
  [path]
  (.isDirectory (io/file path)))

(defn data->file!
  "Write file to disk"
  [path data]
  (if-not (exists? path)
    (spit path data)
    (throw-error (str "file already exists " path)))
  data)

(defn create-file!
  [path content]
  (with-open [wrtr (io/writer path)]
    (.write wrtr content)))

(defn edn-file?
  "File ending with .edn?"
  [file]
  {:pre  [(string? file)]
   :post [(boolean? %)]}
  (boolean (re-matches #".+\.edn$" file)))

(defn str-insert
  "Insert c in string s at index i."
  [s c i]
  {:pre  [(string? s) (string? c) (int? i)]
   :post [(string? %) (>= (count %) (count s))]}
  (str (subs s 0 i) c (subs s i)))

(defn occurrences
  "Count occurences of pattern-str in s"
  [s pattern-str]
  {:pre  [(string? s) (string? pattern-str)]
   :post [(int? %)]}
  (->> s
       (re-seq (re-pattern (str/replace pattern-str "." "\\.")))
       count))

(defn get-matching-edn-files
  "Find matching filenames in the dir-path, they can have names like
  'the-file.edn', 'the-file-1.edn' etc"
  [dir-path name]
  {:pre  [(string? dir-path) (string? name) (edn-file? name)]
   :post [(vector? %)]}
  (let [name-reg (->> (str/index-of name ".edn")
                      (str-insert name "(-\\d+)?")
                      re-pattern)]
    (->> (io/file dir-path)
         .list
         (filterv (partial re-matches name-reg)))))

(defn create-post!
  "Write post as .edn to disk with the title as name, 'post-title.edn'
  DO NOT tolerate duplicated titles..."
  [{:keys [post-root] :as config} {:keys [post]}]
  {:pre [(map? config) (map? post)
         (contains? post :title)
         (not-empty? (:title post))
         (not (exists? (str post-root (core/space->dash (:title post)) ".edn")))]}
  (data->file! (str post-root (core/space->dash (:title post)) ".edn") (clojure.pprint/write post :stream nil))
  post)


(defn string->date
  "Parse a string date in the format yyyy-MM-dd to a java date."
  [str-date]
  (.parse (java.text.SimpleDateFormat. "yyyy-MM-dd") str-date))

(defn date->unix
  [date]
  (timec/to-long date))

(defn get-oldest-post-in-state
  [state]
  (let [posts (:posts state)]
    (when (not-empty? posts)
      (apply min-key (fn [{:keys [date-created]}]
                       (-> (string->date date-created)
                           date->unix))))))

(defn read-posts-from-disk
  "Get posts from 'post-root'"
  [state config]
  (let [files-in-root (list-files-in-directory (:post-root config))
        n-posts-in-state (count (keys (:posts state)))]
    (if (and (> n-posts-in-state 0) (= n-posts-in-state (count files-in-root)))
      {}
      (reduce (fn [acc path]
                (let [post (edn/read-string (slurp path))]
                  (assoc acc (:id post) post))) {} files-in-root))))

(defn get-post
  "Get posts with a particular name from post-root.
  Handles names with duplicates, such as name, name-1, name-2 etc.
  And only deals with .edn files"
  [{:keys [post-root]} post-name]
  {:pre [(string? post-name)
         (dir-exists? post-root)
         (exists? (str post-root (core/space->dash post-name) ".edn"))]}
  (-> (str post-root post-name ".edn")
      slurp
      edn/read-string))