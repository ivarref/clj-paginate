(ns com.github.ivarref.clj-paginate.batch-test
  (:require [clojure.test :refer [deftest is]]
            [com.github.ivarref.clj-paginate :as pv]
            [com.github.ivarref.clj-paginate.stacktrace]))


(deftest batch-first
  (let [v (vec (range 10))
        prep (pv/prepare-paginate
               {:sort-by [:inst]}
               (mapv #(assoc {} :inst %) v))
        seen-data (atom nil)
        batch-f (fn [nodes]
                  (reset! seen-data nodes)
                  (mapv #(assoc % :new-prop 1) nodes))
        conn (pv/paginate prep batch-f {:first 5} :batch? true)]
    (is (= [{:inst 0} {:inst 1} {:inst 2} {:inst 3} {:inst 4}] @seen-data))
    (is (= [{:inst 0, :new-prop 1}
            {:inst 1, :new-prop 1}
            {:inst 2, :new-prop 1}
            {:inst 3, :new-prop 1}
            {:inst 4, :new-prop 1}]
           (mapv :node (:edges conn))))))


(deftest ensure-order-test
  (let [v (mapv #(assoc {} :id %) (range 5))]
    (is (= v (pv/ensure-order v (vec (reverse v)))))))


(deftest ensure-order-sif-dif-test
  (is (= [{:b/id 1}
          {:b/id 2}]
         (pv/ensure-order [{:a/id 1}
                           {:a/id 2}]
                          [{:b/id 2}
                           {:b/id 1}]
                          :sif :a/id
                          :dif :b/id))))


(deftest ensure-order-throws-test
  (is (thrown? Exception (pv/ensure-order [{:id 1}] [{:id 2}])))
  (is (thrown? Exception (pv/ensure-order [{:a/id 1}] [{:b/id 1}])))
  (is (thrown? Exception (pv/ensure-order [{:a/id 1}] [{:id 1}])))
  (is (thrown? Exception (pv/ensure-order [{:id 1}] [{:b/id 1}]))))


(deftest batch-last
  (let [v (vec (range 10))
        prep (pv/prepare-paginate
               {:sort-by [:inst]}
               (mapv #(assoc {} :inst %) v))
        seen-data (atom nil)
        batch-f (fn [nodes]
                  (reset! seen-data nodes)
                  (mapv #(assoc % :new-prop 1) nodes))
        conn (pv/paginate prep batch-f {:last 5} :batch? true)]
    (is (= [{:inst 5} {:inst 6} {:inst 7} {:inst 8} {:inst 9}] @seen-data))
    (is (= [{:inst 5, :new-prop 1}
            {:inst 6, :new-prop 1}
            {:inst 7, :new-prop 1}
            {:inst 8, :new-prop 1}
            {:inst 9, :new-prop 1}]
           (mapv :node (:edges conn))))))