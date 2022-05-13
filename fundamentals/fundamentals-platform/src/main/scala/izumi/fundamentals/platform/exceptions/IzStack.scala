package izumi.fundamentals.platform.exceptions

object IzStack {

  import IzThrowable._

  def currentStack: String = {
    new RuntimeException.shortTrace
  }

  def currentStack(acceptedPackages: Set[String]): String = {
    new RuntimeException.forPackages(acceptedPackages).shortTrace
  }
}
