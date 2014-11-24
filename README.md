# lein-grim

A Leiningen plugin for generating
[Grimoire](https://github.com/clojure-grimoire/grimoire) documentation
folder structures using
[lib-grimoire](https://github.com/clojure-grimoire/lib-grimoire) and
[tools.namespace](https://github.com/clojure/tools.namespace).

## Usage

[![Clojars Project](http://clojars.org/org.clojure-grimoire/lein-grim/latest-version.svg)](http://clojars.org/org.clojure-grimoire/lein-grim)

First, add lein-grim to your leiningen plugins.

Second, create the lein-grim alias:

```
{:user {:aliases {"grim" ["run" "-m" "grimoire.doc"
                          ,,:project/groupid
                          ,,:project/artifactid
                          ,,:project/version
                          ,,:project/source-paths]}
```

lein-grim has two usage modes - source and artifact.

### Source

Source usage traverses the leingen source paths of a lein project,
loading namespaces defined in your source files and generating
Grimoire documentation written into the target directory you
specify. Here the directory `doc` is used as the target, however it is
reccomended that you provide to package Grimoire documentation blobs
independently from your source code so that individuals interested in
hosting Grimoire instances can use it as a submodule or other
resource.

```
$ cd my-project
$ lein grim src doc/
```

### Artifact

Some interesting objects such as the Clojure core don't have nice
leiningen projects which can be introspected. In this case, lein-grim
can introspect a jar as added to the classpath by leiningen from
`.m2`, locate all namespaces and defs in the targeted jar and generate
documentation without source.

```
$ lein new victim
$ cd victim
$ lein grim artifact org.clojure clojure 1.6.0 doc/
```

Here I generated an empty project as a vehicle for getting an instance
of Clojure 1.6.0 on the lein classpath and then invoked lein-grim to
write Grimoire documentation for all of clojure.core into the folder
`doc`.

Note that in the special case of clojure.core, lein-grim will ignore
`clojure.parallel` due to its dependency on non-standard jars which
kill documentation generation.

## License

Copyright © 2014 Reid "arrdem" McKenzie

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
