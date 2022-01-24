(ns com.github.ivarref.clj-paginate.ticker
  (:require [clojure.string :as str])
  (:import (clojure.lang IFn)
           (java.lang AutoCloseable)))

(def is-cursive?
  (try
    (require '[cursive.repl.runtime])
    true
    (catch Exception _
      false)))

(defn ticker [total]
  (let [total (long total)
        start-time (atom (System/nanoTime))
        last-tick (atom 0)
        cnt (atom 0)
        inv-count (atom 0)]
    (reify
      AutoCloseable
      (close [_]
        (when-not is-cursive?
          (println "")))
      IFn
      (invoke [_ n]
        (let [n (long n)
              now (System/nanoTime)
              inv-count (swap! inv-count inc)
              spent-time (- now @start-time)
              time-since-last-tick (double (/ (- now @last-tick) 1e6))
              [old new-count] (swap-vals! cnt (fn [old] (+ old n)))
              cr (if is-cursive?
                   (str \u001b "[1K")
                   "\r")
              clear-to-end-of-line (str \u001b "[0K")
              width 40
              fill-count (int (Math/ceil (* width
                                            (/ new-count total))))]
          (when (or (and (false? is-cursive?)
                         (>= time-since-last-tick 333))
                    (>= time-since-last-tick 1000)
                    (= new-count total))
            (reset! last-tick now)
            (let [s (str (when (and (false? is-cursive?) (not= old 0)) cr)
                         "["
                         (str/join "" (repeat fill-count "#"))
                         (str/join "" (repeat (- width fill-count) "."))
                         "] "
                         (format "%.0f%% done" (double (/ (* 100 new-count) total)))
                         ", "
                         (format "%.0f µs/iter" (double (/ (/ spent-time 1000) inv-count)))
                         clear-to-end-of-line)]
              (if is-cursive?
                (println s)
                (do
                  (print s)
                  (flush)))))
          nil)))))