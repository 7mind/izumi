package org.bitbucket.pshirshov.izumi.sbt

import sbt.Keys._
import sbt.{Def, _}

object CompilerOptionsPlugin extends AutoPlugin {
  def releaseSettings(isSnapshot: Boolean): Seq[String] = {
    if (isSnapshot) {
      Seq.empty
    } else {
      Seq(
        "-opt:_"
      )
    }
  }

  def scalacOptionsVersion(scalaVersion: String): Seq[String] =
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, 12)) => Seq(
        "-opt-warnings:_"
        , "-Ywarn-extra-implicit"
        , "-Ywarn-unused:_"

        , "-Ypartial-unification"
      )
      case _ =>
        Seq()
    }

  override lazy val projectSettings: Seq[Def.Setting[_]] = Seq(
    scalacOptions ++= releaseSettings(isSnapshot.value)
  )

  override lazy val globalSettings: Seq[Def.Setting[_]] = Seq(
      scalacOptions ++= Seq(
        "-encoding", "UTF-8"
        , "-target:jvm-1.8"

        //, "-Xfatal-warnings"
        
        , "-Xfuture"

        , "-feature"
        , "-unchecked"
        , "-deprecation"
        // , "-opt-warnings:_" //2.12 only
        //, "-Ywarn-extra-implicit"        // Warn when more than one implicit parameter section is defined.
        //, "-Ywarn-unused:_"              // Enable or disable specific `unused' warnings: `_' for all, `-Ywarn-unused:help' to list choices.

        , "-Xlint:_"
        , "-Ywarn-adapted-args"          // Warn if an argument list is modified to match the receiver.
        , "-Ywarn-dead-code"             // Warn when dead code is identified.
        , "-Ywarn-inaccessible"          // Warn about inaccessible types in method signatures.
        , "-Ywarn-infer-any"             // Warn when a type argument is inferred to be `Any`.
        , "-Ywarn-nullary-override"      // Warn when non-nullary `def f()' overrides nullary `def f'.
        , "-Ywarn-nullary-unit"          // Warn when nullary methods return Unit.
        , "-Ywarn-numeric-widen"         // Warn when numerics are widened.
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
        //, "-Xdoclint:all" // causes hard to track NPEs
        , "-XDignore.symbol.file"
      )
      , scalacOptions ++= scalacOptionsVersion(scalaVersion.value)
  )

}
