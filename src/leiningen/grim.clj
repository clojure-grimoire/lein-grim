(ns leiningen.grim
  (:require [leiningen.core.eval :refer [eval-in-project]]))

(def deps
  ['[org.clojure/tools.namespace "0.2.7"]
   '[org.clojure/java.classpath "0.2.2"]
   '[org.clojure-grimoire/lib-grimoire "0.1.0-SNAPSHOT"]
   '[org.clojure-grimoire/lein-grim "0.1.0-SNAPSHOT"] ;; FIXME: do this better?
   '[me.arrdem/detritus "0.2.0-SNAPSHOT"]])

(defn grim
  [project & args]
  (let [args (concat ((juxt :group :name :version) project) args)]
    (eval-in-project
     (update-in project [:dependencies] concat deps)
     `(do (require 'grimoire.doc)
          (grimoire.doc/-main ~@args)))))
