(ns seon.yelp
  (:require [re-frame.core :as rf]
            [re-graph.core :as rg]
            [day8.re-frame.http-fx]
            [ajax.core :as ajax]
            [venia.core :as v]))

(def api-key "")

(def config {:http-url "https://api.yelp.com/v3/graphql"
             
             :ws-reconnect-timeout nil
             :http-parameters {:headers (str "Authorization: Bearer " api-key)}})

(rf/dispatch-sync [::rg/init config])

  (def query "{
    search {
        total
        business {
            name
            url
        }
    }
  }")

(rf/reg-event-db
 ::set-restaurants
 (fn [db [_ {data :data :as payload}]]
   (println "As")
   (println payload)
   (assoc db ::data data)))


(rf/reg-event-fx             
 ::search-yelp            
 (fn [{:keys [db]} _]      
   {:db   (assoc db :show-twirly true)
    :http-xhrio {:method          :post
                 :uri             (:http-url config)
                 :timeout         8000
                 :headers         {:Authorization (str "Bearer " api-key)
                                   :Content-type "application/graph-ql"}
                 :body          (v/graphql-query {:venia/queries
                                                  [[:search {:term "burrito"
                                                             :location "san francisco"
                                                             :limit 5}
                                                    [:total [:business [:name :url]]]]]})
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success      [::set-restaurants]
                 :on-failure      [::error]}}))



;; (term: \"burrito\",
;;        location: \"san francisco\",
;;        limit: 5)

(defn list-restaurants []
  (rf/dispatch [::search-yelp])
  [:div "list"])
