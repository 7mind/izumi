# distage-framework

@@toc { depth=2 }

## Roles

Many software suites have more than one entrypoint. For example Scalac provides a REPL and a Compiler. A typical
distributed application may provide both server and client code, as well as a host of utility applications for
maintenance.

In `distage` such entrypoints are called "roles". Role-based applications may contain roles of different types:

- @scaladoc[`RoleService[F]`](izumi.distage.roles.model.RoleService), for persistent services and daemons
- @scaladoc[`RoleTask[F]`](izumi.distage.roles.model.RoleTask), for one-off tasks and applications

To use roles, add `distage-framework` library:

@@dependency[sbt] { group="io.7mind.izumi"
artifact="distage-framework_2.13"
version="$izumi.version$"
}

To declare roles use @scaladoc[RoleModuleDef#makeRole](izumi.distage.roles.model.definition.RoleModuleDef):

```scala mdoc:reset:to-string
import distage.plugins.PluginDef
import izumi.distage.roles.model.definition.RoleModuleDef
import izumi.distage.roles.model.RoleDescriptor
import izumi.distage.roles.model.RoleTask
import izumi.fundamentals.platform.cli.model.raw.RawEntrypointParams
import logstage.LogIO
import zio.UIO

object AppPlugin extends PluginDef {
  include(roleModule)

  def roleModule = new RoleModuleDef {
    makeRole[ExampleRoleTask]
  }
}

class ExampleRoleTask(log: LogIO[UIO]) extends RoleTask[UIO] {
  override def start(roleParameters: RawEntrypointParams, freeArgs: Vector[String]): UIO[Unit] = {
    log.info(s"Running ${ExampleRoleTask.id}!")
  }
}

object ExampleRoleTask extends RoleDescriptor {
  override def id = "exampletask"
}
```

Create a Role Launcher, @scaladoc[RoleAppMain](izumi.distage.roles.RoleAppMain), with the example role:

```scala mdoc:to-string
import distage.plugins.PluginConfig
import izumi.distage.roles.RoleAppMain
import zio.IO

object ExampleLauncher extends RoleAppMain.LauncherBIO2[IO] {
  override def pluginConfig = {
    PluginConfig.const(
      // add the plugin with ExampleRoleTask
      AppPlugin
    )
  }
}
```

You can now specify roles to launch on the command-line:

```
./launcher :exampletask
```

```scala mdoc:to-string
ExampleLauncher.main(Array(":exampletask"))
```

```
I 2020-12-14T10:00:16.172           (RoleAppPlanner.scala:70)  …oleAppPlanner.Impl.makePlan [ 944:run-main-1          ] phase=late Planning finished. main ops=7, integration ops=0, shared ops=0, runtime ops=17
I 2020-12-14T10:00:16.253        (RoleAppEntrypoint.scala:99)  …AppEntrypoint.Impl.runTasks [ 944:run-main-1          ] phase=late Going to run: tasks=1
I 2020-12-14T10:00:16.259       (RoleAppEntrypoint.scala:104)  …R.I.runTasks.101.loggedTask [ 944:run-main-1          ] phase=late Task is about to start: task=repl.MdocSession$App0$ExampleRoleTask@76b7592c
I 2020-12-14T10:00:16.263           (distage-framework.md:43)  ….App0.ExampleRoleTask.start [ 944:run-main-1          ] Running id=exampletask!
I 2020-12-14T10:00:16.267       (RoleAppEntrypoint.scala:106)  ….R.I.r.1.loggedTask.104.105 [ 944:run-main-1          ] phase=late Task finished: task=repl.MdocSession$App0$ExampleRoleTask@76b7592c
```

Only the components required by the specified roles will be created, everything else will be pruned. (see:
@ref[Dependency Pruning](advanced-features.md#dependency-pruning))

@scaladoc[BundledRolesModule](izumi.distage.roles.bundled.BundledRolesModule) contains two example roles:

- @scaladoc[Help](izumi.distage.roles.bundled.Help) - prints help message when launched `./launcher :help`
- @scaladoc[ConfigWriter](izumi.distage.roles.bundled.ConfigWriter) - writes reference config into files, split by
  roles (includes only parts of the config used by the application)

Use `include` to add it to your application:

```scala mdoc:reset:to-string
import distage.plugins.PluginDef
import izumi.distage.roles.bundled.BundledRolesModule
import zio.Task

object RolesPlugin extends PluginDef {
  include(BundledRolesModule[Task](version = "1.0"))
}
```

Further reading:

- [distage-example](https://github.com/7mind/distage-example) - Example web service using roles
- [Roles: a viable alternative to Microservices and Monoliths](https://github.com/7mind/slides/blob/master/02-roles/roles.pdf)

## Compile-time checks

To use compile-time checks, add `distage-framework` library:

@@dependency[sbt] { group="io.7mind.izumi"
artifact="distage-framework_2.13"
version="$izumi.version$"
}

The easiest way to add full compile-time safety to your application is to add an object inheriting
@scaladoc[PlanCheck](izumi.distage.framework.PlanCheck$)@scaladoc[.Main](izumi.distage.framework.PlanCheck$$Main) in __
test scope__ of the same module where you define your @scaladoc[Role Launcher](izumi.distage.roles.RoleAppMain)

```scala mdoc:to-string
import izumi.distage.framework.PlanCheck
import com.example.myapp.MainLauncher

object WiringCheck extends PlanCheck.Main(MainLauncher)
```

This object will emit compile-time errors for any issues or omissions in your `ModuleDefs`, it will be recompiled as
necessary to provide feedback during development.

By default, all possible roles and activations are checked efficiently.

You may exclude specific roles or activations from the check by passing a @scaladoc[PlanCheckConfig](izumi.distage.framework.PlanCheckConfig) case class to PlanCheck API. Most options in `PlanCheckConfig` can also be set using system properties, see @scaladoc[DebugProperties](izumi.distage.framework.DebugProperties$).

### Checking default config

By default `PlanCheck` will check parsing of config bindings (from @ref[distage-extension-config](distage-framework.md#typesafe-config))
using configs loaded with the same settings as the role launcher.

This allows you to ensure correctness of default configs during development without writing tests.

If you need to disable this check, set `checkConfig` option of `PlanCheckConfig` to `false`.

### Using with `distage-teskit`

Use @scaladoc[SpecWiring](izumi.distage.testkit.scalatest.SpecWiring) to spawn a test-suite that also triggers compile-time checks.

`SpecWiring` will check the passed application when compiled, then perform the check again at runtime when ran as a test.

```scala mdoc:reset:to-string
import izumi.distage.framework.PlanCheckConfig
import izumi.distage.testkit.scalatest.SpecWiring
import com.example.myapp.MainLauncher

object WiringCheck extends SpecWiring(
  app = MainLauncher,
  cfg = PlanCheckConfig(checkConfig = false),
  checkAgainAtRuntime = false, // disable the runtime re-check
)
```

### Checking `distage-core` apps

`distage-framework`'s Role-based applications are checkable out of the box, but applications assembled directly via `distage-core`'s `distage.Injector` APIs
must implement the @scaladoc[CheckableApp](izumi.distage.framework.CheckableApp) trait to provide all the data necessary for the checks. You may use @scaladoc[CoreCheckableAppSimple](izumi.distage.framework.CoreCheckableAppSimple) implementation for applications definable by a single collection of modules.

### Low-Level APIs

@scaladoc[PlanCheckMaterializer](izumi.distage.framework.PlanCheckMaterializer),
@scaladoc[ForcedRecompilationToken](izumi.distage.plugins.ForcedRecompilationToken) and
@scaladoc[PlanCheck.runtime](izumi.distage.framework.PlanCheck$$runtime$) provide the low-level APIs behind
`distage` compile-time checks, they can be used to wrap the capability in other APIs or implement similar functionality.

@scaladoc[PlanVerifier](izumi.distage.planning.solver.PlanVerifier) hosts the multi-graph traversal doing
the actual checking and can be invoked at runtime or in macro. It can also be invoked using `Injector#assert` and `Injector#verify` methods.

### Scala 2.12

If you're using Scala `2.12` and get compilation errors such as

```scalak
[error]  type mismatch;
[error]  found   : String("mode:test")
[error]  required: izumi.fundamentals.platform.language.literals.LiteralString{type T = String("mode:test")} with izumi.fundamentals.platform.language.literals.LiteralString
```

Then you'll have to refactor your instance of `PlanCheck.Main` (or similar) to make sure that `PlanCheckConfig` is
defined in a separate `val`. You may do this by moving it from a constructor parameter to an early initializer.

Example:

```scala
object WiringTest extends PlanCheck.Main(
  MyApp,
  PlanCheckConfig(...)
)
// [error]
```

Fix:

```scala
object WiringTest extends {
  val config = PlanCheckConfig(... )
} with PlanCheck.Main(MyApp, config)
```

Note that this issue does not exist on Scala `2.13+`, it is caused by a bug in Scala 2.12's treatment of implicits in class parameter position.

## Typesafe Config

`distage-extension-config` library allows parsing case classes and sealed traits from `typesafe-config` configuration
files.

To use it, add the `distage-extension-config` library:

@@dependency[sbt] { group="io.7mind.izumi"
artifact="distage-extension-config_2.13"
version="$izumi.version$"
}

Use helper functions in `ConfigModuleDef`, `makeConfig` and `make[_].fromConfig` to parse the bound `AppConfig` instance
into case classes, sealed traits or literals:

```scala mdoc:reset-object:to-string
import distage.{Id, Injector}
import distage.config.{AppConfig, ConfigModuleDef}
import com.typesafe.config.ConfigFactory

final case class Conf(
  name: String,
  age: Int,
)

final case class OtherConf(
  other: Boolean
)

final class ConfigPrinter(
  conf: Conf,
  otherConf: OtherConf @Id("other"),
) {
  def print() = {
    println(s"name: ${conf.name}, age: ${conf.age}, other: ${otherConf.other}")
  }
}

val module = new ConfigModuleDef {
  make[ConfigPrinter]

  // declare paths to parse
  makeConfig[Conf]("conf")
  makeConfig[OtherConf]("conf").named("other")

  // add config instance
  make[AppConfig].from(AppConfig(
    ConfigFactory.parseString(
      """conf {
        |  name = "John"
        |  age = 33
        |  other = true
        |}""".stripMargin
    )
  ))
}

Injector().produceRun(module) {
  configPrinter: ConfigPrinter =>
    configPrinter.print()
}
```

Automatic derivation of config codecs is based on [pureconfig-magnolia](https://github.com/pureconfig/pureconfig).

When a given type already has custom `pureconfig.ConfigReader` instances in implicit scope, they will be used, otherwise
they will be derived automatically.

You don't have to explicitly `make[AppConfig]` in @ref[distage-testkit](distage-testkit.md)'s tests and in
@ref[distage-framework](distage-framework.md)'s Roles, unless you want to override default behavior.

By default, tests and roles will try to read the configurations from resources with the following names, in order:

- `${roleName}.conf`
- `${roleName}-reference.conf`
- `${roleName}-reference-dev.conf`
- `application.conf`
- `application-reference.conf`
- `application-reference-dev.conf`
- `common.conf`
- `common-reference.conf`
- `common-reference-dev.conf`

```scala mdoc:reset:invisible
type _ref = izumi.distage.testkit.TestConfig
def _ref = (_: izumi.distage.testkit.TestConfig).configBaseName
```

Where `distage-testkit` uses @scaladoc[`TestConfig#configBaseName`](izumi.distage.testkit.TestConfig#configBaseName)
instead of `roleName`.

Explicit config files passed to the role launcher `-c file.conf` the command-line flag have a higher priority than
resource configs.

Role-specific configs on the command-line, passed via `-c file.conf` option *after* a `:roleName` argument, in turn have
a higher priority than the explicit config files passed *before* the first `:role` argument.

Example:

```
  ./launcher -c global.conf :role1 -c role1.conf :role2 -c role2.conf
```

Here configs will be loaded in the following order, with higher priority earlier and earlier configs overriding the
values in later configs:

- explicits: `role1.conf`, `role2.conf`, `global.conf`,
- resources: `role1[-reference,-dev].conf`, `role2[-reference,-dev].conf`, ,`application[-reference,-dev].conf`, `common[-reference,-dev].conf`

## Plugins

`distage-extension-plugins` module adds classpath discovery for modules that inherit a marker trait `PluginBase`.
Plugins enable extreme late-binding; e.g. they allow a program to extend itself at launch time with new `Plugin` classes
on the classpath. Plugins are compatible with @ref[compile-time checks](distage-framework.md#compile-time-checks) as
long as they're defined in a separate module.

To use plugins, first add the `distage-extension-plugins` library:

@@dependency[sbt] { group="io.7mind.izumi"
artifact="distage-extension-plugins_2.13"
version="$izumi.version$"
}

Create a module extending the `PluginDef` trait instead of `ModuleDef`:

```scala mdoc:reset:invisible
import com.example.petstore._
```

```scala mdoc:fakepackage:to-string
"fakepackage com.example.petstore": Unit

import distage.Injector
import distage.plugins.{PluginConfig, PluginDef, PluginLoader}

object PetStorePlugin extends PluginDef {
  make[PetRepository]
  make[PetStoreService]
  make[PetStoreController]
}
```

Collect all the `PluginDef` classes and objects in a package:

```scala mdoc:to-string
val pluginConfig = PluginConfig.cached(
  // packages to scan
  packagesEnabled = Seq("com.example.petstore")
)

val appModules = PluginLoader().load(pluginConfig)
```

Wire the collected modules as usual:

```scala mdoc:to-string
// combine all modules into one

val appModule = appModules.result.merge

// launch

Injector()
  .produceGet[PetStoreController](appModule)
  .use(_.run())
```

### Compile-time scanning

Plugin scan can be performed at compile-time, this is mainly useful for deployment on platforms with reduced runtime
reflection capabilities compared to the JVM, such as Graal Native Image, Scala.js and Scala Native.
Use @scaladoc[PluginConfig.compileTime](izumi.distage.plugins.PluginConfig$#compileTime) to perform a compile-time scan.

Be warned though, for compile-time scanning to find plugins, they must be placed in a separate module from the one in
which scan is performed. When placed in the same module, scanning will fail.

Example:

```scala mdoc:reset:invisible
import distage.plugins.{PluginConfig, PluginLoader}
```

```scala mdoc:fakepackage:to-string
"fakepackage com.example.petstore.another.module": Unit

val pluginConfig = PluginConfig.compileTime("com.example.petstore")

val loadedPlugins = PluginLoader().load(pluginConfig)
```
