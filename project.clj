(defproject mypage-engine "1.0.0"
  :description "Backend service for https://erkanp.dev"

  :dependencies [
                 [org.markdownj/markdownj "0.3.0-1.0.2b4"]
                 [org.clojure/clojure "1.10.0"]
                 [http-kit "2.3.0"]
                 [clj-time "0.15.0"]
                 [org.clojure/data.json "0.2.6"]
                 [nrepl/nrepl "0.6.0"]
                 [com.taoensso/timbre "5.0.0"]
                 [buddy/buddy-auth "2.2.0"]
                 [org.clojure/core.async "0.4.474"]
                 [com.walmartlabs/lacinia "0.37.0-alpha-1"]]
  :main mypage-engine.main

  :repl-options {:init-ns mypage-engine.main}
  :test-paths ["src" "test"]

  :profiles {:dev     {:injections [(println "including dev profile")]}
             :uberjar {
                       :aot        :all
                       :injections [(println "including prod profile")]
                       }
             :lint    {:source-paths ^:replace ["src"]
                       :test-paths   ^:replace []
                       :plugins      [[jonase/eastwood "0.3.10"]
                                      [lein-kibit "0.1.8"]]}}
  :eastwood {:exclude-linters [:bad-arglists]}
  :aliases {
            "eastwood" ["with-profile" "+lint" "eastwood" "{:namespaces [:source-paths]}"]
            "kibit"    ["with-profile" "+lint" "kibit"]
            "lint"     ["do"
                        ;["kibit"]
                        ["eastwood"]]
            })