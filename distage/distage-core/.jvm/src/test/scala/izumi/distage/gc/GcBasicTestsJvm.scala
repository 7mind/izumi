package izumi.distage.gc

import distage.DIKey
import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.{Activation, ModuleDef}
import izumi.distage.model.plan.Roots
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.immutable

class GcBasicTestsJvm extends AnyWordSpec with MkGcInjector {
  "Garbage-collecting injector" should {
    "keep proxies alive in case of intersecting loops" in {
      import GcCases.InjectorCase1._

      val injector = mkInjector()
      val plan = injector.plan(
        PlannerInput(
          new ModuleDef {
            make[Circular1]
            make[Circular2]
            make[Circular3]
            make[Circular4]
            make[Trash]
          },
          Activation.empty,
          Roots(DIKey.get[Circular2]),
        )
      )

      val result = injector.produce(plan).unsafeGet()
      assert(result.find[Trash].isEmpty)
      assert(result.get[Circular1].c2 != null)
      assert(result.get[Circular2].c1 != null)
      assert(result.get[Circular1].c2.isInstanceOf[Circular2])
      assert(result.get[Circular2].c1.isInstanceOf[Circular1])
    }

    "keep by-name loops alive" in {
      import GcCases.InjectorCase2._

      val injector = mkInjector()
      val plan = injector.plan(
        PlannerInput(
          new ModuleDef {
            make[MkS3Client].from[Impl]
            make[S3Component]
            make[App]
          },
          Activation.empty,
          Roots(DIKey.get[App]),
        )
      )

      val result = injector.produce(plan).unsafeGet()
      assert(result.get[App] != null)
    }

    "keep plans alive in case of complex loops" in {
      import GcCases.InjectorCase3._

      val injector = mkInjector()
      val plan = injector.plan(
        PlannerInput(
          new ModuleDef {
            many[IntegrationComponent].add[S3Component]

            make[MkS3Client].from[Impl]
            make[S3Upload]
            make[Ctx]
            make[S3Component]
          },
          Activation.empty,
          Roots(DIKey.get[Ctx]),
        )
      )

      val result = injector.produce(plan).unsafeGet()
      assert(result.get[Ctx].upload.client != null)
      val c1 = result.get[MkS3Client]
      val c2 = result.get[Ctx].upload.client
      assert(c1 == c2)
    }

    "keep plans alive in case of even more complex loops" in {
      import GcCases.InjectorCase4._

      val injector = mkInjector()
      val plan = injector.plan(
        PlannerInput(
          new ModuleDef {
            make[MkS3Client]
            make[S3Upload]
            make[Ctx]
            make[S3Component]
            many[IntegrationComponent].add[S3Component]
            make[Initiator]
          },
          Activation.empty,
          Roots(DIKey.get[Ctx], DIKey.get[Initiator]),
        )
      )

      println(plan.render())
      val result = injector.produce(plan).unsafeGet()
      assert(result.get[Ctx] != null)
    }

    "keep proxies alive in case of pathologically intersecting loops" in {
      import GcCases.InjectorCase5._

      val injector = mkInjector()
      val plan = injector.plan(
        PlannerInput(
          new ModuleDef {
            make[Circular1]
            make[Circular2]
            make[T1].using[Circular1]
            make[T2].using[Circular2]
          },
          Activation.empty,
          Roots(DIKey.get[Circular2]),
        )
      )

      val result = injector.produce(plan).unsafeGet()
      assert(result.get[Circular1].c2 != null)
      assert(result.get[Circular2].c1 != null)
      assert(result.get[Circular1].c2.isInstanceOf[Circular2])
      assert(result.get[Circular2].c1.isInstanceOf[Circular1])
    }

    "keep proxies alive in case of pathologically intersecting loops with final classes" in {
      import GcCases.InjectorCase9._

      val injector = mkInjector()
      val plan = injector.plan(
        PlannerInput(
          new ModuleDef {
            make[T1].from[Circular1]
            make[T2].from[Circular2]
          },
          Activation.empty,
          Roots(DIKey.get[T1]),
        )
      )

      val result = injector.produce(plan).unsafeGet()
      assert(result.get[T1] != null)
      assert(result.get[T2] != null)
    }

    "keep proxies alive in case of pathologically intersecting provider loops" in {
      import GcCases.InjectorCase6._

      val injector = mkInjector()
      val plan = injector.plan(
        PlannerInput(
          new ModuleDef {
            make[Circular1].from {
              (t1: Circular1, t2: Circular2) =>
                new Circular1 {
                  override def c1: Circular1 = t1

                  override def c2: Circular2 = t2

                  override def nothing: Int = 1
                }
            }
            make[Circular2].from {
              (t1: Circular1, t2: Circular2) =>
                new Circular2 {

                  override def c1: Circular1 = t1

                  override def c2: Circular2 = t2

                  override def nothing: Int = 2
                }
            }
          },
          Activation.empty,
          Roots(DIKey.get[Circular2]),
        )
      )
      val result = injector.produce(plan).unsafeGet()
      assert(result.get[Circular1].nothing == 1)
      assert(result.get[Circular2].nothing == 2)
      assert(result.get[Circular1].c2.nothing == 2)
      assert(result.get[Circular2].c1.nothing == 1)
    }

    "keep proxies alive in case of pathologically intersecting loops with by-name edges" in {
      import GcCases.InjectorCase7._

      val injector = mkInjector()
      val plan = injector.plan(
        PlannerInput(
          new ModuleDef {
            make[Circular1]
            make[Circular2]
          },
          Activation.empty,
          Roots(DIKey.get[Circular2]),
        )
      )

      val result = injector.produce(plan).unsafeGet()

      assert(result.get[Circular1].c2 != null)
      assert(result.get[Circular1].c2 != null)
      assert(result.get[Circular1].c2.isInstanceOf[Circular2])
    }

    "prefer non-final loop break" in {
      import GcCases.InjectorCase11._

      val injector = mkInjector()
      val plan = injector.plan(
        PlannerInput(
          new ModuleDef {
            make[Circular1]
            make[Circular2]
          },
          Activation.empty,
          Roots(DIKey.get[Circular2]),
        )
      )

      val result = injector.produce(plan).unsafeGet()

      assert(result.get[Circular1].c2 != null)
      assert(result.get[Circular1].c2.isInstanceOf[Circular2])
    }

    "handle cglib by-name circular dependencies with sets" in {
      import GcCases.InjectorCase12._

      val injector = mkInjector()
      val plan = injector.plan(
        PlannerInput(
          new ModuleDef {
            make[Circular1]
            make[Circular2]
          },
          Activation.empty,
          Roots(DIKey.get[Circular2], DIKey.get[Set[AutoCloseable]]),
        )
      )

      val result = injector.produce(plan).unsafeGet()

      assert(result.get[Circular1] != null)
      assert(result.get[Circular2] != null)
    }

    "handle cglib by-name circular dependencies with sets through refs" in {
      import GcCases.InjectorCase12._

      val injector = mkInjector()
      val plan = injector.plan(
        PlannerInput(
          new ModuleDef {
            make[Circular1]
            make[Circular2]
            make[Circular3]
            make[Circular4]
            many[T1]
              .ref[Circular1]
              .ref[Circular2]
          },
          Activation.empty,
          Roots(DIKey.get[Circular4], DIKey.get[immutable.Set[T1]], DIKey.get[Circular3]),
        )
      )

      val result = injector.produce(plan).unsafeGet()

      assert(result.get[Circular1] != null)
      assert(result.get[Circular2] != null)
    }
  }
}
