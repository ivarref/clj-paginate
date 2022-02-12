(ns com.github.ivarref.clj-paginate.perf2-test
  (:require [clojure.test :refer [deftest is]]
            [com.github.ivarref.clj-paginate.ticker :as ticker]
            [com.github.ivarref.clj-paginate.impl.pag-first-map :as pm])
  (:import (java.lang AutoCloseable)))


(deftest perftest2
  (let [n 10e6
        total-vec (vec (range n))
        items (atom total-vec)
        eat! (fn [n]
               (let [r (take n @items)]
                 (swap! items (fn [old-items] (vec (drop n old-items))))
                 (mapv #(assoc {} :inst %) r)))
        data {"1" (eat! 1e6)
              "2" (eat! 1e6)
              "3" (eat! 1e6)
              "4" (eat! 1e6)
              "5" (eat! 1e6)
              "6" (eat! 1e6)
              "7" (eat! 1e6)
              "8" (eat! 1e6)
              "9" (eat! 1e6)
              "10" (eat! 1e6)}
        opts {:sort-attrs [:inst]
              :max-items 1000}
        all-items (with-open [^AutoCloseable tick (ticker/ticker n)]
                    (loop [so-far []
                           conn (pm/paginate-first data opts nil)]
                      (if-let [edges (not-empty (:edges conn))]
                        (do
                          (tick (count edges))
                          (recur (into so-far (mapv (comp :inst :node) edges))
                                 (pm/paginate-first data opts (:cursor (last edges)))))
                        so-far)))]
    (is (= all-items total-vec))))