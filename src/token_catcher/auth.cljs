(ns token-catcher.auth
  (:require
   [clojure.edn :as edn]
   [promesa.core :as p]
   ["which$default" :as which]))

(def gpg-command
  (let [gpg2-path (which/sync "gpg2")]
    (str
     gpg2-path " -q --for-your-eyes-only --no-tty -d "
     "resources/creds.gpg")))

(defn creds
  "Reads credentials file content, decrypts and parses it into a map."
  []
  (p/let [exec (.-exec (js/require "child_process"))
          content
          (p/create
           (fn [resolve]
             (exec
              gpg-command
              (fn [err stdout stderr]
                (if (or err (seq stderr))
                  (do
                    (println err)
                    (resolve (or err (seq stderr))))
                  (do
                    (println ".. done")
                    (resolve stdout)))))))]
    (edn/read-string content)))
