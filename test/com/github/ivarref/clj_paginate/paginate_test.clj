(ns com.github.ivarref.clj-paginate.paginate-test
  (:require [clojure.test :refer [deftest is]]
            [com.github.ivarref.clj-paginate.impl.paginate-first :as bm]
            [com.github.ivarref.clj-paginate.impl.paginate-last :as bml]
            [com.github.ivarref.clj-paginate.stacktrace]
            [clojure.set :as set]
            [com.github.ivarref.clj-paginate.impl.utils :as u])
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


(defn pagg2 [data opts fetch-opts]
  (let [opts (atom opts)
        data (atom (u/balanced-tree data @opts))
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
              (do (reset! data (u/balanced-tree s @opts))
                  this)

              (map? s)
              (do (reset! opts s)
                  this)))
      IDeref
      (deref [_]
        (when (:first @fetch-opts)
          (reset! conn (bm/paginate-first
                         @data
                         (set/rename-keys @fetch-opts {:first :max-items})
                         (when-let [conn @conn]
                           (after conn)))))
        (when (:last @fetch-opts)
          (reset! conn (bml/paginate-last
                         @data
                         (set/rename-keys @fetch-opts {:last :max-items})
                         (when-let [conn @conn]
                           (before conn)))))
        (nodes @conn)))))


(deftest invokes-f
  (let [conn (pagg2
               (shuffle
                 [{:inst 0} {:inst 1} {:inst 2} {:inst 3}
                  {:inst 4}])
               {:sort-by [:inst]}
               {:first 2
                :f     (fn [{:keys [inst]}]
                         {:inst (inc inst)})})]
    (is (= [1 2] @conn))))


(deftest invokes-f-on-last
  (let [conn (pagg2
               (shuffle
                 [{:inst 0} {:inst 1} {:inst 2} {:inst 3}
                  {:inst 4}])
               {:sort-by [:inst]}
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
               {:sort-by [:inst]}
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
               {:sort-by [:inst]}
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


(deftest can-select-by-with-keep?-function
  (let [conn (pagg2
               (shuffle
                 [{:inst :a1 :group :a}
                  {:inst :a2 :group :a}
                  {:inst :b1 :group :b}
                  {:inst :b2 :group :b}])
               {:sort-by [:inst]}
               {:first 10
                :keep? #(= :a (:group %))})]
    (is (= [:a1 :a2] @conn))
    (is (= [] @conn))

    (conn [{:inst :a1 :group :a}
           {:inst :a2 :group :a}
           {:inst :a3 :group :a}
           {:inst :b1 :group :b}
           {:inst :b2 :group :b}])

    (is (= [:a3] @conn))
    (is (= 3 (:totalCount conn)))))


(deftest paginate-last-test
  (let [conn (pagg2
               [{:inst 1 :group :a}
                {:inst 2 :group :a}
                {:inst 3 :group :b}
                {:inst 4 :group :b}]
               {:sort-by [:inst :group]}
               {:last 3})]
    (is (= [2 3 4] @conn))
    (is (false? (:hasPrevPage conn)))
    (is (true? (:hasNextPage conn)))

    (is (= [1] @conn))
    (is (true? (:hasPrevPage conn)))
    (is (false? (:hasNextPage conn)))

    (is (= [] @conn))
    (is (true? (:hasPrevPage conn)))
    (is (false? (:hasNextPage conn)))

    (is (= [] @conn))
    (is (true? (:hasPrevPage conn)))
    (is (false? (:hasNextPage conn)))

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
               {:sort-by [:inst]}
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


