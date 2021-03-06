(ns leiningen.new.game-java
  (:require [clojure.java.io :as io]
            [leiningen.droid.new :as droid-new]
            [leiningen.new.templates :as t]))

(defn game-java
  [name package-name]
  (let [render (t/renderer "game-java")
        android-render (t/renderer "android-java")
        lein-droid-render (droid-new/renderer "templates")
        class-name "Main"
        screen-class-name "MainScreen"
        desktop-class-name "DesktopLauncher"
        android-class-name "AndroidLauncher"
        ios-class-name "IOSLauncher"
        package-name (t/sanitize (t/multi-segment (or package-name name)))
        main-ns (str package-name "." class-name)
        screen-ns (str package-name "." screen-class-name)
        desktop-ns (str package-name "." desktop-class-name)
        android-ns (str package-name "." android-class-name)
        ios-ns (str package-name "." ios-class-name)
        data {:app-name name
              :name (t/project-name name)
              :package package-name
              :package-sanitized package-name
              :class-name class-name
              :screen-class-name screen-class-name
              :desktop-class-name desktop-class-name
              :android-class-name android-class-name
              :ios-class-name ios-class-name
              :namespace main-ns
              :screen-namespace screen-ns
              :desktop-namespace desktop-ns
              :android-namespace android-ns
              :ios-namespace ios-ns
              :path (t/name-to-path main-ns)
              :screen-path (t/name-to-path screen-ns)
              :desktop-path (t/name-to-path desktop-ns)
              :android-path (t/name-to-path android-ns)
              :ios-path (t/name-to-path ios-ns)
              :year (t/year)
              :target-sdk "15"}]
    (t/->files data
               ; main
               ["README.md" (render "README.md" data)]
               [".gitignore" (render "gitignore" data)]
               ; desktop
               ["desktop/project.clj" (render "desktop-project.clj" data)]
               ["desktop/src-common/{{path}}.java"
                (render "Main.java" data)]
               ["desktop/src-common/{{screen-path}}.java"
                (render "MainScreen.java" data)]
               ["desktop/src/{{desktop-path}}.java"
                (render "DesktopLauncher.java" data)]
               "desktop/resources"
               ; android
               ["android/project.clj"
                (render "android-project.clj" data)]
               ["android/src/{{android-path}}.java"
                (render "AndroidLauncher.java" data)]
               ["android/AndroidManifest.xml"
                (render "AndroidManifest.xml" data)]
               ["android/res/drawable-hdpi/ic_launcher.png"
                (lein-droid-render "ic_launcher_hdpi.png")]
               ["android/res/drawable-mdpi/ic_launcher.png"
                (lein-droid-render "ic_launcher_mdpi.png")]
               ["android/res/drawable-ldpi/ic_launcher.png"
                (lein-droid-render "ic_launcher_ldpi.png")]
               ["android/res/values/strings.xml"
                (lein-droid-render "strings.xml" data)]
               ["android/libs/armeabi/libgdx.so"
                (-> "armeabi-libgdx.so" io/resource io/input-stream)]
               ["android/libs/armeabi-v7a/libgdx.so"
                (-> "armeabi-v7a-libgdx.so" io/resource io/input-stream)]
               ["android/libs/x86/libgdx.so"
                (-> (io/resource "x86-libgdx.so") io/input-stream)]
               ; ios
               ["ios/project.clj" (render "ios-project.clj" data)]
               ["ios/Info.plist.xml" (render "Info.plist.xml" data)]
               ["ios/src/{{ios-path}}.java" (render "IOSLauncher.java" data)]
               ["ios/libs/libObjectAL.a"
                (-> (io/resource "libObjectAL.a") io/input-stream)]
               ["ios/libs/libgdx.a"
                (-> (io/resource "libgdx.a") io/input-stream)])))
