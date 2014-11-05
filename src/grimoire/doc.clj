(ns grimoire.doc
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

(defn var->type

  [v]
  {:pre [(var? v)]}
  (let [m (meta v)]
    (cond (:macro m)                            :macro
          (or (:dynamic m)
              (.isDynamic ^clojure.lang.Var v)) :var
              (fn? @v)                          :fn
              :else                             :var)))

(defn var->thing

  [{:keys [groupid artifactid version]} var]
  {:pre [(string? groupid)
         (string? artifactid)
         (string? version)
         (var? var)]}
  (t/->Def groupid
           artifactid
           version
           (name (var->ns var))
           (name (var->sym var))))

(defn ns->thing

  [{:keys [groupid artifactid version]} ns-symbol]
  {:pre [(symbol? ns-symbol)
         (string? groupid)
         (string? artifactid)
         (string? version)]}
  (t/->Ns groupid
          artifactid
          version
          (name ns-symbol)))

(defn var->src
  "Adapted from clojure.repl/source-fn. Returns a string of the source code for
  the given var, if it can find it. Returns nil if it can't find the source.

  Example: (var->src #'clojure.core/filter)"

  [v]
  {:pre [(var? v)]}
  (when-let [filepath (:file (meta v))]
    (when-let [strm (.getResourceAsStream (clojure.lang.RT/baseLoader) filepath)]
      (with-open [rdr (java.io.LineNumberReader. (java.io.InputStreamReader. strm))]
        (binding [*ns* (.ns v)]
          (dotimes [_ (dec (:line (meta v)))]
            (.readLine rdr))
          (let [text (StringBuilder.)
                pbr  (proxy [java.io.PushbackReader] [rdr]
                       (read [] (let [i (proxy-super read)]
                                  (.append text (char i))
                                  i)))]
            (if (= :unknown *read-eval*)
              (throw
               (IllegalStateException.
                "Unable to read source while *read-eval* is :unknown."))
              (read (java.io.PushbackReader. pbr)))
            (str text)))))))

(defn ns-stringifier

  [x]
  (cond (instance? clojure.lang.Namespace x)
        ,,(name (ns-name x))
        (string? x)
        ,,x
        (symbol? x)
        ,,(name x)
        :else
        ,,(throw
           (Exception.
            (str "Don't know how to stringify " x)))))

(defn name-stringifier

  [x]
  (cond (symbol? x)
        ,,(name x)
        (string? x)
        ,,x
        :else
        ,,(throw
           (Exception.
            (str "Don't know how to stringify " x)))))

(defn write-docs-for-var

  [config var]
  (let [docs (-> (meta var)
                 (assoc  :src  (var->src var)
                         :type (var->type var))
                 (update :name name-stringifier)
                 (update :ns   ns-stringifier))]
    (api/write-meta config
                    (var->thing config var)
                    docs)))

(defn write-docs-for-specials

  [{:keys [groupid artifactid version] :as config}]
  (doseq [[sym {:keys [forms doc] :as fake-meta}] @#'clojure.repl/special-doc-map]
    (api/write-meta config
                    (t/->Def groupid artifactid version "clojure.core" (name sym))
                    {:ns       "clojure.core"
                     :name     sym
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

    (let [meta  (-> ns the-ns meta)
          thing (ns->thing config ns)]
      (api/write-meta config thing meta))

    ;; write per symbol docs
    (doseq [var ns-vars]
      (write-docs-for-var config var))

    (when (= ns 'clojure.core)
      (write-docs-for-specials config)))

  ;; FIXME: should be a real logging thing
  (println "Finished" ns)
  nil)

(defn -main

  [groupid artifactid version target files-thing]
  (let [config  {:groupid    groupid
                 :artifactid artifactid
                 :version    version
                 :datastore  {:docs target}}]
    (if (= :classpath files-thing)
      ;; classpath searching case
      (let [pattern (format ".*?/%s/%s/%s.*"
                            (string/replace groupid "." "/") artifactid version)
            pattern (re-pattern pattern)]
        (doseq [e (cp/classpath)]
          (when (re-matches pattern (str e))
            (doseq [ns (tns.f/find-namespaces [e])]
              (when-not (= ns 'clojure.parallel) ;; FIXME: get out nobody likes you
                (require ns)
                (write-docs-for-ns config ns))))))

      ;; source dirs vector case
      (doseq [ns (->> files-thing
                      (map io/file)
                      (tns.f/find-namespaces))]
        (require ns)
        (write-docs-for-ns config ns)))))
