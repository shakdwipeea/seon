(ns seon.yelp
  (:require [re-frame.core :as rf]
            [re-graph.core :as rg]
            [day8.re-frame.http-fx]
            [ajax.core :as ajax]
            [stylefy.core :as stylefy :refer [use-style]]
            [snow.comm.core :as comm]
            [venia.core :as v]))

(rf/reg-event-db
 ::set-restaurants
 (fn [db [_ [_ {:keys [businesses region] :as payload}]]]
   (def a (map js->clj businesses))
   (println a)
   (assoc db
          ::business (map js->clj businesses)
          :seon.app/center (:center region)
          :seon.app/markers  (map :coordinates businesses))))


(rf/reg-event-fx             
 ::search
 (fn [{:keys [db]} [_ location]]
   {:db   (assoc db :show-twirly true)
    ::comm/request {:data [::comm/trigger location]
                    :on-success      [::set-restaurants]
                    :on-failure      [::error]}}))


(rf/reg-sub ::business (fn [db _] (::business db)))

(defn rating-box [rating review_count]
  [:span  
   [:span (use-style {:padding-right "5px"})
    (str "(" rating ")")]
   (for [r (range 0 rating)]
     [:span.fa.fa-star (use-style {:color :orange})])
   (for [d (range 0 (- 5 rating))]
     [:span.fa.fa-star])
   [:span (use-style {:padding-left "5px"})
    (str "(" review_count ")")]])

(def small-padding {:padding "3px"})


(defn restaurant [{:keys [name image_url rating review_count price categories
                          location is_closed url] :as r}]
  [:a (use-style {:text-decoration :none
                  :color "#000"}
                 {:href url})
   [:div (use-style {:text-decoration "bold"}
                    {:key name})
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
   (when-let [bs @(rf/subscribe [::business])]
     (map restaurant bs))])
