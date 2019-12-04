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

```scala mdoc:reset-object
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

val locator = Injector().produceUnsafe(PlannerInput(module, roots))

locator.get[Set[SetElem]].size == 1
```

The `Weak` class was not required in any dependency of `Set[SetElem]`, so it was pruned.
The `Strong` class remained, because the reference to it was **strong**, therefore it was counted as a dependency of `Set[SetElem]`

If we change `Strong` to depend on `Weak`, then `Weak` will be retained:

```scala mdoc:reset-object:invisible
import distage._

sealed trait SetElem

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

val locator = Injector().produceUnsafe(PlannerInput(module, roots))
```

```scala mdoc
final class Strong(weak: Weak) extends SetElem {
  println("Strong constructed")
}

locator.get[Set[SetElem]].size == 2
```

### Inner Classes and Path-Dependent Types

Path-dependent types with a known value prefix will instantiate normally:

```scala mdoc:reset
import distage.{GCMode, ModuleDef, Injector}

class Path {
  class A
}
val path = new Path

val module = new ModuleDef {
  make[path.A]
}

Injector()
  .produceUnsafe(module, GCMode.NoGC)
  .get[path.A]
```

Since version `0.10` types with a type (non-value) prefix are no longer supported, see issue: https://github.com/7mind/izumi/issues/764

### Depending on Locator

Objects can depend on the Locator (container of the final object graph):

```scala mdoc:reset
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

val locator = Injector().produceUnsafe(module, GCMode.NoGC)

assert(locator.get[A].c eq locator.get[C]) 
```

Locator contains metadata about the plan and the bindings from which it was ultimately created:

```scala mdoc
// Plan that created this locator
val plan: OrderedPlan = locator.plan

// Bindings from which the Plan was built
val moduleDef: ModuleBase = plan.definition
```
