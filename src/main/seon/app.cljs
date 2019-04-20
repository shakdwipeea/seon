(ns seon.app
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [snow.router :as router]
            [stylefy.core :as stylefy :refer [use-style]]
            [react-google-maps :refer [withGoogleMap withScriptjs GoogleMap Marker]]
            ["react-google-maps/lib/components/places/SearchBox" :refer (SearchBox)]))

(def google-map-key "AIzaSyBkRHn-q8G25pGqRQ980-DVTNBLBqsg1ho")

(def map-url (str "https://maps.googleapis.com/maps/api/js?v=3.exp&libraries=geometry,drawing,places&key=" google-map-key))

(def map-data {:center {:lat 59.95
                        :lng 30.33}
               :zoom 11})

(def input-style  {:boxSizing "border-box"
                   :border "1px solid transparent"
                   :width "240px"
                   :height "32px"
                   :margin-top "20px"
                   :padding "0 12px"
                   :boxShadow "0 4px 16px rgba(0, 0, 0, 0.3)"
                   :fontSize "14px"
                   :textOverflow "ellipses"})

(defn google-map []
  [:> GoogleMap
   {:defaultCenter {:lat -34.397
                    :lng 150.644}
    :defaultZoom 8}
   [:> SearchBox {:controlPosition js/google.maps.ControlPosition.TOP_CENTER}
    [:input (use-style input-style
                       {:type "text"
                        :placeholder "search for stuff"})]]
   [:> Marker {:position {:lat -34.397
                          :lng 150.644}}]])

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
  (println "App started."))

(main!)
