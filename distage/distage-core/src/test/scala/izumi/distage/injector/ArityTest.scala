package izumi.distage.injector

import distage.{Injector, ModuleDef}
import izumi.distage.fixtures.BasicCases.BasicCase8
import izumi.distage.model.PlannerInput
import org.scalatest.wordspec.AnyWordSpec

class ArityTest extends AnyWordSpec with MkInjector {

  "Support classes with more than 22-argument constructors" in {
    import BasicCase8._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[Beep[Int]]
      make[Bop[Int]]
    })

    val context = Injector.Standard().produce(definition).unsafeGet()

    assert(context.get[Bop[Int]].beep == context.get[Beep[Int]])
  }

  "Support traits with more than 22-argument constructors" in {
    import BasicCase8._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[Beep[Int]]
      make[BopTrait[Int]]
    })

    val context = Injector.Standard().produce(definition).unsafeGet()

    assert(context.get[BopTrait[Int]].beep0 == context.get[Beep[Int]])
    assert(context.get[BopTrait[Int]].beep29 == context.get[Beep[Int]])
  }

  "Support abstract classes with more than 22-argument constructors" in {
    import BasicCase8._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[Beep[Int]]
      make[BopAbstractClass[Int]]
    })

    val context = Injector.Standard().produce(definition).unsafeGet()

    assert(context.get[BopAbstractClass[Int]].beep == context.get[Beep[Int]])
    assert(context.get[BopAbstractClass[Int]].beep0 == context.get[Beep[Int]])
    assert(context.get[BopAbstractClass[Int]].beep29 == context.get[Beep[Int]])
  }
//
//  "Support factories with more than 22-argument constructors" in {
//    import BasicCase8._
//
//    val definition = PlannerInput.noGc(new ModuleDef {
//      make[Beep[Int]]
//      make[BopFactory[Int]]
//    })
//
//    val context = Injector.Standard().produce(definition).unsafeGet()
//
//    assert(context.get[BopFactory[Int]].x(5) == BeepDependency1(5)(context.get[Beep[Int]]))
//    assert(context.get[BopFactory[Int]].x() == BeepDependency()(context.get[Beep[Int]]))
//    assert(context.get[BopFactory[Int]].beep0 == context.get[Beep[Int]])
//    assert(context.get[BopFactory[Int]].beep29 == context.get[Beep[Int]])
//  }

}
