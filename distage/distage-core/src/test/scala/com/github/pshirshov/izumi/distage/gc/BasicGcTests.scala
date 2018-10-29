package com.github.pshirshov.izumi.distage.gc

import com.github.pshirshov.izumi.distage.model.definition.{Id, ModuleDef}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.fundamentals.platform.build.ExposedTestScope
import distage.Injector
import org.scalatest.WordSpec

@ExposedTestScope
object InjectorCases {

  object InjectorCase1 {
    class Circular1(val c2: Circular2)
    class Circular2(val c1: Circular1, val c4: Circular4)

    class Circular3(val c4: Circular4)
    class Circular4(val c3: Circular3)
  }

  object InjectorCase2 {
    trait MkS3Client
    class S3Component(client: => MkS3Client)

    class Impl(val c: S3Component) extends MkS3Client
    class App(val client: MkS3Client, val component: S3Component)
  }

  object InjectorCase3 {
    trait MkS3Client
    trait IntegrationComponent
    class Impl(val c: S3Component) extends MkS3Client
    class S3Component(val client: MkS3Client) extends IntegrationComponent
    class S3Upload(val client: MkS3Client)
    class Ctx(val upload: S3Upload, val ics: Set[IntegrationComponent])
  }

  object InjectorCase4 {
    class MkS3Client(val c: S3Component)
    trait IntegrationComponent
    class S3Component(val client: MkS3Client) extends IntegrationComponent
    class S3Upload(val client: MkS3Client)
    class Ctx(val upload: S3Upload)
    class Initiator(val components: Set[IntegrationComponent])
  }
}

trait MkGcInjector {
  def mkInjector(roots: RuntimeDIUniverse.DIKey*): Injector = Injector.gc(roots.toSet).apply()
  def mkInjector0(roots: RuntimeDIUniverse.DIKey*): Injector = Injector.Standard()
}

class BasicGcTests extends WordSpec with MkGcInjector {
  "Garbage-collection injector" should {
    "keep proxy instantiations" in {
      import InjectorCases.InjectorCase1._
      val injector = mkInjector(distage.DIKey.get[Circular2])
      val plan = injector.plan(new ModuleDef {
        make[Circular1]
        make[Circular2]
        make[Circular3]
        make[Circular4]
      })

      import com.github.pshirshov.izumi.distage.model.plan.CompactPlanFormatter._
      println(plan.render)
      val result = injector.produce(plan)
      assert(result.get[Circular1].c2 != null)
      assert(result.get[Circular2].c1 != null)
      assert(result.get[Circular1].c2.isInstanceOf[Circular2])
      assert(result.get[Circular2].c1.isInstanceOf[Circular1])
    }

    "keep proxy instantiations-2" in {
      import InjectorCases.InjectorCase2._
      val injector = mkInjector(distage.DIKey.get[App])
      val plan = injector.plan(new ModuleDef {
        make[MkS3Client].from[Impl]
        make[S3Component]
        make[App]
      })

      import com.github.pshirshov.izumi.distage.model.plan.CompactPlanFormatter._
      println(plan.render)
      val result = injector.produce(plan)
      assert(result.get[App] != null)
    }

    "keep proxy instantiations-3" in {
      import InjectorCases.InjectorCase3._
      val injector = mkInjector(distage.DIKey.get[Ctx])
      val plan = injector.plan(new ModuleDef {
        many[IntegrationComponent].add[S3Component]

        make[MkS3Client].from[Impl]
        make[S3Upload]
        make[Ctx]
        make[S3Component]
      })

      import com.github.pshirshov.izumi.distage.model.plan.CompactPlanFormatter._
      println(plan.render)
      val result = injector.produce(plan)
      assert(result.get[Ctx].upload.client != null)
      val c1 = result.get[MkS3Client]
      val c2 = result.get[Ctx].upload.client
      assert(c1 == c2)
    }

    "keep proxy instantiations-4" in {
      import InjectorCases.InjectorCase4._
      val injector = mkInjector(distage.DIKey.get[Ctx], distage.DIKey.get[Initiator])
      val plan = injector.plan(new ModuleDef {
        make[MkS3Client]
        make[S3Upload]
        make[Ctx]
        make[S3Component]
        many[IntegrationComponent].add[S3Component]
      })

      val plan2 = injector.finish(plan.map(op => op))
      import com.github.pshirshov.izumi.distage.model.plan.CompactPlanFormatter._
      println(plan2.render)
      val result = injector.produce(plan2)
      assert(result.get[Ctx] != null)
    }

    "keep proxy instantiations-5" in {
      import InjectorCases.InjectorCase1._
      val injector = mkInjector(distage.DIKey.get[Circular2])
      val plan = injector.plan(new ModuleDef {
        make[Circular1]
        make[Circular2]
      })

      val plan2 = injector.finish(plan.map(op => op))
      import com.github.pshirshov.izumi.distage.model.plan.CompactPlanFormatter._
      println(plan2.render)
      val result = injector.produce(plan2)
      assert(result.get[Circular1].c2 != null)
      assert(result.get[Circular2].c1 != null)
      assert(result.get[Circular1].c2.isInstanceOf[Circular2])
      assert(result.get[Circular2].c1.isInstanceOf[Circular1])
    }
  }
}
