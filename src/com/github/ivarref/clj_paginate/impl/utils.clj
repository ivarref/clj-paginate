(ns com.github.ivarref.clj-paginate.impl.utils
  (:require [clojure.edn :as edn]
            [clojure.string :as str])
  (:import (java.io StringWriter Writer)))


(defn maybe-decode-cursor [cursor]
  (when cursor
    (when (and (string? cursor)
               (not-empty cursor)
               (str/starts-with? cursor "{"))
      (edn/read-string cursor))))


(defn get-cursor [opts]
  (or
    (let [cursor (or (get opts :after) (get opts :before))]
      (when (and (string? cursor)
                 (not-empty cursor)
                 (str/starts-with? cursor "{"))
        (edn/read-string cursor)))
    {}))


(defn cursor-pre [cursor]
  (let [s (pr-str (dissoc cursor :cursor))
        s (subs s 0 (dec (count s)))]
    (str s " :cursor [")))


(defn node-cursor [cursor-pre node sort-attrs]
  (with-open [^Writer sw (StringWriter.)]
    (.append sw ^String cursor-pre)
    (doseq [attr sort-attrs]
      (print-method (get node attr) sw)
      (.append sw " "))
    (.append sw "]}")
    (.toString sw)))


(defn get-edges [nodes batch-f f sort-attrs cursor]
  (let [nodes (vec nodes)]
    (if (empty? nodes)
      []
      (let [cursor-pre-str (cursor-pre cursor)
            batch-nodes (batch-f nodes)]
        (loop [i 0
               res (transient [])]
          (if (= i (count nodes))
             (persistent! res)
             (recur (inc i)
                    (conj! res
                           {:node (f (nth batch-nodes i))
                            :cursor (node-cursor cursor-pre-str
                                                 (nth nodes i)
                                                 sort-attrs)}))))))))
