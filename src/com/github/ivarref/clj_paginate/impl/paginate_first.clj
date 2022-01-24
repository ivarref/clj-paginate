(ns com.github.ivarref.clj-paginate.impl.paginate-first
  (:require [com.github.ivarref.clj-paginate.impl.bst :as bst]
            [com.github.ivarref.clj-paginate.impl.utils :as u]))


(defn paginate-first [{:keys [root id opts]}
                      {:keys [max-items
                              f
                              batch-f
                              keep?
                              context]
                       :or   {f       identity
                              batch-f identity
                              context nil
                              keep?   (constantly true)}}
                      cursor]
  (let [sort-attrs (get opts :sort-by)
        org-cursor cursor
        decoded-cursor (u/maybe-decode-cursor cursor)
        cursor (-> (merge {:context context} decoded-cursor)
                   (assoc :id id)
                   (update :totalCount (partial u/get-total-count root keep? id decoded-cursor)))
        nodes-plus-1 (if-let [from-value (get cursor :cursor)]
                       (bst/after-value root keep? from-value sort-attrs (inc max-items))
                       (bst/from-beginning root keep? (inc max-items)))
        nodes (if-let [nodes (not-empty (take max-items nodes-plus-1))]
                (vec (batch-f (vec nodes)))
                [])
        edges (mapv (fn [node]
                      {:node   (f node)
                       :cursor (pr-str (assoc cursor :cursor (select-keys node sort-attrs)))})
                    nodes)
        hasPrevPage (or (when (not-empty nodes-plus-1)
                          (not= (first nodes-plus-1)
                                (bst/get-leftmost-value root keep?)))
                        (and (empty? nodes-plus-1)
                             (some? org-cursor)))]
    {:edges    edges
     :pageInfo {:hasPrevPage (true? hasPrevPage)
                :hasNextPage (= (count nodes-plus-1) (inc max-items))
                :startCursor (or (get (first edges) :cursor) org-cursor)
                :endCursor   (or (get (last edges) :cursor) org-cursor)
                :totalCount  (:totalCount cursor)}}))