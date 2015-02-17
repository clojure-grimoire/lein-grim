(ns grimoire.doc
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.repl] ;; gon crowbar into this...
            [clojure.data] ;; this too...
            [clojure.tools.namespace.find :as tns.f]
            [clojure.java.classpath :as cp]
            [grimoire.api :as api]
            [grimoire.api.fs.write]
            [grimoire.things :as t]
            [grimoire.util :as util]
            [detritus.var :refer [var->ns var->sym macro?]]))

(defn var->type
  "Function from a var to the type of the var.

  - Vars tagged as dynamic or satisfying the .isDynamic predicate are tagged
    as :var values.
  - Vars tagged as macros (as required by the macro contract) are tagged
    as :macro values.
  - Vars with fn? or MultiFn values are tagged as :fn values.
  - All other vars are simply tagged as :var."
  [v]
  {:pre [(var? v)]}
  (let [m (meta v)]
    (cond (:macro m)
          ,,:macro
          
          (or (:dynamic m)
              (.isDynamic ^clojure.lang.Var v))
          ,,:var

          (or (fn? @v)
              (instance? clojure.lang.MultiFn @v))
          ,,:fn

          :else
          ,,:var)))

(defn var->thing
  "Function from a groupid, artifactid, version, platform and a var to the Def
  Thing representing the given var in the described Artifact."
  [{:keys [groupid artifactid version platform]} var]
  {:pre [(string? groupid)
         (string? artifactid)
         (string? version)
         (string? platform)
         (var? var)]}
  (-> (t/->Group    groupid)
      (t/->Artifact artifactid)
      (t/->Version  version)
      (t/->Platform platform)
      (t/->Ns       (name (var->ns var)))
      (t/->Def      (name (var->sym var)))))

(defn ns->thing
  "Function from a"
  [{:keys [groupid artifactid version platform]} ns-symbol]
  {:pre [(symbol? ns-symbol)
         (string? groupid)
         (string? artifactid)
         (string? version)
         (string? platform)]}
  (-> (t/->Group    groupid)
      (t/->Artifact artifactid)
      (t/->Version  version)
      (t/->Platform platform)
      (t/->Ns       (name ns-symbol))))

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
  "Function something (either a Namespace instance, a string or a symbol) to a
  string naming the input. Intended for use in computing the logical \"name\" of
  the :ns key which could have any of these values."
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
  "Function from something (either a symbol, string or something else) which if
  possible computes the logical \"name\" of the input as via clojure.core/name
  otherwise throws an explicit exception."
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
  "General case of writing documentation for a Var instance with
  metadata. Compute a \"docs\" structure from the var's metadata and then punt
  off to write-meta which does the heavy lifting."
  [config var]
  (let [docs (-> (meta var)
                 (assoc  :src  (var->src var)
                         :type (var->type var))
                 (update :name name-stringifier)
                 (update :ns   ns-stringifier)
                 (dissoc :inline
                         :protocol))]
    (api/write-meta config
                    (var->thing config var)
                    docs)))

(defn write-docs-for-specials
  "FIXME: this function needs to be purged and support for \"special forms\"
  needs to be added as an argument with some sort of datastructure file rather
  than doing it via the baked in clojure.repl/special-doc-map."
  [{:keys [groupid artifactid version] :as config}]
  (doseq [[sym {:keys [forms doc] :as fake-meta}] @#'clojure.repl/special-doc-map]
    (api/write-meta config
                    (t/->Def groupid artifactid version "clj" "clojure.core" (name sym))
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
  "Function of a configuration and a Namespace which traverses the public vars
  of that namespace, writing documentation for each var as specified by the
  config.

  FIXME: currently provides special handling for the case of documenting
  clojure.core, so that \"special forms\" in core will be documented via the
  write-docs-for-specials function. This behavior will change and be replaced
  with fully fledged support for writing documentation for arbitrary non-def
  symbols via an input datastructure."
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

    ;; FIXME: this shouldn't be needed
    (when (= ns 'clojure.core)
      (write-docs-for-specials config)))

  ;; FIXME: should be a real logging thing
  (println "Finished" ns)
  nil)

;; HACK: declare main so that the var literal can be used to get
;; documentation metadata for printing usage.
(declare -main)

(defn -main
  "Usage: lein grim [src|:src|source|:source] <platform> <dst>
  : lein grim [artifact|:artifact] <platform> <groupid> <artifactid> <version> <dst>

  In source mode, lein-grim traverses the source paths of the current project,
  enumerating and documenting all namespaces. This is intended for documenting
  projects for which you have both source and a lein project.

  In artifact mode, lein-grim traverses an artifact on the classpath enumerating
  and documenting the namespaces therein. This is intended for documenting
  projects such as clojure.core which may not exist as a covenient lein project
  but which do exist as artifacts."
  [p-groupid p-artifactid p-version p-source-paths ;; provided by lein
   mode-selector & args ;; user provided
   ]
  (case mode-selector
    ("artifact" :artifact)
    ,,(let [[platform
             groupid
             artifactid
             version
             dst]    args
             _        (assert platform "Platform missing!")
             platform (util/normalize-platform platform)
             _        (assert platform "Unknown platform!")
             _        (assert groupid "Groupid missing!")
             _        (assert artifactid "Artifactid missing!")
             _        (assert version "Version missing!")
             _        (assert dst "Doc target dir missing!")
             config   {:groupid    groupid
                       :artifactid artifactid
                       :version    version
                       :platform   platform
                       :datastore  {:docs dst
                                    :mode :filesystem}}
             pattern  (format ".*?/%s/%s/%s.*"
                              (string/replace groupid "." "/")
                              artifactid
                              version)
             pattern  (re-pattern pattern)]
        (doseq [e (cp/classpath)]
          (when (re-matches pattern (str e))
            (doseq [ns (tns.f/find-namespaces [e])]
              (when-not (= ns 'clojure.parallel) ;; FIXME: get out nobody likes you
                (require ns)
                (write-docs-for-ns config ns))))))

    ("src" :src "source" :source)
    ,,(let [[platform
             doc]    args
             _        (assert platform "Platform missing!")
             platform (util/normalize-platform platform)
             _        (assert platform "Unknown platform!")
             _        (assert doc "Doc target dir missing!")
             config   {:groupid    p-groupid
                       :artifactid p-artifactid
                       :version    p-version
                       :platform   platform
                       :datastore  {:docs (last args)
                                    :mode :filesystem}}]
        (doseq [ns (->> p-source-paths
                        (map io/file)
                        (tns.f/find-namespaces))]
          (require ns)
          (write-docs-for-ns config ns))

        (:doc (meta #'-main)))))
