package com.github.pshirshov.izumi.distage.testkit

import cats.effect.IO
import com.github.pshirshov.izumi.distage.model.definition.Axis.AxisValue
import com.github.pshirshov.izumi.distage.model.definition.StandardAxis.Env
import com.github.pshirshov.izumi.distage.model.definition.{AxisBase, BootstrapModuleDef}
import com.github.pshirshov.izumi.distage.model.planning.PlanMergingPolicy
import com.github.pshirshov.izumi.distage.plugins.load.PluginLoader.PluginConfig
import com.github.pshirshov.izumi.distage.plugins.merge.{PluginMergeStrategy, SimplePluginMergeStrategy}
import com.github.pshirshov.izumi.distage.roles.BootstrapConfig
import com.github.pshirshov.izumi.distage.roles.model.AppActivation
import com.github.pshirshov.izumi.distage.roles.model.meta.RolesInfo
import com.github.pshirshov.izumi.distage.roles.services.{ActivationParser, IntegrationCheckerImpl, PluginSource, PluginSourceImpl, PruningPlanMergingPolicy}
import com.github.pshirshov.izumi.distage.testkit.DistagePluginTestSupport.{CacheKey, CacheValue}
import com.github.pshirshov.izumi.distage.testkit.DistageTestRunner._
import com.github.pshirshov.izumi.distage.testkit.fixtures2.{ApplePaymentProvider, MockCachedUserService, MockUserRepository}
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.logstage.api.{IzLogger, Log}
import org.scalatest.WordSpec



class DistageTestRunnerTest extends WordSpec {
  "distage test runner" should {
    "support shared component flow" in {
      // each test suite needs to do some initialization job before it can return a test descriptor
      val logger = IzLogger.apply(Log.Level.Debug)("phase" -> "test")
      val env = loadEnvironment(logger)

      // these are all the discovered tests. Not that ProviderMagnet has implicit macro materializer turning
      // a lambda into a whitebox entity which can be introspected at runtime
      val discoveredTests: Seq[DistageTest[IO]] = List(
        DistageTest(TestId("Test1"), {
          service: MockCachedUserService[IO] =>
            for {
              _ <- IO.delay(assert(service != null))
              _ <- IO.delay(println("test1"))
            } yield {

            }

        }, env),
        DistageTest(TestId("Test2"), {
          service: MockUserRepository[IO] =>
            for {
              _ <- IO.delay(assert(service != null))
              _ <- IO.delay(println("test2"))
            } yield {

            }

        }, env),
        DistageTest(TestId("Test3"), {
          service: MockCachedUserService[IO] =>
            ???
        }, env),
        DistageTest(TestId("Test4"), {
          service: ApplePaymentProvider[IO] =>
            ???
        }, env),
      )


      // a callback for our test framework
      val reporter = new TestReporter {
        override def testStatus(test: TestId, testStatus: TestStatus): Unit = {
          println(s"Test ${test.string} is $testStatus")
        }
      }


      // control gets back to custom logic
      val checker = new IntegrationCheckerImpl(logger)
      val runner = new DistageTestRunner[IO](reporter, checker)

      // we add all the collected tests into our custom runner
      discoveredTests.foreach {
        t =>
          runner.register(t)
      }

      // and run custom logic (continue with runner.run()...)
      runner.run()

    }
  }



  /**
    * Merge strategy will be applied only once for all the tests with the same bootstrap config when memoization is on
    */
  final def loadEnvironment(logger: IzLogger): TestEnvironment = {
    val config = bootstrapConfig

    def env(): CacheValue = {
      val plugins = makePluginLoader(config).load()
      val mergeStrategy = makeMergeStrategy(logger)
      val defApp = mergeStrategy.merge(plugins.app)
      val bootstrap = mergeStrategy.merge(plugins.bootstrap)
      val availableActivations = ActivationParser.findAvailableChoices(logger, defApp)
      CacheValue(plugins, bootstrap, defApp, availableActivations)
    }

    val plugins = if (memoizePlugins) {
      DistagePluginTestSupport.Cache.getOrCompute(CacheKey(config), env())
    } else {
      env()
    }

    doLoad(logger, plugins)
  }

  protected final def doLoad(logger: IzLogger, env: CacheValue): TestEnvironment = {
    val roles = loadRoles(logger)
    val appActivation = AppActivation(env.availableActivations, activation)
    val defBs = env.bsModule overridenBy new BootstrapModuleDef {
      make[PlanMergingPolicy].from[PruningPlanMergingPolicy]
      make[AppActivation].from(appActivation)
    }
    TestEnvironment(
      defBs,
      env.appModule,
      roles,
      appActivation,
    )
  }

  protected def memoizePlugins: Boolean = {
    import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

    System.getProperty("izumi.distage.testkit.plugins.memoize")
      .asBoolean(true)
  }

  protected def loadRoles(logger: IzLogger): RolesInfo = {
    Quirks.discard(logger)
    // For all normal scenarios we don't need roles to setup a test
    RolesInfo(Set.empty, Seq.empty, Seq.empty, Seq.empty, Set.empty)
  }

  protected def activation: Map[AxisBase, AxisValue] = Map(Env -> Env.Test)

  protected def makeMergeStrategy(lateLogger: IzLogger): PluginMergeStrategy = {
    Quirks.discard(lateLogger)
    SimplePluginMergeStrategy
  }

  protected def bootstrapConfig: BootstrapConfig = {
    BootstrapConfig(
      PluginConfig(debug = false, pluginPackages, Seq.empty),
      pluginBootstrapPackages.map(p => PluginConfig(debug = false, p, Seq.empty)),
    )
  }

  protected def makePluginLoader(bootstrapConfig: BootstrapConfig): PluginSource = {
    new PluginSourceImpl(bootstrapConfig)
  }

  protected final def thisPackage: Seq[String] = Seq(this.getClass.getPackage.getName)

  protected def pluginPackages: Seq[String] = thisPackage

  protected def pluginBootstrapPackages: Option[Seq[String]] = None
}
