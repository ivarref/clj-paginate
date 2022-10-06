(ns com.github.ivarref.clj-paginate.perf2-test
  (:require [clojure.test :refer [deftest is]]
            [com.github.ivarref.clj-paginate.ticker :as ticker]
            [com.github.ivarref.clj-paginate.impl.pag-first-map :as pm])
  (:import (java.lang AutoCloseable)
           (java.util ArrayList Random Collections Collection)
           (clojure.lang RT)))

(defn deterministic-shuffle
  [^Collection coll seed]
  (let [al (ArrayList. coll)
        rng (Random. seed)]
    (Collections/shuffle al rng)
    (RT/vector (.toArray al))))

(deftest perftest2
  (let [n 1e5
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
                           conn (pm/paginate-first data opts nil)]
                      (if-let [edges (not-empty (:edges conn))]
                        (do
                          (tick (count edges))
                          (recur (into so-far (mapv (comp :inst :node) edges))
                                 (pm/paginate-first data opts (:cursor (last edges)))))
                        so-far)))]
    (is (= all-items (vec (range total-n))))))
; [########################################] 100% done, 2383 µs/iter

(comment
  (do
    ; echo 1 | sudo tee /proc/sys/kernel/perf_event_paranoid
    (require '[clj-async-profiler.core :as prof])
    #_(prof/serve-files 8080)
    (doseq [f (->> (file-seq (clojure.java.io/file "/tmp/clj-async-profiler/results"))
                   (remove #(.isDirectory %)))]
      (.delete f))
    (prof/profile (clojure.test/test-var #'perftest2))))

(comment
  (prof/serve-files 8080))
