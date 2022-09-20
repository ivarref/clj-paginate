(ns com.github.ivarref.clj-paginate.reverse-test
  (:require [clojure.test :refer [deftest is]]
            [com.github.ivarref.clj-paginate :as cp]))

(def sort-fn (juxt (comp - :foo) :bar))

(def data (->> [{:foo 2 :bar 11}
                {:foo 2 :bar 55}
                {:foo 1 :bar 77}
                {:foo 1 :bar 99}]
               (shuffle)
               (sort-by sort-fn)
               (vec)))

(deftest first-reverse-test
  (let [conn (cp/paginate data
                          [:foo :bar]
                          identity
                          {:first 2}
                          :sort-fn sort-fn)]
    (is (= [{:foo 2, :bar 11} {:foo 2, :bar 55}]
           (mapv :node (:edges conn))))
    (is (= [{:foo 1 :bar 77} {:foo 1 :bar 99}]
           (mapv :node (:edges (cp/paginate data
                                            [:foo :bar]
                                            identity
                                            {:first 2 :after (get-in conn [:pageInfo :endCursor])}
                                            :sort-fn sort-fn)))))))

(deftest first-2-reverse-test
  (let [conn (cp/paginate data
                          [:bar :foo]
                          identity
                          {:first 2}
                          :sort-fn sort-fn)]
    (is (= [{:foo 2, :bar 11} {:foo 2, :bar 55}]
           (mapv :node (:edges conn))))
    (is (= [{:foo 1 :bar 77} {:foo 1 :bar 99}]
           (mapv :node (:edges (cp/paginate data
                                            [:bar :foo]
                                            identity
                                            {:first 2 :after (get-in conn [:pageInfo :endCursor])}
                                            :sort-fn sort-fn)))))))

(deftest last-reverse-test
  (let [conn (cp/paginate data
                          [:foo :bar]
                          identity
                          {:last 2}
                          :sort-fn sort-fn)]
    (is (= [{:foo 1 :bar 77} {:foo 1 :bar 99}]
           (mapv :node (:edges conn))))
    (is (= [{:foo 2 :bar 11} {:foo 2 :bar 55}]
           (mapv :node (:edges (cp/paginate data
                                            [:foo :bar]
                                            identity
                                            {:last 2 :before (get-in conn [:pageInfo :startCursor])}
                                            :sort-fn sort-fn)))))))
