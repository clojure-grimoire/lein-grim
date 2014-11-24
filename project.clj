(defproject org.clojure-grimoire/lein-grim (slurp "VERSION")
  :description "A Leiningen plugin for generating Grimoire documentation"
  :url "http://github.com/clojure-grimoire/lein-grim"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure-grimoire/lib-grimoire "0.1.2"]
                 [me.arrdem/detritus "0.2.0"]
                 [org.clojure/tools.namespace "0.2.7"]])
