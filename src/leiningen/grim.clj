(ns leiningen.grim
  (:require [leiningen.core.eval :refer [eval-in-project]]))

(def deps
  ['[org.clojure/tools.namespace "0.2.7"]
   '[org.clojure/java.classpath "0.2.2"]
   '[org.clojure-grimoire/lib-grimoire "0.1.1"]
   '[org.clojure-grimoire/lein-grim "0.1.1"] ;; FIXME: do this better?
   '[me.arrdem/detritus "0.2.0"]])

(defn grim
  "Usage: lein grim src <dst>
      : lein grim artifact <groupid> <artifactid> <version> <dst>

In source mode, lein-grim traverses the source paths of the current project,
enumerating and documenting all namespaces. This is intended for documenting
projects for which you have both source and a lein project.

In artifact mode, lein-grim traverses an artifact on the classpath enumerating
and documenting the namespaces therein. This is intended for documenting
projects such as clojure.core which may not exist as a covenient lein project
but which do exist as artifacts."
  [project mode & args]
  {:pre [(#{"src" "artifact"} mode)]}
  (let [args (if (= "src" mode)
               (concat ((juxt :group :name :version) project) args)
               args)
        paths (if (= "src" mode)
                (vec (:source-paths project))
                :classpath)]
    (eval-in-project
     (update-in project [:dependencies] concat deps)
     `(do (require 'grimoire.doc)
          (grimoire.doc/-main ~@args ~paths)))))
