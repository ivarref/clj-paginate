(ns com.github.ivarref.clj-paginate.global-sort-test
  (:require [clojure.test :refer [deftest is]]
            [com.github.ivarref.clj-paginate.impl.pag-last-map :as pl]
            [com.github.ivarref.clj-paginate.impl.pag-first-map :as pf]
            [clojure.set :as set]
            [com.github.ivarref.clj-paginate.impl.utils :as u])
  (:import (clojure.lang IDeref IFn ILookup)))


(defn nodes [res]
  (mapv :node (get res :edges)))


(defn after [res]
  (->> res
       :pageInfo
       :endCursor))


(defn before [res]
  (->> res
       :pageInfo
       :startCursor))


(defn pagg2 [data sort-attrs fetch-opts]
  (let [data (atom data)
        fetch-opts (atom fetch-opts)
        conn (atom nil)]
    (reify
      ILookup
      (valAt [_ attr]
        (cond (contains? #{:hasNextPage :hasPrevPage :totalCount :endCursor :startCursor} attr)
              (get-in @conn [:pageInfo attr])

              (= :after attr)
              (after @conn)

              (= :before attr)
              (before @conn)

              :else
              (get @conn attr)))
      IFn
      (invoke [this s]
        (cond (vector? s)
              (do (reset! data s)
                  this)))
      IDeref
      (deref [_]
        (when (:first @fetch-opts)
          (reset! conn (pf/paginate-first
                         {"default" @data}
                         (merge
                           {:sort-attrs sort-attrs}
                           (set/rename-keys @fetch-opts {:first :max-items}))
                         (when-let [conn @conn]
                           (after conn)))))
        (nodes @conn)))))


(deftest paginate-groups-test
  (let [conn (pagg2
               [{:inst 1 :group :a}
                {:inst 1 :group :b}]
               [:inst :group]
               {:first 2})]
    (is (= [{:inst 1, :group :a} {:inst 1, :group :b}] @conn))
    (is (false? (:hasPrevPage conn)))
    (is (false? (:hasNextPage conn)))

    (is (= [] @conn))
    (is (true? (:hasPrevPage conn)))
    (is (false? (:hasNextPage conn)))

    (is (= [] @conn))
    (is (true? (:hasPrevPage conn)))
    (is (false? (:hasNextPage conn)))

    ; new data arrives:
    (conn [{:inst 1 :group :a}
           {:inst 1 :group :b}
           {:inst 2 :group :a}
           {:inst 2 :group :b}])

    (is (= [{:inst 2, :group :a} {:inst 2, :group :b}] @conn))
    (is (true? (:hasPrevPage conn)))
    (is (false? (:hasNextPage conn)))

    (conn [{:inst 2 :group :a}
           {:inst 2 :group :b}
           {:inst 3 :group :a}
           {:inst 3 :group :b}])

    (is (= [{:inst 3, :group :a} {:inst 3, :group :b}] @conn))
    (is (true? (:hasPrevPage conn)))
    (is (false? (:hasNextPage conn)))

    (conn [{:inst 4 :group :a}
           {:inst 4 :group :b}])

    (is (= [{:inst 4, :group :a} {:inst 4, :group :b}] @conn))
    (is (false? (:hasPrevPage conn)))
    (is (false? (:hasNextPage conn)))

    (conn [{:inst 4 :group :a}
           {:inst 5 :group :a}
           {:inst 5 :group :b}
           {:inst 6 :group :a}
           {:inst 7 :group :a}])

    (is (= [{:inst 5, :group :a} {:inst 5, :group :b}] @conn))
    (is (true? (:hasPrevPage conn)))
    (is (true? (:hasNextPage conn)))

    (is (= [{:inst 6, :group :a} {:inst 7, :group :a}] @conn))
    (is (true? (:hasPrevPage conn)))
    (is (false? (:hasNextPage conn)))))