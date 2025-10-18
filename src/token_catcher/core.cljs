(ns token-catcher.core
  (:require
   ["playwright$default" :as pw]
   ["fs" :as fs]
   [cljs-bean.core :refer [bean ->clj ->js]]
   [clojure.edn :as edn]
   [promesa.core :as p]))

(def browser-type pw/chromium)
(def browser-obj (atom nil))

(defn read-config []
  (-> (fs/readFileSync "resources/config.edn" "utf-8")
      edn/read-string))

(def ^:private config (read-config))

(defn get-browser []
  (p/create
   (fn [resolve _reject]
     (if (some-> @browser-obj (.isConnected))
       (resolve @browser-obj)
       (-> (.launch
            browser-type
            (-> config :browser ->js))
           (p/then
            (fn [b]
              (reset! browser-obj b)
              (resolve b))))))))

(defn get-page
  "Get nth page - for provided `n`, otherwise the last  one"
  ([]
   (get-page (get-browser) nil))
  ([browser]
   (get-page browser nil))
  ([browser n]
   (p/let [browser browser
           ctx (if (empty? (.contexts browser))
                 (.newContext browser)
                 (last (.contexts browser)))
           page (if (empty? (.pages ctx))
                  (.newPage ctx)
                  (if (nil? n)
                    (last (.pages ctx))
                    (nth (.pages ctx) n)))]
     page)))
