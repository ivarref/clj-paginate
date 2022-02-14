(ns com.github.ivarref.clj-paginate.impl.bst2
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

(defn after-value-index
  [keep? find-value sort-fn [v start end]]
  (if (>= start end)
    nil
    (let [mid (int (/ (+ start end) 2))
          curr-val (nth v mid)
          cmp-int (compare (sort-fn find-value) (sort-fn curr-val))]
      (cond (= 0 cmp-int)
            (contains-index v (inc mid) keep?)

            (neg-int? cmp-int)
            (or (after-value-index keep? find-value sort-fn [v start mid])
                (contains-index v mid keep?))

            :else
            (after-value-index keep? find-value sort-fn [v (inc mid) end])))))


(defrecord tmpres [v vidx idx])


(defn after-value-take [^IPersistentVector vecs keep? sort-fn max-items starting-indexes]
  (loop [res (transient [])
         indexes starting-indexes]
    (if (= max-items (count res))
        (persistent! res)
        (if-let [^tmpres v (let [cnt (count indexes)]
                             (loop [vec-idx 0
                                    ^tmpres res nil]
                               (if (= vec-idx cnt)
                                 res
                                 (let [idx (get-nth indexes vec-idx)]
                                   (if (nil? idx)
                                     (recur (inc vec-idx) res)
                                     (let [v (get-nth (get-nth vecs vec-idx) idx)]
                                       (if (and (keep? v)
                                                (or (nil? res)
                                                    (neg-int? (compare (sort-fn v) (sort-fn (.-v res))))))
                                         (recur (inc vec-idx) (tmpres. v vec-idx idx))
                                         (recur (inc vec-idx) res))))))))]
          (recur (conj! res (.-v v))
                 (assoc indexes (.-vidx v) (contains-index (get-nth vecs (.-vidx v))
                                                           (inc (.-idx v))
                                                           keep?)))
          (persistent! res)))))

(defn after-value2 [vecs keep? from-value sort-fn max-items]
  (->> vecs
       (mapv init-vector)
       (mapv (partial after-value-index keep? from-value sort-fn))
       (after-value-take vecs keep? sort-fn max-items)))

(defn from-beginning
  [vecs keep? sort-fn max-items]
  (after-value-take vecs keep? sort-fn max-items (mapv (constantly 0) vecs)))


(defn exclusion-count [[v start end] keep?]
  (if (>= start end)
    0
    (let [mid (int (/ (+ start end) 2))
          curr-val (nth v mid)]
      (if (keep? curr-val)
        (exclusion-count [v (inc mid) end] keep?)
        (+ (- end mid)
           (exclusion-count [v start mid] keep?))))))

(defn total-count [vecs keep?]
  (->> vecs
       (map (fn [v] (- (count v) (exclusion-count (init-vector v) keep?))))
       (reduce + 0)))
