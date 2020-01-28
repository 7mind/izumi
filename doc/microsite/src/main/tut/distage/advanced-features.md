Advanced Features
==============

@@toc { depth=2 }

### Garbage Collection

A garbage collector is included in `distage` by default. Given a set of `GC root` keys, GC will remove all bindings that
are neither direct nor transitive dependencies of the supplied roots – these bindings will be thrown out and never instantiated.

GC serves two important purposes:

* It enables faster @ref[tests](distage-testkit.md) by omitting unrequired instantiations and initialization of potentially heavy resources,
* It enables multiple independent applications, aka "@ref[Roles](distage-framework.md#roles)" to be hosted within a single `.jar` file.

To use garbage collector, pass GC roots as an argument to `Injector.produce*` methods:

```scala mdoc:reset:to-string
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

// declare `A` as a GC root

val roots = GCMode.GCRoots(Set[DIKey](DIKey.get[A]))

// create an object graph from description in `module`
// with `A` as a GC root

val objects = Injector().produce(module, roots).unsafeGet()

// A and B are in the object graph

objects.find[A]
objects.find[B]

// C is missing

objects.find[C]
```

Class `C` was removed because neither `B` nor `A` depended on it. It's not present in the `Locator` and the `"C!"` message was never printed.
But, if class `B` were to depend on `C` as in `case class B(c: C)`, it would've been retained, because `A` - the GC root, would depend on `B` which in turns depends on `C`.

### Circular Dependencies Support

`distage` automatically resolves arbitrary circular dependencies, including self-references:

```scala mdoc:reset-object:to-string
import distage.{GCMode, ModuleDef, Injector}

class A(val b: B)
class B(val a: A) 
class C(val c: C)

val objects = Injector().produce(new ModuleDef {
  make[A]
  make[B]
  make[C]
}, GCMode.NoGC).unsafeGet()

objects.get[A] eq objects.get[B].a
objects.get[B] eq objects.get[A].b
objects.get[C] eq objects.get[C].c
```

#### Automatic Resolution with generated proxies

The above strategy depends on `distage-core-proxy-cglib` module which is a default dependency of `distage-core`.

If you want to disable it, use `NoProxies` bootstrap configuration:

```scala mdoc:to-string
Injector.NoProxies()
```

#### Manual Resolution with by-name parameters

Most cycles can be resolved manually when identified using By Name parameters.

Circular dependencies in the following example are all resolved via Scala's native By Name, no proxies are generated:

```scala mdoc:reset:to-string
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

// disable proxies and execute the module

val locator = Injector.NoProxies()
  .produce(module, GCMode.NoGC)
  .unsafeGet()

locator.get[A].b eq locator.get[B]
locator.get[B].a eq locator.get[A]
locator.get[C].c eq locator.get[C]
```

The proxy generation via `cglib` is currently enabled by default, because in scenarios with extreme late-binding cycles
can emerge unexpectedly, out of control of the origin module.

NB: Currently a limitation applies to by-names - ALL dependencies of a class engaged in a by-name circular dependency must
be by-name, otherwise distage will revert to generating proxies.

### Auto-Sets

AutoSet @scaladoc[Planner](izumi.distage.model.Planner) Hooks can traverse the plan and collect all future objects that match a predicate.

Using Auto-Sets you can e.g. collect all `AutoCloseable` classes and `.close()` them after the application has finished work.

NOTE: please use @ref[Resource bindings](basics.md#resource-bindings-lifecycle) for real lifecycle, this is just an example.

```scala mdoc:reset:to-string
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
  .produceGet(appModule, GCMode.NoGC)
  .use(_.get[Set[PrintResource]])

resources.foreach(_.start())
resources.toSeq.reverse.foreach(_.stop())
```

Calling `.foreach` on an auto-set is safe; the actions will be executed in order of dependencies.
Auto-Sets preserve ordering, they use `ListSet` under the hood, unlike user-defined @ref[Sets](basics.md#set-bindings).
e.g. If `C` depends on `B` depends on `A`, autoset order is: `A, B, C`, to start call: `A, B, C`, to close call: `C, B, A`.
When you use auto-sets for finalization, you **must** `.reverse` the autoset.

Note: Auto-Sets are NOT subject to @ref[Garbage Collection](advanced-features.md#garbage-collection), they are assembled *after* garbage collection is done,
as such they can't contain garbage by construction. Because of that they also cannot be used as GC Roots.

See also: same concept in [MacWire](https://github.com/softwaremill/macwire#multi-wiring-wireset)

### Weak Sets

@ref[Set bindings](basics.md#set-bindings) can contain *weak* references. References designated as weak will
be retained *only* if there are other dependencies on them **except** for the set addition.

Example:

```scala mdoc:reset-object:to-string
import distage._

sealed trait Elem

final class Strong extends Elem {
  println("Strong constructed")
}

final class Weak extends Elem {
  println("Weak constructed")
}

val module = new ModuleDef {
  make[Strong]
  make[Weak]
  
  many[Elem]
    .ref[Strong]
    .weak[Weak]
}

// Designate Set[Elem] as the garbage collection root,
// everything that Set[Elem] does not strongly depend on will be garbage collected
// and will not be constructed. 

val roots = Set[DIKey](DIKey.get[Set[Elem]])

val objects = Injector().produce(PlannerInput(module, roots)).unsafeGet()

objects.get[Set[Elem]].size == 1
```

The `Weak` class was not required by any dependency of `Set[Elem]`, so it was pruned.
The `Strong` class remained, because the reference to it was **strong**, so it was counted as a dependency of `Set[Elem]`.

If we change `Strong` to depend on the `Weak`, then `Weak` will be retained:

```scala mdoc:reset-object:invisible:to-string
import distage._

sealed trait Elem

final class Weak extends Elem {
  println("Weak constructed")
}

val module = new ModuleDef {
  make[Strong]
  make[Weak]
  
  many[Elem]
    .ref[Strong]
    .weak[Weak]
}

val roots = Set[DIKey](DIKey.get[Set[Elem]])
```

```scala mdoc:to-string
final class Strong(weak: Weak) extends Elem {
  println("Strong constructed")
}

val objects = Injector().produce(PlannerInput(module, roots)).unsafeGet()

objects.get[Set[Elem]].size == 2
```

### Inner Classes and Path-Dependent Types

Path-dependent types with a value prefix will instantiate normally:

```scala mdoc:reset:to-string
import distage.{GCMode, ModuleDef, Injector}

class Path {
  class A
}
val path = new Path

val module = new ModuleDef {
  make[path.A]
}

Injector()
  .produce(module, GCMode.NoGC)
  .use(_.get[path.A])
```

Since version `0.10`, path-dependent types with a type (non-value) prefix are no longer supported, see issue: https://github.com/7mind/izumi/issues/764

### Depending on Locator

Objects can depend on the Locator (container of the final object graph):

```scala mdoc:reset:to-string
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

val objects = Injector().produce(module, GCMode.NoGC).unsafeGet()

assert(objects.get[A].c eq objects.get[C]) 
```

Locator contains metadata about the plan and the bindings from which it was ultimately created:

```scala mdoc:to-string
// Plan that created this locator

val plan: OrderedPlan = locator.plan

// Bindings from which the Plan was built

val moduleDef: ModuleBase = plan.definition
```
