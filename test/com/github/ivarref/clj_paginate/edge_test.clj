(ns com.github.ivarref.clj-paginate.edge-test
  (:require [clojure.test :refer [deftest is]]
            [com.github.ivarref.clj-paginate :as pag]))


(deftest empty-input
  (is  (= [] (:edges (pag/paginate {"empty" []} :inst identity {:first 10})))))