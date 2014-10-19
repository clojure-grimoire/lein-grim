(ns leiningen.grim
  (:require [leiningen.core.eval :refer [eval-in-project]]))

(def deps
  ['[org.clojure/tools.namespace "0.2.7"]
   '[org.clojure-grimoire/lib-grimoire "0.1.0-SNAPSHOT"]])

(defn grim
  [project & args]
  (eval-in-project
   (update-in project [:dependencies] concat deps)
   `(do (require 'grimoire.doc)
        (grimoire.doc/-main ~@ args))))
