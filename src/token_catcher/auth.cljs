(ns token-catcher.auth
  (:require
   ["fs" :as fs]
   ["os" :as os]
   ["which$default" :as which]
   [clojure.edn :as edn]
   [clojure.string :as str]
   [goog.string :refer [format]]
   [promesa.core :as p]))

(def slack-passwords-file "resources/creds.gpg")
(def destination-gpg-file "~/.doom.d/.secrets.gpg")

(defn expand-tilde [path]
  (if (.startsWith path "~")
    (str (os/homedir) (subs path 1))
    path))

(defn gpg-read-command [file]
  (format
   "%s -q --for-your-eyes-only --no-tty -d %s"
   (which/sync "gpg2")
   file))

(defn gpg-encrypt-command [data & {:keys [recipient]}]
  (let [cmd (cond-> "echo '%s' | %s --encrypt --armor"
              recipient (str " --recipient %s"))]
    (format cmd data (which/sync "gpg2") recipient)))

(defn read-encrypted
  "Reads gpg encrypted file content, decrypts into a string."
  [file]
  (p/let [exec (.-exec (js/require "child_process"))
          content
          (p/create
           (fn [resolve reject]
             (exec
              (gpg-read-command file)
              (fn [err stdout stderr]
                (if (or err (seq stderr))
                  (do
                    (println err)
                    (reject (or err (seq stderr))))
                  (do
                    (println "decrypted " file)
                    (resolve stdout)))))))]
    content))

(defn encrypt&save
  "Saves `data` into a gpg encrypted `file`."
  [file data]
  (p/let [exec (.-exec (js/require "child_process"))]
    (p/create
     (fn [resolve reject]
       (exec
        (gpg-encrypt-command data)
        (fn [err stdout stderr]
          (if (or err (seq stderr))
            (do
              (println
               "Error while encrypting with command: "
               (gpg-encrypt-command data)
               err)
              (reject (or err (seq stderr))))
            (do
              (fs/writeFileSync (expand-tilde file) stdout "utf-8")
              (println "encrypted " file)
              (resolve file)))))))))

(defn read-passwords-file []
  (-> slack-passwords-file
      read-encrypted
      (p/then edn/read-string)))

(defn merge-netrc-data
  "Merges two netrc texts,
  replacing any hosts in `a` with corresponding data in `b`"
  [a b]
  (let [grp (fn [data]
              (->> data str/split-lines
                   (remove str/blank?)
                   (group-by #(second (re-find #"machine\s+(\S+)" %)))))]
    (->> (merge (grp a) (grp b))
         vals
         (map (partial str/join "\n"))
         (str/join "\n\n"))))

(defn save-token-data
  "Saves token data in netrc format into a gpg-encrypted file.

  Note that it updates any existing records with the same host data,
  e.g., if you have: 'machine slack:clojurians ...' in `token-data` -
  all the records in the file for the same host will be overwritten."
  [dest-file token-data]
  (let [destf (or dest-file destination-gpg-file)]
    (-> destf
        read-encrypted
        (p/then
         (fn [decrypted-content]
           (->>
            token-data
            (merge-netrc-data decrypted-content)
            (encrypt&save destf)))))))
