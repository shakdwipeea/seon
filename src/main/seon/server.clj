(ns seon.server
  (:require [com.stuartsierra.component :as component]
            [snow.comm.core :as comm]
            [snow.systems :as system]
            [snow.client :as client]
            [snow.env :as env]
            [venia.core :as v]
            [re-frame.core :as rf]
            [compojure.core :refer [routes GET ANY]]
            [ring.util.http-response :as response]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :as params]
            [ring.middleware.anti-forgery :as anti-forgery]
            [hiccup.core :as h]
            [taoensso.timbre :as timbre :refer [info]]
            (system.components
             [immutant-web :refer [new-immutant-web]]
             [endpoint :refer [new-endpoint]]
             [middleware :refer [new-middleware]]
             [handler :refer [new-handler]])))


;;;;;;;;;;;;;;;
;; home-page ;;
;;;;;;;;;;;;;;;

(defn home-page [csrf-token]
  [:html
   [:head
    [:meta {:content "text/html; charset=UTF-8"
            :http-equiv "Content-Type"}]
    [:meta {:content "width=device-width, initial-scale=1"
            :name "viewport"}]
    [:style {:id "_stylefy-constant-styles_"}]
    [:style {:id "_stylefy-styles_"}]
    [:link {:rel "stylesheet"
            :href "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/4.7.0/css/font-awesome.min.css"}]
    [:title "seon"]]
   [:body {:data-csrf-token csrf-token}
    [:div {:id "app"}]
    [:script {:src "https://maps.googleapis.com/maps/api/js"}]
    [:script {:src "/js/main.js"
              :type "text/javascript"}]]])


(defn serve-page [req]
  (-> req
     :anti-forgery-token
     home-page
     h/html
     response/ok
     (response/header "Content-Type" "text/html"))) 


(defn site [_]
  (routes
   (GET "/" req (serve-page req))
   (ANY "*" req (serve-page req))))


;;;;;;;;;;;;;;;;
;; yelp-proxy ;;
;;;;;;;;;;;;;;;;

(def api-key  (:yelp-api-key (env/profile)))

(def yelp-config {:url "https://api.yelp.com/v3/"
                  :headers {:Authorization (str "Bearer " api-key)}})


(defn search-yelp [location]
  (info "searching for " location)
  (client/get (-> yelp-config :url (str "businesses/search"))
              {:headers (:headers yelp-config)
               :query-params (merge location
                                    {:term "restaurants"
                                     :limit 10})}))

(defn request-handler [{:keys [event ?reply-fn data] :as ev-msg}]
  (info "ever" event)
  (def d ev-msg)
  (if (nil? data)
    (rf/dispatch (conj event ?reply-fn))
    (?reply-fn [:seon.yelp/set-restaurants (search-yelp data)])))

;; ((:?reply-fn d) [:snow.comm.core/trigger (search-yelp (:data d))])

(defn system-config [config]
  [::comm/comm (comm/new-comm (fn [component] request-handler)
                              comm/broadcast
                              request-handler)
   :middleware (new-middleware {:middleware [wrap-session
                                             anti-forgery/wrap-anti-forgery
                                             params/wrap-params
                                             wrap-keyword-params
                                             [wrap-resource "public"]]})
   ::site-endpoint (component/using (new-endpoint site)
                                    [:middleware])
   ::sente-endpoint (component/using (new-endpoint comm/sente-routes)
                                     [:middleware ::comm/comm])
   ::handler (component/using (new-handler)
                              [::sente-endpoint ::site-endpoint :middleware])
   ::api-server (component/using (new-immutant-web :port (system/get-port config :http-port))
                                 [::handler])])
