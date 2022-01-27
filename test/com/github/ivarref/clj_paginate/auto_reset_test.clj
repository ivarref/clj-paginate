(ns com.github.ivarref.clj-paginate.auto-reset-test
  (:require [clojure.test :refer [deftest is]]
            [com.github.ivarref.clj-paginate :as cp]))


(defn nodes [conn]
  (->> conn
       :edges
       (mapv :node)
       (mapv :inst)))

(deftest autoreset-first
  (let [data [{:inst 0} {:inst 1} {:inst 2} {:inst 3}]
        p1 (cp/prepare-paginate {:version "git-sha-1" :sort-by [:inst]} data)
        p2 (cp/prepare-paginate {:version "git-sha-2" :sort-by [:inst]} data)
        conn (cp/paginate p1 identity {:first 2})]
    (is (= [0 1] (nodes conn)))
    (is (= [2 3] (nodes (cp/paginate p1 identity {:first 2 :after (->> conn
                                                                       :edges
                                                                       last
                                                                       :cursor)}))))
    (is (= [2 3] (nodes (cp/paginate (cp/prepare-paginate {:version "git-sha-1" :sort-by [:inst]} data)
                                     identity {:first 2 :after (->> conn
                                                                    :edges
                                                                    last
                                                                    :cursor)}))))
    (is (= [0 1] (nodes (cp/paginate p2 identity {:first 2 :after (->> conn
                                                                       :edges
                                                                       last
                                                                       :cursor)}
                                     :auto-reset? true))))))


(deftest autoreset-last
  (let [data [{:inst 0} {:inst 1} {:inst 2} {:inst 3}]
        p1 (cp/prepare-paginate {:version "git-sha-1" :sort-by [:inst]} data)
        p2 (cp/prepare-paginate {:version "git-sha-2" :sort-by [:inst]} data)
        conn (cp/paginate p1 identity {:last 2})]
    (is (= [2 3] (nodes conn)))
    (is (= [0 1] (nodes (cp/paginate p1 identity {:last 2 :before (->> conn
                                                                       :edges
                                                                       first
                                                                       :cursor)}))))
    (is (= [2 3] (nodes (cp/paginate p2 identity {:last 2 :before (->> conn
                                                                       :edges
                                                                       first
                                                                       :cursor)}
                                     :auto-reset? true))))))