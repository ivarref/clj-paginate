(ns com.github.ivarref.clj-paginate.paginate-test
  (:require [clojure.test :refer [deftest is]]
            [com.github.ivarref.clj-paginate.impl.pag-first-map :as pf]
            [com.github.ivarref.clj-paginate.impl.pag-last-map :as pl]
            [com.github.ivarref.clj-paginate.stacktrace]
            [clojure.set :as set])
  (:import (clojure.lang ILookup IFn IDeref)))


(defn nodes [res]
  (mapv (comp :inst :node) (get res :edges)))


(defn after [res]
  (->> res
       :pageInfo
       :endCursor))


(defn before [res]
  (->> res
       :pageInfo
       :startCursor))


(defn pagg2 [data sort-attrs fetch-opts]
  (let [data (atom (vec (sort-by (apply juxt sort-attrs) data)))
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
              (do (reset! data (vec (sort-by (apply juxt sort-attrs) s)))
                  this)))

      IDeref
      (deref [_]
        (when (:first @fetch-opts)
          (reset! conn (pf/paginate-first
                         {"default" @data}
                         (merge
                           @fetch-opts
                           {:sort-attrs sort-attrs}
                           (set/rename-keys @fetch-opts {:first :max-items}))
                         (when-let [conn @conn]
                           (after conn)))))
        (when (:last @fetch-opts)
          (reset! conn (pl/paginate-last
                         {"default" @data}
                         (merge
                           @fetch-opts
                           {:sort-attrs sort-attrs}
                           (set/rename-keys @fetch-opts {:last :max-items}))
                         (when-let [conn @conn]
                           (before conn)))))
        (nodes @conn)))))


(deftest invokes-f
  (let [conn (pagg2
               (shuffle
                 [{:inst 0} {:inst 1} {:inst 2} {:inst 3} {:inst 4}])
               [:inst]
               {:first 2
                :f     (fn [{:keys [inst]}]
                         {:inst (inc inst)})})]
    (is (= [1 2] @conn))))


(deftest invokes-f-on-last
  (let [conn (pagg2
               (shuffle
                 [{:inst 0} {:inst 1} {:inst 2} {:inst 3}
                  {:inst 4}])
               [:inst]
               {:last 2
                :f     (fn [{:keys [inst]}]
                         {:inst (inc inst)})})]
    (is (= [4 5] @conn))))


(deftest invokes-f-not-necessary
  (let [cnt (atom 0)
        conn (pagg2
               (shuffle
                 [{:inst 0} {:inst 1} {:inst 2} {:inst 3}
                  {:inst 4}])
               [:inst]
               {:first 2
                :f     (fn [{:keys [inst]}]
                         (swap! cnt inc)
                         {:inst (inc inst)})})]
    (is (= [1 2] @conn))
    (is (= 2 @cnt))))


(deftest can-create-map-tree-paginator
  (let [conn (pagg2
               (shuffle
                 [{:inst 0} {:inst 1} {:inst 2} {:inst 3}
                  {:inst 4}])
               [:inst]
               {:first 2})]
    (is (= [0 1] @conn))
    (is (false? (:hasPrevPage conn)))
    (is (true? (:hasNextPage conn)))

    (is (= [2 3] @conn))
    (is (true? (:hasPrevPage conn)))
    (is (true? (:hasNextPage conn)))

    (is (= [4] @conn))
    (is (true? (:hasPrevPage conn)))
    (is (false? (:hasNextPage conn)))

    (is (= [] @conn))
    (is (true? (:hasPrevPage conn)))
    (is (false? (:hasNextPage conn)))

    (is (= [] @conn))
    (is (true? (:hasPrevPage conn)))
    (is (false? (:hasNextPage conn)))

    ; add more data
    (conn [{:inst 0} {:inst 1} {:inst 2} {:inst 3}
           {:inst 4} {:inst 5} {:inst 6} {:inst 7}])

    (is (= [5 6] @conn))
    (is (true? (:hasPrevPage conn)))
    (is (true? (:hasNextPage conn)))

    (is (= [7] @conn))
    (is (true? (:hasPrevPage conn)))
    (is (false? (:hasNextPage conn)))

    (is (= [] @conn))
    (is (true? (:hasPrevPage conn)))
    (is (false? (:hasNextPage conn)))

    (is (= [] @conn))
    (is (true? (:hasPrevPage conn)))
    (is (false? (:hasNextPage conn)))))


(deftest paginate-last-test
  (let [conn (pagg2
               [{:inst 1 :group :a}
                {:inst 2 :group :a}
                {:inst 3 :group :b}
                {:inst 4 :group :b}]
               [:inst :group]
               {:last 3})]
    (is (= [2 3 4] @conn))
    (is (true? (:hasPrevPage conn)))
    (is (false? (:hasNextPage conn)))

    (is (= [1] @conn))
    (is (false? (:hasPrevPage conn)))
    (is (true? (:hasNextPage conn)))

    (is (= [] @conn))
    (is (false? (:hasPrevPage conn)))
    (is (true? (:hasNextPage conn)))

    (is (= [] @conn))
    (is (false? (:hasPrevPage conn)))
    (is (true? (:hasNextPage conn)))

    (conn [{:inst 1 :group :a}
           {:inst 2 :group :a}
           {:inst 3 :group :a}
           {:inst 4 :group :b}
           {:inst 5 :group :b}
           {:inst 6 :group :b}])

    (is (= [] @conn))))


(deftest first-global-sorting
  (let [conn (pagg2
               [{:inst 0 :group :a}
                {:inst 2 :group :a}
                {:inst 1 :group :b}
                {:inst 3 :group :b}]
               [:inst]
               {:first 4})]
    (is (= [0 1 2 3] @conn))

    (conn [{:inst 0 :group :a}
           {:inst 2 :group :a}
           {:inst 4 :group :a}
           {:inst 6 :group :a}
           {:inst 1 :group :b}
           {:inst 3 :group :b}
           {:inst 5 :group :b}
           {:inst 7 :group :b}])
    (is (= [4 5 6 7] @conn))))


