(ns com.github.ivarref.clj-paginate.impl.bst)

; based on https://gist.github.com/dmh43/83a7b3e452e83e80eb30

(defrecord Node [v start end])

(defn valid? [^Node node]
  (when node
    (when instance? Node node
                    (if (>= (.-start node) (.-end node))
                      false
                      true))))

(defn mid [^Node node]
  (int (/ (+ (.-start node) (.-end node)) 2)))

(defn value [^Node node]
  (when (valid? node)
    (nth (.-v node) (mid node))))

(defn new-if-valid [v start end]
  (if (>= start end)
    nil
    (Node. v start end)))

(defn left [^Node node]
  (when (valid? node)
    (new-if-valid (.-v node) (.-start node) (mid node))))

(defn right [^Node node]
  (when (valid? node)
    (new-if-valid (.-v node) (inc (mid node)) (.-end node))))

(defn node? [x]
  (when x
    (instance? Node x)))

(defn depth-first-vals
  ([root]
   (persistent! (depth-first-vals (transient []) root)))
  ([res root]
   (if (nil? root)
     res
     (-> res
         (depth-first-vals (left root))
         (conj! (value root))
         (depth-first-vals (right root))))))


(defn visit-all-depth-first
  [root keep? f]
  (if (nil? root)
    nil
    (do
      (visit-all-depth-first (left root) keep? f)
      (when (keep? (value root))
        (f (value root)))
      (visit-all-depth-first (right root) keep? f))))


(defn balanced-tree [sorted-vec]
  (new-if-valid sorted-vec 0 (count sorted-vec)))


(defn tree-vals [node]
  (if (nil? node)
    []
    (reduce into [(value node)]
            [(tree-vals (left node))
             (tree-vals (right node))])))


(defn maybe-conj! [res root keep? max-items]
  (if (or (nil? root)
          (= max-items (count res))
          (not (keep? (value root))))
    res
    (conj! res (value root))))


(defn maybe-conj2! [res value keep? max-items]
  (if (or (= max-items (count res))
          (not (keep? value)))
    res
    (conj! res value)))


(defn take-all-last
  [res root keep? max-items]
  (if (or (= max-items (count res))
          (nil? root))
    res
    (-> res
        (take-all-last (right root) keep? max-items)
        (maybe-conj! root keep? max-items)
        (take-all-last (left root) keep? max-items))))


(defn cmp-attrs [sort-attrs a b]
  (let [jxt (apply juxt sort-attrs)]
    (compare (jxt a) (jxt b))))


(defn take-all-first
  [res root keep? max-items]
  (if (or (= max-items (count res))
          (nil? root))
    res
    (-> res
        (take-all-first (left root) keep? max-items)
        (maybe-conj! root keep? max-items)
        (take-all-first (right root) keep? max-items))))

(defn take-all-first2
  [res v start end keep? max-items]
  (if (or (= max-items (count res))
          (>= start end))
    res
    (let [mid (int (/ (+ start end) 2))]
      (-> res
          (take-all-first2 v start mid keep? max-items)
          (maybe-conj2! (nth v mid) keep? max-items)
          (take-all-first2 v (inc mid) end keep? max-items)))))


(defn- after-value-inner
  [res v start end keep? find-value sort-fn max-items]
  (if (or (= max-items (count res))
          (>= start end))
    res
    (let [mid (int (/ (+ start end) 2))
          curr-val (nth v mid)
          cmp-int (compare (sort-fn find-value) (sort-fn curr-val))]
      (cond (= 0 cmp-int)
            (take-all-first2 res v (inc mid) end keep? max-items)

            (neg-int? cmp-int)
            (-> res
                (after-value-inner v start mid keep? find-value sort-fn max-items)
                (maybe-conj2! curr-val keep? max-items)
                (take-all-first2 v (inc mid) end keep? max-items))

            :else
            (after-value-inner res v (inc mid) end keep? find-value sort-fn max-items)))))


(defn after-value
  [root keep? from-value sort-attrs max-items]
  (persistent! (after-value-inner (transient []) (.-v root) 0 (count (.-v root)) keep? from-value (apply juxt sort-attrs) max-items)))


(defn- before-value-inner
  [res root keep? find-value sort-fn max-items]
  (if (or (= max-items (count res))
          (nil? root))
    res
    (let [curr-val (value root)
          cmp-int (compare (sort-fn find-value) (sort-fn curr-val))]
      #_(println (sort-fn find-value) "vs" (sort-fn curr-val) "=>" cmp-int)
      (cond (= 0 cmp-int)
            (take-all-last res (left root) keep? max-items)

            (pos-int? cmp-int)                              ; find-value > curr-val
            (-> res
                (before-value-inner (right root) keep? find-value sort-fn max-items)
                (maybe-conj! root keep? max-items)
                (take-all-last (left root) keep? max-items))

            :else                                           ; find-value < curr-val
            (before-value-inner res (left root) keep? find-value sort-fn max-items)))))


(defn before-value
  [root keep? from-value sort-attrs max-items]
  (vec (reverse (persistent! (before-value-inner (transient []) root keep? from-value (apply juxt sort-attrs) max-items)))))


(defn tree-contains?
  [root v sort-attrs]
  (if (nil? root)
    false
    (let [cmp-int (cmp-attrs sort-attrs v (value root))]
      (cond (= 0 cmp-int)
            true

            (pos-int? cmp-int)
            (tree-contains? (right root) v sort-attrs)

            :else
            (tree-contains? (left root) v sort-attrs)))))


(defn get-leftmost-value
  [root keep?]
  (if (nil? root)
    nil
    (or
      (get-leftmost-value (left root) keep?)
      (when (keep? (value root)) (value root))
      (get-leftmost-value (right root) keep?))))


(defn get-rightmost-value
  [root keep?]
  (if (nil? root)
    nil
    (or
      (get-rightmost-value (right root) keep?)
      (when (keep? (value root)) (value root))
      (get-rightmost-value (left root) keep?))))


(defn from-beginning
  ([root keep? max-items]
   (persistent! (from-beginning (transient []) root keep? max-items)))
  ([res root keep? max-items]
   (cond (or (nil? root)
             (= max-items (count res)))
         res

         (nil? (left root))
         (-> res
             (maybe-conj! root keep? max-items)
             (take-all-first (right root) keep? max-items))

         :else
         (-> (from-beginning res (left root) keep? max-items)
             (maybe-conj! root keep? max-items)
             (take-all-first (right root) keep? max-items)))))


(defn from-end
  ([root keep? max-items]
   (-> (transient [])
       (from-end root keep? max-items)
       (persistent!)
       (reverse)
       (vec)))
  ([res root keep? max-items]
   (cond (or (nil? root)
             (= max-items (count res)))
         res

         (nil? (right root))
         (-> res
             (maybe-conj! root keep? max-items)
             (take-all-last (left root) keep? max-items))

         :else
         (-> res
             (from-end (right root) keep? max-items)
             (maybe-conj! root keep? max-items)
             (take-all-last (left root) keep? max-items)))))