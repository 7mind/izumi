package izumi.fundamentals.platform.build

object MacroParameters {
  def scalaVersion(): Option[String] = ???
  def scalaCrossVersions(): Option[String] = ???

  def projectGroupId(): Option[String] = ???
  def artifactVersion(): Option[String] = ???
  def artifactName(): Option[String] = ???

  def sbtVersion(): Option[String] = ???
  def scalatestVersion(): Option[String] = ???

  def gitRepoClean(): Option[Boolean] = ???
  def gitBranch(): Option[String] = ???
  def gitHeadCommit(): Option[String] = ???
  def gitDescribedVersion(): Option[String] = ???

  def sbtIsInsideCI(): Option[Boolean] = ???

  def macroSetting(name: String): Option[String] = ???

  def macroSettingBool(name: String): Option[Boolean] = ???
}
