package izumi.fundamentals.platform.build

import scala.language.experimental.macros

/**
  * These helpers allow you to save various build-time properties defined by Scala 2
  * `-Xmacro-settings` commandline argument.
  *
  * Add the following properties into your `build.sbt` to make this work:
  *
  * {{{
  * scalacOptions ++= Seq(
  *   s"-Xmacro-settings:product-version=${version.value}",
  *   s"-Xmacro-settings:product-group=${organization.value}",
  *   s"-Xmacro-settings:product-name=${name.value}",
  *   s"-Xmacro-settings:sbt-version=${sbtVersion.value}",
  *   s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}",
  *
  *   s"-Xmacro-settings:scalatest-version=${V.scalatest}",
  *
  *   s"-Xmacro-settings:git-repo-clean=${gitUncommittedChanges.value}",
  *   s"-Xmacro-settings:git-branch=${gitCurrentBranch.value}",
  *
  *   s"-Xmacro-settings:is-ci=${insideCI.value}",
  * )
  * }}}
  *
  * Git snippets:
  *
  * {{{
  *    gitDescribedVersion.value.map(v => s"-Xmacro-settings:git-described-version=$v"),
  *    gitHeadCommit.value.map(v => s"-Xmacro-settings:git-head-commit=$v"),
  * }}}
  *
  * Or
  *
  * {{{
  *   s"-Xmacro-settings:git-described-version=${gitDescribedVersion.value.getOrElse("")}",
  *   s"-Xmacro-settings:git-head-commit=${gitHeadCommit.value.getOrElse("")}",
  * }}}
  */
object MacroParameters {
  def scalaVersion(): Option[String] = macro MacroParametersImpl.scalaVersionMacro
  def scalaCrossVersions(): Option[String] = macro MacroParametersImpl.scalaVersionsMacro

  def projectGroupId(): Option[String] = macro MacroParametersImpl.projectGroupIdMacro
  def artifactVersion(): Option[String] = macro MacroParametersImpl.projectVersionMacro
  def artifactName(): Option[String] = macro MacroParametersImpl.projectNameMacro

  def sbtVersion(): Option[String] = macro MacroParametersImpl.sbtVersionMacro
  def scalatestVersion(): Option[String] = macro MacroParametersImpl.scalatestVersionMacro

  def gitRepoClean(): Option[Boolean] = macro MacroParametersImpl.gitRepoClean
  def gitBranch(): Option[String] = macro MacroParametersImpl.gitBranch
  def gitHeadCommit(): Option[String] = macro MacroParametersImpl.gitHeadCommit
  def gitDescribedVersion(): Option[String] = macro MacroParametersImpl.gitHeadCommit

  def sbtIsInsideCI(): Option[Boolean] = macro MacroParametersImpl.sbtIsCi


  def macroSetting(name: String): Option[String] = macro MacroParametersImpl.extractAttrMacro

  def macroSettingBool(name: String): Option[Boolean] = macro MacroParametersImpl.extractAttrBoolMacro
}
