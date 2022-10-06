(ns com.github.ivarref.clj-paginate.multi-sort-test
  (:require [clojure.test :refer [deftest is]]
            [com.github.ivarref.clj-paginate :as cp]))

(def data [{:id 1 :date #inst"2000"}
           {:id 2 :date #inst"2001"}
           {:id 3 :date #inst"2002"}
           {:id 4 :date #inst"2002"}])

(deftest bad-input-not-asc-or-desc
  (try
    (cp/paginate
      data
      [[:id :asc] [:date :bad]]
      identity
      {:first 2})
    (is (= 0 1))
    (catch Exception e
      (is (= 1 1)))))

(defn nodes [conn] (mapv (comp :id :node) (:edges conn)))

(deftest basic
  (is (= [4 3] (nodes (cp/paginate
                        (vec (shuffle data))
                        [[:id :desc]]
                        identity
                        {:first 2}))))
  (is (= [1 2] (nodes (cp/paginate
                        (vec (shuffle data))
                        [[:id :asc]]
                        identity
                        {:first 2})))))

(deftest multi
  (is (= [3 4] (nodes (cp/paginate
                        (vec (shuffle data))
                        [[:date :desc] [:id :asc]]
                        identity
                        {:first 2}))))
  (is (= [4 3] (nodes (cp/paginate
                         (vec (shuffle data))
                         [[:date :desc] [:id :desc]]
                         identity
                         {:first 2})))))
