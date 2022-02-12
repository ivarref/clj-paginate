(ns com.github.ivarref.clj-paginate.impl.bst2)

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