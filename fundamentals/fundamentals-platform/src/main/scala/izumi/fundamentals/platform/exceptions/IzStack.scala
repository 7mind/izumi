package izumi.fundamentals.platform.exceptions

import izumi.fundamentals.platform.IzPlatformEffectfulUtil

trait IzStack extends IzPlatformEffectfulUtil {
  def currentStack: String
  def currentStack(acceptedPackages: Set[String]): String
}

object IzStack extends IzStack {

  import IzThrowable._

  def currentStack: String = {
    new RuntimeException().shortTrace
  }

  def currentStack(acceptedPackages: Set[String]): String = {
    new RuntimeException().forPackages(acceptedPackages).shortTrace
  }
}
