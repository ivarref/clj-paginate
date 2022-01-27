(ns com.github.ivarref.clj-paginate.impl.paginate-last
  (:require [com.github.ivarref.clj-paginate.impl.bst :as bst]
            [com.github.ivarref.clj-paginate.impl.utils :as u]))


(defn paginate-last [{:keys [root id opts version]}
                     {:keys [max-items
                             f
                             batch-f
                             keep?
                             context]
                      :or   {f       identity
                             batch-f identity
                             context nil
                             keep?   (constantly true)}}
                     cursor-str]
  (let [sort-attrs (get opts :sort-by)
        decoded-cursor (u/maybe-decode-cursor cursor-str)
        cursor (-> (merge {:context context} decoded-cursor)
                   (assoc :id id :version version)
                   (update :totalCount (partial u/get-total-count root keep? id decoded-cursor)))
        nodes-plus-1 (if-let [from-value (get cursor :cursor)]
                       (bst/before-value root keep? (zipmap sort-attrs from-value) sort-attrs (inc max-items))
                       (bst/from-end root keep? (inc max-items)))
        nodes (if-let [nodes (not-empty (take-last max-items nodes-plus-1))]
                (vec (batch-f (vec nodes)))
                [])
        cursor-pre (u/cursor-pre cursor)
        edges (mapv (fn [node]
                      {:node   (f node)
                       :cursor (u/node-cursor cursor-pre node sort-attrs)})
                    nodes)
        hasPrevPage (or (when (not-empty nodes-plus-1)
                          (not= (last nodes-plus-1)
                                (bst/get-rightmost-value root keep?)))
                        (and (empty? nodes-plus-1)
                             (some? cursor-str)))]
    {:edges    edges
     :pageInfo {:hasPrevPage (true? hasPrevPage)
                :hasNextPage (= (count nodes-plus-1) (inc max-items))
                :startCursor (or (get (first edges) :cursor) cursor-str)
                :endCursor   (or (get (last edges) :cursor) cursor-str)
                :totalCount  (:totalCount cursor)}}))