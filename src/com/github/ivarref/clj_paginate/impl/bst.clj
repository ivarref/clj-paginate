(ns com.github.ivarref.clj-paginate.impl.bst)

; based on https://gist.github.com/dmh43/83a7b3e452e83e80eb30

(defrecord Node [value left right])

(defn make-node
  ([value left right]
   (Node. value left right))
  ([value]
   (Node. value nil nil)))

(defn node? [root]
  (when root
    (instance? Node root)))

(defn value [^Node node]
  (when node
    (.-value node)))

(defn left [^Node node]
  (when node
    (.-left node)))

(defn right [^Node node]
  (when node
    (.-right node)))

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


(defn balanced-tree
  ([sorted-vec]
   (balanced-tree sorted-vec 0 (count sorted-vec)))
  ([sorted-vec start end]
   (if (>= start end)
     nil
     (let [mid (int (/ (+ start end) 2))]
       (make-node (nth sorted-vec mid)
                  (balanced-tree sorted-vec start mid)
                  (balanced-tree sorted-vec (inc mid) end))))))


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


(defn- after-value-inner
  [res root keep? find-value sort-fn max-items]
  (if (or (= max-items (count res))
          (nil? root))
    res
    (let [curr-val (value root)
          cmp-int (compare (sort-fn find-value) (sort-fn curr-val))]
      (cond (= 0 cmp-int)
            (take-all-first res (right root) keep? max-items)

            (neg-int? cmp-int)
            (-> res
                (after-value-inner (left root) keep? find-value sort-fn max-items)
                (maybe-conj! root keep? max-items)
                (take-all-first (right root) keep? max-items))

            :else
            (after-value-inner res (right root) keep? find-value sort-fn max-items)))))


(defn after-value
  [root keep? from-value sort-attrs max-items]
  (persistent! (after-value-inner (transient []) root keep? from-value (apply juxt sort-attrs) max-items)))


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