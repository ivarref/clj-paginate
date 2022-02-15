(ns com.github.ivarref.clj-paginate.edge-test
  (:require [clojure.test :refer [deftest is]]
            [com.github.ivarref.clj-paginate :as pag]))


(deftest empty-input
  (is (= [] (:edges (pag/paginate {"empty" []} :inst identity {:first 10})))))


(deftest differing-lengths
  (is (= [0 1 2 3 4]
         (mapv :node (:edges (pag/paginate {"even" [{:inst 0} {:inst 2} {:inst 4}]
                                            "odd"  [{:inst 1} {:inst 3}]}
                                           :inst
                                           :inst
                                           {:first 10}))))))