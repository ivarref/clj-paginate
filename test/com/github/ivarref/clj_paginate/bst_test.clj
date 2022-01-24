(ns com.github.ivarref.clj-paginate.bst-test
  (:require [clojure.test :refer [deftest is]]
            [com.github.ivarref.clj-paginate.impl.bst :as bst]
            [com.github.ivarref.clj-paginate.stacktrace]))


(defn number-nodes [res]
  (mapv :inst res))


(deftest beginning-with-pred
  (let [n 10
        numbers (vec (range n))
        all-nodes (mapv (fn [n] (assoc {} :inst n)) numbers)
        root (bst/balanced-tree all-nodes)]
    (is (= numbers (number-nodes (bst/from-beginning root (constantly true) 10))))
    (is (= (filterv even? numbers) (number-nodes (bst/from-beginning root (comp even? :inst) 10))))))


(deftest from-end-with-pred
  (let [n 10
        numbers (vec (range n))
        all-nodes (mapv (fn [n] (assoc {} :inst n)) numbers)
        root (bst/balanced-tree all-nodes)]
    (is (= numbers (number-nodes (bst/from-end root (constantly true) 10))))
    (is (= (filterv even? numbers) (number-nodes (bst/from-end root (comp even? :inst) 10))))))


(deftest can-iterate-map-tree-from-beginning
  (let [n 100]
    (dotimes [i n]
      (let [nodes (mapv (fn [n] (assoc {} :inst n)) (range i))
            root (bst/balanced-tree nodes)
            pred #(even? (:inst %))]
        (dotimes [j i]
          (is (= (number-nodes (take j (filter pred nodes)))
                 (number-nodes (bst/from-beginning root pred j))))
          (is (= (vec (take j nodes))
                 (bst/from-beginning root (fn [_] true) j))))))))


(deftest can-iterate-map-tree-from-end
  (let [n 100]
    (dotimes [i n]
      (let [nodes (mapv (fn [n] (assoc {} :inst n)) (range i))
            tree (bst/balanced-tree nodes)]
        (dotimes [j i]
          (is (= (vec (take-last j nodes))
                 (bst/from-end tree (constantly true) j))))))))


(deftest balanced-tree
  (doseq [n (range 1000)]
    (let [v (vec (range n))]
      (is (= v (bst/depth-first-vals (bst/balanced-tree v)))))))


(defn node-range-tree [r]
  (bst/balanced-tree (mapv (fn [n] (assoc {} :inst n)) (range r))))


(deftest after-value-test
  (let [n 100]
    (dotimes [i n]
      (dotimes [j i]
        (is (= (->> (range i)
                    (drop (inc j))
                    (take 10)
                    (vec))
              (mapv :inst (bst/after-value
                            (node-range-tree i)
                            (constantly true)
                            {:inst j}
                            [:inst]
                            10))))))))


(deftest after-value-handle-disappearing-values
  (let [root (node-range-tree 10)]
    (is (= (vec (range 10))
           (number-nodes (bst/after-value
                           root
                           (constantly true)
                           {:inst -1}
                           [:inst]
                           10))))
    (is (= []
           (number-nodes (bst/after-value
                           root
                           (constantly true)
                           {:inst 10}
                           [:inst]
                           10))))
    (is (= [2 4]
           (number-nodes (bst/after-value
                           (bst/balanced-tree [{:inst 0}
                                               {:inst 2}
                                               {:inst 3}
                                               {:inst 4}])
                           (comp even? :inst)
                           {:inst 1}
                           [:inst]
                           10)))))
  (let [n 1000
        root (node-range-tree 1000)]
    (doseq [cnt (range n)]
      (is (= (vec (take 10 (filterv (partial < cnt) (range n))))
             (number-nodes (bst/after-value
                             root
                             (constantly true)
                             {:inst (+ 0.5 cnt)}
                             [:inst]
                             10)))))))


(deftest before-value-handle-disappearing-values
  (let [root (node-range-tree 10)]
    (is (= (vec (range 10))
           (number-nodes (bst/before-value
                           root
                           (constantly true)
                           {:inst 10}
                           [:inst]
                           10))))
    (is (= (vec (range 6))
           (number-nodes (bst/before-value
                           root
                           (constantly true)
                           {:inst 5.5}
                           [:inst]
                           10))))
    (is (= []
           (number-nodes (bst/before-value
                           root
                           (constantly true)
                           {:inst 0}
                           [:inst]
                           10))))
    (is (= [0 2]
           (number-nodes (bst/before-value
                           (bst/balanced-tree [{:inst 0}
                                               {:inst 2}
                                               {:inst 3}
                                               {:inst 4}])
                           (comp even? :inst)
                           {:inst 3}
                           [:inst]
                           10))))))


(deftest before-value-test
  (let [n 100]
    (dotimes [i n]
      (dotimes [j i]
        (is (= (->> (range i)
                    (filter #(< % j))
                    (take-last 10)
                    (vec))
               (mapv :inst (bst/before-value
                             (node-range-tree i)
                             (constantly true)
                             {:inst j}
                             [:inst]
                             10))))))))


(deftest tree-contains-test
  (let [root (node-range-tree 1000)
        found? #(bst/tree-contains? root {:inst %} [:inst])]
    (is (false? (found? -1)))
    (dotimes [x 1000]
      (is (true? (found? x))))
    (is (false? (found? 1000)))))