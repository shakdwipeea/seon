(ns seon.app
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [snow.router :as router]
            [stylefy.core :as stylefy :refer [use-style]]
            [react-google-maps :refer [withGoogleMap withScriptjs GoogleMap Marker]]
            ["react-google-maps/lib/components/places/SearchBox" :refer (SearchBox)]))

;; google map constants

(def google-map-key "AIzaSyBkRHn-q8G25pGqRQ980-DVTNBLBqsg1ho")

(def map-url (str "https://maps.googleapis.com/maps/api/js?v=3.exp&libraries=geometry,drawing,places&key=" google-map-key))


;; defaults to get started

(def map-defaults {:center {:lat 59.95
                            :lng 30.33}
                   :zoom 11})


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


;; handler called when we update place in the google map
(rf/reg-event-db
 ::init
 (fn [db _]
   (assoc db ::center (:center map-defaults))))


(rf/reg-event-db
 ::update-places
 (fn [{bounds ::bounds :as db} [_ places]]
   (def places places)
   (let [markers (map (fn [p]
                        {:position (get-location p)})
                      places)]
     (assoc db
            ::places places
            ::bounds (update-bounds db)
            ::markers markers
            ::center (-> markers first :position)))))


(rf/reg-event-db
 ::update-bounds
 (fn [db {:keys [::bounds ::center]}]
   (assoc db
          ::bounds bounds
          ::center center)))


;; subscriptions to get google map data
(rf/reg-sub ::center (fn [db _] (::center db)))

(rf/reg-sub ::bounds (fn [db _] (::bounds db)))

(rf/reg-sub ::markers (fn [db _] (::markers db)))


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
                                         {::bounds (.getBounds ref)
                                          ::center (.getCenter ref)}])))}
     [:> SearchBox {:ref #(reset! !searchbox-ref %)
                    :bounds @(rf/subscribe [::bounds])
                    :controlPosition js/google.maps.ControlPosition.TOP_CENTER
                    :onPlacesChanged (fn []
                                       (when-let [places (some-> @!searchbox-ref .getPlaces)]
                                         (rf/dispatch [::update-places places])))}
      [:input (use-style input-style
                         {:type "text"
                          :placeholder "search for stuff"})]]
     (for [m @(rf/subscribe [::markers])]
       [:> Marker {:position (:position m)}])]))


(defn map-container []
  [:div  [:> (withScriptjs (withGoogleMap 
                            (r/reactify-component google-map)))
          {:googleMapURL map-url
           :loadingElement (r/as-element [:div (use-style {:height "100%"})])
           :containerElement (r/as-element [:div (use-style {:height "100vh"})])
           :mapElement (r/as-element [:div (use-style {:height "100%"})])}]])


(defn app []
  (map-container))


(defn main! []
  (enable-console-print!)
  (stylefy/init)
  (rf/clear-subscription-cache!)
  (r/render [app]
            (js/document.getElementById "app"))
  (rf/dispatch [::init])
  (println "App started."))


(main!)
