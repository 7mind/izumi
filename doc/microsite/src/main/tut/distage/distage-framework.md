distage-framework
=======================

@@toc { depth=2 }

### Roles

A "Role" is an entrypoint for a specific application hosted in a larger software suite.
Bundling multiple roles in a single `.jar` file can simplify deployment and operations.

Add `distage-framework` library for Roles API:

@@dependency[sbt,Maven,Gradle] {
  group="io.7mind.izumi"
  artifact="distage-framework_2.13"
  version="$izumi.version$"
}

With default @scaladoc[RoleAppMain](izumi.distage.roles.RoleAppMain), roles to launch are specified on the command-line: `./launcher role1 role2 role3`.
Only the components required by the specified roles will be created, everything else will be pruned. (see: @ref[Dependency Pruning](advanced-features.md#dependency-pruning))

Two roles are bundled by default: @scaladoc[Help](izumi.distage.roles.bundled.Help) and @scaladoc[ConfigWriter](izumi.distage.roles.bundled.ConfigWriter).

Further reading: [Roles: a viable alternative to Microservices](https://github.com/7mind/slides/blob/master/02-roles/roles.pdf)

### Typesafe Config

`distage-extension-config` library allows summoning case classes and sealed traits from `typesafe-config` configuration

To use it, add the `distage-extension-config` library:

@@dependency[sbt,Maven,Gradle] {
  group="io.7mind.izumi"
  artifact="distage-extension-config_2.13"
  version="$izumi.version$"
}

Use helper functions in `ConfigModuleDef` to parse the Typesafe Config instance bound to `AppConfig` into case classes:

```scala mdoc:reset-object:to-string
import distage.{Id, Injector}
import distage.config.{AppConfig, ConfigModuleDef}
import com.typesafe.config.ConfigFactory

final case class Conf(name: String, age: Int)

final case class OtherConf(other: Boolean)

final class ConfigPrinter(conf: Conf, otherConf: OtherConf @Id("other")) {
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
  make[AppConfig].from(AppConfig(ConfigFactory.parseString(
    """conf {
      |  name = "John"
      |  age = 33
      |  other = true
      |}""".stripMargin
  )))
}

Injector().produceRun(module) {
  configPrinter: ConfigPrinter =>
    configPrinter.print()
}
```

Automatic derivation of config codecs is based on [pureconfig-magnolia](https://github.com/pureconfig/pureconfig).
Pureconfig codecs for a type will be used if they exist.

You don't have to explicitly `make[AppConfig]` in @ref[distage-testkit](distage-testkit.md)'s tests and in @ref[distage-framework](distage-framework.md)'s Roles, unless you want to override default behavior. By default, tests and roles will try to read the configurations from resources with the following names, in order:

```
- $roleName.conf
- $roleName-reference.conf
- $roleName-reference-dev.conf
- application.conf
- application-reference.conf
- application-reference-dev.conf
- common.conf
- common-reference.conf
- common-reference-dev.conf
```

```scala mdoc:reset:invisible
type _ref = izumi.distage.testkit.TestConfig
```

Where `distage-testkit` uses @scaladoc[`TestConfig#testBaseName`](izumi.distage.testkit.TestConfig#testBaseName) instead of `roleName`.

When explicit configs are passed to the role launcher on the command-line using the `-c` option, they have higher priority than all the reference configs.
Role-specific configs on the command-line (`-c` option after `:role` argument) override global command-line configs (`-c` option given before the first `:role` argument).

Example:

```
  ./launcher -c global.conf :role1 -c role1.conf :role2 -c role2.conf
```

Here configs will be loaded in the following order, with higher priority earlier:

  - explicits: `role1.conf`, `role2.conf`, `global.conf`,
  - resources: `role1[-reference,-dev].conf`, `role2[-reference,-dev].conf`, ,`application[-reference,-dev].conf`, `common[-reference,-dev].conf`

### Plugins

`distage-extension-plugins` module adds classpath discovery for modules that inherit a marker trait `PluginBase`.
Plugins enable extreme late-binding; e.g. they allow a program to extend itself at launch time with new `Plugin` classes
on the classpath. Plugins are compatible with @ref[compile-time checks](distage-framework.md#compile-time-checks) as long
as they're defined in a separate module.

To use plugins, first add the `distage-extension-plugins` library:

@@dependency[sbt,Maven,Gradle] {
  group="io.7mind.izumi"
  artifact="distage-extension-plugins_2.13"
  version="$izumi.version$"
}

Create a module extending the `PluginDef` trait instead of `ModuleDef`:

```scala mdoc:reset:invisible
import com.example.petstore._
```

```scala mdoc:to-string
// package com.example.petstore

import distage._
import distage.plugins._

object PetStorePlugin extends PluginDef {
  make[PetRepository]
  make[PetStoreService]
  make[PetStoreController]
}
```

Collect all `PluginDefs` in a package:

```scala mdoc:to-string
val pluginConfig = PluginConfig.cached(
  packagesEnabled = Seq("com.example.petstore") // packages to scan
)

val appModules = PluginLoader().load(pluginConfig)
```

Execute collected modules as usual:

```scala mdoc:to-string
// combine all modules into one

val appModule = appModules.merge

// launch

Injector()
  .produceGet[PetStoreController](appModule)
  .use(_.run())
```

### Compile-time checks

An experimental compile-time verification API is available in the `distage-framework` module.

To use it add `distage-framework` library:

@@dependency[sbt,Maven,Gradle] {
  group="io.7mind.izumi"
  artifact="distage-framework_2.13"
  version="$izumi.version$"
}

Only plugins defined in a different module can be checked at compile-time.

`test` scope counts as a separate module.

Example:

In main scope:

```scala mdoc:reset:invisible:to-string
```

```scala mdoc:fakepackage:to-string
"fakepackage com.example": Unit

import distage.DIKey
import distage.StandardAxis.Env
import distage.config.ConfigModuleDef
import distage.plugins.PluginDef
import izumi.distage.staticinjector.plugins.ModuleRequirements

final case class HostPort(host: String, port: Int)

final case class Config(hostPort: HostPort)

final class Service(conf: Config, otherService: OtherService)
final class OtherService

// error: OtherService is not bound here, even though Service depends on it
final class AppPlugin extends PluginDef with ConfigModuleDef {
  tag(Env.Prod)

  make[Service]
  makeConfig[Config]("config")
}

// Declare OtherService as an external dependency
final class AppRequirements extends ModuleRequirements(
  // If we remove this line, compilation will rightfully break
  Set(DIKey[OtherService])
)
```

In config:

```scala
// src/main/resources/application.conf
config {
  host = localhost
  port = 8080
}
```

In test scope:

```scala mdoc:reset-object:invisible:to-string
```

```scala mdoc:fakepackage:to-string
"fakepackage com.example.test": Unit

import com.example._
import org.scalatest.wordspec.AnyWordSpec
import izumi.distage.staticinjector.plugins.StaticPluginChecker

final class AppPluginTest extends AnyWordSpec {
  "App plugin will work (if OtherService is provided later)" in {
    StaticPluginChecker.checkWithConfig[AppPlugin, AppRequirements]("mode:prod", ".*.application.conf")
  }
}
```

`checkWithConfig` will run at compile-time whenever `AppPluginTest` is recompiled.

Note: Since version `0.10.0`, configuration files are no longer checked for correctness by the compile-time checker, see: https://github.com/7mind/izumi/issues/763
