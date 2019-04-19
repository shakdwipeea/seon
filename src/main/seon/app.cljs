(ns seon.app
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [snow.router :as router]))

(enable-console-print!)

(defn app []
  [:h1 "welcome to roshar"])

(defn main! []
  (println "hello")
  (rf/clear-subscription-cache!)
  (r/render [app]
            (js/document.getElementById "app")))

(main!)
