package izumi.fundamentals.platform.properties

trait EnvVars {
  case class EnvVar protected (name: String) {
    def get(): Option[String] = Option(System.getenv().get(name))
    def isDefined(): Boolean = System.getenv().containsKey(name)
  }
}

object EnvVarsCI extends EnvVars {
  final val CI_JENKINS = EnvVar("CI_JENKINS")
  final val CI_BRANCH = EnvVar("CI_BRANCH")

  def isIzumiCI(): Boolean = Seq(CI_JENKINS, CI_BRANCH).exists(_.isDefined())
}
