package izumi.sbt.plugins.optional

import coursier.core.Version
import sbt.Keys.{isSnapshot, scalaOrganization, _}
import sbt.{Def, _}

object IzumiCompilerOptionsPlugin extends AutoPlugin {

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    scalacOptions ++=
      generalScalaSettings ++
      dynamicSettings(scalaOrganization.value, scalaVersion.value, isSnapshot.value),
    javacOptions ++= generalJavaSettings,
  )

  protected def dynamicSettings(scalaOrganization: String, scalaVersion: String, isSnapshot: Boolean): Seq[String] = {
    scalacOptionsVersion(scalaOrganization, scalaVersion) ++ releaseSettings(scalaVersion, isSnapshot)
  }

  def generalScalaSettings: Seq[String] = Seq(
    "-encoding", "UTF-8",
    "-target:jvm-1.8",
    "-language:higherKinds",
    "-feature",
    "-unchecked",
    "-deprecation",
  )

  def generalJavaSettings: Seq[String] = Seq(
    "-encoding", "UTF-8",
    "-source", "1.8",
    "-target", "1.8",
    "-deprecation",
    "-parameters",
    "-Xlint:all",
    "-XDignore.symbol.file"
    //, "-Xdoclint:all" // causes hard to track NPEs
  )

  protected def releaseSettings(scalaVersion: String, isSnapshot: Boolean): Seq[String] = {
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, 12|13)) if optimizerEnabled(scalaVersion, isSnapshot) =>
        Seq(
          "-opt:l:inline",
          "-opt-inline-from:izumi.**",
          "-opt-warnings:_"
        )
      case _ =>
        Seq()
    }
  }

  private def optimizerEnabled(scalaVersion: String, isSnapshot: Boolean) = {
    val javaVersion = sys.props.get("java.version") getOrElse {
      sys.error("failed to get system property java.version")
    }
    !isSnapshot && (Version(scalaVersion) > Version("2.12.6") || javaVersion.startsWith("1.8"))
  }

  protected def scalacOptionsVersion(scalaOrganization: String, scalaVersion: String): Seq[String] = {
    val versionSettings = CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, 12)) => Seq(
        "-Ypartial-unification"
        , "-Xsource:2.13"
        , "-Ybackend-parallelism", math.max(1, sys.runtime.availableProcessors() / 2).toString
        , "-opt-warnings:_"
        , "-Ywarn-unused:_"
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

        , "-opt-warnings:_" //2.12 only
        , "-Ywarn-extra-implicit"        // Warn when more than one implicit parameter section is defined.
        , "-Ywarn-unused:_"              // Enable or disable specific `unused' warnings: `_' for all, `-Ywarn-unused:help' to list choices.
        , "-Ywarn-adapted-args" // Warn if an argument list is modified to match the receiver.
        , "-Ywarn-dead-code" // Warn when dead code is identified.
        , "-Ywarn-inaccessible" // Warn about inaccessible types in method signatures.
        , "-Ywarn-infer-any" // Warn when a type argument is inferred to be `Any`.
        , "-Ywarn-nullary-override" // Warn when non-nullary `def f()' overrides nullary `def f'.
        , "-Ywarn-nullary-unit" // Warn when nullary methods return Unit.
        , "-Ywarn-numeric-widen" // Warn when numerics are widened.
        //, "-Ywarn-self-implicit"
        , "-Ywarn-unused-import" // Warn when imports are unused.
        , "-Ywarn-value-discard" // Warn when non-Unit expression results are unused.

      )
      case Some((2, 13)) => Seq(
//        "-Xsource:2.14", // Delay -Xsource:2.14 due to spurious warnings https://github.com/scala/bug/issues/11639
        "-Xsource:2.13",
        "-explaintypes",
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
//        "-Wself-implicit", // Spurious warnings for any top-level implicit, including scala.language._
        "-Wvalue-discard",
        "-Wunused:_",
        "-Xlint:_",
        "-Ybackend-parallelism", math.max(1, sys.runtime.availableProcessors() / 2).toString,
      )
      case _ =>
        Seq.empty
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
