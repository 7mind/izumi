package com.github.pshirshov.izumi.distage.gc

import com.github.pshirshov.izumi.distage.model.definition.ModuleDef
import com.github.pshirshov.izumi.distage.model.exceptions.UnsupportedOpException
import org.scalatest.WordSpec


class GcBasicTests extends WordSpec with MkGcInjector {
  "Garbage-collecting injector" should {
    "keep proxies alive in case of intersecting loops" in {
      import GcCases.InjectorCase1._
      val injector = mkInjector(distage.DIKey.get[Circular2])
      val plan = injector.plan(new ModuleDef {
        make[Circular1]
        make[Circular2]
        make[Circular3]
        make[Circular4]
        make[Trash]
      })

      val result = injector.produce(plan)
      assert(result.find[Trash].isEmpty)
      assert(result.get[Circular1].c2 != null)
      assert(result.get[Circular2].c1 != null)
      assert(result.get[Circular1].c2.isInstanceOf[Circular2])
      assert(result.get[Circular2].c1.isInstanceOf[Circular1])
    }

    "keep by-name loops alive" in {
      import GcCases.InjectorCase2._
      val injector = mkInjector(distage.DIKey.get[App])
      val plan = injector.plan(new ModuleDef {
        make[MkS3Client].from[Impl]
        make[S3Component]
        make[App]
      })

      val result = injector.produce(plan)
      assert(result.get[App] != null)
    }

    "keep plans alive in case of complex loops" in {
      import GcCases.InjectorCase3._
      val injector = mkInjector(distage.DIKey.get[Ctx])
      val plan = injector.plan(new ModuleDef {
        many[IntegrationComponent].add[S3Component]

        make[MkS3Client].from[Impl]
        make[S3Upload]
        make[Ctx]
        make[S3Component]
      })

      val result = injector.produce(plan)
      assert(result.get[Ctx].upload.client != null)
      val c1 = result.get[MkS3Client]
      val c2 = result.get[Ctx].upload.client
      assert(c1 == c2)
    }

    "keep plans alive in case of even more complex loops" in {
      import GcCases.InjectorCase4._
      val injector = mkInjector(distage.DIKey.get[Ctx], distage.DIKey.get[Initiator])
      val plan = injector.plan(new ModuleDef {
        make[MkS3Client]
        make[S3Upload]
        make[Ctx]
        make[S3Component]
        many[IntegrationComponent].add[S3Component]
      })

      val result = injector.produce(plan)
      assert(result.get[Ctx] != null)
    }


    "keep proxies alive in case of pathologically intersecting loops" in {
      import GcCases.InjectorCase5._
      val injector = mkInjector(distage.DIKey.get[Circular2])
      val plan = injector.plan(new ModuleDef {
        make[Circular1]
        make[Circular2]
        make[T1].using[Circular1]
        make[T2].using[Circular2]
      })

      val result = injector.produce(plan)
      assert(result.get[Circular1].c2 != null)
      assert(result.get[Circular2].c1 != null)
      assert(result.get[Circular1].c2.isInstanceOf[Circular2])
      assert(result.get[Circular2].c1.isInstanceOf[Circular1])
    }

    "keep proxies alive in case of pathologically intersecting loops with final classes" in {
      import GcCases.InjectorCase9._
      val injector = mkInjector(distage.DIKey.get[T1])
      val plan = injector.plan(new ModuleDef {
        make[T1].from[Circular1]
        make[T2].from[Circular2]
      })

      val result = injector.produce(plan)
      assert(result.get[T1] != null)
      assert(result.get[T2] != null)
    }

    "keep proxies alive in case of pathologically intersecting provider loops" in {
      import GcCases.InjectorCase6._
      val injector = mkInjector(distage.DIKey.get[Circular2])
      val plan = injector.plan(new ModuleDef {
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
      })
      val result = injector.produce(plan)
      assert(result.get[Circular1].nothing == 1)
      assert(result.get[Circular2].nothing == 2)
      assert(result.get[Circular1].c2.nothing == 2)
      assert(result.get[Circular2].c1.nothing == 1)
    }

    "keep proxies alive in case of pathologically intersecting loops with by-name edges" in {
      import GcCases.InjectorCase7._
      val injector = mkInjector(distage.DIKey.get[Circular2])
      val plan = injector.plan(new ModuleDef {
        make[Circular1]
        make[Circular2]
      })

      val result = injector.produce(plan)

      assert(result.get[Circular1].c2 != null)
      assert(result.get[Circular1].c2 != null)
      assert(result.get[Circular1].c2.isInstanceOf[Circular2])
    }

    "fail on totally final loops" in {
      import GcCases.InjectorCase10._
      val injector = mkInjector(distage.DIKey.get[Circular2])
      intercept[UnsupportedOpException] {
        injector.plan(new ModuleDef {
          make[Circular1]
          make[Circular2]
        })
      }
    }

    "prefer non-final loop break" in {
      import GcCases.InjectorCase11._
      val injector = mkInjector(distage.DIKey.get[Circular2])
      val plan = injector.plan(new ModuleDef {
        make[Circular1]
        make[Circular2]
      })

      val result = injector.produce(plan)

      assert(result.get[Circular1].c2 != null)
      assert(result.get[Circular1].c2.isInstanceOf[Circular2])
    }
  }
}
