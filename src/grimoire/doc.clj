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

(defn var->type [v]
  {:pre [(var? v)]}
  (let [m (meta v)]
    (cond (:macro m)   :macro
          (:dynamic m) :var
          (fn? @v)     :fn
          :else        :var)))

(defn var->thing

  [{:keys [groupid artifactid version]} var]
  ;; FIXME: get the ns, name out of the var & make a :def thing
  )

(defn var->src
  "Adapted from clojure.repl/source-fn. Returns a string of the source code for
  the given var, if it can find it. Returns nil if it can't find the source.

  Example: (var->src #'clojure.core/filter)"
  
  [v]
  {:pre [(var? v)]}
  (when-let [filepath (:file (meta v))]
    (when-let [strm (.getResourceAsStream (clojure.lang.RT/baseLoader) filepath)]
      (with-open [rdr (java.io.LineNumberReader. (java.io.InputStreamReader. strm))]
        (dotimes [_ (dec (:line (meta v)))] (.readLine rdr))
        (let [text (StringBuilder.)
              pbr (proxy [java.io.PushbackReader] [rdr]
                    (read [] (let [i (proxy-super read)]
                               (.append text (char i))
                               i)))]
          (if (= :unknown *read-eval*)
            (throw
             (IllegalStateException.
              "Unable to read source while *read-eval* is :unknown."))
            (read (java.io.PushbackReader. pbr)))
          (str text))))))

(defn write-docs-for-var

  [config var]
  (let [docs (-> (meta var)
                 (assoc  :src  (var->src var)
                         :type (var->type var))
                 (update :name name)
                 (update :ns   ns->name))]
    (api/write-meta config
                    (var->thing config var)
                    docs)))

(defn write-docs-for-specials

  [config]
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
      (write-docs-for-var config var))

    (when (= ns 'clojure.core)
      (write-docs-for-specials config)))

  (println "Finished" ns)
  nil)

(defn -main

  [groupid artifactid version & args]
  (doseq [ns (tns.f/find-namespaces
              (clojure.java.classpath/classpath))]
    (require ns)
    (write-docs-for-ns config ns)))
