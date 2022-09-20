(ns com.github.ivarref.clj-paginate
  (:require [com.github.ivarref.clj-paginate.impl.pag-first-map :as pf]
            [com.github.ivarref.clj-paginate.impl.pag-last-map :as pl]
            [com.github.ivarref.clj-paginate.impl.utils :as u]))


(defn paginate
  "Required parameters
  ===================
  data: The data to paginate. Must be either a vector or a map with vectors as values.
        All vectors must be sorted based on `node-id-attrs` or `:sort-fn`.
        The actual nodes in the vectors must be maps.

  node-id-attrs: Attributes that gives a unique identifier of a node.
                 Should be a single keyword or a vector of keywords.
                 This value represents what attributes the vector is sorted by,
                 but does not contain information about descending or ascending order.
                 Evaluating `((apply juxt node-id-attrs) node)` must give a unique identifier for the node.
                 This value will be stored in the cursor.

  f: Invoked on each node.
     Invoked a single time on all nodes if :batch? is true.

  opts: A map specifying what data is to be fetched.
        It must contain either:
        :first: an int specifying how many nodes to fetch,
        starting at the beginning of the data.

        Or:
        :last: an int specifying how many nodes to fetch,
        starting at the end of the data.

        The cursor, i.e. where to continue fetching data from
        on subsequent queries, should be given as a string
        in :after if :first is used, or :before if :last is used.

        Opts may also contain `:filter`, which, if present, should be a collection of
        keys to filter the data map on. The filter value will be persisted in
        the cursor string, and will automatically be used on subsequent queries.
        If `:filter` is not specified, no filtering is done.
        Thus the default behaviour is to include everything.


  Optional named parameters
  =========================
  :sort-fn: Supply a custom sorting function for how the data was sorted.
            This allows the data to be sorted descending based on some attribute,
            e.g. you may pass `(juxt (comp - :foo) :bar)` for `:sort-fn`
            while `:node-id-attrs` is given as `[:foo :bar]`.
            Defaults to `(apply juxt node-id-attrs)`, i.e. ascending sorting
            for all attributes.

  :context: User-defined data to store in every cursor. Must be pr-str-able.
            Defaults to {}. Can be retrieved on subsequent queries using
            `(get-context ...)`.

  :batch?: Set to true if f should be invoked a single time on all nodes,
           and not once for each node. If this is set to true,
           f must return the output nodes in the same order as the input nodes.
           Please see the `ensure-order` function for a helper function
           that makes sure the ordering is correct.
           The default value of :batch? is false.


  Return value
  ============
  The paginated data.
  Returns a map of
    {:edges [{:node { ...} :cursor ...}
             {:node { ...} :cursor ...}
              ...]
     :pageInfo {:hasNextPage Boolean
                :hasPrevPage Boolean
                :totalCount Integer
                :startCursor String
                :endCursor String}}"
  [data node-id-attrs f opts & {:keys [context batch? sort-fn] :or {context {} batch? false}}]
  (assert (map? opts) "Expected opts to be a map")
  (let [f (if (keyword? f) (fn [node] (get node f)) f)
        _ (assert (fn? f) "Expected f to be a function")
        sort-attrs (if (keyword? node-id-attrs)
                     [node-id-attrs]
                     node-id-attrs)
        sort-fn (if sort-fn sort-fn (apply juxt sort-attrs))
        _ (assert (and (vector? sort-attrs)
                       (every? keyword? sort-attrs))
                  "Expected sort-attrs to be a single keyword or a vector of keywords")
        f-map (if batch?
                {:batch-f f}
                {:f f})
        cursor-str (or (get opts :after) (get opts :before) "")
        data (cond
               (vector? data)
               {"default" data}
               (and (map? data) (every? vector? (vals data)))
               data
               :else (throw (ex-info "Unsupported data type" {:data data})))
        filter (when-let [filter (not-empty (or (get opts :filter)
                                                (get (u/maybe-decode-cursor cursor-str) :filter)))]
                 (vec (distinct filter)))
        data (if (not-empty filter)
               (select-keys data filter)
               data)]
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

        (pos-int? (get opts :first))
        (pf/paginate-first data
                           (merge f-map
                                  {:filter     filter
                                   :max-items  (get opts :first)
                                   :context    context
                                   :sort-attrs sort-attrs
                                   :sort-fn    sort-fn})
                           cursor-str)

        (pos-int? (get opts :last))
        (pl/paginate-last data
                          (merge f-map
                                 {:filter     filter
                                  :max-items  (get opts :last)
                                  :context    context
                                  :sort-attrs sort-attrs
                                  :sort-fn    sort-fn})
                          cursor-str)

        :else
        (throw (ex-info "Bad opts given, expected either :first or :last to be a positive integer." {:opts opts}))))))


(defn ensure-order
  "Orders dst-vec according to src-vec.

  Optional named parameters `sf and `df`
  that defaults to `:id`.

  (sf source-node) must be equal to some
  (df dest-node) for one element in dst-vec."
  [src-vec dst-vec & {:keys [sf df] :or {sf :id df :id}}]
  (assert (vector? src-vec) "src-vec must be a vector")
  (assert (vector? dst-vec) "dst-vec must be a vector")
  (let [ks (mapv (fn [node]
                   (if-some [id (sf node)]
                     id
                     (throw (ex-info "No key value for src node" {:sf sf :node node}))))
                 src-vec)
        m (persistent!
            (reduce (fn [o node]
                      (assoc! o (if-some [id (df node)]
                                  id
                                  (throw (ex-info "No key value for dst node" {:df df :node node}))) node))
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


(defn get-context
  "Gets the :context from a previous invocation of paginate as stored in :before or :after"
  [opts]
  (:context (u/get-cursor opts)))
