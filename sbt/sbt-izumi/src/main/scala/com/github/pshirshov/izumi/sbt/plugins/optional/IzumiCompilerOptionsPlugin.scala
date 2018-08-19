package com.github.pshirshov.izumi.sbt.plugins.optional

import sbt.Keys.{isSnapshot, scalaOrganization, _}
import sbt.{Def, _}

object IzumiCompilerOptionsPlugin extends AutoPlugin {
  override lazy val globalSettings: Seq[Def.Setting[_]] = Seq(
    scalacOptions := Seq(
      "-encoding", "UTF-8"
      , "-target:jvm-1.8"

      //, "-Xfatal-warnings"

      , "-Xfuture"
      , "-language:higherKinds"

      , "-feature"
      , "-unchecked"
      , "-deprecation"
      // , "-opt-warnings:_" //2.12 only
      //, "-Ywarn-extra-implicit"        // Warn when more than one implicit parameter section is defined.
      //, "-Ywarn-unused:_"              // Enable or disable specific `unused' warnings: `_' for all, `-Ywarn-unused:help' to list choices.

      , "-Xlint:_"


      , "-Ywarn-adapted-args" // Warn if an argument list is modified to match the receiver.
      , "-Ywarn-dead-code" // Warn when dead code is identified.
      , "-Ywarn-inaccessible" // Warn about inaccessible types in method signatures.
      , "-Ywarn-infer-any" // Warn when a type argument is inferred to be `Any`.
      , "-Ywarn-nullary-override" // Warn when non-nullary `def f()' overrides nullary `def f'.
      , "-Ywarn-nullary-unit" // Warn when nullary methods return Unit.
      , "-Ywarn-numeric-widen" // Warn when numerics are widened.
      , "-Ywarn-unused-import" // Warn when imports are unused.
      , "-Ywarn-value-discard" // Warn when non-Unit expression results are unused.
    )

    , javacOptions ++= Seq(
      "-encoding", "UTF-8"
      , "-source", "1.8"
      , "-target", "1.8"
      , "-deprecation"
      , "-parameters"
      , "-Xlint:all"
      , "-XDignore.symbol.file"
      //, "-Xdoclint:all" // causes hard to track NPEs
    )
  )

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    scalacOptions ++= dynamicSettings(scalaOrganization.value, scalaVersion.value, isSnapshot.value)
  )

  protected def dynamicSettings(scalaOrganization: String, scalaVersion: String, isSnapshot: Boolean): Seq[String] = {
    scalacOptionsVersion(scalaOrganization, scalaVersion) ++ releaseSettings(scalaVersion, isSnapshot)
  }

  protected def releaseSettings(scalaVersion: String, isSnapshot: Boolean): Seq[String] = {
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, 12)) if !isSnapshot => Seq(
        "-opt:l:inline"
        , "-opt-inline-from"
      )
      case _ =>
        Seq()
    }
  }

  protected def scalacOptionsVersion(scalaOrganization: String, scalaVersion: String): Seq[String] = {
    val versionSettings = CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, 12)) => Seq(
        "-opt-warnings:_"
        , "-Ywarn-extra-implicit"
        , "-Ywarn-unused:_"
        , "-Ypartial-unification"
        , "-Yno-adapted-args"
        , "-explaintypes" // Explain type errors in more detail.
        , "-Xlint:adapted-args" // Warn if an argument list is modified to match the receiver.
        , "-Xlint:by-name-right-associative" // By-name parameter of right associative operator.
        , "-Xlint:constant" // Evaluation of a constant arithmetic expression results in an error.
        , "-Xlint:delayedinit-select" // Selecting member of DelayedInit.
        , "-Xlint:doc-detached" // A Scaladoc comment appears to be detached from its element.
        , "-Xlint:inaccessible" // Warn about inaccessible types in method signatures.
        , "-Xlint:infer-any" // Warn when a type argument is inferred to be `Any`.
        , "-Xlint:missing-interpolator" // A string literal appears to be missing an interpolator id.
        , "-Xlint:nullary-override" // Warn when non-nullary `def f()' overrides nullary `def f'.
        , "-Xlint:nullary-unit" // Warn when nullary methods return Unit.
        , "-Xlint:option-implicit" // Option.apply used implicit view.
        , "-Xlint:package-object-classes" // Class or object defined in package object.
        , "-Xlint:poly-implicit-overload" // Parameterized overloaded implicit methods are not visible as view bounds.
        , "-Xlint:private-shadow" // A private field (or class parameter) shadows a superclass field.
        , "-Xlint:stars-align" // Pattern sequence wildcard must align with sequence component.
        , "-Xlint:type-parameter-shadow" // A local type parameter shadows a type already in scope.
        , "-Xlint:unsound-match" // Pattern match may not be typesafe.
      )
      case _ =>
        Seq()
    }
    val orgSettings =
      if (scalaOrganization == "org.typelevel") {
        Seq(
          "-Yinduction-heuristics" // speeds up the compilation of inductive implicit resolution
          , "-Xstrict-patmat-analysis" // more accurate reporting of failures of match exhaustivity
          , "-Xlint:strict-unsealed-patmat" // warn on inexhaustive matches against unsealed traits
        )
      } else {
        Seq()
      }
    versionSettings ++ orgSettings
  }
}
