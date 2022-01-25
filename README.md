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

## Background

This library was developed for supporting pagination for "heavy" Datomic queries that
spent too much time on delivering the initial result that would then have to be sorted and
paginated.

## Basic usage example

You will want to store the result of `cp/prepare-paginate` in an atom, and
periodically re-generate this value at some fixed interval in a background thread.
[Recurring-cup's dereffable job](...) is a good fit for this.

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
              (Thread/sleep 10) ; do some heavy work
              (assoc node :value-from-db 1))
            
            ; The third argument should be a map containing the arguments to the pagination.
            ; Thus this map should contain either:
            ; :first (Integer), how many items to fetch from the start, and optionally :after, the cursor,
            ; or :last (Integer), how many items to fetch from the end, and optionally :before, the cursor
            http-body)))
```

That is all that is needed for the basic usage case to work.

## Filtering and context

Sometimes you may want to provide dynamic filters on the data.
This is done in four steps:

1. Your HTTP endpoint must support parameters that represents the filters.
2. Create the filters based on either the given cursor or the initial query.
3. Create a filter function based on the filters. Pass this to the paginate function. 
4. Pass `:context` to the paginate function to store the filters in the cursor, 
i.e. it will be used in subsequent queries.

Let's add a `:status` property to our previous example and make it filterable:

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
                  (Thread/sleep 10) ; do some heavy work
                  (assoc node :value-from-db 1))
                
                http-body ; :first or :last and optionally :after or :before
                
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

## Performance