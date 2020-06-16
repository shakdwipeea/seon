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
                   :zoom 14})



;; some styling
(def phone-width "414px")

(def input-style  {:boxSizing "border-box"
                   :border "1px solid transparent"
                   :width "240px"
                   :height "40px"
                   :margin-top "20px"
                   :padding "0 12px"
                   :boxShadow "0 4px 16px rgba(0, 0, 0, 0.3)"
                   :fontSize "14px"
                   :textOverflow "ellipses"
                   ::stylefy/media {{:max-width phone-width} {:margin-top "70px"}}})

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
   {:db (assoc db
               ::center (:center map-defaults)
               ::business nil
               ::show-map true)}))


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


(defn get-current-location []
  (when-not (nil? (.-geolocation js/navigator))
    (-> js/navigator
        .-geolocation
        (.getCurrentPosition (fn [pos] (rf/dispatch
                                       [::y/search {:latitude  (.. pos -coords -latitude)
                                                    :longitude (.. pos -coords -longitude)}]))))))


(rf/reg-event-db
 ::current-location
 (fn [db [_]]
   (get-current-location)
   db))


(defn gmap-conversion [{:keys [latitude longitude] :as a}]
  (merge a {:lat latitude
            :lng longitude}))

(defn gmaps-conversion [coordinates]
  (map gmap-conversion coordinates))


;; subscriptions to get google map data
(rf/reg-sub ::center (fn [{center ::center} _] (gmap-conversion center)))

(rf/reg-sub ::bounds (fn [db _] (::bounds db)))

(rf/reg-sub ::markers (fn [db _] (gmaps-conversion (::markers db))))

(rf/reg-sub ::show-map (fn [db _] (::show-map db)))

(defn google-map []
  (r/with-let [!searchbox-ref (r/atom nil)
               !gmap-ref (r/atom nil)
               show-map? (rf/subscribe [::show-map])]
    
    [:div
     [:div (use-style {::stylefy/media {{:max-width phone-width}
                                        (when-not (true? @show-map?)
                                          {:display :none})}})
      [:> GoogleMap
       {:ref (set-ref! !gmap-ref)
        :center @(rf/subscribe [::center])
        :defaultZoom (:zoom map-defaults)
        :onClick (fn [event] (rf/dispatch [::y/search (location->coordinate (.-latLng event))]))
        :onBoundsChanged (fn []
                           (println "bounds changed")
                           (when-let [ref (some-> @!gmap-ref)]
                             (rf/dispatch [::update-bounds
                                           {::bounds (.getBounds ref)}])))}
       
       (map-indexed (fn [i m]
                      [:div {:key i}
                       [:> Marker {:position m
                                   :onClick (fn [_]
                                              (aset js/location "href" (:url m)))
                                   }]])
                    @(rf/subscribe [::markers]))]]
     
     [:div.search (use-style {:z-index 12})
      [:> SearchBox {:ref #(reset! !searchbox-ref %)
                     :bounds @(rf/subscribe [::bounds])
                     :controlPosition js/google.maps.ControlPosition.TOP_CENTER
                     :onPlacesChanged (fn []
                                        (when-let [places (some-> @!searchbox-ref .getPlaces)]
                                          (rf/dispatch [::update-places places])))}
       [:input (use-style input-style
                          {:type "text"
                           :placeholder "search for stuff"})]]]]))

(def map-style (use-style
                (cond-> {:height "100%"}
                  (false? @(rf/subscribe [::show-map])) (merge {:height "auto"}))))


(defn map-container []
  [:div  [:> (withScriptjs (withGoogleMap 
                            (r/reactify-component google-map)))
          {:googleMapURL map-url
           :loadingElement (r/as-element [:div map-style])
           :containerElement (r/as-element [:div map-style])
           :mapElement (r/as-element [:div map-style])}]])


(defn app []
  (r/with-let [b (rf/subscribe [::y/business])
               show-map? (rf/subscribe [::show-map])]
    [:div (use-style {:display :flex
                      :flex-direction :row
                      :height "100%"
                      ::stylefy/media {{:max-width phone-width}
                                       {:flex-direction :column-reverse}}})
     [:div.yelp (use-style (if (nil?  @b)
                             {:display :none}
                             {:flex-basis "30%"
                              :overflow :auto
                              ::stylefy/media {{:max-width phone-width}
                                               (if (true? @show-map?)
                                                 {:display :none}
                                                 {:flex-basis "100%"})}}))
      [y/list-restaurants]]
     [:div.map (use-style {:flex-grow 11}) [map-container]]
     ;; [:button "Use my location"]
     ]))


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
