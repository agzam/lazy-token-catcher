(ns token-catcher.get-it
  (:require
   ["playwright$default" :as pw]
   [cljs-bean.core :refer [bean ->clj ->js]]
   [goog.string :refer [format]]
   [promesa.core :as p]
   [token-catcher.auth :refer [creds]]))


(def browser-type pw/chromium)

(def browser (.launch browser-type #js {:headless false}))

(-> browser
    (.then
     #(.close %)
     ))

#_(p/let [browser (.launch browser-type #js {:headless false})]
  )


#_(-> (p/let [browser browser
            context (.newContext browser)
            page (.newPage context)
            _ (.goto page "https://clojurians.slack.com/customize")
            _ (-> page
                  (.locator "a[data-qa=sign_in_password_link]")
                  (.click))
            _ (-> page (.locator "input#email")
                  (.fill "agzam.ibragimov@gmail.com"))
            - (-> page (.locator "input#password")
                  (.fill "V73MZ8eO069p"))
            _ (-> page (.locator "button#signin_btn")
                        (.click))]))

#_(p/let [browser browser
        context (.newContext browser)
        page (.newPage context)
        _ (.goto page "https://qlikdev.slack.com/customize")
        _ (p/do
            (-> page
                (.locator "a#index_saml_sign_in_with_saml")
                (.click))
            (.waitForSelector
             page
             "div#list_emoji_section"
             #js {:waitUntil "domcontentloaded"}))
        token (.evaluate page "TS.boot_data.api_token")
        cookies (-> page (.context) (.cookies))]
  (cljs.pprint/pprint
   (->clj cookies))
  (prn token))

(defn do-login [page email pass]
  (p/create
   (fn [resolve reject]
     (let [logp (if (= :saml email)
                  (-> page
                      (.locator "a#index_saml_sign_in_with_saml")
                      (.click))
                  (-> page
                      (.locator "a[data-qa=sign_in_password_link]")
                      (.click)
                      (p/then #(-> page (.locator "input#email") (.fill email)))
                      (p/then #(-> page (.locator "input#password") (.fill pass)))
                      (p/then #(-> page (.locator "button#signin_btn") (.click)))))]
       (-> logp
           (p/then
            (fn []
              (p/-> (.waitForLoadState page "load")
                    #(.waitForSelector page "div#list_emoji_section" #js {:state "attached"})
                    resolve)))
           (p/catch reject))))))

(defn gather-token&cookie [page]
  (p/create
   (fn [resolve reject]
     (p/let [token (.evaluate page "TS.boot_data.api_token")
             cookies (-> page (.context) (.cookies))]
       (-> cookies
           (p/then (fn [cs]
                     (resolve {:token token
                               :cookies (->> cs ->clj
                                             (filter #(->> % :name (contains? #{"d" "d-s" "lc"})))
                                             (map #(select-keys % [:name :value]))
                                             (map (juxt :name :value))
                                             (into {}))})))
           (p/catch reject))))))

(p/let [browser (.launch browser-type #js {:headless false})
        orgs (creds)]
  (->
   (p/all
    (for [[org [email pass]] (take 1 orgs) ; TODO: remove take 1 later
          ]
      (p/create
       (fn [resolve reject]
         (p/let [ctx (.newContext browser)
                 page (.newPage ctx)
                 _ (.goto page (format "https://%s/customize" org))
                  token&cookie (-> (do-login page email pass)
                                   (p/then #(gather-token&cookie page))
                                   (p/catch prn))]
           (-> token&cookie
               (p/then
                (fn [tc]
                  (cljs.pprint/pprint tc)
                  (resolve)))
               (p/catch reject)))))))
   (p/then #(.close browser))
   (p/catch prn)))



#_(cljs.pprint/pprint
              (->> cookies ->clj (some #(-> % :name (= "d"))) first :value))
