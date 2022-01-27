(ns com.github.ivarref.clj-paginate
  (:require [com.github.ivarref.clj-paginate.impl.paginate-first :as pf]
            [com.github.ivarref.clj-paginate.impl.paginate-last :as pl]
            [com.github.ivarref.clj-paginate.impl.bst :as bst]
            [com.github.ivarref.clj-paginate.impl.utils :as u]))


(defn get-context
  "Gets the :context from a previous invocation of paginate as stored in :before or :after"
  [opts]
  (:context (u/get-cursor opts)))


(defn prepare-paginate
  "Prepares data for pagination. Coll should be a set or a vector.

  Opts must contain :sort-by, which should be a vector of keywords
  that determine the ordering of coll."
  [opts coll]
  (u/balanced-tree coll opts))


(defn paginate
  "Paginates the prepared data. Returns a map of
  {:edges [{:node { ...}
            :cursor ...}
           {:node { ...}
            :cursor ...}
           ...]
   :pageInfo {:hasNextPage Boolean
              :hasPrevPage Boolean
              :totalCount Integer
              :startCursor String
              :endCursor String}}

  Required parameters:
  prepared: The previously prepared data from prepare-paginate.

  f: Invoked on each node. Invoked once on all nodes if :batch? is true.

  opts: A map that should contain :first or :last, as well as optionally :after or :before.

  Optional named parameters:
  :filter: For a node to be included, this function must return truthy, i.e. any value except nil and false.
           Defaults to (constantly true).

  :context: User-defined data to store in every cursor. Must be pr-str-able.
            Defaults to {}.

  :batch?: Set to true if f should be invoked once on all nodes,
           and not once for each node. If this is set to true,
           f must return the output nodes in the same order as the input nodes.
           Defaults to false."
  [prepared f opts & {:keys [filter context batch?] :or {filter (constantly true) context {} batch? false}}]
  (assert (fn? f) "Expected f to be a function")
  (assert (fn? filter) "Expected keep? to be a function")
  (assert (map? opts) "Expected opts to be a map")
  (let [f-map (if batch?
                {:batch-f f}
                {:f f})
        cursor-str (or (get opts :after) (get opts :before))]
    (binding [*print-dup* false
              *print-meta* false
              *print-readably* true
              *print-length* nil
              *print-level* nil
              *print-namespace-maps* false]
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
                           (merge f-map
                                  {:max-items (get opts :first)
                                   :keep?     filter
                                   :context   context})
                           cursor-str)

        (pos-int? (get opts :last))
        (pl/paginate-last prepared
                          (merge f-map
                                 {:max-items (get opts :last)
                                  :keep?     filter
                                  :context   context})
                          cursor-str)

        :else
        (throw (ex-info "Bad opts given, expected either :first or :last to be a positive integer." {:opts opts}))))))


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
