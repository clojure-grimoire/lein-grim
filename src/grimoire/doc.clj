(ns grimoire.doc
  (:refer-clojure :exclude [munge])
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.repl] ;; gon crowbar into this...
            [clojure.data] ;; this too...
            [clojure.tools.namespace.find :as tns.f]
            [clojure.java.classpath :as cp]
            [grimoire.api :as api]
            [grimoire.things :as t]
            [grimoire.util :as util]
            [detritus.var :refer [var->ns var->sym macro?]]
            [detritus.update :refer [update]]))

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
  (t/->Def groupid
           artifactid
           version
           (name (var->ns var))
           (name (var->sym var))))

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
                 (update :ns   ns-name))]
    (api/write-meta config
                    (var->thing config var)
                    docs)))

(defn write-docs-for-specials

  [{:keys [groupid artifactid version] :as config}]
  (doseq [[sym {:keys [forms doc] :as fake-meta}] @#'clojure.repl/special-doc-map]
    (api/write-meta config
                    (t/->Def groupid artifactid version "clojure.core" (name sym))
                    {:ns       "clojure.core"
                     :name     (name sym)
                     :doc      doc
                     :arglists forms
                     :src      ";; Special forms have no source"
                     :line     nil
                     :column   nil
                     :file     nil
                     :type     :special})))


(def var-blacklist
  #{#'clojure.data/Diff})

(defn write-docs-for-ns
  [config ns]
  (let [ns-vars (->> (ns-publics ns) vals (remove var-blacklist))
        macros  (filter macro? ns-vars)
        fns     (filter #(and (fn? @%1)
                              (not (macro? %1)))
                        ns-vars)
        vars    (filter #(not (fn? @%1)) ns-vars)]

    ;; write per symbol docs
    (doseq [var ns-vars]
      (write-docs-for-var config var))

    (when (= ns 'clojure.core)
      (write-docs-for-specials config)))

  ;; FIXME: should be a real logging thing
  (println "Finished" ns)
  nil)

(defn -main

  [groupid artifactid version pattern target]
  (let [pattern (re-pattern pattern)
        config  {:groupid    groupid
                 :artifactid artifactid
                 :version    version
                 :datastore  {:docs target}}]
    (doseq [ns (tns.f/find-namespaces
                (cp/classpath))]
      (when (re-matches pattern (name ns))
        (require ns)
        (write-docs-for-ns config ns)))))
