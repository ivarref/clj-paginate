(ns com.github.ivarref.clj-paginate.impl.pag-last-map
  (:require [com.github.ivarref.clj-paginate.impl.utils :as u]
            [com.github.ivarref.clj-paginate.impl.bst2 :as bst2]
            [com.github.ivarref.clj-paginate.impl.bst-before :as bst]))

(defn paginate-last [m
                     {:keys [max-items
                             f
                             batch-f
                             sort-attrs
                             filter
                             context
                             sort-fn
                             inclusive?
                             compare-fn]
                      :or   {f       identity
                             batch-f identity
                             context nil}}
                     cursor-str]
  (let [vecs (into [] (vals m))
        decoded-cursor (u/maybe-decode-cursor cursor-str)
        cursor (-> (merge {:context context}
                          (when filter {:filter filter})
                          decoded-cursor))
        sort-fn (if sort-fn sort-fn (apply juxt sort-attrs))
        compare-fn (if compare-fn compare-fn #(compare (sort-fn %1) (sort-fn %2)))
        nodes-plus-1 (if-let [from-value (get cursor :cursor)]
                       (do
                         (when (not= (count from-value) (count sort-attrs))
                           (throw (ex-info "Mismatch in size of :node-id-attrs and :cursor" {:node-id-attrs sort-attrs
                                                                                             :cursor        from-value})))
                         (bst/before-value vecs (zipmap sort-attrs from-value) compare-fn (inc max-items) inclusive?))
                       (bst/from-end vecs compare-fn (inc max-items)))
        edges (u/get-edges (take-last max-items nodes-plus-1) batch-f f sort-attrs cursor)]
    {:edges    edges
     :pageInfo {:hasPrevPage (> (count nodes-plus-1) max-items)
                :hasNextPage (or (when (not-empty nodes-plus-1)
                                   (not= (last nodes-plus-1)
                                         (first (bst/from-end vecs compare-fn 1))))
                                 (and (empty? nodes-plus-1)
                                      (some? cursor-str)))
                :startCursor (or (get (first edges) :cursor) cursor-str)
                :endCursor   (or (get (last edges) :cursor) cursor-str)
                :totalCount  (bst2/total-count vecs)}}))
