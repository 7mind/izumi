package izumi.distage.injector

import distage.{Injector, ModuleDef}
import izumi.distage.gc.MkGcInjector
import izumi.distage.injector.JSRAnnotationTest.*
import izumi.distage.model.PlannerInput
import izumi.fundamentals.platform.assertions.ScalatestGuards
import org.scalatest.wordspec.AnyWordSpec

class JSRAnnotationTest extends AnyWordSpec with MkGcInjector with ScalatestGuards {
  "JSR330 @Named anno" should {
    "work with combined annos when no functoid is involved" in brokenOnScala3 {
      val definition = PlannerInput.everything(new ModuleDef {
        make[Int].named("port").from(80)
        make[String].named("address").from("localhost")
        make[Int].named("port1").from(90)
        make[String].named("address1").from("localhost1")
        make[ServerConfig]
      })

      val context = Injector.Standard().produce(definition).unsafeGet()

      assert(context.get[ServerConfig].port == context.get[Int]("port"))
      assert(context.get[ServerConfig].port1 == context.get[Int]("port1"))

      assert(context.get[ServerConfig].address == context.get[String]("address"))
      assert(context.get[ServerConfig].address1 == context.get[String]("address1"))
    }

    "work with field annos when no functoid is involved" in {
      val definition = PlannerInput.everything(new ModuleDef {
        make[Int].named("port1").from(90)
        make[String].named("address1").from("localhost1")
        make[ServerConfigWithFieldAnnos]
      })

      val context = Injector.Standard().produce(definition).unsafeGet()

      assert(context.get[ServerConfigWithFieldAnnos].port1 == context.get[Int]("port1"))
      assert(context.get[ServerConfigWithFieldAnnos].address1 == context.get[String]("address1"))
    }

    "work with alias annos when no functoid is involved" in brokenOnScala3 {
      val definition = PlannerInput.everything(new ModuleDef {
        make[Int].named("port").from(80)
        make[String].named("address").from("localhost")
        make[ServerConfigWithTypeAnnos]
      })

      val context = Injector.Standard().produce(definition).unsafeGet()
      assert(context.get[ServerConfigWithTypeAnnos].port == context.get[Int]("port"))
      assert(context.get[ServerConfigWithTypeAnnos].address == context.get[String]("address"))
    }

    "work with param annos when no functoid is involved" in brokenOnScala3 {
      val definition = PlannerInput.everything(new ModuleDef {
        make[Int].named("port").from(80)
        make[String].named("address").from("localhost")
        make[Int].named("port1").from(90)
        make[String].named("address1").from("localhost1")
        make[ServerConfigWithParamAnnos].from(new ServerConfigWithParamAnnos(_, _, _, _))
      })

      val context = Injector.Standard().produce(definition).unsafeGet()

      assert(context.get[ServerConfigWithParamAnnos].port == context.get[Int]("port"))
      assert(context.get[ServerConfigWithParamAnnos].port1 == context.get[Int]("port1"))

      assert(context.get[ServerConfigWithParamAnnos].address == context.get[String]("address"))
      assert(context.get[ServerConfigWithParamAnnos].address1 == context.get[String]("address1"))
    }

    "work with field annos when functoid takes .apply" in {
      val definition = PlannerInput.everything(new ModuleDef {
        make[Int].named("port1").from(90)
        make[String].named("address1").from("localhost1")
        make[ServerConfigWithFieldAnnos].from(ServerConfigWithFieldAnnos.apply _)
      })

      val context = Injector.Standard().produce(definition).unsafeGet()

      assert(context.get[ServerConfigWithFieldAnnos].port1 == context.get[Int]("port1"))
      assert(context.get[ServerConfigWithFieldAnnos].address1 == context.get[String]("address1"))
    }

    "work with alias annos when functoid takes .apply" in brokenOnScala3 {
      val definition = PlannerInput.everything(new ModuleDef {
        make[Int].named("port").from(80)
        make[String].named("address").from("localhost")
        make[ServerConfigWithTypeAnnos].from(ServerConfigWithTypeAnnos.apply _)
      })

      val context = Injector.Standard().produce(definition).unsafeGet()
      assert(context.get[ServerConfigWithTypeAnnos].port == context.get[Int]("port"))
      assert(context.get[ServerConfigWithTypeAnnos].address == context.get[String]("address"))
    }

    "work with combined annos when functoid takes .apply" in brokenOnScala3 {
      val definition = PlannerInput.everything(new ModuleDef {
        make[Int].named("port").from(80)
        make[String].named("address").from("localhost")
        make[Int].named("port1").from(90)
        make[String].named("address1").from("localhost1")
        make[ServerConfig].from(ServerConfig.apply _)
      })

      val context = Injector.Standard().produce(definition).unsafeGet()

      assert(context.get[ServerConfig].port == context.get[Int]("port"))
      assert(context.get[ServerConfig].port1 == context.get[Int]("port1"))

      assert(context.get[ServerConfig].address == context.get[String]("address"))
      assert(context.get[ServerConfig].address1 == context.get[String]("address1"))
    }

    "work with param annos when functoid takes .apply" in brokenOnScala3 {
      val definition = PlannerInput.everything(new ModuleDef {
        make[Int].named("port").from(80)
        make[String].named("address").from("localhost")
        make[Int].named("port1").from(90)
        make[String].named("address1").from("localhost1")
        make[ServerConfigWithParamAnnos].from(ServerConfigWithParamAnnos.apply _)
      })

      val context = Injector.Standard().produce(definition).unsafeGet()

      assert(context.get[ServerConfigWithParamAnnos].port == context.get[Int]("port"))
      assert(context.get[ServerConfigWithParamAnnos].port1 == context.get[Int]("port1"))

      assert(context.get[ServerConfigWithParamAnnos].address == context.get[String]("address"))
      assert(context.get[ServerConfigWithParamAnnos].address1 == context.get[String]("address1"))
    }

    "work with field annos when functoid takes overriden companion as function" in brokenOnScala3 {
      val definition = PlannerInput.everything(new ModuleDef {
        make[Int].named("port1").from(90)
        make[String].named("address1").from("localhost1")
        make[ServerConfigWithFieldAnnos].from(ServerConfigWithFieldAnnos)
      })

      val context = Injector.Standard().produce(definition).unsafeGet()

      assert(context.get[ServerConfigWithFieldAnnos].port1 == context.get[Int]("port1"))
      assert(context.get[ServerConfigWithFieldAnnos].address1 == context.get[String]("address1"))
    }

    "work with alias annos when functoid takes companion as function" in brokenOnScala3 {
      val definition = PlannerInput.everything(new ModuleDef {
        make[Int].named("port").from(80)
        make[String].named("address").from("localhost")
        make[ServerConfigWithTypeAnnos].from(ServerConfigWithTypeAnnos)
      })

      val context = Injector.Standard().produce(definition).unsafeGet()
      assert(context.get[ServerConfigWithTypeAnnos].port == context.get[Int]("port"))
      assert(context.get[ServerConfigWithTypeAnnos].address == context.get[String]("address"))
    }

    "work with combined annos when functoid takes companion as function" in brokenOnScala3 {
      val definition = PlannerInput.everything(new ModuleDef {
        make[Int].named("port").from(80)
        make[String].named("address").from("localhost")
        make[Int].named("port1").from(90)
        make[String].named("address1").from("localhost1")
        make[ServerConfig].from(ServerConfig)
      })

      val context = Injector.Standard().produce(definition).unsafeGet()

      assert(context.get[ServerConfig].port == context.get[Int]("port"))
      assert(context.get[ServerConfig].port1 == context.get[Int]("port1"))

      assert(context.get[ServerConfig].address == context.get[String]("address"))
      assert(context.get[ServerConfig].address1 == context.get[String]("address1"))
    }

    "work with param annos when functoid takes companion as function" in brokenOnScala3 {
      val definition = PlannerInput.everything(new ModuleDef {
        make[Int].named("port").from(80)
        make[String].named("address").from("localhost")
        make[Int].named("port1").from(90)
        make[String].named("address1").from("localhost1")
        make[ServerConfigWithParamAnnos].from(ServerConfigWithParamAnnos)
      })

      val context = Injector.Standard().produce(definition).unsafeGet()

      assert(context.get[ServerConfigWithParamAnnos].port == context.get[Int]("port"))
      assert(context.get[ServerConfigWithParamAnnos].port1 == context.get[Int]("port1"))

      assert(context.get[ServerConfigWithParamAnnos].address == context.get[String]("address"))
      assert(context.get[ServerConfigWithParamAnnos].address1 == context.get[String]("address1"))
    }

    "work with param annos when functoid takes overriden companion as function" in brokenOnScala3 {
      val definition = PlannerInput.everything(new ModuleDef {
        make[Int].named("port").from(80)
        make[String].named("address").from("localhost")
        make[Int].named("port1").from(90)
        make[String].named("address1").from("localhost1")
        make[ServerConfigWithParamAnnosOverridenObject].from(ServerConfigWithParamAnnosOverridenObject)
      })

      val context = Injector.Standard().produce(definition).unsafeGet()

      assert(context.get[ServerConfigWithParamAnnosOverridenObject].port == context.get[Int]("port"))
      assert(context.get[ServerConfigWithParamAnnosOverridenObject].port1 == context.get[Int]("port1"))

      assert(context.get[ServerConfigWithParamAnnosOverridenObject].address == context.get[String]("address"))
      assert(context.get[ServerConfigWithParamAnnosOverridenObject].address1 == context.get[String]("address1"))
    }
  }
}

object JSRAnnotationTest {
  type Port = Int @javax.inject.Named(value = "port")
  type Address = String @javax.inject.Named("address")

  final case class ServerConfig(
    port: Port,
    address: Address,
    port1: Int @javax.inject.Named(value = "port1"),
    address1: String @javax.inject.Named("address1"),
  )
  // FIXME: explicit function inheritance for dotty
  object ServerConfig extends ((Port, Address, Int @javax.inject.Named(value = "port1"), String @javax.inject.Named("address1")) => ServerConfig)

  final case class ServerConfigWithFieldAnnos(
    port1: Int @javax.inject.Named(value = "port1"),
    address1: String @javax.inject.Named("address1"),
  )
  // FIXME: explicit function inheritance for dotty
  object ServerConfigWithFieldAnnos extends ((Int, Address) => ServerConfigWithFieldAnnos)

  final case class ServerConfigWithTypeAnnos(
    port: Port,
    address: Address,
  )
  // FIXME: explicit function inheritance for dotty
  object ServerConfigWithTypeAnnos extends ((Port, Address) => ServerConfigWithTypeAnnos)

  final case class ServerConfigWithParamAnnos(
    port: Port,
    address: Address,
    @javax.inject.Named(value = "port1") port1: Int,
    @javax.inject.Named("address1") address1: String,
  )
  // FIXME: explicit function inheritance for dotty
  object ServerConfigWithParamAnnos extends ((Port, Address, Int, String) => ServerConfigWithParamAnnos)

  final case class ServerConfigWithParamAnnosOverridenObject(
    port: Port,
    address: Address,
    @javax.inject.Named(value = "port1") port1: Int,
    @javax.inject.Named("address1") address1: String,
  )
  // FIXME: explicit function inheritance for dotty
  object ServerConfigWithParamAnnosOverridenObject extends ((Int, Address, Int, String) => ServerConfigWithParamAnnosOverridenObject)

}
