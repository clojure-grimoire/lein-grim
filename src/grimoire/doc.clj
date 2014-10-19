(ns grimoire.doc
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.repl] ;; gon crowbar into this...
            [clojure.tools.namespace.find :as tns.f]
            [grimoire.api :as api]
            [grimoire.util :refer :all]))

(defn file->ns [fpath]
  (-> fpath
      (replace #".clj$" "")
      (replace #"_" "-")
      (replace #"/" ".")))

(defn var-type
  [v]
  {:pre [(var? v)]}
  (let [m (meta v)]
    (cond (:macro m)   "macro"
          (:dynamic m) "var"
          (fn? @v)     "fn"
          :else        "var")))

(defn write-docs-for-var [config var]
  ;; FIXME
  )

(defn write-docs-for-specials [config]
  (doseq [[sym fake-meta] @#'clojure.repl/special-doc-map]
    ;; FIXME
    ))

(defn write-docs-for-ns
  [config ns]
  (let [ns-vars (->> (ns-publics ns) vals (remove var-blacklist))
        macros  (filter macro? ns-vars)
        fns     (filter #(and (fn? @%1)
                              (not (macro? %1)))
                        ns-vars)
        vars    (filter #(not (fn? @%1)) ns-vars)
        ns-dir  (io/file root (name ns))]

    ;; write per symbol docs
    (doseq [var ns-vars]
      (write-docs-for-var ns-dir var))

    (when (= ns 'clojure.core)
      (write-docs-for-specials ns-dir)))

  (println "Finished" ns)
  nil)

(defn -main [groupid artifactid version & args]
  (doseq [ns (tns.f/find-namespaces (clojure.java.classpath/classpath))]
    (require ns)
    (write-docs-for-ns config ns)))
