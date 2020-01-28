package izumi.distage.injector

import distage.ModuleDef
import izumi.distage.fixtures.ProviderCases.ProviderCase3
import izumi.distage.fixtures.ProviderCases.ProviderCase3.TestDependency
import org.scalatest.wordspec.AnyWordSpec

class ProvidersTestJVM extends AnyWordSpec with MkInjector {

  "provider equality works for def/class/trait ModuleDefs with functions inside on the JVM" in {
    import ProviderCase3._

    def x() = (i: Int) => new TestDependency
    assert(x() == x())

    class Definition extends ModuleDef {
      make[TestDependency].from {
        () => new TestDependency
      }
    }

    val definition = new Definition
    val combinedDefinition = new Definition ++ new Definition
    val valDefinition = definition ++ definition

    assert(combinedDefinition == definition)
    assert(valDefinition == definition)
  }

}
