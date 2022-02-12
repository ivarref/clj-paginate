(ns com.github.ivarref.clj-paginate.impl.pag-first-map
  (:require [com.github.ivarref.clj-paginate.impl.bst2 :as bst2]
            [com.github.ivarref.clj-paginate.impl.utils :as u]))

(defn paginate-first [m
                      {:keys [max-items
                              f
                              batch-f
                              keep?
                              sort-attrs
                              context]
                       :or   {f       identity
                              batch-f identity
                              context nil
                              keep?   (constantly true)}}
                      cursor-str]
  (let [vecs (vals m)
        decoded-cursor (u/maybe-decode-cursor cursor-str)
        cursor (-> (merge {:context context} decoded-cursor))
        nodes-plus-1 (if-let [from-value (get cursor :cursor)]
                       (bst2/after-value vecs keep? (zipmap sort-attrs from-value) sort-attrs (inc max-items))
                       (bst2/from-beginning vecs keep? sort-attrs (inc max-items)))
        edges (u/get-edges (take max-items nodes-plus-1) batch-f f sort-attrs cursor)
        hasPrevPage (or (when (not-empty nodes-plus-1)
                          (not= (first nodes-plus-1)
                                (first (bst2/from-beginning vecs keep? sort-attrs 1))))
                        (and (empty? nodes-plus-1)
                             (some? cursor-str)))]
    {:edges    edges
     :pageInfo {:hasPrevPage (true? hasPrevPage)
                :hasNextPage (= (count nodes-plus-1) (inc max-items))
                :startCursor (or (get (first edges) :cursor) cursor-str)
                :endCursor   (or (get (last edges) :cursor) cursor-str)
                :totalCount  (bst2/total-count vecs keep?)}}))