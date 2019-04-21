(ns seon.app
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [snow.router :as router]
            [snow.comm.core :as comm]
            [stylefy.core :as stylefy :refer [use-style]]
            [react-google-maps :refer [withGoogleMap withScriptjs GoogleMap Marker]]
            ["react-google-maps/lib/components/places/SearchBox" :refer (SearchBox)]

            [seon.yelp :as y]))

;; google map constants

(def google-map-key "")

(def map-url (str "https://maps.googleapis.com/maps/api/js?v=3.exp&libraries=geometry,drawing,places&key=" google-map-key))


;; defaults to get started

(def map-defaults {:center {:latitude 59.95
                            :longitude 30.33}
                   :zoom 16})


;; some styling
(def input-style  {:boxSizing "border-box"
                   :border "1px solid transparent"
                   :width "240px"
                   :height "32px"
                   :margin-top "20px"
                   :padding "0 12px"
                   :boxShadow "0 4px 16px rgba(0, 0, 0, 0.3)"
                   :fontSize "14px"
                   :textOverflow "ellipses"})

;; interop helpers

(defn get-location
  "get location from a place"
  [place]
  (.. place -geometry -location))

(defn get-viewport
  "get viewport from a places"
  [place]
  (.. place -geometry -viewport))

(defn set-ref!
  "set ref to given atom"
  [ref-atom]
  (fn [comp] (reset! ref-atom comp)))


;; google map helpers

(defn update-bounds
  "will update google map bounds for the given places and will return the bounds"
  [places]
  (let [bounds (js/google.maps.LatLngBounds.)]
    (for [p places]
      (if-let [viewport (get-viewport p)]
        (.union bounds viewport)
        (.extend bounds (get-location p))))
    bounds))


(rf/reg-event-fx
 ::init
 (fn [{:keys [db]} _]
   {:db (assoc db ::center (:center map-defaults))}))


(defn location->coordinate [location]
  {:latitude (.lat location)
   :longitude (.lng location)})

;; handler called when we update place in the google map
(rf/reg-event-fx
 ::update-places
 (fn [{{bounds ::bounds :as db} :db} [_ places]]
   (let [location (-> places first get-location)]
     (def location location)
     {:db (assoc db
                 ::places  places
                 ::bounds  (update-bounds db)
                 ::center  (location->coordinate location))
      :dispatch [::y/search (location->coordinate location)]})))


(rf/reg-event-db
 ::update-bounds
 (fn [db {:keys [::bounds]}]
   (assoc db ::bounds bounds)))


(defn gmap-conversion [{:keys [latitude longitude] :as a}]
  {:lat latitude
   :lng longitude})

(defn gmaps-conversion [coordinates]
  (map gmap-conversion coordinates))


;; subscriptions to get google map data
(rf/reg-sub ::center (fn [{center ::center} _] (gmap-conversion center)))

(rf/reg-sub ::bounds (fn [db _] (::bounds db)))

(rf/reg-sub ::markers (fn [db _] (gmaps-conversion (::markers db))))


(defn google-map []
  (r/with-let [!searchbox-ref (r/atom nil)
               !gmap-ref (r/atom nil)]
    [:> GoogleMap
     {:ref (set-ref! !gmap-ref)
      :center @(rf/subscribe [::center])
      :defaultZoom (:zoom map-defaults)
      :onBoundsChanged (fn []
                         (when-let [ref (some-> @!gmap-ref)]
                           (rf/dispatch [::update-bounds
                                         {::bounds (.getBounds ref)}])))}
     [:> SearchBox {:ref #(reset! !searchbox-ref %)
                    :bounds @(rf/subscribe [::bounds])
                    :controlPosition js/google.maps.ControlPosition.TOP_CENTER
                    :onPlacesChanged (fn []
                                       (when-let [places (some-> @!searchbox-ref .getPlaces)]
                                         (rf/dispatch [::update-places places])))}
      [:input (use-style input-style
                         {:type "text"
                          :placeholder "search for stuff"})]]
     (map-indexed (fn [i m]
                    [:div {:key i}
                     [:> Marker {:position m}]])
                  @(rf/subscribe [::markers]))]))


(defn map-container []
  [:div  [:> (withScriptjs (withGoogleMap 
                            (r/reactify-component google-map)))
          {:googleMapURL map-url
           :loadingElement (r/as-element [:div (use-style {:height "100%"})])
           :containerElement (r/as-element [:div (use-style {:height "100%"})])
           :mapElement (r/as-element [:div (use-style {:height "100%"})])}]])


(defn app []
  [:div (use-style {:display :flex
                    :flex-direction :row})
   [:div.yelp (use-style (if (nil? @(rf/subscribe [::y/business]))
                           {:display :none}
                           {:flex-basis "40%"
                            :overflow :auto}))   [y/list-restaurants]]
   [:div.map (use-style {:flex-grow 11}) [map-container]]])


(defn main! []
  (enable-console-print!)
  (stylefy/init)
  (set! js/document.body.style.overflow "hidden")
  (comm/start! (.getAttribute js/document.body "data-csrf-token"))
  (rf/clear-subscription-cache!)
  (rf/dispatch-sync [::init])
  (r/render [app]
            (js/document.getElementById "app"))
  (println "App started."))


(main!)
