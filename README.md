# lein-grim

A Leiningen plugin for generating [Grimoire](https://github.com/clojure-grimoire/grimoire) documentation folder structures using [lib-grimoire](https://github.com/clojure-grimoire/lib-grimoire) and [tools.namespace](https://github.com/clojure/tools.namespace).

## Usage

[![Clojars Project](http://clojars.org/org.clojure-grimoire/lein-grim/latest-version.svg)](http://clojars.org/org.clojure-grimoire/lein-grim)

In `.lein/profiles.clj`, add lein-grim to your leiningen `:dependencies` and create the lein-grim alias:

```
{:user
   {:dependencies [org.clojure-grimoire/lein-grim <latest version>]}
   {:aliases
     {"grim" ["run" "-m" "grimoire.doc"
              ,,:project/group
              ,,:project/name
              ,,:project/version
              ,,nil]}}}
```

**FOR FULL DOCS** simply run `lein grim help`.

lein-grim has two usage modes - source and artifact.

### Source

**DUE TO A BUG IN LEIN THIS MODE IS BROKEN**.
Do _not_ attempt to use this mode, it'll fail for reasons beyond my control.
technomancy/leiningen#1835 is the issue in question.
Until the next Leiningen release I'm afraid this feature is simply broken.

Source usage traverses the leingen source paths of a lein project, loading namespaces defined in your source files and generating Grimoire documentation written into the target directory you specify.
Here the directory `doc` is used as the target, however it is reccomended that you provide to package Grimoire documentation blobs independently from your source code so that individuals interested in hosting Grimoire instances can use it as a submodule or other resource.

```
$ cd my-project
$ lein grim src clj doc/
```

### Artifact

Some interesting objects such as the Clojure core don't have nice leiningen projects which can be introspected.
In this case, lein-grim can introspect a jar as added to the classpath by leiningen from `.m2`, locate all namespaces and defs in the targeted jar and generate documentation without source.

```
$ lein new victim
$ cd victim
$ lein grim artifact clj org.clojure clojure 1.6.0 doc/
```

Here I generated an empty project as a vehicle for getting an instance of Clojure 1.6.0 on the lein classpath and then invoked lein-grim to write Grimoire documentation for all of clojure.core into the folder `doc`.

**Note** In the special case of clojure.core, lein-grim will ignore `clojure.parallel` due to its dependency on non-standard jars which kill documentation generation.

**Note** Normally you have to add the artifact you want to introspect to the `:dependencies` in `project.clj`. This step wasn't necessary in the example above, because `[org.clojure/clojure "1.6.0"]` is in there by default.

## License

Copyright © 2014 Reid "arrdem" McKenzie

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
