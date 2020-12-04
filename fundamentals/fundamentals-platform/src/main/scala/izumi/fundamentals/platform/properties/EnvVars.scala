package izumi.fundamentals.platform.properties

trait EnvVars {
  case class EnvVar protected (name: String) {
    def get(): Option[String] = Option(System.getenv().get(name))
    def isDefined(): Boolean = System.getenv().containsKey(name)
  }
}
