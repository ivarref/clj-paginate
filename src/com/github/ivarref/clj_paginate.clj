(ns com.github.ivarref.clj-paginate
  (:require [com.github.ivarref.clj-paginate.impl.paginate-first :as pf]
            [com.github.ivarref.clj-paginate.impl.paginate-last :as pl]
            [com.github.ivarref.clj-paginate.impl.bst :as bst]
            [com.github.ivarref.clj-paginate.impl.utils :as u]))


(defn get-context [opts]
  (:context (u/get-cursor opts)))


(defn prepare-paginate
  [opts coll]
  (u/balanced-tree coll opts))


(defn paginate
  [prepared f opts & {:keys [keep? context] :or {keep? (constantly true) context {}}}]
  (assert (fn? f) "Expected f to be a function")
  (assert (fn? keep?) "Expected keep? to be a function")
  (assert (map? opts) "Expected opts to be a map")
  (let [cursor-str (or (get opts :after) (get opts :before))]
    (cond
      (and (some? (get opts :first)) (some? (get opts :last)))
      (throw (ex-info "Both :first and :last given, don't know what to do." {:opts opts}))

      (and (some? (get opts :first)) (some? (get opts :before)))
      (throw (ex-info ":first and :before given, please use :first with :after" {:opts opts}))

      (and (some? (get opts :last)) (some? (get opts :after)))
      (throw (ex-info ":last and :after given, please use :last with :before" {:opts opts}))

      (nil? (:root prepared))
      {:edges    []
       :pageInfo {:hasNextPage false
                  :hasPrevPage false
                  :startCursor (pr-str {:context context})
                  :endCursor   (pr-str {:context context})
                  :totalCount  0}}

      (false? (bst/node? (:root prepared)))
      (throw (ex-info "Expected input parameter `prepared` to be of type com.github.ivarref.clj-paginate.impl.bst Node, please use com.github.ivarref.clj-paginate/prepare-paginate" {:prepared prepared}))

      (pos-int? (get opts :first))
      (pf/paginate-first prepared
                         {:max-items (get opts :first)
                          :f         f
                          :keep?     keep?
                          :context   context}
                         cursor-str)

      (pos-int? (get opts :last))
      (pl/paginate-last prepared
                        {:max-items (get opts :last)
                         :f         f
                         :keep?     keep?
                         :context   context}
                        cursor-str)

      :else
      (throw (ex-info "Bad opts given, expected either :first or :last to be a positive integer." {:opts opts})))))


(defn batch-paginate
  [prepared batch-f opts & {:keys [keep? context] :or {keep? (constantly true) context {}}}]
  (assert (fn? batch-f) "Expected batch-f to be a function")
  (assert (fn? keep?) "Expected keep? to be a function")
  (assert (map? opts) "Expected opts to be a map")
  (let [cursor-str (or (get opts :after) (get opts :before))]
    (cond
      (and (some? (get opts :first)) (some? (get opts :last)))
      (throw (ex-info "Both :first and :last given, don't know what to do." {:opts opts}))

      (and (some? (get opts :first)) (some? (get opts :before)))
      (throw (ex-info ":first and :before given, please use :first with :after" {:opts opts}))

      (and (some? (get opts :last)) (some? (get opts :after)))
      (throw (ex-info ":last and :after given, please use :last with :before" {:opts opts}))

      (nil? (:root prepared))
      {:edges    []
       :pageInfo {:hasNextPage false
                  :hasPrevPage false
                  :startCursor (pr-str {:context context})
                  :endCursor   (pr-str {:context context})
                  :totalCount  0}}

      (false? (bst/node? (:root prepared)))
      (throw (ex-info "Expected input parameter `prepared` to be of type com.github.ivarref.clj-paginate.impl.bst Node, please use com.github.ivarref.clj-paginate/prepare-paginate" {:prepared prepared}))

      (pos-int? (get opts :first))
      (pf/paginate-first prepared
                         {:max-items (get opts :first)
                          :batch-f   batch-f
                          :keep?     keep?
                          :context   context}
                         cursor-str)

      (pos-int? (get opts :last))
      (pl/paginate-last prepared
                        {:max-items (get opts :last)
                         :batch-f   batch-f
                         :keep?     keep?
                         :context   context}
                        cursor-str)

      :else
      (throw (ex-info "Bad opts given, expected either :first or :last to be a positive integer." {:opts opts})))))


(defn ensure-order [src-vec dst-vec & {:keys [sif dif] :or {sif :id dif :id}}]
  (assert (vector? src-vec) "src-vec must be a vector")
  (assert (vector? dst-vec) "dst-vec must be a vector")
  (let [ks (mapv (fn [node]
                   (if-some [id (sif node)]
                     id
                     (throw (ex-info "No key value for src node" {:sif sif :node node}))))
                 src-vec)
        m (persistent!
            (reduce (fn [o node]
                      (assoc! o (if-some [id (dif node)]
                                  id
                                  (throw (ex-info "No key value for dst node" {:dif dif :node node}))) node))
                    (transient {})
                    dst-vec))]
    (persistent!
      (reduce
        (fn [res k]
          (when-not (contains? m k)
            (throw (ex-info (str "Missing key " k) {:key k})))
          (conj! res (get m k)))
        (transient [])
        ks))))
