(defproject org.clojure-grimoire/lein-grim (slurp "VERSION")
  :description "A Leiningen plugin for generating Grimoire documentation"
  :url "http://github.com/clojure-grimoire/lein-grim"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure-grimoire/lib-grimoire "0.9.3"]
                 [org.clojure/java.classpath "0.2.2"]
                 [org.clojure/tools.namespace "0.2.7"]]
  :aliases {"grim" ["run" "-m" "grimoire.doc"
                    ,,:project/groupid
                    ,,:project/artifactid
                    ,,:project/version
                    ,,nil]})
