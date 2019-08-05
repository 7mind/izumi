package com.github.pshirshov.izumi.distage.fixtures

import com.github.pshirshov.izumi.fundamentals.platform.build.ExposedTestScope
import distage.Id

import scala.util.Random

@ExposedTestScope
object CircularCases {

  object CircularCase1 {

    trait Circular1 {
      def arg: Circular2
    }

    final class Circular1Impl(override val arg: Circular2) extends Circular1

    class Circular2(val arg: Circular1)

    final class Circular2Impl(override val arg: Circular1) extends Circular2(arg)

  }

  object CircularCase2 {

    trait Circular1 {
      def arg: Circular2
    }

    trait Circular2 {
      def arg: Circular3
    }


    trait Circular3 {
      def arg: Circular4

      def arg2: Circular5

      def method: Long = 2L
    }

    trait Circular4 {
      def arg: Circular1

      def factoryFun(c4: Circular4, c5: Circular5): Circular3

      def testVal: Int = 1
    }

    trait Circular5 {
      def arg: Circular1

      def arg2: Circular4
    }

    trait CircularBad1 {
      def arg: CircularBad2

      def bad() = {
        if (Random.nextInt(10) < 100) {
          throw new RuntimeException()
        }
      }

      bad()
    }

    trait CircularBad2 {
      def arg: CircularBad1

      def bad() = {
        if (Random.nextInt(10) < 100) {
          throw new RuntimeException()
        }
      }

      bad()
    }

  }

  object CircularCase3 {

    class SelfReference(val self: SelfReference)

    class ByNameSelfReference(_self: => ByNameSelfReference) {
      final lazy val self = _self
    }

  }

  object CircularCase4 {

    class IdTypeCircular(val dep: Dependency[IdTypeCircular] @Id("special"))
    class IdParamCircular(@Id("special") val dep: Dependency[IdParamCircular])
    class Dependency[T](val dep: T)
  }

  object CircularCase5 {
    class GenericCircular[T](val dep: T)
    class Dependency(val dep: GenericCircular[Dependency])

    class ErasedCircular[T](val dep: PhantomDependency[T])
    class PhantomDependency[T]()
  }

  object CircularCase6 {
    class RefinedCircular(val dep: Dependency { def dep: RefinedCircular })
    trait Dependency {
      def dep: Any
    }
    class RealDependency(val dep: RefinedCircular) extends Dependency
  }

  object CircularCase7 {

    // AutoSets
    trait RoleService extends RoleComponent
    trait RoleComponent
    trait IntegrationComponent

    class K8ProbesHttpRoutes
    (
      private val healthCheckService: HealthCheckService
    )

    class TGLegacyRestService
    class HealthChecker

    class HealthCheckHttpRoutes(private val healthCheckService: HealthCheckService)
    class HealthCheckService(private val healthCheckers: Set[HealthChecker])

    class IRTMultiplexorWithRateLimiter
    (
      private val list: Set[IRTWrappedService]
    )

    class WsSessionListener[A]

    class IRTWrappedClient
    class IRTWrappedService


    class DynamoClient(private val dynamoComponent: DynamoComponent)

    class DynamoComponent(
                           val dynamoDDLService: DynamoDDLService
                         )
      extends RoleComponent
      with IntegrationComponent

    class DynamoDDLService(
                            private val groups: Set[DynamoDDLGroup]
                          , private val dynamoClient: DynamoClient
                          )

    class DynamoQueryExecutorService(
                                      private val client: DynamoClient
                                    )

    case class DynamoDDLGroup(private val a: Set[DynamoTable])
    case class DynamoTable()


    class ConfigurationRepository
    (
      private val dynamoQueryExecutorService: DynamoQueryExecutorService
    )

    class IRTClientMultiplexor(private val clients: Set[IRTWrappedClient])
    class IRTServerBindings(
                             private val codec: IRTClientMultiplexor
                           , private val listeners: Set[WsSessionListener[String]]
                           , private val limiter: IRTMultiplexorWithRateLimiter
                           )

    class HttpServerLauncher(
                              private val bindings: IRTServerBindings
                            , private val healthCheck: HealthCheckHttpRoutes
                            , private val restServices: Set[TGLegacyRestService]
                            , private val k8Probes: K8ProbesHttpRoutes
                            ) extends AutoCloseable {
      override def close(): Unit = ()
    }

    class TgHttpComponent(
                           private val server: HttpServerLauncher
                         ) extends RoleComponent

    class RoleStarter(
                       private val services: Set[RoleService]
                     , private val closeables: Set[AutoCloseable]
                     , private val lifecycleManager: ComponentsLifecycleManager
                     )

    class ComponentsLifecycleManager(private val components: Set[RoleComponent])

    class Sonar(
                 private val TgHttpComponent: TgHttpComponent
               , private val dynamoDDL: DynamoDDLService
               ) extends RoleService
  }

  object ByNameCycle {
    class Circular1(arg: => Circular2) {
      def test: Object = arg
    }

    class Circular2(arg: => Circular1, int: Int) {
      def test: Object = arg
      def testInt: Int = int
    }
  }

}
