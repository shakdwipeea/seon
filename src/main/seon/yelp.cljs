(ns seon.yelp
  (:require [re-frame.core :as rf]
            [re-graph.core :as rg]
            [day8.re-frame.http-fx]
            [ajax.core :as ajax]
            [stylefy.core :as stylefy :refer [use-style]]
            [snow.comm.core :as comm]
            [venia.core :as v]))

(defn gmap-conversion [{:keys [latitude longitude] :as a}]
  (merge a {:lat latitude
            :lng longitude}))

(rf/reg-event-db
 ::set-restaurants
 (fn [db [_ update-center? map-ref [_ {:keys [businesses region] :as payload}]]]
   (println "ce" (:center region))
   (if update-center?
     (.panTo map-ref (clj->js (gmap-conversion (:center region)))))
   (merge db
          {::business (map js->clj businesses)
           :seon.app/markers  (map
                               (fn [{:keys [coordinates url]}]
                                 (merge coordinates
                                        {:url url}))
                               businesses)}
          (if update-center?
            {:seon.app/center (:center region)}
            {}))))


(rf/reg-event-fx             
 ::search
 (fn [{:keys [db]} [_ {:keys [location update-center? map-ref]}]]
   (println "searching for " location)
   {:db   (assoc db :show-twirly true)
    ::comm/request {:data [::comm/trigger location]
                    :on-success      [::set-restaurants update-center? map-ref]
                    :on-failure      [::error]}}))


(rf/reg-sub ::business (fn [db _] (::business db)))

(defn rating-box [rating review_count]
  [:span  
   [:span (use-style {:padding-right "5px"})
    (str "(" rating ")")]
   (doall (for [r (range 0 rating)]
             [:span.fa.fa-star (use-style {:color :orange}
                                          {:key r})]))
   (doall (for [d (range 0 (- 5 rating))]
             [:span.fa.fa-star {:key d}]))
   [:span (use-style {:padding-left "5px"})
    (str "(" review_count ")")]])

(def small-padding {:padding "3px"})


(defn restaurant [{:keys [name image_url rating review_count price categories
                          location is_closed url] :as r}]
  [:a (use-style {:text-decoration :none
                  :color "#000"}
                 {:href url
                  :key name})
   [:div (use-style {:text-decoration "bold"})
    [:div (use-style {:min-height "100px"
                      :display :flex
                      :flex-direction :row})
     [:div (use-style {:flex-basis "70%"}) [:div
                                            [:div name]
                                            [:div
                                             [rating-box (int rating) review_count]
                                             [:span (use-style small-padding) price]
                                             [:span (use-style small-padding) (-> categories
                                                                                  first
                                                                                  :title)]]
                                            [:div (use-style {:padding-top "10px"})
                                             (:address1 location)]
                                            [:div
                                             (use-style (merge {:padding-top "10px"}
                                                               (if is_closed {:color "red"} {:color "green"})))
                                             (if is_closed "Closed" "Open")]]]
     [:div (use-style {}) [:img {:src image_url
                                 :height "100px"
                                 :width "100px"}]]]
    [:hr]]])


(defn list-restaurants []
  [:div (use-style {:height "100%"})
   (if-let [bs  @(rf/subscribe [::business])]
     (doall (map restaurant bs))
     "No restaurants found")])
