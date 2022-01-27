# clj-paginate

A Clojure implementation of the 
[GraphQL Cursor Connections Specification](https://relay.dev/graphql/connections.htm) 
with vector/set as the backing data.

Supports: 
* Collection that grows and/or changes.
* Long polling (`:first` only, not `:last`).
* Filtering and user-defined context.
* Batching.

## Prerequisites

The user of this library is assumed to be moderately
familiar with [GraphQL pagination](https://graphql.org/learn/pagination/)
and know the basic structure of the 
[GraphQL Cursor Connections Specification](https://relay.dev/graphql/connections.htm),
particularly the fact that the desired response looks like the following:

```
{"edges": [{"node": ..., "cursor": ...},
           {"node": ..., "cursor": ...},
           {"node": ..., "cursor": ...},
           ...] 
 "pageInfo": {"hasNextPage":  Boolean
              "hasPrevPage":  Boolean
              "totalCount":   Integer
              "startCursor":  String
              "endCursor":    String}}
```

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
; The poller, i.e. a different backend, should now sleep for some time before attempting again.


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

## Background

This library was developed for supporting pagination for "heavy" Datomic queries that
spent too much time on delivering the initial result that would then have to be sorted and
paginated.

## Intended usage

You will want to store the result of `cp/prepare-paginate` in an atom, as this is
a somewhat expensive function, and
periodically re-generate this value at some fixed interval in a background thread.
[Recurring-cup's dereffable job](...) is a good fit for this.

We will omit this step in the examples that follows.


## Basic use case example

```clojure
(require '[com.github.ivarref.clj-paginate :as cp])

(def my-cache 
     (cp/prepare-paginate
       ; Specify how to sort the data.
       {:sort-by [:inst]}
       ; The data to paginate. A single element is hereby called a node.
       [{:inst 0 :id 1}
        {:inst 1 :id 2}
        {:inst 2 :id 3}]))

(defn http-post-handler 
  [response my-cache http-body]
  (assoc response
    :status 200
    :body (cp/paginate
            ; The first argument is the result of cp/prepare-paginate, i.e.
            ; the data to paginate.
            my-cache
            
            ; The second argument is a function that further processes the node.
            ; The function may for example load more data from a database or other external storage.
            (fn [{:keys [inst id] :as node}]  
              (Thread/sleep 10) ; Do some heavy work.
              (assoc node :value-from-db 1))
            
            ; The third argument should be a map containing the arguments to the pagination.
            ; This map must contain either:
            ; :first (Integer), how many items to fetch from the start, and optionally :after, the cursor,
            ; or :last (Integer), how many items to fetch from the end, and optionally :before, the cursor.
            ; If this requirement is not satisfied, an exception will be thrown.
            http-body)))
```

That is all that is needed for the basic use case to work.

## Filtering and context

Sometimes you may want to provide dynamic filters on the data.
This is done in four steps:

1. Your HTTP endpoint must support parameters that represents the filters.
2. Create the filters based on the initial query or the given cursor.
3. Create a filter function based on the filters. Pass this to the paginate function using `:filter`. 
4. Pass `:context` to the paginate function to store the filters in the cursor, 
i.e. it will be used in subsequent queries.

As an example, let's add a `:status` property to our previous example and make it filterable:

```clojure
(require '[com.github.ivarref.clj-paginate :as cp])

(def my-cache 
     (cp/prepare-paginate
       {:sort-by [:inst]}
       [{:inst 0 :id 1 :status :init}
        {:inst 1 :id 2 :status :pending}
        {:inst 2 :id 3 :status :done}
        {:inst 3 :id 4 :status :error}]))

(defn http-post-handler
  [response my-cache {:keys [statuses] :as http-body}]
  (let [statuses (into #{} (or 
                             ; Prefer the statuses that was stored in the cursor:
                             (:statuses (cp/get-context http-body))
                             
                             ; Use the specified statuses given on the initial request:
                             (not-empty statuses)
                             
                             ; Default to include all statuses:
                             [:init :pending :done :error]))]
    (assoc response
        :status 200
        :body (cp/paginate
                my-cache
                
                (fn [{:keys [inst id] :as node}]
                  (Thread/sleep 10) ; Do some heavy work.
                  (assoc node :value-from-db 1))
                
                http-body ; :first or :last, and optionally :after or :before.
                
                ; Filter nodes by specifying :filter. 
                ; Only nodes for which :filter returns truthy is included in the returned edges.
                :filter (fn [{:keys [status]}] (contains? statuses status))
                
                ; Pass the named parameter :context to add data to the cursor.
                ; The context must be `pr-str`-able.  
                :context {:statuses statuses}))))
```

The consumer client only needs to send `:statuses` on the initial query.
When subsequent iteration is done, the cursor, `:after` or `:before`,
already includes `:statuses`, and thus it is not necessary to re-send
this information on every request.

### Batching

Batching is supported. Add `:batch? true` when calling `paginate`.
`f` must now accept a vector of nodes, and return 
a vector of processed nodes. The returned vector must have the same
ordering as the input vector.  You may want to use the function
`ensure-order` to make sure the order is correct:

```clojure
(require '[com.github.ivarref.clj-paginate :as cp])

(defn load-batch [nodes]
  (let [loaded-nodes (->> nodes
                          ; Pretend to load data from the database:
                          (mapv #(assoc % :db/id (:id %)))
                          ; We got the ordering mixed up:
                          (shuffle))]
    (cp/ensure-order nodes 
                     loaded-nodes
                     :sif :id ; Source id function, defaults to :id.
                     :dif :db/id ; Dest id function, defaults to :id.
                     
                     ; (sif input-node) must be equal to some (dif output-node).
                     ; ensure-order uses this to order `loaded-nodes` according
                     ; to how `nodes` were ordered.
                     )))
```

## Performance

`prepare-paginate` turns the input collection into a binary search tree,
and thus the general performance is `O(log n)` for finding where to continue
giving out data.
However, if `:filter` is used and seldom matches anything, it may very
well be much worse, `O(n)`. Use `:filter` at your own risk!

Using `:first 1000` and 10 million dummy entries, the average
overhead was about 1 ms per iteration on my machine. That is about
1 microseconds per returned node.

## License

Copyright © 2022 Ivar Refsdal

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.