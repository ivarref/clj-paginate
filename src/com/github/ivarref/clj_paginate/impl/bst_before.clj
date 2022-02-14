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

(defrecord tmpres [v vidx idx])

(defn before-value-take [^IPersistentVector vecs keep? sort-fn max-items starting-indexes]
  (vec
    (reverse
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
                                                      (pos-int? (compare (sort-fn v) (sort-fn (.-v res))))))
                                           (recur (inc vec-idx) (tmpres. v vec-idx idx))
                                           (recur (inc vec-idx) res))))))))]
            (recur (conj! res (.-v v))
                   (assoc indexes (.-vidx v) (contains-index (get-nth vecs (.-vidx v))
                                                             (dec (.-idx v))
                                                             keep?)))
            (persistent! res)))))))

(defn before-value [vecs keep? from-value sort-fn max-items]
  (->> vecs
       (mapv init-vector)
       (mapv (partial before-value-index keep? from-value sort-fn))
       (before-value-take vecs keep? sort-fn max-items)))

(defn from-end
  [vecs keep? sort-fn max-items]
  (before-value-take vecs keep? sort-fn max-items (mapv (fn [v]
                                                          (when (>= (count v) 1)
                                                            (dec (count v))))
                                                        vecs)))

(comment
  (before-value
    [[0 2 4 6]
     [1 3 5 7]]
    (constantly true)
    100
    identity
    3))

(comment
  (from-end [[1 2 3 4 5]]
            (constantly true)
            identity
            3))

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