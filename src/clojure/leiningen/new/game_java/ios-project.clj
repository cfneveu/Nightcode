(defproject {{app-name}} "0.0.1-SNAPSHOT"
  :description "FIXME: write description"
  :dependencies [[com.badlogicgames.gdx/gdx "0.9.9"]
                 [com.badlogicgames.gdx/gdx-backend-robovm "0.9.9"]]
  :java-source-paths ["src" "../desktop/src-common"]
  :javac-options ["-target" "1.7" "-source" "1.7" "-Xlint:-options"]
  :java-only true
  :ios {:robovm-opts ["-libs" "libs/libObjectAL.a:libs/libgdx.a"
                      "-frameworks" "UIKit:OpenGLES:QuartzCore:CoreGraphics:OpenAL:AudioToolbox:AVFoundation"
                      "-resources" "../desktop/resources/**"]}
  :aot :all
  :main {{ios-namespace}})
