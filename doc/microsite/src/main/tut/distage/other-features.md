Other Features
==============

@@toc { depth=2 }

### Garbage Collection

A garbage collector is included in `distage` by default. Given a set of `GC root` keys, GC will remove all bindings that
are neither direct nor transitive dependencies of the supplied roots – these bindings will be thrown out and never instantiated.

GC serves two important purposes:

* It enables faster @ref[tests](basics.md#testkit) by omitting unrequired instantiations and initialization of potentially heavy resources,
* It enables multiple independent applications, aka "@ref[Roles](#roles)" to be hosted within a single `.jar` file.

To use garbage collector, pass GC roots as an argument to `Injector.produce*` methods:

```scala mdoc:reset-class
import distage._

case class A(b: B)
case class B()
case class C() {
  println("C!")
}

val module = new ModuleDef {
  make[A]
  make[B]
  make[C]
}
```

```scala mdoc:invisible
val HACK_OVERRIDE_module = new ModuleDef {
  make[A].from(new A(_))
  make[B].from(new B)
  make[C].from(new C)
}
```

```scala mdoc:override
val roots = Set[DIKey](DIKey.get[A])

val locator = Injector().produceUnsafe(HACK_OVERRIDE_module, roots = roots)

// A and B are here
locator.find[A]
locator.find[B]

// C was not created
locator.find[C]
```

Class `C` was removed because neither `B` nor `A` depended on it. It's not present in the `Locator` and the `"C!"` message was never printed.
But, if class `B` were to depend on `C` as in `case class B(c: C)`, it would've been retained, because `A` - the GC root, would depend on `B` which in turns depends on `C`.

### Auto-Traits

@@@ warning { title='TODO' }
Sorry, this page is not ready yet
@@@

...

### Auto-Factories

`distage` can automatically create 'factory' classes from suitable traits.
This feature is especially useful with `Akka`.

Given a class `ActorFactory`:

```scala mdoc
import distage._
import java.util.UUID

class SessionStorage

class UserActor(sessionId: UUID, sessionStorage: SessionStorage)

trait ActorFactory {
  def createActor(sessionId: UUID): UserActor
}
```

And a binding of `ActorFactory` *without* an implementation

```scala mdoc
class ActorModule extends ModuleDef {
  make[ActorFactory]
}
```

`distage` will derive and bind the following implementation for `ActorFactory`:

```scala mdoc
class ActorFactoryImpl(sessionStorage: SessionStorage) extends ActorFactory {
  override def createActor(sessionId: UUID): UserActor = {
    new UserActor(sessionId, sessionStorage)
  }
}
```

You can use this feature to concisely provide non-singleton semantics for some of your components.

By default, the factory implementation class will be created automatically at runtime.
To create factories at compile-time use `distage-static` module.

### Plugins

Plugins are a distage extension that allows you to automatically pick up all `Plugin` modules that are defined in specified package on the classpath.

Plugins are especially useful in scenarios with [extreme late-binding](#roles), when the list of loaded application modules is not known ahead of time.
Plugins are compatible with [compile-time checks](#compile-time-checks) as long as they're defined in a separate module.

To use plugins add `distage-plugins` library:

```scala
libraryDependencies += Izumi.R.distage_plugins
```

or

@@@vars

```scala
libraryDependencies += "com.github.pshirshov.izumi.r2" %% "distage-plugins" % "$izumi.version$"
```

@@@

If you're not using @ref[sbt-izumi-deps](../sbt/00_sbt.md#bills-of-materials) plugin.

Create a module extending the `PluginDef` trait instead of `ModuleDef`:

```scala
package com.example.petstore

import distage._
import distage.plugins._

trait PetStorePlugin extends PluginDef {
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

### Compile-Time Instantiation

@@@ warning { title='TODO' }
Sorry, this page is not ready yet

Relevant ticket: https://github.com/pshirshov/izumi-r2/issues/453
@@@

WIP

You can participate in this ticket at https://github.com/pshirshov/izumi-r2/issues/453

### Compile-Time Checks

@@@ warning { title='TODO' }
Sorry, this page is not ready yet

Relevant ticket: https://github.com/pshirshov/izumi-r2/issues/51
@@@

As of now, an experimental plugin-checking API is available in `distage-app` module.

To use it add `distage-app` library:

```scala
libraryDependencies += Izumi.R.distage_app
```

or

@@@vars

```scala
libraryDependencies += "com.github.pshirshov.izumi.r2" %% "distage-app" % "$izumi.version$"
```

@@@

If you're not using @ref[sbt-izumi-deps](../sbt/00_sbt.md#bills-of-materials) plugin.

Only plugins defined in a different module can be checked at compile-time, `test` scope counts as a different module.

##### Example:

In main scope:

```scala
// src/main/scala/com/example/AppPlugin.scala
package com.example
import distage._
import distage.plugins._
import distage.config._
import com.github.pshirshov.izumi.distage.app.ModuleRequirements

final case class HostPort(host: String, port: Int)

final case class Config(hostPort: HostPort)

final class Service(conf: Config @ConfPath("config"), otherService: OtherService)

// OtherService class is not defined here, even though Service depends on it
final class AppPlugin extends PluginDef {
  make[Service]
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
// We are going to check if our starting configuration is correct as well.
config {
  // If we remove these, the compilation will rightfully break, as the `HostPort` case class won't deserialize from the config
  host = localhost
  port = 8080
}
```

In test scope:

```scala
// src/test/scala/com/example/test/AppPluginTest.scala
package com.example.test

import com.example._
import org.scalatest.WordSpec
import com.github.pshirshov.izumi.distage.app.StaticPluginChecker

final class AppPluginTest extends WordSpec {
  
  "App plugin will work (if OtherService will be provided later)" in {
    StaticPluginChecker.checkWithConfig[AppPlugin, AppRequirements](disableTags = "", configFileRegex = "*.application.conf")   
  }

}
```

`checkWithConfig` will run at compile-time, every time that AppPluginTest is recompiled.

You can participate in this ticket at https://github.com/pshirshov/izumi-r2/issues/51

### Roles

@@@ warning { title='TODO' }
Sorry, this page is not ready yet
@@@

"Roles" are a pattern of multi-tenant applications, in which multiple separate microservices all reside within a single `.jar`.
This strategy helps cut down development, maintenance and operations costs associated with maintaining fully separate code bases and binaries.
Apps are chosen via command-line parameters: `./launcher app1 app2 app3`. If you're not launching all apps
hosted by the launcher at the same time, the redundant components from unlaunched apps will be [garbage collected](#garbage-collection)
and won't be started.

consult slides [Roles: a viable alternative to Microservices](https://github.com/7mind/slides/blob/master/02-roles/target/roles.pdf)
for more details.

`distage-roles` module hosts the current experimental Roles API:

```scala
libraryDependencies += Izumi.R.distage_roles
```

or

@@@vars

```scala
libraryDependencies += "com.github.pshirshov.izumi.r2" %% "distage-roles" % "$izumi.version$"
```

@@@

If you're not using @ref[sbt-izumi-deps](../sbt/00_sbt.md#bills-of-materials) plugin.


### Circular Dependencies support

`distage` automatically resolves circular dependencies, including self-reference:

```scala
import distage._

case class A(b: B)
case class B(a: A) 
case class C(c: C)

val locator = Injector().produce(new ModuleDef {
  make[A]
  make[B]
  make[C]
})

locator.get[A] eq locator.get[B].a
// res0: Boolean = true
locator.get[B] eq locator.get[A].b
// res1: Boolean = true
locator.get[C] eq locator.get[C].c
// res2: Boolean = true
```

#### Automatic Resolution with generated proxies

The above strategy depends on `distage-proxy-cglib` module which is brought in by default with `distage-core`.

It's enabled by default. If you want to disable it, use `noCogen` bootstrap environment:

```scala
import com.github.pshirshov.izumi.distage.bootstrap.DefaultBootstrapContext
import distage._

Injector(DefaultBootstrapContext.noCogen)
```

#### Manual Resolution with by-name parameters

Most cycles can also be resolved manually when identified, using `by-name` parameters.

Circular dependencies in the following example are all resolved via Scala's native `by-name`'s, without any proxy generation:

```scala
import com.github.pshirshov.izumi.distage.bootstrap.DefaultBootstrapContext.noCogen
import distage._

class A(b0: => B) {
  def b: B = b0
}

class B(a0: => A) {
  def a: A = a0
}

class C(self: => C) {
  def c: C = self
}

val module = new ModuleDef {
  make[A]
  make[B]
  make[C]
}

val locator = Injector(noCogen).produce(module)

assert(locator.get[A].b eq locator.get[B])
assert(locator.get[B].a eq locator.get[A])
assert(locator.get[C].c eq locator.get[C])
```

The proxy generation via `cglib` is still enabled by default, because in scenarios with [extreme late-binding](#roles),
cycles can emerge unexpectedly, outside of control of the origin module.

NB: Currently a limitation applies to by-names - ALL dependencies on a class engaged in a by-name circular dependency have to be by-name,
otherwise distage will transparently revert to generating proxies.

### Auto-Sets: Collecting Bindings By Predicate

AutoSet @scaladoc[Planner](com.github.pshirshov.izumi.distage.model.Planner) Hooks traverse the plan and collect all future objects matching a predicate.

Using Auto-Sets you can e.g. collect all `AutoCloseable` classes and `.close()` them after the application has finished work.

```scala
trait PrintResource(name: String) {
  def start(): Unit = println(s"$name started")
  def stop(): Unit = println(s"$name stopped")
}

class A extends PrintResource("A")
class B(val a: A) extends PrintResource("B")
class C(val b: B) extends PrintResource("C")

val resources = Injector(new BootstrapModuleDef {
  many[PlanningHook]
    .add(new AssignableFromAutoSetHook[PrintResource])
}).produce(new ModuleDef {
  make[C]
  make[B]
  make[A]
}).get[Set[PrintResource]]

resources.foreach(_.start())
resources.reverse.foreach(_.stop())

// Will print:
// A started
// B started
// C started
// C stopped
// B stopped
// A stopped
```

Calling `.foreach` on an auto-set is safe; the actions will be executed in order of dependencies.
Auto-Sets preserve ordering, they use `ListSet` under the hood, unlike user-defined [Sets](#multibindings--set-bindings).
e.g. If `C` depends on `B` depends on `A`, autoset order is: `A, B, C`, to start call: `A, B, C`, to close call: `C, B, A`.
When you use auto-sets for finalization, you **must** `.reverse` the autoset.

Note: Auto-Sets are NOT subject to [Garbage Collection](#using-garbage-collector), they are assembled *after* garbage collection is done,
as such they can't contain garbage by construction.

### Weak Sets

[Set bindings](#set-bindings--multibindings) can contain *weak* references. References designated as weak will
be retained by [Garbage Collector](#using-garbage-collector) _only_ if there are other references to them except the
set binding itself.

Example:

```scala
import distage._

sealed trait SetElem

final class Strong extends SetElem {
  println("Strong constructed")
}

final class Weak extends SetElem {
  println("Weak constructed")
}

val module = new ModuleDef {
  make[Strong]
  make[Weak]
  
  many[SetElem]
    .ref[Strong]
    .weak[Weak]
}

// Designate Set[SetElem] as the garbage collection root,
// everything that Set[SetElem] does not strongly depend on will be garbage collected
// and will not be constructed. 
val roots = Set[DIKey](DIKey.get[Set[SetElem]])

val locator = Injector().produceUnsafe(HACK_OVERRIDE_module, roots = roots)

locator.get[Set[SetElem]].size == 1
// res0: Boolean = true
```

The `Weak` class was not required in any dependency of `Set[SetElem]`, so it was pruned.
The `Strong` class remained, because the reference to it was **strong**, therefore it was counted as a dependency of `Set[SetElem]`

If we change `Strong` to depend on `Weak`, then `Weak` will be retained:

```scala
final class Strong(weak: Weak) {
  println("Strong constructed")
}

locator.get[Set[SetElem]].size == 2
// res1: Boolean = true
```

### Cats Integration

You can use `produceF` with `cats.effect.IO` with just `distage-core`.

@ref[Cats Resource Bindings](basics.md#resource-bindings--lifecycle) will also work out of the box without any additional modules.

@@@ warning { title='deprecated' }
All cats instances & syntax will be rolled into `distage-core`. (via sbt's `Optional` dependencies, they will only be
available if your project depends on `cats`.)
@@@

To import cats integration add `distage-cats` library:

```scala
libraryDependencies += Izumi.R.distage_cats
```

or

@@@vars

```scala
libraryDependencies += "com.github.pshirshov.izumi.r2" %% "distage-cats" % "$izumi.version$"
```

@@@

If you're not using @ref[sbt-izumi-deps](../sbt/00_sbt.md#bills-of-materials) plugin.

Usage:

```scala
import cats.implicits._
import cats.effect._
import distage._
import distage.interop.cats._
import com.example.{DBConnection, AppEntrypoint}

object Main extends IOApp {
  def run(args: List[String]): IO[Unit] = {
    val myModules = module1 |+| module2 // Monoid instance for ModuleDef is available now
    
    for {
      plan <- myModules.resolveImportsF[IO] { // resolveImportsF is now available
        case i if i.target == DIKey.get[DBConnection] =>
           DBConnection.create[IO]
      } 
      classes <- Injector().produceF[IO](plan) // produceF allows using `IO` with effect and resource bindings
      _ <- classes.get[AppEntrypoint].run
    } yield ()
  }
}
```
