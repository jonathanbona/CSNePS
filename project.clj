(defproject Sneps3-Clojure "1.0.0-SNAPSHOT"
  :description "CSNePS - SNePS 3 in Clojure"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/core.incubator "0.1.2"]
                 [org.clojure/math.numeric-tower "0.0.1"]
                 [org.clojure/math.combinatorics "0.0.2"]
                 [org.clojure/tools.trace "0.7.3"]
                 [org.clojure/tools.nrepl "0.2.0"]
                 [net.sf.jung/jung-graph-impl "2.0.1"]
                 [net.sf.jung/jung-api "2.0.1"]
                 [net.sf.jung/jung-visualization "2.0.1"]
                 [net.sf.jung/jung-io "2.0.1"]
                 [net.sf.jung/jung-algorithms "2.0.1"]
                 [net.sf.jung/jung-jai "2.0.1"]
                 [net.sf.jung/jung-3d "2.0.1"]
                 [junit/junit "3.8.2"]
                 [jdom/jdom "1.0"]
                 [org.freehep/freehep-graphicsio "2.1.4"]
                 [org.freehep/freehep-export "2.1.3"]
                 [org.freehep/freehep-graphicsio-emf "2.1.4"]
                 [org.freehep/freehep-graphicsio-java "2.1.4"]
                 [org.freehep/freehep-graphicsio-gif "1.2.3"]
                 [org.freehep/freehep-graphicsio-pdf "2.1.4"]
                 [org.freehep/freehep-graphicsio-ps "2.1.4"]
                 [org.freehep/freehep-graphicsio-svg "2.1.4"]
                 [org.freehep/freehep-graphicsio-swf "2.1.4"]
                 [org.freehep/freehep-xml "2.1.9"]
                 [jpedal/jpedal "4.45-b-105"]
                 [org.swinglabs/swingx "1.6.1"]
                 [net.xeon/jspf.core "1.0.2"]]
  :dev [[org/clojure/tools.namespace "0.1.2"]]
  :repositories {"FreeHEP" "http://java.freehep.org/maven2"
                 "jpedal" "http://maven.geomajas.org"
                 "local" ~(str (.toURI (java.io.File. "local_maven_repo")))}
  :plugins [[lein-swank "1.4.4"]]
  :source-paths ["src/clj/"]
  :source-path "src/clj/"
  :java-source-paths ["src/jvm/"] ;leiningen 2 compat.
  :java-source-path "src/jvm/" ;leiningen 1.x compat.
  ;:project-init (require 'clojure.pprint) 
  :repl-options [:print clojure.core/println] ;[:print clojure.pprint/pprint]
  :jvm-opts ["-server"] 
  :main csneps.core.snuser)
