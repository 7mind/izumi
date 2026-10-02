package izumi.distage.testkit.runner.spec

import distage.{Functoid, TagK}
import izumi.distage.modules.DefaultModule
import izumi.distage.plugins.load.PluginLoader
import izumi.distage.plugins.merge.PluginMergeStrategy
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.testkit.model.{DistageTest, SuiteId as EngineSuiteId, SuiteMeta, TestConfig, TestEnvironment, TestId as EngineTestId, TestMeta}
import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.*
import izumi.distage.testkit.runner.di.{DistageExecutionProvider, DistageRunnerOptions, RegisteredDistageTest, ResolvedDistageTest}
import izumi.distage.testkit.runner.impl.services.TestConfigLoader
import izumi.distage.testkit.spec.{DistageTestEnv, TestConfiguration}
import izumi.fundamentals.assertions.Assertions
import izumi.fundamentals.platform.language.SourceFilePosition
import izumi.fundamentals.platform.language.types.HigherKindedAny.AnyF

import scala.util.control.NonFatal

abstract class DistageSpec[F[_]](implicit val tagMonoIO: TagK[F], val defaultModulesIO: DefaultModule[F])
  extends TestConfiguration with DistageTestEnv with Assertions with TestSuite {
  private final class Registration(val path: Vector[String], val location: SourceLocation, val position: SourceFilePosition, val function: Functoid[F[Any]])
  private var prefix = Vector.empty[String]
  private var registrations = Vector.empty[Registration]
  private var ownedProvider = Option.empty[DistageExecutionProvider]

  override protected def config: TestConfig = TestConfig.forSuite(getClass)
  protected def distageSuiteName: String = getClass.getSimpleName.stripSuffix("$")
  protected def distageSuiteId: EngineSuiteId = EngineSuiteId(getClass.getName)
  protected final lazy val testEnv: TestEnvironment = makeTestEnv()
  protected def makeTestEnv(): TestEnvironment = loadEnvironment(config, tagMonoIO, defaultModulesIO)
  private lazy val environmentResolution: Either[Failure, TestEnvironment] = try Right(testEnv) catch {
    case NonFatal(cause) => Left(RunnerFailure.fromThrowable(FailurePhase.Planning, cause))
  }

  override protected def makePluginloader(): PluginLoader = provider.defaultPluginLoader

  override private[distage] def loadEnvironment[G[_]](testConfig: TestConfig, tagK: TagK[G], defaultModule: DefaultModule[G]): TestEnvironment = {
    val roles = loadRoles()
    val merge = makeMergeStrategy()
    val loader = makePluginloader()
    makeEnv(testConfig, loader, roles, merge, tagK, defaultModule)
  }

  override private[distage] def makeEnv[G[_]](
    testConfig: TestConfig,
    pluginLoader: PluginLoader,
    roles: RolesInfo,
    mergeStrategy: PluginMergeStrategy,
    tagK: TagK[G],
    defaultModule: DefaultModule[G],
  ): TestEnvironment = provider.environments.load[G](testConfig, pluginLoader, roles, mergeStrategy, tagK, defaultModule)

  private def provider: DistageExecutionProvider = ownedProvider.getOrElse(throw new IllegalStateException("Distage environment is unavailable before session registration"))

  private[spec] final def path(text: String): Vector[String] = prefix :+ text

  private[spec] final def branch(text: String, verb: String, body: () => Unit): Unit = {
    require(ownedProvider.isEmpty, "Suite registration is already frozen")
    val outer = prefix
    prefix = outer ++ Vector(text, verb)
    try body() finally { prefix = outer }
  }

  private[spec] final def add[A](path: Vector[String], function: Functoid[F[A]], location: SourceLocation, position: SourceFilePosition): Unit = {
    require(ownedProvider.isEmpty, "Suite registration is already frozen")
    require(!registrations.exists(_.path == path), s"Duplicate distage test path: $path")
    registrations :+= new Registration(path, location, position, function.asInstanceOf[Functoid[F[Any]]])
  }

  final override def register(context: RegistrationContext): RegisteredSuite = synchronized {
    require(ownedProvider.isEmpty, "Suite instance cannot be shared between sessions")
    val execution = context.provider(ProviderId("distage"), () => new DistageExecutionProvider(context.executionContext, new TestConfigLoader.TestConfigLoaderImpl, DistageRunnerOptions(false, false)))
    ownedProvider = Some(execution)
    val suite = distageSuiteId
    val meta = SuiteMeta(suite, distageSuiteName, getClass.getName)
    val descriptor = SuiteDescriptor(SuiteId(suite.suiteId), meta.suiteName)
    val tests = registrations.map { registration =>
      val id = TestId(context.target, descriptor.id, registration.path, None)
      val test = TestDescriptor(id, registration.path.mkString(" "), registration.location, EffectiveSettings(Vector.empty, memoization = true))
      RegisteredDistageTest(test, overrides => environmentResolution.flatMap(environment => execution.resolveEnvironment(environment, overrides)).map { effective =>
        val engineTest = DistageTest(registration.function, effective.environment, TestMeta(EngineTestId(registration.path, suite), registration.position, 0L), meta)
        ResolvedDistageTest(test.copy(settings = effective.settings), engineTest.asInstanceOf[DistageTest[AnyF]])
      })
    }
    execution.add(tests)
    RegisteredSuite(descriptor, tests.map(_.descriptor), execution)
  }
}
