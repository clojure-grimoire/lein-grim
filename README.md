# lein-grim

A Leiningen plugin for generating
[Grimoire](https://github.com/clojure-grimoire/grimoire) documentation
folder structures using
[lib-grimoire](https://github.com/clojure-grimoire/lib-grimoire) and
[tools.namespace](https://github.com/clojure/tools.namespace).

## Usage

[![Clojars Project](http://clojars.org/org.clojure-grimoire/lein-grim/latest-version.svg)](http://clojars.org/org.clojure-grimoire/lein-grim)

1. Add the latest version to your lein `:plugins`
2. `cd` into the root of a target project
3. `$ lein grim $NS_REGEX $DOC_DST_DIR`

lein-grim will enumerate all the namespaces on your classpath, load
them and generate documentation records for all public vars and
namespaces writing it out into the specified destination dir using
`lib-grimoire` for all file I/O. This output directory can either be
used to run a local instance of Grimoire, or version controlled and
submitted to Grimoire for web hosting.

## License

Copyright © 2014 Reid "arrdem" McKenzie

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
