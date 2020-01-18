distage-framework
=======================

@@toc { depth=2 }

### Roles

A "Role" is an entrypoint for a specific application hosted in a larger software suite.
Bundling multiple roles in a single `.jar` file can simplify deployment and operations.

`distage-framework` module contains the distage Role API:

@@@vars

```scala
libraryDependencies += "io.7mind.izumi" %% "distage-framework" % "$izumi.version$"
```

@@@

With default @scaladoc[RoleAppLauncherImpl](izumi.distage.roles.RoleAppLauncherImpl), roles to launch are specified on the command-line: `./launcher role1 role2 role3`.
Only the components required by the specified roles will be created, everything else will be pruned. (see: @ref[GC](advanced-features.md#garbage-collection))

Two roles are bundled by default: @scaladoc[Help](izumi.distage.roles.examples.Help) and @scaladoc[ConfigWriter](izumi.distage.roles.examples.ConfigWriter).

Further reading: [Roles: a viable alternative to Microservices](https://github.com/7mind/slides/blob/master/02-roles/target/roles.pdf)

### Typesafe Config

`distage-extension-config` library allows summoning case classes and sealed traits from `typesafe-config` configuration

To use it, add `distage-extension-config` library:

@@@vars

```scala
libraryDependencies += "io.7mind.izumi" %% "distage-extension-config" % "$izumi.version$"
```

@@@

Add a configuration file in HOCON format:

```hocon
# resources/application.conf
conf {
    name = "John"
    age = 33
    other = true
}
```

Parse it into case classes and summon into your object graph:

```scala mdoc:reset-object:to-string
import distage.{DIKey, GCMode, ModuleDef, Id, Injector}
import distage.config.{AppConfigModule, ConfigModuleDef}
import com.typesafe.config.ConfigFactory

final case class Conf(name: String, age: Int)

final case class OtherConf(other: Boolean)

final class ConfigPrinter(conf: Conf, otherConf: OtherConf @Id("other")) {
  def print() = {
    println(s"name: ${conf.name}, age: ${conf.age}, other: ${otherConf.other}")
  }
}

// load
val config = ConfigFactory.defaultApplication()

// declare paths to parse
val configModule = new ConfigModuleDef {
  makeConfig[Conf]("conf")
  makeConfig[OtherConf]("conf").named("other")
}

// add config itself to the graph
val appConfigModule = AppConfigModule(config)

val appModule = new ModuleDef {
  make[ConfigPrinter]
}

val objects = Injector().produceUnsafe(
  input = Seq(appModule, configModule, appConfigModule).merge,
  mode  = GCMode(DIKey.get[ConfigPrinter])
)

objects.get[ConfigPrinter].print()
```

Automatic derivation of config codecs is based on [circe-config](https://github.com/circe/circe-config) & [circe-derivation](https://github.com/circe/circe-derivation). 
[Circe](https://github.com/circe/circe) codecs for a type will be reused if they exist.

### Plugins

`distage-extension-plugins` module adds classpath discovery for modules that inherit a marker trait `PluginBase`. 
Plugins enable extreme late-binding; e.g. they allow a program to extend itself at launch time with new `Plugin` classes
on the classpath. Plugins are compatible with @ref[compile-time checks](distage-framework.md#compile-time-checks) as long
as they're defined in a separate module.

To use plugins, first add the `distage-extension-plugins` library:

@@@vars

```scala
libraryDependencies += "io.7mind.izumi" %% "distage-extension-plugins" % "$izumi.version$"
```

@@@

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

Injector().produce(appModule, GCMode.NoGC).use {
  _.get[PetStoreController].run()
}
```

### Compile-time checks

An experimental compile-time verification API is available in the `distage-framework` module.

To use it add `distage-framework` library:

@@@vars

```scala
libraryDependencies += "io.7mind.izumi" %% "distage-framework" % "$izumi.version$"
```

@@@

Only plugins defined in a different module can be checked at compile-time, `test` scope counts as a different module.

Example:

In main scope:

```scala mdoc:reset:to-string
// package com.example

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
  Set(DIKey.get[OtherService])
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

```scala mdoc:reset-object:to-string
// package com.example.test

import com.example._
import org.scalatest.wordspec.AnyWordSpec
import izumi.distage.staticinjector.plugins.StaticPluginChecker

final class AppPluginTest extends AnyWordSpec {
  "App plugin will work (if OtherService is provided later)" in {
    StaticPluginChecker.checkWithConfig[AppPlugin, AppRequirements]("env:prod", ".*.application.conf")   
  }
}
```

`checkWithConfig` will run at compile-time whenever `AppPluginTest` is recompiled.

Note: Since version `0.10.0`, configuration files are no longer checked for correctness by the compile-time checker, see: https://github.com/7mind/izumi/issues/763
