package izumi.distage.injector

import izumi.distage.fixtures.CircularCases.*
import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.ModuleDef
import org.scalatest.wordspec.AnyWordSpec

class TrueCircularDependenciesTest extends AnyWordSpec with MkInjector {
  "support proxies in sets (non-immediate case) https://github.com/7mind/izumi/issues/482" in {
    import CircularCase11.*

    val definition = PlannerInput.everything(new ModuleDef {
      make[T1].from[Circular1Impl]
      make[T2].from[Circular2Impl]

      many[Service]
        .ref[T1]
        .ref[T2]
    })

    val injector = mkInjector()
    val context = injector.produce(definition).unsafeGet()

    // in fact we won't even try to add the proxy into the set, it'll be done later
    val set = context.get[Set[Service]]
    assert(set.hashCode() != 0)
    assert(set.toString().nonEmpty)
    assert(set == set)
    assert(set.size == 2)
  }

  "support proxies in sets (immediate case) https://github.com/7mind/izumi/issues/482" in {
    import CircularCase12.*

    val definition = PlannerInput.everything(new ModuleDef {
      make[T1].from[Circular1Impl]
      make[T2].from[Circular2Impl]

      many[Service]
        .ref[T1]
        .ref[T2]
    })

    val injector = mkInjector()
    val context = injector.produce(definition).unsafeGet()

    val set = context.get[Set[Service]]

    assert(set.hashCode() != 0)
    assert(set.toString().nonEmpty)
    assert(set == set)
    assert(set.forall(_.arg.size == 2))
    assert(set.size == 2)
  }

  "support proxies in sets (completely immediate case) https://github.com/7mind/izumi/issues/482" in {
    import CircularCase13.*

    val definition = PlannerInput.everything(new ModuleDef {
      make[T1].from[Circular1Impl]
      make[T2].from[Circular2Impl]

      many[Service]
        .ref[T1]
        .ref[T2]
    })

    val injector = mkInjector()
    val context = injector.produce(definition).unsafeGet()

    val set = context.get[Set[Service]]

    // there is no sane way to represent a case class having itself as an argument
    intercept[java.lang.reflect.InvocationTargetException] {
      assert(set.toString().nonEmpty)
    }
    intercept[java.lang.reflect.InvocationTargetException] {
      assert(set.hashCode() != 0)
    }
    assert(set == set)
    assert(set.forall(_.arg.size == 2))
    assert(set.size == 2)
  }
}
