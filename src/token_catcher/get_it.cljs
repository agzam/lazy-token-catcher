(ns token-catcher.get-it
  (:require
   ["playwright$default" :as pw]
   [cljs-bean.core :refer [bean ->clj ->js]]
   [clojure.string :as str]
   [goog.string :refer [format]]
   [promesa.core :as p]
   [token-catcher.auth :as auth]))

(def browser-type pw/chromium)

(defn do-login [page email pass]
  (p/create
   (fn [resolve reject]
     (prn "Login page...")
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
              (prn "Sign-in button clicked.")
              (-> page (.waitForNavigation)
                  (p/then #(.waitForLoadState page "domcontentloaded"))
                  (p/then #(.waitForSelector page "div#list_emoji_section" #js {:state "visible"}))
                  (p/then (fn []
                            (prn "Customization page loaded")
                            (resolve))))))
           (p/catch #(prn "Error in do-login" %)))))))

(defn gather-token&cookie [page]
  (p/let [token (.evaluate page "TS.boot_data.api_token")
          cookies (-> page (.context) (.cookies))]
    {:token token
     :cookies (->> cookies
                   ->clj
                   (filter #(->> % :name (contains? #{"d" "d-s" "lc"})))
                   (map #(select-keys % [:name :value]))
                   (map (juxt (comp keyword :name) :value))
                   (into {}))}))

(defn token&cookie->netrc [org tc-map]
  (let [{:keys [token]
         {:keys [d lc d-s]} :cookies} tc-map]
    (->> [(str "machine " org " login token password " token)
          (str "machine " org " login cookie password "
               d ";d-s=" d-s ";lc=" lc)]
        (str/join "\n"))))

(defn -main []
  (p/let [browser (.launch browser-type #js {:headless true})
          orgs (auth/read-passwords-file)]
    (let [res (for [[org [email pass]] orgs]
                (p/let [ctx (.newContext browser)
                        page (.newPage ctx)
                        _ (.goto page (format "https://%s/customize" org))
                        token&cookie (-> (do-login page email pass)
                                         (p/then #(gather-token&cookie page))
                                         (p/catch #(prn "Error in login/gather:" %)))
                        _ (->> token&cookie
                               (token&cookie->netrc org)
                               (auth/save-token-data nil))]
                  (prn "all operations done for:" org)))]
      (-> res
          p/all
          (p/then #(prn "All done!"))
          (p/finally #(.close browser))))))

