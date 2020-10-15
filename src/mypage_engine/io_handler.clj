(ns mypage-engine.io-handler
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.core.specs.alpha :as s]
            [clojure.test :refer [is]]))

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
  (map (fn [file] file) (.list (io/file root))))

(defn exists?
  "Check if a file exists"
  [file]
  (.exists (io/file file)))

(defn data->file!
  "Write file to disk"
  [path data]
  (if-not (exists? path)
    (spit path data)
    (throw-error (str "file already exists " path))))

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
  "Write post as .edn to disk with the title as name, 'post-title.edn', if the post exists already,
  a number is inserted, 'post-title-2.edn', returns the created post if successful"
  [{:keys [post-root] :as config} {:keys [post]}]
  {:pre [(map? config) (map? post) (contains? post :title) (not-empty? (:title post))]}
  (let [title (:title post)
        file-name (str title ".edn")
        matching-files (get-matching-edn-files post-root file-name)]
    (->
      (data->file! (str post-root
                        (if (empty? matching-files)
                          file-name
                          (str title "-" (count matching-files) ".edn")))
                   (clojure.pprint/write post :stream nil)))
    post))
