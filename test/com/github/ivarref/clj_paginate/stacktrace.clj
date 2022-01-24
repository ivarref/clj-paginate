(ns com.github.ivarref.clj-paginate.stacktrace
  (:require [io.aviso.exception :as exception]
            [io.aviso.repl :as repl]))


(alter-var-root
  #'exception/*fonts*
  (constantly nil))

(alter-var-root
  #'exception/*default-frame-rules*
  (constantly [[:package "clojure.lang" :omit]
               [:name #"clojure\.test.*" :hide]
               [:name #"clojure\.core.*" :hide]
               [:name #"clojure\.main.*" :hide]
               [:file "REPL Input" :hide]
               [:name #"nrepl.*" :hide]
               [:name "" :hide]
               [:name #"clojure\.main/repl/read-eval-print.*" :hide]]))

(repl/install-pretty-exceptions)