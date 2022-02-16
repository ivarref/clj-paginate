# clj-paginate

A Clojure (JVM only) implementation of the 
[GraphQL Cursor Connections Specification](https://relay.dev/graphql/connections.htm) 
with vector or map as the backing data.

Supports: 
* Collections that grows and/or changes.
* Long polling (`:first` only, not `:last`).
* Basic OR filtering (maps only).
* Batching (optional).

No external dependencies.

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

## 1-minute example

```clojure
(require '[com.github.ivarref.clj-paginate :as cp])

(defn nodes [page]
  (->> page
       :edges
       (mapv :node)))

(def data [{:inst 0}
           {:inst 1}
           {:inst 2}])

; Get the initial page:
(def page-1 (cp/paginate data 
                         :inst ; sort-attrs: Specifies how `data` is sorted.
                         identity ; A function to transform an initial node into a final node,
                                  ; i.e. load more data from a database.
                         {:first 2}))
; page-1
;=>
;{:edges
; [{:node {:inst 0}, :cursor "{:context {} :cursor [0 ]}"}
;  {:node {:inst 1}, :cursor "{:context {} :cursor [1 ]}"}],
; :pageInfo
; {:hasPrevPage false,
;  :hasNextPage true,
;  :startCursor "{:context {} :cursor [0 ]}",
;  :endCursor "{:context {} :cursor [1 ]}",
;  :totalCount 3}}


; Get the second page:
(def page-2 (cp/paginate data
                         :inst
                         identity
                         {:first 2
                          :after (get-in page-1 [:pageInfo :endCursor])}))
; (nodes page-2)
; => [{:inst 2}]

; Get the next (empty) page:
(def page-3 (cp/paginate data
                         :inst
                         identity
                         {:first 2
                          :after (get-in page-2 [:pageInfo :endCursor])}))
; (nodes page-3)
; => []
; No more data! 
; The poller, i.e. a different backend, should now sleep for some time before attempting again.


; More data has arrived:
(def data [{:inst 0} ; old
           {:inst 1} ; old
           {:inst 2} ; old
           {:inst 3} ; new item
           {:inst 4} ; new item
           ])

; Time for another poll. Growing data is handled:
(def page-4 (cp/paginate data
                         :inst
                         identity
                         {:first 2
                          :after (get-in page-3 [:pageInfo :endCursor])}))
; (nodes page-4)
; => [{:inst 3} {:inst 4}]

; More data has arrived, and old data expired/got removed:
(def data [{:inst 6}
           {:inst 7}
           {:inst 8}])

; Changed data is handled as long as the newer data adheres to sorting
(def page-5 (cp/paginate data
                         :inst
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

## Data requirements

1. Nodes must be maps.
2. Vectors passed to `paginate` must be sorted according to `sort-attrs`.

## Basic use case example

```clojure
(require '[com.github.ivarref.clj-paginate :as cp])

(def data 
  [{:inst 0 :id 1}
   {:inst 1 :id 2}
   {:inst 2 :id 3}])

(defn http-post-handler 
  [response data http-body]
  (assoc response
    :status 200
    :body (cp/paginate
            ; The first argument is the data to paginate.
            data
            
            ; The second argument specifies how the data was sorted.
            ; It may be a single keyword, or a vector of keywords.
            [:inst]
            
            ; The third argument is a function that further processes the node.
            ; The function may for example load more data from a database or other external storage.
            (fn [{:keys [inst id] :as node}]  
              (Thread/sleep 10) ; Do some heavy work.
              (assoc node :value-from-db 1))
            
            ; The fourth argument should be a map containing the arguments to the pagination.
            ; This map must contain either:
            ; :first (Integer), how many items to fetch from the start, and optionally :after, the cursor,
            ; or :last (Integer), how many items to fetch from the end, and optionally :before, the cursor.
            ; If this requirement is not satisfied, an exception will be thrown.
            http-body)))
```

That is all that is needed for the basic use case to work.

## OR filters

Sometimes you may want to provide filtering of the data.
This is done in two steps:

1. Your HTTP endpoint must support a parameter that represents the or filter.
2. Pass a map to the `paginate` function along with `:filter` in `opts`.
   `:filter` should be a vector of the keys of the map that you want to filter on. 

As an example, let's add a `:status` property to our previous example and make it filterable:

```clojure
(require '[com.github.ivarref.clj-paginate :as cp])

(def data
   (group-by :status
             [{:inst 0 :id 1 :status :init}
              {:inst 1 :id 2 :status :pending}
              {:inst 2 :id 3 :status :done}
              {:inst 3 :id 4 :status :error}
              {:inst 4 :id 5 :status :done}]))

(defn http-post-handler
   [response data http-body]
   (assoc response
      :status 200
      :body (cp/paginate
               data ; data is now a map.
               :inst
               (fn [{:keys [inst id] :as node}]
                  (Thread/sleep 10) ; Do some heavy work.
                  (assoc node :value-from-db 1))

               ; Assume that the HTTP endpoint accepts a parameter `:statuses` for the body,
               ; and that when present, this is a vector such as `[:init :pending :done :error]` or similar,
               ; i.e. the keys of `data` that we want to filter on.
               ;
               ; Paginate's `opts` accepts a key `:filter` that does exactly this for data maps.
               ; Thus we can simply rename `:statuses` to `:filter` in the http body.
               ; clj-paginate takes care of storing the value of `:filter` in the cursor
               ; for subsequent queries.
               (clojure.set/rename-keys http-body {:statuses :filter}))))

; To illustrate this, consider the following code:
(let [conn (cp/paginate
              data
              :inst
              identity
              {:first  1
               :filter [:done]})]
   ; Will print [{:inst 2, :id 3, :status :done}].
   (println (mapv :node (:edges conn))) 

   ; Will print [{:inst 4, :id 5, :status :done}].
   ; Notice here that we do not re-specify `:filter`.
   ; It is already stored in the cursor from the original connection.
   (println (mapv :node (:edges (cp/paginate
                                   data
                                   :inst
                                   identity
                                   {:first 1
                                    :after (get-in conn [:pageInfo :endCursor])})))))
```

The consumer client only needs to send `:statuses` on the initial query.
When subsequent iteration is done, the cursor, `:after` or `:before`,
already includes `:filter`, and thus it is not necessary to re-send
this information on every request. If `:filter` is not specified for
a map, every key is included.

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
                     :sf :id ; Source id function, defaults to :id.
                     :df :db/id ; Dest id function, defaults to :id.
                     
                     ; (sf input-node) must be equal to some (df output-node).
                     ; ensure-order uses this to order `loaded-nodes` according
                     ; to how `nodes` were ordered.
                     )))
```


## Performance

`clj-paginate` treats the (sorted) input vectors as binary trees,
and thus the general performance is `O(log n)` for finding where to continue
giving out data. When paginating over maps, this
has to be multiplied by the number of selected keys.

Using `:first 1000` and 10 million dummy entries, the average
overhead was about 1-5 ms per iteration on my machine. That is about
1-5 microsecond per returned node.

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