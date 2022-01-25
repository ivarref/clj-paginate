(ns com.github.ivarref.clj-paginate.perf-test
  (:require [clojure.test :refer [deftest is]]
            [com.github.ivarref.clj-paginate.paginate-test :as bmt]
            [com.github.ivarref.clj-paginate.ticker :as ticker]))

(deftest perftest
  (let [n 1e6
        total-vec (vec (range n))
        conn (bmt/pagg2 (mapv #(assoc {} :inst %) total-vec)
                        {:sort-by [:inst]}
                        {:first 2})
        all-items (with-open [tick (ticker/ticker n)]
                    (loop [so-far []]
                      (if-let [new-items (not-empty @conn)]
                        (do
                          (tick (count new-items))
                          (recur (into so-far new-items)))
                        so-far)))]
    (is (= all-items total-vec))))

(comment
  (do
    (require '[clj-async-profiler.core :as prof])
    (prof/profile
      (clojure.test/test-var #'perftest))))

(comment
  (prof/serve-files 8080))