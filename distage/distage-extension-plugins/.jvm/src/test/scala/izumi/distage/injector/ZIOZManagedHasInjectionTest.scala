package izumi.distage.injector

import distage.Injector
import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.ModuleDef
import izumi.functional.lifecycle.Lifecycle
import izumi.fundamentals.platform.assertions.ScalatestGuards
import izumi.fundamentals.platform.functional.Identity
import org.scalatest.wordspec.AnyWordSpec
import zio.*
import zio.managed.ZManaged

import scala.annotation.nowarn

@nowarn("msg=reflectiveSelectable")
class ZIOZManagedHasInjectionTest extends AnyWordSpec with ScalatestGuards {

  protected def unsafeRun[E, A](eff: => ZIO[Any, E, A]): A = Unsafe.unsafe(implicit unsafe => zio.Runtime.default.unsafe.run(eff).getOrThrowFiberFailure())

  def mkNoCyclesInjector(): Injector[Identity] = Injector.NoCycles()

  object TraitCase2 {

    class Dependency1 {
      override def toString: String = "Hello World"
    }

    class Dependency2

    class Dependency3

    trait Trait1 {
      def dep1: Dependency1
    }

    trait Trait2 extends Trait1 {
      override def dep1: Dependency1

      def dep2: Dependency2
    }

    trait Trait3 extends Trait1 with Trait2 {
      def dep3: Dependency3

      def prr(): String = dep1.toString
    }

  }

  import TraitCase2.*

  type HasInt = Int
  type HasX[B] = B
  type HasIntBool = HasInt & HasX[Boolean]

  def trait1(d1: Dependency1): Trait1 = new Trait1 { override def dep1: Dependency1 = d1 }

  def getDep1: URIO[Dependency1, Dependency1] = ZIO.service[Dependency1]
  def getDep2: URIO[Dependency2, Dependency2] = ZIO.service[Dependency2]

  final class ResourceHasImpl()
    extends Lifecycle.LiftF(for {
      d1 <- getDep1
      d2 <- getDep2
    } yield new Trait2 { val dep1 = d1; val dep2 = d2 })

  final class ResourceEmptyHasImpl(
    d1: Dependency1
  ) extends Lifecycle.LiftF[UIO, Trait1](
      ZIO.succeed(trait1(d1))
    )

  "ZManaged ZEnvConstructor" should {

    "handle multi-parameter Has with mixed args & env injection and a refinement return" in {
      import scala.language.reflectiveCalls

      def getDep1: URIO[Dependency1, Dependency1] = ZIO.environmentWith[Dependency1](_.get)
      def getDep2: URIO[Dependency2, Dependency2] = ZIO.environmentWith[Dependency2](_.get)

      val definition = PlannerInput.everything(new ModuleDef {
        make[Dependency1]
        make[Dependency2]
        make[Dependency3]
        make[Trait3 { def acquired: Boolean }].fromZIOEnv(
          (d3: Dependency3) =>
            for {
              d1 <- getDep1
              d2 <- getDep2
              res: (Trait3 { def acquired: Boolean; def acquired_=(b: Boolean): Unit }) @unchecked = new Trait3 {
                override val dep1 = d1
                override val dep2 = d2
                override val dep3 = d3
                var acquired = false
              }
              _ <- ZIO.acquireRelease(
                ZIO.succeed(res.acquired = true)
              )(_ => ZIO.succeed(res.acquired = false))
            } yield res
        )

        make[Trait2].fromZManagedEnv(for {
          d1 <- ZManaged.environmentWith[Dependency1](_.get)
          d2 <- ZManaged.environmentWith[Dependency2](_.get)
        } yield new Trait2 { val dep1 = d1; val dep2 = d2 })

        make[Trait1].fromZLayerEnv {
          (d1: Dependency1) =>
            ZLayer.succeed(new Trait1 { val dep1 = d1 })
        }

        make[Trait2].named("classbased").fromZEnvResource[ResourceHasImpl]
        make[Trait1].named("classbased").fromZEnvResource[ResourceEmptyHasImpl]

        many[Trait2].addZEnvResource[ResourceHasImpl]
        many[Trait1].addZEnvResource[ResourceEmptyHasImpl]
      })

      val injector = mkNoCyclesInjector()
      val plan = injector.planUnsafe(definition)

      val instantiated = unsafeRun(injector.produceCustomF[Task](plan).use {
        context =>
          ZIO.succeed {

            assert(context.find[Trait3].isEmpty)

            val instantiated = context.get[Trait3 { def acquired: Boolean }]

            assert(instantiated.dep1 eq context.get[Dependency1])
            assert(instantiated.dep2 eq context.get[Dependency2])
            assert(instantiated.dep3 eq context.get[Dependency3])
            assert(instantiated.acquired)

            val instantiated10 = context.get[Trait2]
            assert(instantiated10.dep2 eq context.get[Dependency2])

            val instantiated2 = context.get[Trait1]
            assert(instantiated2 ne null)

            val instantiated3 = context.get[Trait2]("classbased")
            assert(instantiated3.dep2 eq context.get[Dependency2])

            val instantiated4 = context.get[Trait1]("classbased")
            assert(instantiated4 ne null)

            val instantiated5 = context.get[Set[Trait2]].head
            assert(instantiated5.dep2 eq context.get[Dependency2])

            val instantiated6 = context.get[Set[Trait1]].head
            assert(instantiated6 ne null)

            instantiated
          }
      })
      assert(!instantiated.acquired)
    }

  }

}
