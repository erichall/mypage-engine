(defproject mypage-engine "1.0.0"
  :description "Backend service for https://erkanp.dev"
  :dependencies [
                 [org.markdownj/markdownj "0.3.0-1.0.2b4"]
                 [org.clojure/clojure "1.10.0"]
                 [ysera "1.2.0"]
                 [http-kit "2.3.0"]
                 [clj-totp "0.1.0"]
                 [clj-time "0.15.0"]
                 [org.clojure/data.json "0.2.6"]
                 [ring/ring-core "1.7.1"]
                 [ring/ring-jetty-adapter "1.7.1"]
                 [ring/ring-devel "1.7.1"]
                 [nrepl/nrepl "0.6.0"]
                 [buddy/buddy-auth "2.2.0"]
                 [org.clojure/core.async "0.4.474"]
                 [reagi "0.10.1"]
                 [com.walmartlabs/lacinia "0.37.0-alpha-1"]]
  :main mypage-engine.main
  :profiles {:uberjar {:aot [mypage-engine.main]}}
  :repl-options {:init-ns mypage-engine.main}

  :extra-paths ["resources"])