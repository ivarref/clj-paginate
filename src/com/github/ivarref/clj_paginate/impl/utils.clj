(ns com.github.ivarref.clj-paginate.impl.utils
  (:require [clojure.edn :as edn]
            [com.github.ivarref.clj-paginate.impl.bst :as bst]
            [clojure.string :as str])
  (:import (java.util UUID)
           (java.io StringWriter Writer)))


(defonce version (delay (str (UUID/randomUUID))))


(defn balanced-tree
  [v opts]
  (let [srt-by (get opts :sort-by)
        v (vec (sort-by (apply juxt srt-by) v))]
    {:root         (bst/balanced-tree v)
     :input-vector v
     :id           (str (UUID/randomUUID))
     :version      (str (get opts :version @version))
     :opts         {:sort-by srt-by}}))


(defn maybe-decode-cursor [cursor]
  (when cursor
    (when (and (string? cursor)
               (not-empty cursor)
               (str/starts-with? cursor "{"))
      (edn/read-string cursor))))


(defn old-cursor? [cursor version]
  (when (map? cursor)
    (not= version (get cursor :version))))


(defn get-cursor [opts]
  (or
    (let [cursor (or (get opts :after) (get opts :before))]
      (when (and (string? cursor)
                 (not-empty cursor)
                 (str/starts-with? cursor "{"))
        (edn/read-string cursor)))
    {}))


(defonce constantly-true (constantly true))


(defn get-total-count [root keep? id decoded-cursor old-count]
  (when root
    (cond
      (= keep? constantly-true)
      (count (.-v root))

      (and old-count (= id (:id decoded-cursor)))
      old-count

      :else
      (let [v (.-v root)]
        (loop [i 0
               cnt 0]
          (if (= i (count v))
            cnt
            (recur (inc i)
                   (+ cnt
                      (if (keep? (nth v i))
                        1
                        0)))))))))


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
