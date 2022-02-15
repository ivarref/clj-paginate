(ns com.github.ivarref.clj-paginate.perf-test
  (:require [clojure.test :refer [deftest is]]
            [com.github.ivarref.clj-paginate.paginate-test :as bmt]
            [com.github.ivarref.clj-paginate.ticker :as ticker])
  (:import (java.lang AutoCloseable)))

(deftest perftest
  (let [n 10e5
        total-vec (vec (range n))
        conn (bmt/pagg2 (mapv #(assoc {} :inst %) total-vec)
                        [:inst]
                        {:first 1000})
        all-items (with-open [^AutoCloseable tick (ticker/ticker n)]
                    (loop [so-far []]
                      (if-let [new-items (not-empty @conn)]
                        (do
                          (tick (count new-items))
                          (recur (into so-far new-items)))
                        so-far)))]
    (is (= all-items total-vec))))

(comment
  (do
    ; echo 1 | sudo tee /proc/sys/kernel/perf_event_paranoid
    (require '[clj-async-profiler.core :as prof])
    (prof/profile
      (clojure.test/test-var #'perftest))))

(comment
  (do
    (require '[clj-async-profiler.core :as prof])
    (doseq [f (->> (file-seq (clojure.java.io/file "/tmp/clj-async-profiler/results"))
                   (remove #(.isDirectory %)))]
      (.delete f))
    (prof/serve-files 8080)))

(comment
  (def vv (time (mapv #(assoc {} :inst %) (range 10e6)))))

(comment
  (require '[com.github.ivarref.clj-paginate :as pg])
  (time (pg/prepare-paginate)))
