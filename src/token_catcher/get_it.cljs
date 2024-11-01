(ns token-catcher.get-it
  (:require
   ["playwright$default" :as pw]
   [cljs-bean.core :refer [bean ->clj ->js]]
   [promesa.core :as p]))

(def browser-type pw/chromium)

(def browser (.launch browser-type #js {:headless false}))

(-> browser
    (.then
     #(.close %)
     ))

#_(p/let [browser (.launch browser-type #js {:headless false})]
  )


(-> (p/let [browser browser
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

(p/let [browser browser
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
