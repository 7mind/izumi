Other Features
==============

@@toc { depth=2 }

### Garbage Collection

A garbage collector is included in `distage` by default. Given a set of `GC root` keys, GC will remove all bindings that
are neither direct nor transitive dependencies of the supplied roots – these bindings will be thrown out and never instantiated.

GC serves two important purposes:

* It enables faster @ref[tests](distage-testkit.md#distage-testkit) by omitting unrequired instantiations and initialization of potentially heavy resources,
* It enables multiple independent applications, aka "@ref[Roles](distage-framework.md#roles)" to be hosted within a single `.jar` file.

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

val gc = GCMode.GCRoots(Set[DIKey](DIKey.get[A]))

val locator = Injector().produceUnsafe(module, mode = gc)

// A and B are here
locator.find[A]
locator.find[B]

// C was not created
locator.find[C]
```

Class `C` was removed because neither `B` nor `A` depended on it. It's not present in the `Locator` and the `"C!"` message was never printed.
But, if class `B` were to depend on `C` as in `case class B(c: C)`, it would've been retained, because `A` - the GC root, would depend on `B` which in turns depends on `C`.

### Plugins

Plugins are a distage extension that allows you to automatically pick up all `Plugin` modules that are defined in specified package on the classpath.

Plugins are especially useful in scenarios with @ref[extreme late-binding](distage-framework.md#roles), when the list of loaded application modules is not known ahead of time.
Plugins are compatible with @ref[compile-time checks](other-features.md#compile-time-checks) as long as they're defined in a separate module.

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

@@@ warning { title='TODO' }
Sorry, this page is not ready yet

Relevant ticket: https://github.com/7mind/izumi/issues/51
@@@

As of now, an experimental plugin-checking API is available in `distage-framework` module.

To use it add `distage-framework` library:

@@@vars

```scala
libraryDependencies += "io.7mind.izumi" %% "distage-framework" % "$izumi.version$"
```

@@@

Only plugins defined in a different module can be checked at compile-time, `test` scope counts as a different module.

##### Example:

In main scope:

```scala
// src/main/scala/com/example/AppPlugin.scala
package com.example
import distage._
import distage.plugins._
import distage.config._
import izumi.distage.app.ModuleRequirements

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
import izumi.distage.app.StaticPluginChecker

final class AppPluginTest extends WordSpec {
  
  "App plugin will work (if OtherService will be provided later)" in {
    StaticPluginChecker.checkWithConfig[AppPlugin, AppRequirements](disableTags = "", configFileRegex = "*.application.conf")   
  }

}
```

`checkWithConfig` will run at compile-time, every time that AppPluginTest is recompiled.

You can participate in this ticket at https://github.com/7mind/izumi/issues/51

### Circular Dependencies Support

`distage` automatically resolves arbitrary circular dependencies, including self-references:

```scala mdoc:reset-object
import distage.{GCMode, ModuleDef, Injector}

class A(val b: B)
class B(val a: A) 
class C(val c: C)

val locator = Injector().produceUnsafe(new ModuleDef {
  make[A]
  make[B]
  make[C]
}, GCMode.NoGC)

locator.get[A] eq locator.get[B].a
locator.get[B] eq locator.get[A].b
locator.get[C] eq locator.get[C].c
```

#### Automatic Resolution with generated proxies

The above strategy depends on `distage-proxy-cglib` module which is brought in by default with `distage-core`.

It's enabled by default. If you want to disable it, use `noProxies` bootstrap environment:

```scala mdoc
distage.Injector.NoProxies()
```

#### Manual Resolution with by-name parameters

Most cycles can also be resolved manually when identified, using `by-name` parameters.

Circular dependencies in the following example are all resolved via Scala's native `by-name`'s, without any proxy generation:

```scala mdoc:reset
import distage.{GCMode, ModuleDef, Injector}

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

val locator = Injector.NoProxies().produceUnsafe(module, GCMode.NoGC)

assert(locator.get[A].b eq locator.get[B])
assert(locator.get[B].a eq locator.get[A])
assert(locator.get[C].c eq locator.get[C])
```

The proxy generation via `cglib` is currently enabled by default, because in scenarios with extreme late-binding cycles
can emerge unexpectedly, out of control of the origin module.

NB: Currently a limitation applies to by-names - ALL dependencies of a class engaged in a by-name circular dependency must
be by-name, otherwise distage will revert to generating proxies.

### Auto-Sets

AutoSet @scaladoc[Planner](izumi.distage.model.Planner) Hooks can traverse the plan and collect all future objects that match a predicate.

Using Auto-Sets you can e.g. collect all `AutoCloseable` classes and `.close()` them after the application has finished work.

NOTE: please use @ref[Resource bindings](basics.md#resource-bindings-lifecycle) for real lifecycle, this is just an example.

```scala mdoc:reset
import distage.{BootstrapModuleDef, ModuleDef, Injector, GCMode}
import izumi.distage.model.planning.PlanningHook
import izumi.distage.planning.AutoSetHook

class PrintResource(name: String) {
  def start(): Unit = println(s"$name started")
  def stop(): Unit = println(s"$name stopped")
}

class A extends PrintResource("A")
class B(val a: A) extends PrintResource("B")
class C(val b: B) extends PrintResource("C")

val bootstrapModule = new BootstrapModuleDef {
  many[PlanningHook].add(new AutoSetHook[PrintResource, PrintResource](identity))
}

val appModule = new ModuleDef {
  make[C]
  make[B]
  make[A]
}

val resources = Injector(bootstrapModule)
  .produceUnsafe(appModule, GCMode.NoGC)
  .get[Set[PrintResource]]

resources.foreach(_.start())
resources.toSeq.reverse.foreach(_.stop())
```

Calling `.foreach` on an auto-set is safe; the actions will be executed in order of dependencies.
Auto-Sets preserve ordering, they use `ListSet` under the hood, unlike user-defined @ref[Sets](basics.md#set-bindings).
e.g. If `C` depends on `B` depends on `A`, autoset order is: `A, B, C`, to start call: `A, B, C`, to close call: `C, B, A`.
When you use auto-sets for finalization, you **must** `.reverse` the autoset.

Note: Auto-Sets are NOT subject to @ref[Garbage Collection](other-features.md#garbage-collection), they are assembled *after* garbage collection is done,
as such they can't contain garbage by construction. Because of that they also cannot be used as GC Roots.

See also: same concept in [MacWire](https://github.com/softwaremill/macwire#multi-wiring-wireset)

### Weak Sets

@ref[Set bindings](basics.md#set-bindings) can contain *weak* references. References designated as weak will
be retained by @ref[Garbage Collector](other-features.md#garbage-collection) _only_ if there are other references to them except the
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

### Depending on Locator

Objects can depend on the Locator (container of the final object graph):

```scala
import distage._

class A(all: LocatorRef) {
  def c = all.get.get[C]
}
class B
class C

val module = new ModuleDef {
  make[A]
  make[B]
  make[C]
}

val locator = Injector().produce(module)

assert(locator.get[A].c eq locator.get[C]) 
```

Locator contains metadata about the plan and the bindings from which it was ultimately created:

```scala
// Plan that created this locator
val plan: OrderedPlan = locator.plan

// Bindings from which the Plan was built
val moduleDef: ModuleBase = plan.definition
```
