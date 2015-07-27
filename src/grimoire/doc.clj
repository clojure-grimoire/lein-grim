(ns grimoire.doc
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.repl] ;; gon crowbar into this...
            [clojure.data] ;; this too...
            [clojure.edn :as edn]
            [clojure.tools.namespace.find :as tns.f]
            [clojure.java.classpath :as cp]
            [grimoire.api :as api]
            [grimoire.api.fs :refer [->Config]]
            [grimoire.api.fs.write]
            [grimoire.api.fs.read]
            [grimoire.things :as t]
            [grimoire.util :as util]
            [grimoire.either :as e]
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

(defn guarded-write-meta
  "Guard around api/write-meta which checks to make sure that there is _not_
  already metadata in the store for the given Thing and issues a warning if
  there already _is_ metadata there without overwriting it."
  [config thing meta]
  (if (and (not (:clobber config))
           (e/succeed? (api/read-meta (:datastore config) thing)))
    (println
     (format "Warning: metadata for thing %s already exists! continuing w/o clobbering..."
             (t/thing->path thing)))
    (do (println (t/thing->path thing))
        (api/write-meta (:datastore config) thing meta))))

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
                         :protocol
                         :inline
                         :inline-arities))]
    (guarded-write-meta config
                        (var->thing config var)
                        docs)))

(defn write-docs-for-specials
  "Function of a config and what is presumed to be a legitimate .edn file
  containing a map from platform naming strings to maps from namespace qualified
  symbols to metadata for these symbols."
  [{:keys [groupid artifactid version platform] :as config} ?special-file]
  (assert (.exists ?special-file) "No such special symbols file!")
  (let [specials-data (edn/read-string (slurp ?special-file))
        ?specials     (get specials-data platform)]
    (if-not ?specials
      (println "Warning: No special forms for the given platform!, continuing...")
      (doseq [[sym meta] ?specials]
        (if-not (namespace sym)
          (println (str "Error: namespace unqualified special symbol " sym ", continuing..."))
          (guarded-write-meta config
                              (t/->Def groupid artifactid version
                                       platform (namespace sym) (name sym))
                              meta))))))

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
        vars    (filter #(not (fn? @%1)) ns-vars)
        ns-meta (-> ns the-ns meta (or {}))]

    (when-not (:skip-wiki ns-meta)
      ;; Respect ^:skip-wiki from clojure-grimoire/lein-grim#4

      (let [thing (ns->thing config ns)]
        (api/write-meta (:datastore config) thing ns-meta))

      ;; write per symbol docs
      (doseq [var ns-vars]
        (write-docs-for-var config var)))

    ;; FIXME: should be a real logging thing
    (println "Finished" ns)
    nil))

(defn maybe-take-pair [leader args]
  (if (= (first args) leader)
    [(second args) (drop 2 args)]
    [nil args]))

;; HACK: declare main so that the var literal can be used to get
;; documentation metadata for printing usage.
(declare -main)

(defn -main
  "Usage: lein grim [opts] [src|:src|source|:source] <platform> <dst>
     : lein grim [opts] [artifact|:artifact] <platform> <groupid> <artifactid> <version> <dst>

  In source mode, lein-grim traverses the source paths of the current project,
  enumerating and documenting all namespaces. This is intended for documenting
  projects for which you have both source and a lein project.

  In artifact mode, lein-grim traverses an artifact on the classpath enumerating
  and documenting the namespaces therein. This is intended for documenting
  projects such as clojure.core which may not exist as a covenient lein project
  but which do exist as artifacts.

  Both modes accept the following options which must be specified in order or
  omitted.

  Arguments
  --------------------------------------------------------------------------------
  <platform>
    One of clj cljs or cljclr. Indicates what Clojure platform is being
  documented. Only one may be selected at a time. At present, only clj is
  supported.

  <groupid>
    A string naming the Maven group of the artifact being documented.

  <artifactid>
    A string naming the Maven artifactId of the artifact being documented.

  <version>
    A string giving the Maven version of the artifact being documented.

  <dst>
    A string naming the file path of a directory where the generated
  documentation will be stored.

  Options
  --------------------------------------------------------------------------------
  --specials <file>
    Specifies an EDN map from platform strings to namespaced symbols to
  metadata. Symbols listed in this file will be added to the generated
  documentation with the specified metadata. If specified, specials must be the
  1st option given.

  --clobber [true | false]
    Enables or disables overwriting metadata which already exists. When
  disabled, attempting to write metadata to a symbol in an artifact which has
  already been documented will generate a warning. Should be enabled only if
  re-generating documentation in place without cleaning the target dir or if the
  specials file should _overwrite_ generated documentation. If specified, this
  option may be proceeded only by specials. Other values than true or false will
  be interpreted as false."
  [p-groupid
   p-artifactid   ;; note that lein calls this "name" and not "artifactid"
   p-version
   p-source-paths ;; all provided by lein via profile
   & args ;; user provided
   ]
  (let [[?special-file args]             (maybe-take-pair "--specials" args)
        ?special-file                    (when ?special-file (io/file ?special-file))
        [?clobber args]                  (maybe-take-pair "--clobber" args)
        clobber                          (if (= "true" ?clobber) true false)
        [mode-selector ?platform & args] args]
    (println ?special-file clobber args)
    (case mode-selector
      ("artifact" :artifact)
      ,,(let [[groupid
               artifactid
               version
               dst]     args
               _        (assert ?platform "Platform missing!")
               platform (util/normalize-platform ?platform)
               _        (assert platform "Unknown platform!")
               _        (assert groupid "Groupid missing!")
               _        (assert artifactid "Artifactid missing!")
               _        (assert version "Version missing!")
               _        (assert dst "Doc target dir missing!")
               config   {:groupid    groupid
                         :artifactid artifactid
                         :version    version
                         :platform   platform
                         :datastore  (->Config dst "" "")
                         :clobber    clobber}
               pattern  (format ".*?/%s/%s/%s.*"
                                (string/replace groupid "." "/")
                                artifactid
                                version)
               pattern  (re-pattern pattern)]

          ;; write placeholder meta
          ;;----------------------------------------
          (reduce (fn [acc f]
                    (api/write-meta (:datastore config) acc {})
                    (f acc))
                  (t/->Group groupid)
                  [#(t/->Artifact % artifactid)
                   #(t/->Version % version)
                   #(t/->Platform % platform)
                   identity])

          (doseq [e (cp/classpath)]
            (when (re-matches pattern (str e))
              (doseq [ns (tns.f/find-namespaces [e])]
                (when-not (= ns 'clojure.parallel) ;; FIXME: get out nobody likes you
                  (require ns)
                  (write-docs-for-ns config ns)))))

          (when ?special-file
            (write-docs-for-specials config ?special-file)))

      ("src" :src "source" :source)
      ,,(let [[doc]    args
              _        (assert ?platform "Platform missing!")
              platform (util/normalize-platform ?platform)
              _        (assert platform "Unknown platform!")
              _        (assert doc "Doc target dir missing!")
              config   {:groupid    p-groupid
                        :artifactid p-artifactid
                        :version    p-version
                        :platform   platform
                        :datastore  (->Config (last args) "" "")
                        :clobber    clobber}]

          ;; write placeholder meta
          ;;----------------------------------------
          (reduce (fn [acc f]
                    (api/write-meta (:datastore config) acc {})
                    (f acc))
                  (t/->Group p-groupid)
                  [#(t/->Artifact % p-artifactid)
                   #(t/->Version % p-version)
                   #(t/->Platform % platform)
                   identity])

          (doseq [ns (->> p-source-paths
                          (map io/file)
                          (tns.f/find-namespaces))]
            (require ns)
            (write-docs-for-ns config ns))

          (when ?special-file
            (write-docs-for-specials config ?special-file)))

      ;; Implicit else
      (println (:doc (meta #'-main))))))
