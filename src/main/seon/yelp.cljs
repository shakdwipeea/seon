(ns seon.yelp
  (:require [re-frame.core :as rf]
            [re-graph.core :as rg]
            [day8.re-frame.http-fx]
            [ajax.core :as ajax]
            [snow.comm.core :as comm]
            [venia.core :as v]))

(rf/reg-event-db
 ::set-restaurants
 (fn [db [_ [_ {:keys [businesses region] :as payload}]]]
   (assoc db
          ::business businesses
          :seon.app/center (:center region)
          :seon.app/markers  (map :coordinates businesses))))


(rf/reg-event-fx             
 ::search
 (fn [{:keys [db]} [_ location]]
   {:db   (assoc db :show-twirly true)
    ::comm/request {:data [::comm/trigger location]
                    :on-success      [::set-restaurants]
                    :on-failure      [::error]}}))


;; (term: \"burrito\",
;;        location: \"san francisco\",
;;        limit: 5)

(defn list-restaurants []
  [:div "list"])
