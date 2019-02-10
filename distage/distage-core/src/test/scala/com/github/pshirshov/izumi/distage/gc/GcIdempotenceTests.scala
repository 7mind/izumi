package com.github.pshirshov.izumi.distage.gc

import com.github.pshirshov.izumi.distage.model.PlannerInput
import com.github.pshirshov.izumi.distage.model.definition.ModuleDef
import distage.DIKey
import org.scalatest.WordSpec


class GcIdempotenceTests extends WordSpec with MkGcInjector {
  "Garbage-collecting injector" when {
    "plan is re-finished" should {
      "keep proxies alive in case of intersecting loops" in {
        import GcCases.InjectorCase1._
        val injector = mkInjector()
        val plan = injector.plan(PlannerInput(new ModuleDef {
          make[Circular1]
          make[Circular2]
          make[Circular3]
          make[Circular4]
        }, roots = DIKey.get[Circular2]))

        val result = injector.fproduce(plan)
        assert(result.get[Circular1].c2 != null)
        assert(result.get[Circular2].c1 != null)
        assert(result.get[Circular1].c2.isInstanceOf[Circular2])
        assert(result.get[Circular2].c1.isInstanceOf[Circular1])
      }

      "keep by-name loops alive" in {
        import GcCases.InjectorCase2._
        val injector = mkInjector()
        val plan = injector.plan(PlannerInput(new ModuleDef {
          make[MkS3Client].from[Impl]
          make[S3Component]
          make[App]
        }, roots = DIKey.get[App]))

        val result = injector.fproduce(plan)
        assert(result.get[App] != null)
      }

      "keep plans alive in case of complex loops" in {
        import GcCases.InjectorCase3._
        val injector = mkInjector()
        val plan = injector.plan(PlannerInput(new ModuleDef {
          many[IntegrationComponent].add[S3Component]

          make[MkS3Client].from[Impl]
          make[S3Upload]
          make[Ctx]
          make[S3Component]
        }, roots = DIKey.get[Ctx]))

        val result = injector.fproduce(plan)
        assert(result.get[Ctx].upload.client != null)
        val c1 = result.get[MkS3Client]
        val c2 = result.get[Ctx].upload.client
        assert(c1 == c2)
      }

      "keep plans alive after conversion back to SemiPlan in case of complex loops" in {
        import GcCases.InjectorCase4._
        val injector = mkInjector()
        val plan = injector.plan(PlannerInput(new ModuleDef {
          make[MkS3Client]
          make[S3Upload]
          make[Ctx]
          make[S3Component]
          many[IntegrationComponent].add[S3Component]
        }, roots = DIKey.get[Ctx], DIKey.get[Initiator]))

        val result = injector.fproduce(plan)
        assert(result.get[Ctx] != null)
      }


      "keep proxies alive in case of pathologically intersecting loops" in {
        import GcCases.InjectorCase5._
        val injector = mkInjector()
        val plan = injector.plan(PlannerInput(new ModuleDef {
          make[Circular1]
          make[Circular2]
          make[T1].using[Circular1]
          make[T2].using[Circular2]
        }, roots = DIKey.get[Circular2]))

        val result = injector.fproduce(plan)
        assert(result.get[Circular1].c2 != null)
        assert(result.get[Circular2].c1 != null)
        assert(result.get[Circular1].c2.isInstanceOf[Circular2])
        assert(result.get[Circular2].c1.isInstanceOf[Circular1])
      }

      "keep proxies alive in case of pathologically intersecting provider loops" in {
        import GcCases.InjectorCase6._
        val injector = mkInjector()
        val plan = injector.plan(PlannerInput(new ModuleDef {
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
        }, roots = DIKey.get[Circular2]))
        val result = injector.fproduce(plan)
        assert(result.get[Circular1].nothing == 1)
        assert(result.get[Circular2].nothing == 2)
        assert(result.get[Circular1].c2.nothing == 2)
        assert(result.get[Circular2].c1.nothing == 1)
      }

      "work with autosets" in {
        import GcCases.InjectorCase8._
        val injector = mkInjector()
        val plan = injector.plan(PlannerInput(new ModuleDef {
          many[Component]
            .add[TestComponent]

          make[App]
        }, roots = DIKey.get[App]))

        val updated = injector.finish(plan.toSemi)
        val result = injector.produceUnsafe(updated)
        assert(updated.steps.size == plan.steps.size)

        assert(result.get[App].components.size == 1)
        assert(result.get[App].closeables.size == 1)
      }
    }

  }
}
