package izumi.fundamentals.platform.build

import scala.quoted.{Expr, Quotes, Type}

object MacroParameters {

  inline def scalaVersion(): Option[String] = macroSetting("scala-version")
  inline def scalaCrossVersions(): Option[String] = macroSetting("scala-versions")

  inline def projectGroupId(): Option[String] = macroSetting("product-group")
  inline def artifactVersion(): Option[String] = macroSetting("product-version")
  inline def artifactName(): Option[String] = macroSetting("product-name")

  inline def sbtVersion(): Option[String] = macroSetting("sbt-version")
  inline def scalatestVersion(): Option[String] = macroSetting("scalatest-version")

  inline def gitRepoClean(): Option[Boolean] = macroSettingBool("git-repo-clean")
  inline def gitBranch(): Option[String] = macroSetting("git-branch")
  inline def gitHeadCommit(): Option[String] = macroSetting("git-head-commit")
  inline def gitDescribedVersion(): Option[String] = macroSetting("git-described-version")

  inline def sbtIsInsideCI(): Option[Boolean] = macroSettingBool("is-ci")

  inline def macroSetting(inline name: String): Option[String] = {
    ${ MacroParametersImpl.extractString('{ name }) }
  }

  inline def macroSettingBool(inline name: String): Option[Boolean] = {
    ${ MacroParametersImpl.extractBool('{ name }) }
  }

}
