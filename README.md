# clj-paginate

A Clojure implementation of the 
[GraphQL Cursor Connections Specification](https://relay.dev/graphql/connections.htm) 
with vector/set as the backing data.

Supports: 
* Collection that grows and/or changes
* Polling pagination (`:first` only, not `:last`)

## Installation

[![Clojars Project](https://img.shields.io/clojars/v/com.github.ivarref/clj-paginate.svg)](https://clojars.org/com.github.ivarref/clj-paginate)

## 2-minute example

```clojure
(require '[com.github.ivarref.clj-paginate :as cp])

(def my-data [{:inst 0}
              {:inst 1}
              {:inst 2}])

(defn nodes [page]
  (->> page
       :edges
       (mapv :node)))


(def my-cache (cp/prepare-paginate {:sort-by [:inst]}
                                   my-data))

; Get the initial page:
(def page-1 (cp/paginate my-cache identity {:first 2}))
; page-1
;=>
;{:edges [{:node {:inst 0}, :cursor "{:context {}, :id \"673c016d-9f81-4ee8-8b7a-0e45a386a0fe\", :totalCount 3, :cursor {:inst 0}}"}
;         {:node {:inst 1}, :cursor "{:context {}, :id \"673c016d-9f81-4ee8-8b7a-0e45a386a0fe\", :totalCount 3, :cursor {:inst 1}}"}],
; :pageInfo {:hasPrevPage false,
;            :hasNextPage true,
;            :startCursor "{:context {}, :id \"673c016d-9f81-4ee8-8b7a-0e45a386a0fe\", :totalCount 3, :cursor {:inst 0}}",
;            :endCursor "{:context {}, :id \"673c016d-9f81-4ee8-8b7a-0e45a386a0fe\", :totalCount 3, :cursor {:inst 1}}",
;            :totalCount 3}}


; Get the second page:
(def page-2 (cp/paginate my-cache
                         identity
                         {:first 2
                          :after (get-in page-1 [:pageInfo :endCursor])}))
; (nodes page-2)
; => [{:inst 2}]

; Get the next (empty) page:
(def page-3 (cp/paginate my-cache
                         identity
                         {:first 2
                          :after (get-in page-2 [:pageInfo :endCursor])}))
; (nodes page-3)
; => []
; No more data! 
; The poller should now sleep for some time before attempting again.


; More data has arrived:
(def my-cache (cp/prepare-paginate {:sort-by [:inst]}
                                   [{:inst 0}
                                    {:inst 1}
                                    {:inst 2}
                                    {:inst 3}
                                    {:inst 4}]))

; Time for another poll. Growing data is handled:
(def page-4 (cp/paginate my-cache
                         identity
                         {:first 2
                          :after (get-in page-3 [:pageInfo :endCursor])}))
; (nodes page-4)
; => [{:inst 3} {:inst 4}]

; More data has arrived, and old data expired/got removed:
(def my-cache (cp/prepare-paginate {:sort-by [:inst]}
                                   [{:inst 6}
                                    {:inst 7}
                                    {:inst 8}]))

; Changed data is handled as long as the newer data adheres to :sort-by:
(def page-5 (cp/paginate my-cache
                         identity
                         {:first 2
                          :after (get-in page-4 [:pageInfo :endCursor])}))
; (nodes page-5)
; => [{:inst 6} {:inst 7}]
```
