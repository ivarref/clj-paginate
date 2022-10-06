(ns com.github.ivarref.clj-paginate.inclusive-test
  (:require [clojure.test :refer [deftest is]]
            [com.github.ivarref.clj-paginate :as cp]))

(defn nodes [pag]
  (mapv :node (:edges pag)))

(deftest inclusive?-after-test
  (let [data [{:id 1}
              {:id 2}
              {:id 3}]
        data2 [{:id 0}
               {:id 1}
               {:id 2}
               {:id 3}]
        {:keys [pageInfo]} (cp/paginate data
                                        [:id]
                                        identity
                                        {:first 2})]
    (is (false? (:hasPrevPage pageInfo)))
    (is (= [{:id 1}
            {:id 2}]
           (nodes (cp/paginate data [:id] identity {:first 2 :after (:startCursor pageInfo)} :inclusive? true))))
    (is (= [{:id 1}
            {:id 2}]
           (nodes (cp/paginate data2 [:id] identity {:first 2 :after (:startCursor pageInfo)} :inclusive? true))))
    (is (true? (:hasPrevPage (:pageInfo (cp/paginate data2 [:id] identity {:first 2 :after (:startCursor pageInfo)} :inclusive? true)))))))


(deftest inclusive?-before-test
  (let [data [{:id 1}
              {:id 2}
              {:id 3}]
        data2 [{:id 1}
               {:id 2}
               {:id 3}
               {:id 4}]
        {:keys [pageInfo] :as con} (cp/paginate data
                                                [:id]
                                                identity
                                                {:last 2})]
    (is (= [{:id 2} {:id 3}] (nodes con)))
    (is (false? (:hasNextPage pageInfo)))
    (is (= [{:id 2}
            {:id 3}]
           (nodes (cp/paginate data [:id] identity {:last 2 :before (:endCursor pageInfo)} :inclusive? true))))
    (is (= [{:id 2}
            {:id 3}]
           (nodes (cp/paginate data2 [:id] identity {:last 2 :before (:endCursor pageInfo)} :inclusive? true))))
    (is (true? (:hasNextPage (:pageInfo (cp/paginate data2 [:id] identity {:last 2 :before (:startCursor pageInfo)} :inclusive? true)))))))

(defn startCursor [conn]
  (:startCursor (:pageInfo conn)))

(defn endCursor [conn]
  (:endCursor (:pageInfo conn)))

(defn node-ids [pag]
  (mapv :id (mapv :node (:edges pag))))

(deftest inclusive?-before-test-2
  (let [data (mapv (fn [x] {:id (inc x)}) (range 10))
        conn (cp/paginate data [:id] identity {:last 5})]
    (is (= [6 7 8 9 10] (node-ids conn)))
    (is (= [6 7 8 9 10] (node-ids (cp/paginate data [:id] identity {:last 5 :before (endCursor conn)} :inclusive? true))))
    (is (= [1 2 3 4 5] (node-ids (cp/paginate data [:id] identity {:last 5 :before (startCursor conn)}))))))
