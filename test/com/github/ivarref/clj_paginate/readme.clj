(ns com.github.ivarref.clj-paginate.readme
  (:require [com.github.ivarref.clj-paginate :as cp]))

(def data
  (group-by :status
            [{:inst 0 :id 1 :status :init}
             {:inst 1 :id 2 :status :pending}
             {:inst 2 :id 3 :status :done}
             {:inst 3 :id 4 :status :error}
             {:inst 4 :id 5 :status :done}]))

(defn http-post-handler
  [response data http-body]
  (assoc response
    :status 200
    :body (cp/paginate
            data
            :inst
            (fn [{:keys [inst id] :as node}]
              (Thread/sleep 10) ; Do some heavy work.
              (assoc node :value-from-db 1))

            ; Assume that the HTTP endpoint accepts a parameter `:statuses` for the body,
            ; and that when present, this is a vector such as `[:init :pending :done :error]` or similar,
            ; i.e. the keys of `data` that we want to filter on.
            ;
            ; Paginate's `opts` accepts a key `:filter` that does exactly this for data maps.
            ; Thus we can simply rename `:statuses` to `:filter` in the http body.
            ; clj-paginate takes care of storing the value of `:filter` in the cursor
            ; for subsequent queries.
            (clojure.set/rename-keys http-body {:statuses :filter}))))

(let [conn (cp/paginate
             data
             :inst
             identity
             {:first  1
              :filter [:done]})]
  (println (mapv :node (:edges conn)))

  (println (mapv :node (:edges (cp/paginate
                                 data
                                 :inst
                                 identity
                                 {:first 1
                                  :after (get-in conn [:pageInfo :endCursor])})))))
