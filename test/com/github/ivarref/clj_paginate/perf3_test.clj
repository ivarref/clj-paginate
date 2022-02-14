(ns com.github.ivarref.clj-paginate.perf3-test
  (:require [clojure.test :refer [deftest is]]
            [com.github.ivarref.clj-paginate.ticker :as ticker]
            [com.github.ivarref.clj-paginate.impl.pag-last-map :as pm])
  (:import (java.lang AutoCloseable)
           (java.util ArrayList Random Collections Collection)
           (clojure.lang RT)))

(defn deterministic-shuffle
  [^Collection coll seed]
  (let [al (ArrayList. coll)
        rng (Random. seed)]
    (Collections/shuffle al rng)
    (RT/vector (.toArray al))))

(deftest perftest3
  (let [n 10
        total-n (* n 10)
        total-vec (deterministic-shuffle (vec (range total-n)) 789)
        items (atom total-vec)
        eat! (fn []
               (let [r (take (int n) @items)]
                 (swap! items (fn [old-items] (vec (drop (int n) old-items))))
                 (mapv #(assoc {} :inst %) (sort r))))
        data {"1" (eat!)
              "2" (eat!)
              "3" (eat!)
              "4" (eat!)
              "5" (eat!)
              "6" (eat!)
              "7" (eat!)
              "8" (eat!)
              "9" (eat!)
              "10" (eat!)}
        opts {:sort-attrs [:inst]
              :max-items 1000}
        all-items (with-open [^AutoCloseable tick (ticker/ticker total-n)]
                    (loop [so-far []
                           conn (pm/paginate-last data opts nil)]
                      (if-let [edges (not-empty (:edges conn))]
                        (do
                          (tick (count edges))
                          (recur (into (mapv (comp :inst :node) edges) so-far)
                                 (pm/paginate-last data opts (:cursor (first edges)))))
                        so-far)))]
    (println all-items)
    (is (= all-items (vec (range total-n))))))