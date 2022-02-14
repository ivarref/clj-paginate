(ns com.github.ivarref.clj-paginate.impl.bst2
  (:import (clojure.lang IPersistentVector)))

(defn maybe-conj2! [res value keep? max-items]
  (if (or (= max-items (count res))
          (not (keep? value)))
    res
    (conj! res value)))

(defn take-all-first2
  [res [v start end] keep? max-items]
  (if (or (= max-items (count res))
          (>= start end))
    res
    (let [mid (int (/ (+ start end) 2))]
      (-> res
          (take-all-first2 [v start mid] keep? max-items)
          (maybe-conj2! (nth v mid) keep? max-items)
          (take-all-first2 [v (inc mid) end] keep? max-items)))))

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

(comment
  (let [v [1 2 3 4]
        idx (after-value-index identity
                               0
                               identity
                               (init-vector v))]
    (println "index:" idx)
    (when idx
      (nth v idx))))

(comment
  (set! *warn-on-reflection* true))

(defn get-nth [^IPersistentVector x idx]
  (.nth x idx))

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

(comment
  (let [d [[0 1 5 7]
           [3 4 9 21]]]
    (->> d
         (map init-vector)
         (map (partial after-value-index (constantly true) 2 identity))
         (after-value-take d (constantly true) identity 4))))


(defn- after-value-inner
  [res [v start end] keep? find-value sort-fn max-items]
  (if (or (= max-items (count res))
          (>= start end))
    res
    (let [mid (int (/ (+ start end) 2))
          curr-val (nth v mid)
          cmp-int (compare (sort-fn find-value) (sort-fn curr-val))]
      (cond (= 0 cmp-int)
            (take-all-first2 res [v (inc mid) end] keep? max-items)

            (neg-int? cmp-int)
            (-> res
                (after-value-inner [v start mid] keep? find-value sort-fn max-items)
                (maybe-conj2! curr-val keep? max-items)
                (take-all-first2 [v (inc mid) end] keep? max-items))

            :else
            (after-value-inner res [v (inc mid) end] keep? find-value sort-fn max-items)))))

(defn after-value-single
  [v keep? find-value sort-fn max-items]
  (persistent! (after-value-inner
                 (transient [])
                 (init-vector v)
                 keep?
                 find-value
                 sort-fn
                 max-items)))

(comment
  (persistent!
    (after-value-inner
      (transient [])
      (init-vector [{:inst 0} {:inst 1} {:inst 2} {:inst 3}])
      (constantly true)
      {:inst 1}
      (juxt :inst)
      10)))

(defn from-beginning
  [vecs keep? sort-attrs max-items]
  (->> vecs
       (map #(take max-items (filter keep? %)))
       (reduce into [])
       (sort-by (apply juxt sort-attrs))
       (take max-items)
       vec))

(defn after-value [vecs keep? value sort-attrs max-items]
  (let [sort-fn (apply juxt sort-attrs)
        vecs (mapv #(after-value-single % keep? value sort-fn max-items) vecs)]
    (->> vecs
         (reduce into [])
         (sort-by sort-fn)
         (take max-items)
         (vec))))

(defn- after-value-inner
  [res [v start end] keep? find-value sort-fn max-items]
  (if (or (= max-items (count res))
          (>= start end))
    res
    (let [mid (int (/ (+ start end) 2))
          curr-val (nth v mid)
          cmp-int (compare (sort-fn find-value) (sort-fn curr-val))]
      (cond (= 0 cmp-int)
            (take-all-first2 res [v (inc mid) end] keep? max-items)

            (neg-int? cmp-int)
            (-> res
                (after-value-inner [v start mid] keep? find-value sort-fn max-items)
                (maybe-conj2! curr-val keep? max-items)
                (take-all-first2 [v (inc mid) end] keep? max-items))

            :else
            (after-value-inner res [v (inc mid) end] keep? find-value sort-fn max-items)))))

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


(comment
  (exclusion-count
    (init-vector [{:inst 0} {:inst 1} {:inst 5} {:inst 7}])
    (fn [{:keys [inst]}] (< inst 10))))

(comment
  (def v (->> (range 1000)
              (mapv #(assoc {} :inst %)))))

(comment
  (after-value
    [[{:inst 0} {:inst 1} {:inst 5} {:inst 7}]
     [{:inst 3} {:inst 4} {:inst 9} {:inst 21}]]
    (constantly true)
    {:inst 1}
    [:inst]
    10))