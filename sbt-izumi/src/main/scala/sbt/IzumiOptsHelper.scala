package sbt

import sbt.Keys.libraryDependencies

object IzumiOptsHelper {
  val withCoursier: _root_.sbt.Def.SettingsDefinition = {
    if (sys.props.get("build.coursier.use").contains("true")) {
      addSbtPlugin("io.get-coursier" % "sbt-coursier" % sys.props.get("build.coursier.version").getOrElse("1.0.0-RC13"))
    } else {
      libraryDependencies ++= Seq()
    }
  }
}
