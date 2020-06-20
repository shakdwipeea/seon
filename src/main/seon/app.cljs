(ns seon.app
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [snow.router :as router]
            [snow.comm.core :as comm]
            [stylefy.core :as stylefy :refer [use-style]]
            [react-google-maps :refer [withGoogleMap withScriptjs GoogleMap Marker
                                       OverlayView] :as m]
            ["react-google-maps/lib/components/places/SearchBox" :refer (SearchBox)]
            ;; ["react-google-maps/lib/components/addons/MarkerClusterer"
            ;;  :refer [MarkerClusterer]]

            [seon.yelp :as y]))

;; google map constants

(def google-map-key "")

(def map-url (str "https://maps.googleapis.com/maps/api/js?v=3.exp&libraries=geometry,drawing,places&key=" google-map-key))

(def cluster-icon
  "https://raw.githubusercontent.com/mahnunchik/markerclustererplus/master/images/m2.png")

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
 (fn [{{bounds ::bounds :as db} :db} [_ places map-ref]]
   (let [location (-> places first get-location)]
     (def location location)
     {:db (assoc db
                 ::places  places
                 ::bounds  (update-bounds db)
                 ::center  (location->coordinate location))
      :dispatch [::y/search {:location (location->coordinate location)
                             :update-center? true
                             :map-ref map-ref}]})))


(rf/reg-event-db
 ::update-timer
 (fn [db [_ timer]]
   (assoc db :timer timer)))

(defn dispatch-with-delay [dispatch delay]
  (.setTimeout js/window (fn [] (rf/dispatch dispatch)) delay))

(rf/reg-fx :debounced-dispatch
           (fn [{:keys [dispatch timeout timer]}]
             (when-not (nil? timer)
               (.clearTimeout js/window timer))
             (rf/dispatch
              [::update-timer (dispatch-with-delay dispatch timeout)])))

(rf/reg-event-fx
 ::update-bounds
 (fn [{db :db} [_ {:keys [::bounds ::projection]}]]
   (println "asas" )
   {:db (assoc db
               ::bounds bounds
               ::projection projection
               ::center1 (location->coordinate (.getCenter bounds)))
    :debounced-dispatch {:dispatch [::y/search {:location  (-> bounds
                                                               .getCenter
                                                               location->coordinate)
                                                :update-center? false}]
                         :timer (:timer db)
                         :timeout 300}}))

(rf/reg-event-fx
 ::update-zoom-level
 (fn [{{:keys [::bounds] :as db} :db} [_ {:keys [::zoom-level]}]]
   (println "dispatch for " (.getCenter bounds))
   {:db (assoc db ::zoom-level zoom-level)}))

(rf/reg-event-fx
 ::comm/connected
 (fn [{db :db} _]
   {:db db
    ::comm/request {:data [::comm/trigger {:data {::a 12}}]}}))


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

(rf/reg-event-db ::update-center
                 (fn [db [_ {new-center ::center}]]
                   (println "new " new-center)
                   (assoc db ::center (location->coordinate new-center))))

;; (rf/reg-event-fx ::staggered-center-update
;;                  (fn [{db :db} [_ {:keys [dispatch]}]]
;;                    ))


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


;; clustering markers


(def +grid-size+ 60)

(defn coordinate->latlng [{:keys [latitude longitude]}]
  (js/google.maps.LatLng. latitude longitude))

(defn coordinate->pixel [projection top-right bottom-left]
  (println "top-right " top-right bottom-left)
  {:pixel-top-right (.fromLatLngToDivPixel projection (coordinate->latlng top-right))
   :pixel-bottom-left (.fromLatLngToDivPixel projection (coordinate->latlng bottom-left))})

(defn scale-pixel! [pixel x-scaler y-scaler]
  ;; extend top right
  (set! (.-x pixel) (x-scaler (.-x pixel) +grid-size+))
  (set! (.-y pixel) (y-scaler (.-y pixel) +grid-size+))
  pixel)

(defn pixel->position [{:keys [pixel-top-right pixel-bottom-left]} projection]
  {:north-east (.fromDivPixelToLatLng projection (scale-pixel! pixel-top-right + -))
   :south-west (.fromDivPixelToLatLng projection (scale-pixel! pixel-bottom-left - +))})
;; (define scale-pixel  )

(defn get-extended-bounds [bounds projection]
  (let [top-right {:latitude (-> bounds .getNorthEast .lat)
                   :longitude (-> bounds .getNorthEast .lng)}
        bottom-left {:latitude (-> bounds .getSouthWest .lat)
                     :longitude (-> bounds .getSouthWest .lng)}

        {:keys [north-east south-west]} (-> projection
                                            (coordinate->pixel top-right bottom-left)
                                            (pixel->position projection))]
    (.extend bounds north-east)
    (.extend bounds south-west)
    bounds))

(defn get-marker-position [marker] (clj->js (gmap-conversion marker)))

(defn marker-in-bounds? [marker bounds]
  (println "marker is " marker (get-marker-position marker))
  (.contains bounds (get-marker-position marker)))

;; (defn latlng->bounds [])

(defn degree->radian [deg]  (* deg (/ Math/PI 180)))

(defn distance [{lat1 :latitude lng1 :longitude} {lat2 :latitude lng2 :longitude}]
  (let [radius 6371
        dlat (degree->radian (- lat2 lat1))
        dlon (degree->radian (- lng2 lng1))
        a (+ (* (Math/sin (/ dlat 2))
                (Math/sin (/ dlat 2)))
             (* (Math/cos (degree->radian lat1))
                (Math/cos (degree->radian lat2))
                (Math/sin (/ dlon 2))
                (Math/sin (/ dlon 2))))
        c (* 2 (Math/atan2 (Math/sqrt a) (Math/sqrt (- 1 a))))]
    (* radius c)))

(defn cluster-bounds [center markers projection]
  (println "center position" (get-marker-position center))
  (let [bounds (js/google.maps.LatLngBounds. (get-marker-position center)
                                             (get-marker-position center))]
    (println "center bounds" bounds)
    (doall (map #(->> % get-marker-position (.extend bounds))
                markers))
    (println "extending bounds")
    (get-extended-bounds bounds projection)))

(defn add-marker-to-cluster [clusters marker projection]
  (letfn [(new-cluster []
            (conj clusters {:center marker
                            :bounds (cluster-bounds marker [marker] projection)
                            :markers [marker]}))]
    (println "adding marker to a cluster")
    (if (= (count clusters) 0)
      (new-cluster)
      (let [sorted-clusters (->> clusters
                                 (map (fn [{:keys [center] :as cluster}]
                                        (assoc cluster
                                               :distance (distance center marker))))
                                 (sort-by :distance))
            {:keys [bounds center markers] :as closest-cluster} (first
                                                                         sorted-clusters)
            other-clusters  (rest sorted-clusters)]
        (println "closest cluster" closest-cluster other-clusters)
        (if (marker-in-bounds? marker bounds)
          (let [new-marker-set (conj markers marker)]
            (println "new marker set" new-marker-set)
            (conj other-clusters (assoc closest-cluster
                                        :bounds (cluster-bounds center
                                                                new-marker-set
                                                                projection)
                                        :markers new-marker-set)))
          (new-cluster))))))

(defn create-clusters [{:keys [map-bounds projection markers] :as m}]
  (println "m is " m)
  (let [bounds (get-extended-bounds map-bounds projection)]
    (reduce (fn [clusters marker]
              (if (marker-in-bounds? marker bounds)
                (add-marker-to-cluster clusters marker projection)
                clusters))
            []
            markers)))

(rf/reg-event-db
 ::cluster
 (fn [{:keys [::projection ::bounds] :as db} [_ markers]]
   (let [clusters (create-clusters {:map-bounds bounds
                                    :projection projection
                                    :markers markers})]
     (assoc db ::clustered-markers (map (fn [{:keys [center markers]}]
                                          (println "center is " center)
                                          (if (= (count markers) 1)
                                            center
                                            (assoc center :label (count markers))))
                                        clusters)))))


(rf/reg-sub ::clustered-markers (fn [db _] (::clustered-markers db)))



(defn google-map []
  (r/with-let [!searchbox-ref (r/atom nil)
               !gmap-ref (r/atom nil)
               !overlay-view-ref (r/atom nil)
               show-map? (rf/subscribe [::show-map])]
    
    [:div
     [:div (use-style {::stylefy/media {{:max-width phone-width}
                                        (when-not (true? @show-map?)
                                          {:display :none})}})
      [:> GoogleMap
       {:ref (set-ref! !gmap-ref)
        ;; :center @(rf/subscribe [::center])
        :defaultCenter (gmap-conversion (:center map-defaults))
        :defaultZoom (:zoom map-defaults)
        :onClick (fn [event]
                   (rf/dispatch [::y/search (location->coordinate (.-latLng event))]))
        :onZoomChanged (fn [e]
                         ;; (when-let [ref (some-> @!gmap-ref)]
                         ;;   (rf/dispatch [::update-zoom {::zoom-level (.getZoom ref)}]))
                         )
        ;; :onCenterChanged (fn []
        ;;                    (when-let [ref (some-> @!gmap-ref)]
        ;;                      (rf/dispatch [::update-center {::center (-> ref .getCenter)}])))

        :onBoundsChanged
        (fn []
          (when-let [ref (some-> @!gmap-ref)]
            ;; (println "clusters are"
            ;;          (map (comp count :markers)
            ;;               (create-clusters
            ;;                {:map-bounds (some-> @!gmap-ref .getBounds)
            ;;                 :projection (some-> @!overlay-view-ref .getProjection)
            ;;                 :markers @(rf/subscribe [::markers])})))
            (println "overlat projection "
                     (.getProjection (some-> @!overlay-view-ref)))
            (println "extended bounds"
                     (get-extended-bounds (.getBounds ref)
                                          (.getProjection
                                           (some-> @!overlay-view-ref))))
            (rf/dispatch [::update-bounds {::bounds (.getBounds ref)
                                           ::projection (some-> @!overlay-view-ref
                                                                .getProjection)}])))}
       [:> OverlayView {:position #js {:lat -34.398 :lng 150.644}
                        :ref (set-ref! !overlay-view-ref)
                        :mapPaneName (.-OVERLAY_LAYER  m/OverlayView)}
        
        [:div (map-indexed (fn [i m]
                             (println "marker is " m)
                             [:div {:key i}
                              [:> Marker {:position (get-marker-position m)
                                          :onClick (fn [_]
                                                     (aset js/location "href" (:url m)))
                                          :icon (when (:label m) cluster-icon)
                                          :label (some-> (:label m) str)}]])
                           @(rf/subscribe [::clustered-markers]))]]]]
     
     [:div.search (use-style {:z-index 12})
      [:> SearchBox {:ref #(reset! !searchbox-ref %)
                     ;; :bounds @(rf/subscribe [::bounds])
                     :controlPosition js/google.maps.ControlPosition.TOP_CENTER
                     :onPlacesChanged (fn []
                                        (when-let [places (some-> @!searchbox-ref .getPlaces)]
                                          (when-let [map-ref (some-> @!gmap-ref)]
                                            (rf/dispatch [::update-places places map-ref]))))}
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
  (println "App started.")
  )


(main!)
