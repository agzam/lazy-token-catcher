(ns token-catcher.get-it
  (:require
   ["playwright$default" :as pw]
   [cljs-bean.core :refer [bean ->clj ->js]]
   [clojure.string :as str]
   [goog.string :refer [format]]
   [promesa.core :as p]
   [token-catcher.auth :as auth]
   [token-catcher.core :as c
    :refer [get-browser get-page]]))

(defn do-login [page
                {:keys [email pass saml?]}]
  (p/create
   (fn [resolve _reject]
     (prn (format "Login page... %s." (if saml? "with SAML. Watch your mobile device for verify push!" "")))
     (let [logp (if saml?
                  (-> page
                      (.locator "a#index_saml_sign_in_with_saml")
                      (.click)
                      (p/then #(.waitForSelector page "input[name=identifier]" #js {:state "visible"}))
                      (p/then #(-> page (.locator "input[name=identifier]") (.fill email)))
                      (p/then #(-> page (.locator "input[name=rememberMe]") (.setChecked true #js {:force true})))
                      (p/then #(-> page (.locator "input[type=submit]") (.click)))
                      (p/then #(.waitForSelector page "input[type=password]" #js {:state "visible"}))
                      (p/then #(-> page (.locator "input[type=password]") (.fill pass)))
                      (p/then #(-> page (.locator "input[type=submit]") (.click)))
                      (p/then #(-> page (.getByLabel "Select to get a push notification to the Okta Verify app.") (.click))))
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
              (-> page
                  ;; (.waitForNavigation)
                  (p/then #(.waitForLoadState page "domcontentloaded"))
                  (p/then #(.waitForSelector page "div#list_emoji_section" #js {:state "visible"}))
                  (p/then #(.waitForFunction page "window.TS?.boot_data?.api_token" #js {:timeout 10000}))
                  (p/then (fn []
                            (prn "Customization page loaded")
                            (resolve))))))
           (p/catch #(prn "Error in do-login" %)))))))

(defn gather-token&cookie [page]
  (prn "Gathering token and cookie data...")
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
  (prn "Converting token&cookie to netrc format...")
  (let [{:keys [token]
         {:keys [d lc d-s]} :cookies} tc-map]
    (->> [(str "machine " org " login token password " token)
          (str "machine " org " login cookie password "
               d ";d-s=" d-s ";lc=" lc)]
        (str/join "\n"))))

(defn process-org [page org creds]
  (p/create
   (fn [resolve reject]
     (prn "Processing " org)
     (p/let [_ (prn "")
             _ (.goto page (format "https://%s/customize" org))
             token&cookie (-> (do-login page creds)
                              (p/then #(gather-token&cookie page))
                              (p/catch
                                  (fn [e]
                                    (prn "Error in login/gather:" e)
                                    (reject e))))
             _ (->> token&cookie
                    (token&cookie->netrc org)
                    (auth/save-token-data nil))]
       (prn "all operations done for:" org)
       (.close page)
       (resolve org)))))

(defn -main []
  (p/let [browser (get-browser)
          orgs (auth/read-passwords-file)
          results 
          (->> orgs
               (map-indexed
                (fn [idx [org creds]]
                  (p/let [page (get-page browser idx)]
                    (process-org page org creds)))))]
    (-> results
        p/all
        (p/then #(prn "All done!"))
        (p/finally #(.close browser)))))


