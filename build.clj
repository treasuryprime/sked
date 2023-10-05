(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'com.treasuryprime/sked)
(def version "1.0.2")
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file (format "target/%s-%s.jar" (name lib) version))

(defn clean [_]
  (b/delete {:path "target"}))

(defn jar [_]
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis basis
                :src-dirs ["src"]})
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn deploy [opts]
  (jar opts)
  (-> {:installer :remote
       :artifact jar-file
       :pom-file (b/pom-path {:lib lib :class-dir class-dir})}
      (merge opts)
      ((requiring-resolve 'deps-deploy.deps-deploy/deploy))))

(defn install [opts]
  (deploy (assoc opts :installer :local)))
