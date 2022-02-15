(ns com.github.ivarref.clj-paginate
  (:require [com.github.ivarref.clj-paginate.impl.pag-first-map :as pf]
            [com.github.ivarref.clj-paginate.impl.pag-last-map :as pl]
            [com.github.ivarref.clj-paginate.impl.utils :as u]))


(defn get-context
  "Gets the :context from a previous invocation of paginate as stored in :before or :after"
  [opts]
  (:context (u/get-cursor opts)))


(defn paginate
  "Required parameters
  ===================
  data: The data to paginate. Must be either a vector or a map with vectors as values.
        All vectors must be sorted according to `sort-attrs`.
        Elements in the vectors must be maps.

  sort-attrs: How the vectors in `data` is sorted.
              Should be a single keyword or a vector of keywords.

  f: Invoked on each node. Invoked once on all nodes if :batch? is true.

  opts: A map that should contain :first or :last, as well as optionally :after or :before.

  Optional named parameters
  =========================
  :context: User-defined data to store in every cursor. Must be pr-str-able.
            Defaults to {}. Can be retrieved on subsequent queries using
            `(get-context ...)`.

  :batch?: Set to true if f should be invoked once on all nodes,
           and not once for each node. If this is set to true,
           f must return the output nodes in the same order as the input nodes.
           Defaults to false.


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
  [data sort-attrs f opts & {:keys [time-filter context batch?]
                             :or {time-filter (constantly true) context {} batch? false}}]
  (assert (fn? time-filter) "Expected keep? to be a function")
  (assert (map? opts) "Expected opts to be a map")
  (let [f (if (keyword? f) (fn [node] (get node f)) f)
        _ (assert (fn? f) "Expected f to be a function")
        f-map (if batch?
                {:batch-f f}
                {:f f})
        cursor-str (or (get opts :after) (get opts :before))
        sort-attrs (if (keyword? sort-attrs)
                     [sort-attrs]
                     sort-attrs)
        data (if (vector? data)
               {"default" data}
               data)
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

        (empty? data)
        {:edges    []
         :pageInfo {:hasNextPage false
                    :hasPrevPage false
                    :startCursor (pr-str {:context context})
                    :endCursor   (pr-str {:context context})
                    :totalCount  0}}

        (pos-int? (get opts :first))
        (pf/paginate-first data
                           (merge f-map
                                  {:filter     filter
                                   :max-items  (get opts :first)
                                   :keep?      time-filter
                                   :context    context
                                   :sort-attrs sort-attrs})
                           cursor-str)

        (pos-int? (get opts :last))
        (pl/paginate-last data
                          (merge f-map
                                 {:filter     filter
                                  :max-items  (get opts :last)
                                  :keep?      time-filter
                                  :context    context
                                  :sort-attrs sort-attrs})
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
