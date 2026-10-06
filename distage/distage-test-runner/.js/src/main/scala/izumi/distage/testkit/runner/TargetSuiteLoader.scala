package izumi.distage.testkit.runner

import scala.scalajs.reflect.Reflect

final class TargetSuiteLoader {
  def load(name: String): TestSuite = {
    val retained = Reflect.lookupInstantiatableClass(name).getOrElse(throw new IllegalArgumentException("Suite is not retained in the target: " + name))
    retained.newInstance() match {
      case suite: TestSuite => suite
      case _ => throw new IllegalArgumentException("Selected class is not a test suite: " + name)
    }
  }
}
