(ns seon.proxy
  (:require [com.stuartsierra.component :as component]
            [snow.comm.core :as comm]
            [snow.systems :as system]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :as params]
            [ring.middleware.anti-forgery :as anti-forgery]
            (system.components
             [immutant-web :refer [new-immutant-web]]
             [endpoint :refer [new-endpoint]]
             [middleware :refer [new-middleware]]
             [handler :refer [new-handler]])))


(defn request-handler [data]
  (println data))


(defn system-config [config]
  [::comm/comm (comm/new-comm comm/event-msg-handler
                              comm/broadcast
                              request-handler)
   :middleware (new-middleware {:middleware [wrap-session
                                             anti-forgery/wrap-anti-forgery
                                             params/wrap-params
                                             wrap-keyword-params
                                             [wrap-resource "public"]]})
   ::sente-endpoint (component/using (new-endpoint comm/sente-routes)
                                     [:middleware ::comm/comm])
   ::handler (component/using (new-handler)
                              [::sente-endpoint :middleware])
   ::api-server (component/using (new-immutant-web :port (system/get-port config :http-port))
                                 [::handler])])
