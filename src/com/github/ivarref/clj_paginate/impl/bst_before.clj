(ns com.github.ivarref.clj-paginate.impl.bst-before
  (:import (clojure.lang IPersistentVector)))

(defn get-nth [^IPersistentVector x idx]
  (.nth x idx))

(defn init-vector [v]
  [v 0 (count v)])

(defn contains-index [^IPersistentVector v idx]
  (when (and (< idx (count ^IPersistentVector v))
             (>= idx 0))
    idx))


(defn before-value-index
  [find-value compare-fn inclusive? [v start end]]
  (if (>= start end)
    (if (and (= start end)
             (= end (count v)))
      (dec end)
      nil)
    (let [mid (int (/ (+ start end) 2))
          curr-val (nth v mid)
          cmp-int (compare-fn find-value curr-val)]
      (cond (= 0 cmp-int)
            (contains-index v (if inclusive? mid (dec mid)))

            (neg-int? cmp-int)
            (before-value-index find-value compare-fn inclusive? [v start mid])

            :else
            (or (before-value-index find-value compare-fn inclusive? [v (inc mid) end])
                (contains-index v mid))))))

(defrecord tmpres [v vidx idx])

(defn before-value-take [^IPersistentVector vecs compare-fn max-items starting-indexes]
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
                                         (if (or (nil? res)
                                                 (pos-int? (compare-fn v (.-v res))))
                                           (recur (inc vec-idx) (tmpres. v vec-idx idx))
                                           (recur (inc vec-idx) res))))))))]
            (recur (conj! res (.-v v))
                   (assoc indexes (.-vidx v) (contains-index (get-nth vecs (.-vidx v))
                                                             (dec (.-idx v)))))
            (persistent! res)))))))

(defn before-value [vecs from-value compare-fn max-items inclusive?]
  (->> vecs
       (mapv init-vector)
       (mapv (partial before-value-index from-value compare-fn inclusive?))
       (before-value-take vecs compare-fn max-items)))

(defn from-end
  [vecs compare-fn max-items]
  (before-value-take vecs compare-fn max-items (mapv (fn [v]
                                                       (when (>= (count v) 1)
                                                         (dec (count v))))
                                                     vecs)))
