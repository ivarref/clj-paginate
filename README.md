# clj-paginate

A Clojure (JVM only) implementation of the 
[GraphQL Cursor Connections Specification](https://relay.dev/graphql/connections.htm) 
with vector or map as the backing data.

Supports: 
* Collections that grows and/or changes.
* Long polling (`:first` only, not `:last`).
* Multiple sort criteria.
* Ascending or descending sorting.
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

(def data (vec (shuffle [{:inst 0}
                         {:inst 1}
                         {:inst 2}])))

; Get the initial page:
(def page-1 (cp/paginate 
               data
               
               ; The next argument, `sort-attrs`, specifies how the vector should be sorted. 
               ; It must be a single keyword, a vector of keywords or a vector of pairs (keyword and :asc/:desc).
               ; See more documentation below for information about ascending or descending
               ; sorting.
               :inst
               
               ; A function to transform an initial node into a final node,
               ; i.e. load more data from a database.
               identity
               
               ; What to get, the first two elements in this case:
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

Nodes must be maps.

## Basic use case example

```clojure
(require '[com.github.ivarref.clj-paginate :as cp])

(def data
  (vec (shuffle [{:inst 0 :id 1}
                 {:inst 1 :id 2}
                 {:inst 2 :id 3}])))

(defn http-post-handler 
  [response data http-body]
  (assoc response
    :status 200
    :body (cp/paginate
            ; The first argument is the data to paginate.
            data
            
            ; The second argument specifies how the vector is sorted.
            ; It thus also specifies what constitute a unique identifier for a node.
            ; It may be a single keyword, a vector of keywords,
            ; or a vector of pairs where each pair has a keyword and :asc or :desc.
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

## Multiple sort criteria and descending values example

The default behaviour of `clj-paginate` is to assume that all attributes in `:sort-attrs` is sorted ascendingly.
It's possible to override this behaviour using pairs of `keyword :asc/:desc` in the `:sort-attrs` vector:

```clojure
(require '[com.github.ivarref.clj-paginate :as cp])

(def data
  (vec (shuffle [{:inst #inst"2000" :id 1}
                 {:inst #inst"2000" :id 2}
                 {:inst #inst"2001" :id 3}])))

(cp/paginate
   ; The first argument is the data to paginate.
   data
   
   ; The second argument specifies how the vector should be sorted.
   [[:inst :asc] [:id :desc]]
   
   identity
   {:first 2})

(def conn *1)

(mapv :node (:edges conn))
=> [{:inst #inst"2000", :id 2} {:inst #inst"2000", :id 1}]

(cp/paginate
   ; The first argument is the data to paginate.
   ; The data must already be sorted.
   data

   ; The second argument specifies which attributes constitute a unique identifier for a node.
   ; It may be a single keyword, or a vector of keywords.
   [[:inst :asc] [:id :desc]]

   identity
   {:first 2 :after (get-in conn [:pageInfo :endCursor])})

(mapv :node (:edges *1))
=> [{:inst #inst"2001", :id 3}]
```

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

### Refresh a page

If you want to refresh a page, you may add `:inclusive? true` as
a named parameter when calling `paginate`.
The results will then include the given cursor. This is useful
if you want to check for updates of a given page only based on a
previous pageInfo.

```clojure
(require '[com.github.ivarref.clj-paginate :as cp])

; Using :first:
(cp/paginate
  data
  [:sort-attrs]
  identity
  {:first 10 :after (:startCursor (:pageInfo connection))}
  :inclusive? true)

; Using :last:
(cp/paginate
   data
   [:sort-attrs]
   identity
   {:last 10 :before (:endCursor (:pageInfo connection))}
   :inclusive? true)
```

### Batching

Batching is supported. Add `:batch? true` when calling `paginate`.
`f`, the third parameter to paginate, must now accept a vector of nodes, and return 
a vector of processed nodes. The returned vector must have the same
ordering as the input vector.  You may want to use the function
`ensure-order` to make sure the order is correct:

```clojure
(require '[com.github.ivarref.clj-paginate :as cp])

(defn load-batch [nodes]
  (let [loaded-nodes (->> (mapv :id nodes)
                          
                          ; load data from database using pull-many:
                          (datomic.api/pull-many datomic-db '[:*])

                          ; Do we have any ordering guarantees? Pretend the ordering got mixed up:
                          (shuffle))]
    (cp/ensure-order nodes 
                     loaded-nodes
                     :sf :id ; Source id function, defaults to :id.
                     :df :db/id ; Dest id function, defaults to :id.
                     
                     ; (sf input-node) must be equal to some (df output-node).
                     ; ensure-order uses this to order `loaded-nodes` according
                     ; to how `nodes` were ordered.
                     )))

; Using load-batch
(cp/paginate
   data
   :id
   load-batch
   {:first 100}
   
   ; The named parameter :batch? is set to `true`:
   :batch? true
   )
```


## Performance

`clj-paginate` treats the (sorted) input vectors as binary trees,
and thus the general performance is `O(log n)` for finding where to continue
giving out data. When paginating over maps, this
has to be multiplied by the number of selected keys.

Using `:first 1000` and 10 million dummy entries, the average
overhead was about 1-5 ms per iteration on my machine. That is about
1-5 microsecond per returned node.

## Change log

### 2022-10-06 0.3.54

Add support for `inclusive?`, multiple sort criteria with `:asc` or `:desc`.
Added named parameter `sort?` which defaults to `true`.

### 2022-09-23 0.2.53
Bugfix. Values for `pageInfo.hasPrevPage` and `pageInfo.hasNextPage` for `last/before` pagination were reversed. Thanks [@kthu](https://github.com/kthu)!

### 2022-09-20 0.2.52
Support descending values.

### 2022-02-16 0.2.51
Initial release publicly announced.

## Misc

A few days after I made the initial announcement, I came across
[java.util.NavigableSet](https://docs.oracle.com/javase/8/docs/api/java/util/NavigableSet.html)
that looks like a perfect fit for doing pagination
in JVM-land.

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
