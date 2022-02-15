(ns com.github.ivarref.clj-paginate.or-filter-test
  (:require [clojure.test :refer [deftest is]]
            [com.github.ivarref.clj-paginate :as cp]))


(def data
  (group-by :status
            [{:inst 0 :status :init}
             {:inst 1 :status :pending}
             {:inst 2 :status :done}
             {:inst 3 :status :error}
             {:inst 4 :status :done}
             {:inst 5 :status :done}
             {:inst 6 :status :init}]))

(deftest basic-filtering
  (let [conn (cp/paginate
               data
               :inst
               identity
               {:first  2
                :filter [:done]})]
    (is (= [2 4] (mapv (comp :inst :node) (:edges conn))))
    (is (= [5] (mapv (comp :inst :node)
                     (:edges (cp/paginate
                               data
                               :inst
                               identity
                               {:first 2
                                :after (get-in conn [:pageInfo :endCursor])})))))))


(deftest missing-filter->include-everything
  (let [conn (cp/paginate
               data
               :inst
               identity
               {:first 2})]
    (is (= [0 1] (mapv (comp :inst :node) (:edges conn))))
    (is (= [2 3] (mapv (comp :inst :node)
                       (:edges (cp/paginate
                                 data
                                 :inst
                                 identity
                                 {:first 2
                                  :after (get-in conn [:pageInfo :endCursor])})))))))