package org.bitbucket.pshirshov.izumi.sbt

import sbt.Keys._
import sbt.{Def, _}

object CompilerOptionsPlugin extends AutoPlugin {
  def releaseSettings(isRelease: Boolean): Seq[Def.Setting[_]] = {
    if (isRelease) {
      Seq(
        scalacOptions ++= Seq(
          "-opt:_"
        )
      )
    } else {
      Seq.empty
    }
  }

  override lazy val globalSettings: Seq[Def.Setting[_]] = Seq(
      scalacOptions ++= Seq(
        "-encoding", "UTF-8"
        , "-target:jvm-1.8"

        , "-Xfuture"

        //, "-Xfatal-warnings"

        , "-feature"
        , "-unchecked"
        , "-deprecation"
        , "-opt-warnings:_"

        , "-Xlint:_"

        , "-Ywarn-adapted-args"          // Warn if an argument list is modified to match the receiver.
        , "-Ywarn-dead-code"             // Warn when dead code is identified.
        , "-Ywarn-extra-implicit"        // Warn when more than one implicit parameter section is defined.
        , "-Ywarn-inaccessible"          // Warn about inaccessible types in method signatures.
        , "-Ywarn-infer-any"             // Warn when a type argument is inferred to be `Any`.
        , "-Ywarn-nullary-override"      // Warn when non-nullary `def f()' overrides nullary `def f'.
        , "-Ywarn-nullary-unit"          // Warn when nullary methods return Unit.
        , "-Ywarn-numeric-widen"         // Warn when numerics are widened.
        , "-Ywarn-unused:_"              // Enable or disable specific `unused' warnings: `_' for all, `-Ywarn-unused:help' to list choices.
        , "-Ywarn-unused-import"         // Warn when imports are unused.
        , "-Ywarn-value-discard"         // Warn when non-Unit expression results are unused.
      )

      , javacOptions ++= Seq(
        "-encoding", "UTF-8"
        , "-source", "1.8"
        , "-target", "1.8"
        , "-deprecation"
        , "-parameters"
        , "-Xlint:all"
        , "-Xdoclint:all"
        , "-XDignore.symbol.file"
      )
  ) //++ releaseSettings(!isSnapshot.value)

}
