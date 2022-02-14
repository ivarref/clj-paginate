(ns com.github.ivarref.clj-paginate.impl.bst-before
  (:import (clojure.lang IPersistentVector)))

(defn get-nth [^IPersistentVector x idx]
  (.nth x idx))

(defn init-vector [v]
  [v 0 (count v)])

(defn contains-index [^IPersistentVector v idx keep?]
  (when (and (< idx (count ^IPersistentVector v))
             (>= idx 0)
             (keep? (nth ^IPersistentVector v idx)))
    idx))


(defn before-value-index
  [keep? find-value sort-fn [v start end]]
  (if (>= start end)
    (if (and (= start end)
             (= end (count v)))
      (dec end)
      nil)
    (let [mid (int (/ (+ start end) 2))
          curr-val (nth v mid)
          cmp-int (compare (sort-fn find-value) (sort-fn curr-val))]
      (cond (= 0 cmp-int)
            (contains-index v (dec mid) keep?)

            (neg-int? cmp-int)
            (before-value-index keep? find-value sort-fn [v start mid])

            :else
            (or (before-value-index keep? find-value sort-fn [v (inc mid) end])
                (contains-index v mid keep?))))))

(comment
  (let [v [0 1 2 3 4 5 6 7 8 9]
        start-index (before-value-index
                      (constantly true)
                      0
                      identity
                      (init-vector v))]
    (println "start-index: " start-index)
    (when start-index
      (println "value:" (nth v start-index)))))