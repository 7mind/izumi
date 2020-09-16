package izumi.distage.injector

import distage.{Id, Injector, ModuleDef}
import izumi.distage.gc.MkGcInjector
import izumi.distage.model.PlannerInput
import izumi.distage.model.exceptions.ProvisioningException
import org.scalatest.wordspec.AnyWordSpec

class JSRAnnotationTest extends AnyWordSpec with MkGcInjector {
  import JSRAnnotationTest._
  "JSR330 @Named anno" should {
    "work when no functoid is involved" in {
      val definition = PlannerInput.noGC(new ModuleDef {
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

    "progression: work with combined annos when functoid takes companion as function" in
    intercept[ProvisioningException] {
      val definition = PlannerInput.noGC(new ModuleDef {
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

    "work with field annos when functoid takes .apply" in {
      val definition = PlannerInput.noGC(new ModuleDef {
        make[Int].named("port1").from(90)
        make[String].named("address1").from("localhost1")
        make[ServerConfigWithFieldAnnos].from(ServerConfigWithFieldAnnos.apply _)
      })

      val context = Injector.Standard().produce(definition).unsafeGet()

      assert(context.get[ServerConfigWithFieldAnnos].port1 == context.get[Int]("port1"))
      assert(context.get[ServerConfigWithFieldAnnos].address1 == context.get[String]("address1"))
    }

    "work with alias annos when functoid takes .apply" in {
      val definition = PlannerInput.noGC(new ModuleDef {
        make[Int].named("port").from(80)
        make[String].named("address").from("localhost")
        make[ServerConfigWithTypeAnnos].from(ServerConfigWithTypeAnnos.apply _)
      })

      val context = Injector.Standard().produce(definition).unsafeGet()
      assert(context.get[ServerConfigWithTypeAnnos].port == context.get[Int]("port"))
      assert(context.get[ServerConfigWithTypeAnnos].address == context.get[String]("address"))
    }

    "work with field annos when functoid takes companion as function" in
    intercept[ProvisioningException] {
      val definition = PlannerInput.noGC(new ModuleDef {
        make[Int].named("port1").from(90)
        make[String].named("address1").from("localhost1")
        make[ServerConfigWithFieldAnnos].from(ServerConfigWithFieldAnnos)
      })

      val context = Injector.Standard().produce(definition).unsafeGet()

      assert(context.get[ServerConfigWithFieldAnnos].port1 == context.get[Int]("port1"))
      assert(context.get[ServerConfigWithFieldAnnos].address1 == context.get[String]("address1"))
    }

    "work with alias annos when functoid takes companion as function" in {
      val definition = PlannerInput.noGC(new ModuleDef {
        make[Int].named("port").from(80)
        make[String].named("address").from("localhost")
        make[ServerConfigWithTypeAnnos].from(ServerConfigWithTypeAnnos)
      })

      val context = Injector.Standard().produce(definition).unsafeGet()
      assert(context.get[ServerConfigWithTypeAnnos].port == context.get[Int]("port"))
      assert(context.get[ServerConfigWithTypeAnnos].address == context.get[String]("address"))
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

  final case class ServerConfigWithFieldAnnos(
    port1: Int @javax.inject.Named(value = "port1"),
    address1: String @javax.inject.Named("address1"),
  )

  final case class ServerConfigWithTypeAnnos(
    port: Port,
    address: Address,
  )
}
