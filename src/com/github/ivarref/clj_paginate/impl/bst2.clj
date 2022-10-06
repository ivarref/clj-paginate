(ns com.github.ivarref.clj-paginate.impl.bst2
  (:import (clojure.lang IPersistentVector)))

(defn get-nth [^IPersistentVector x idx]
  (.nth x idx))

(defn init-vector [v]
  [v 0 (count v)])

(defn contains-index [^IPersistentVector v idx]
  (when (and (< idx (count ^IPersistentVector v))
             (>= idx 0))
    idx))

(defn after-value-index
  [find-value compare-fn inclusive? [v start end]]
  (if (>= start end)
    nil
    (let [mid (int (/ (+ start end) 2))
          curr-val (nth v mid)
          cmp-int (compare-fn find-value curr-val)]
      (cond (= 0 cmp-int)
            (contains-index v (if inclusive? mid (inc mid)))

            (neg-int? cmp-int)
            (or (after-value-index find-value compare-fn inclusive? [v start mid])
                (contains-index v mid))

            :else
            (after-value-index find-value compare-fn inclusive? [v (inc mid) end])))))


(defrecord tmpres [v vidx idx])


(defn after-value-take [^IPersistentVector vecs compare-fn max-items starting-indexes]
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
                                       (if (or (nil? res)
                                               (neg-int? (compare-fn v (.-v res))))
                                         (recur (inc vec-idx) (tmpres. v vec-idx idx))
                                         (recur (inc vec-idx) res))))))))]
          (recur (conj! res (.-v v))
                 (assoc indexes (.-vidx v) (contains-index (get-nth vecs (.-vidx v))
                                                           (inc (.-idx v)))))
          (persistent! res)))))

(defn after-value2 [vecs from-value compare-fn max-items inclusive?]
  (->> vecs
       (mapv init-vector)
       (mapv (partial after-value-index from-value compare-fn inclusive?))
       (after-value-take vecs compare-fn max-items)))

(defn from-beginning
  [vecs compare-fn max-items]
  (after-value-take vecs compare-fn max-items (mapv (fn [v] (if (empty? v) nil 0)) vecs)))


(defn total-count [vecs]
  (->> vecs
       (map (fn [v] (count v)))
       (reduce + 0)))
