(ns user.core
  (:require [clojure.spec.alpha :as s]
            [snow.repl :as repl]
            [snow.env :refer [read-edn]]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.core :as appenders]
            [seon.server :refer [system-config]]
            [shadow.cljs.devtools.server :as server]
            [shadow.cljs.devtools.api :as shadow]))

(s/check-asserts true)

;; (defn cljs-repl []
;;   (cemerick.piggieback/cljs-repl :app))

(defn restart-systems! []
  (do (repl/stop!)
      (repl/start! system-config)))

#_(restart-systems!)

#_(cljs-repl)

;; (defn compile-cljs []
;;   (server/start!)
;;   (shadow/dev :app))

#_(compile-cljs)

#_(shadow/release :app)

;; (repl/stop!)

(defn -main [& args]
  (timbre/refer-timbre)
  (timbre/merge-config!
   {:appenders {:spit (appenders/spit-appender {:fname "seon.log"})}})
  
  (repl/start-nrepl)
  (println "nrepl started")
  
  (println "Starting systems...")
  (repl/start! system-config)

  (println "Compiling cljs")
  (server/start!)
  (shadow/dev :app))

#_(shadow/stop-worker :app)
