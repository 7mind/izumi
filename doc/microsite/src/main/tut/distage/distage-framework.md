distage-framework
=======================

### Roles

@@@ warning { title='TODO' }
Sorry, this page is not ready yet
@@@

"Roles" are a pattern of multi-tenant applications, in which multiple separate microservices all reside within a single `.jar`.
This strategy helps cut down development, maintenance and operations costs associated with maintaining fully separate code bases and binaries.
Apps are chosen via command-line parameters: `./launcher app1 app2 app3`. If you're not launching all apps
hosted by the launcher at the same time, the redundant components from unlaunched apps will be @ref[garbage collected](other-features.md#garbage-collection)
and won't be started.

consult slides [Roles: a viable alternative to Microservices](https://github.com/7mind/slides/blob/master/02-roles/target/roles.pdf)
for more details.

`distage-framework` module hosts the current experimental Roles API:

@@@vars

```scala
libraryDependencies += "io.7mind.izumi" %% "distage-framework" % "$izumi.version$"
```

@@@

### Plugins

Plugins are a distage extension that allows you to automatically pick up all `Plugin` modules that are defined in specified package on the classpath.

Plugins are especially useful in scenarios with @ref[extreme late-binding](distage-framework.md#roles), when the list of loaded application modules is not known ahead of time.
Plugins are compatible with @ref[compile-time checks](distage-framework.md#compile-time-checks) as long as they're defined in a separate module.

To use plugins add `distage-plugins` library:

@@@vars

```scala
libraryDependencies += "io.7mind.izumi" %% "distage-plugins" % "$izumi.version$"
```

@@@

Create a module extending the `PluginDef` trait instead of `ModuleDef`:

```scala
package com.example.petstore

import distage._
import distage.plugins._

object PetStorePlugin extends PluginDef {
  make[PetRepository]
  make[PetStoreService]
  make[PetStoreController]
}
```

At your app entry point use a plugin loader to discover all `PluginDefs`:

```scala
val pluginLoader = new PluginLoaderDefaultImpl(
  PluginConfig(
    debug = true
  , packagesEnabled = Seq("com.example.petstore") // packages to scan
  , packagesDisabled = Seq.empty         // packages to ignore
  )
)

val appModules: Seq[PluginBase] = pluginLoader.load()
val app: ModuleBase = appModules.merge
```

Launch as normal with the loaded modules:

```scala
Injector().produce(app).use {
  _.get[PetStoreController].run
}
```

Plugins also allow a program to extend itself at runtime by adding new `Plugin` classes to the classpath via `java -cp`

### Compile-Time Checks

An experimental compile-time verification API is available in the `distage-framework` module.

To use it add `distage-framework` library:

@@@vars

```scala
libraryDependencies += "io.7mind.izumi" %% "distage-framework" % "$izumi.version$"
```

@@@

Only plugins defined in a different module can be checked at compile-time, `test` scope counts as a different module.

##### Example:

In main scope:

```scala mdoc:reset
// src/main/scala/com/example/AppPlugin.scala
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

```scala mdoc:reset-object
// src/test/scala/com/example/test/AppPluginTest.scala
// package com.example.test

import com.example._
import org.scalatest.WordSpec
import izumi.distage.staticinjector.plugins.StaticPluginChecker

final class AppPluginTest extends WordSpec {
  
  "App plugin will work (if OtherService will be provided later)" in {
    StaticPluginChecker.checkWithConfig[AppPlugin, AppRequirements]("env:prod", ".*.application.conf")   
  }

}
```

`checkWithConfig` will run at compile-time, every time that AppPluginTest is recompiled.

Note: Since version 0.10, configuration files are no longer checked for correctness by compile-time checker, see: https://github.com/7mind/izumi/issues/763
