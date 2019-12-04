distage-framework
=======================

@@toc { depth=2 }

### Roles

A "Role" is an entrypoint for a specific application hosted in a larger software suite. Bundling multiple roles in a
single `.jar` file can simplify deployment and operations.

`distage-framework` module contains the distage Role API:

@@@vars

```scala
libraryDependencies += "io.7mind.izumi" %% "distage-framework" % "$izumi.version$"
```

@@@

With default `RoleLauncher` implementation, roles to be launched are chosen by command-line parameters: `./launcher role1 role2 role3`.
Only the components required by the chosen roles will be created, everything else will be pruned. (see: @ref[GC](other-features.md#garbage-collection))

Further reading: [Roles: a viable alternative to Microservices](https://github.com/7mind/slides/blob/master/02-roles/target/roles.pdf)

### Plugins

`distage-plugins` module adds classpath discovery for modules that inherit a marker trait `PluginBase`. 
Plugins enable extreme late-binding; e.g. they allow a program to extend itself at launch time by adding new `Plugin` classes
to the classpath. Plugins are compatible with @ref[compile-time checks](distage-framework.md#compile-time-checks) as long as they're defined in a separate module.

To use plugins, first add the `distage-plugins` library:

@@@vars

```scala
libraryDependencies += "io.7mind.izumi" %% "distage-plugins" % "$izumi.version$"
```

@@@

Create a module extending the `PluginDef` trait instead of `ModuleDef`:

```scala mdoc:invisible
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
val pluginLoader = PluginLoader(
  PluginConfig(
    debug = false,
    packagesEnabled = Seq("com.example.petstore"), // packages to scan
    packagesDisabled = Seq.empty,                  // packages to ignore
  )
)

val appModules = pluginLoader.load()
```

Execute collected modules as usual:

```scala mdoc:to-string
// combine all modules into one

val appModule = appModules.merge

// launch

Injector().produce(appModule, GCMode.NoGC).use {
  _.get[PetStoreController].run
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
import org.scalatest.WordSpec
import izumi.distage.staticinjector.plugins.StaticPluginChecker

final class AppPluginTest extends WordSpec {
  "App plugin will work (if OtherService is provided later)" in {
    StaticPluginChecker.checkWithConfig[AppPlugin, AppRequirements]("env:prod", ".*.application.conf")   
  }
}
```

`checkWithConfig` will run at compile-time whenever `AppPluginTest` is recompiled.

Note: Since version `0.10.0`, configuration files are no longer checked for correctness by the compile-time checker, see: https://github.com/7mind/izumi/issues/763
