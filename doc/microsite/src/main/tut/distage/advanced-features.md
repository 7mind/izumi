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

class A(b: B) {
  println("A!")
}
class B() {
  println("B!")
}
class C() {
  println("C!")
}

val module = new ModuleDef {
  make[A]
  make[B]
  make[C]
}

// declare `A` as a GC root

val gc = GCMode(root = DIKey.get[A])

// create an object graph from description in `module`
// with `A` as a GC root

val objects = Injector().produce(module, gc).unsafeGet()

// A and B are in the object graph

objects.find[A]

objects.find[B]

// C is missing

objects.find[C]
```

Class `C` was removed because neither `B` nor `A` depended on it. It's not present in the `Locator` and the `"C!"` message has never been printed.
If class `B` had a `C` parameter, like `class B(c: C)`; `C` would have been retained, because `A` - the GC root, would depend on `B`, and `B` would depend on `C`.

```scala mdoc:reset-object:invisible:to-string
import distage._

class A(b: B) {
  println("A!")
}
class C() {
  println("C!")
}

val module = new ModuleDef {
  make[A]
  make[B]
  make[C]
}
```

```scala mdoc:to-string
class B(c: C) {
  println("B!")
}

val objects = Injector().produce(module, GCMode(DIKey.get[A])).unsafeGet()

objects.find[C]
```

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

Proxies are not supported on Scala.js.

#### Manual Resolution with by-name parameters

Most cycles can be resolved without proxies, using By-Name parameters:

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

The proxy generation via `cglib` is enabled by default, because in scenarios with extreme late-binding cycles can emerge unexpectedly,
out of control of the origin module.

Note: Currently a limitation applies to by-names - ALL dependencies of a class engaged in a by-name circular dependency must
be by-name, otherwise `distage` will revert to generating proxies.

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
  many[PlanningHook]
    .add(new AutoSetHook[PrintResource, PrintResource](identity))
}

val appModule = new ModuleDef {
  make[A]
  make[B]
  make[C]
}

val resources = Injector(bootstrapModule)
  .produce(appModule, GCMode.NoGC)
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
be retained *only* if there are *other* dependencies on the referred bindings, *NOT* if there's a dependency only on the entire Set.

Example:

```scala mdoc:reset-object:to-string
import distage._

sealed trait Elem

final case class Strong() extends Elem {
  println("Strong constructed")
}

final case class Weak() extends Elem {
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

// Strong is around

objects.find[Strong]

// Weak is not

objects.find[Strong]

// There's only Strong in the Set

objects.get[Set[Elem]]
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

// Weak is around

objects.find[Weak]

// both Strong and Weak are in the Set

objects.get[Set[Elem]]
```

### Inner Classes and Path-Dependent Types

Path-dependent types with a value prefix will be instantiated normally:

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

Since version `0.10`, support for path-dependent types with a non-value (type) prefix hasn't reimplemented after a rewrite of the internals, see issue: https://github.com/7mind/izumi/issues/764

However, there's a gotcha with value prefixes, when seen by distage they're based on the **literal variable name** of the prefix,
not the full type information available to the compiler, therefore the following usage will fail:

```scala mdoc:to-string
def pathModule(p: Path) = new ModuleDef {
  make[p.A]
}

val path1 = new Path
val path2 = new Path
```

```scala mdoc:to-string:crash
Injector()
  .produceRun(pathModule(path1) ++ pathModule(path2)) {
    (p1a: path1.A, p2a: path2.A) =>
      println((p1a, p2a))
  }
```

This will fail because while `path1.A` and `p.A` inside `new ModuleDef` are the same type, the varialbes
`path1` & `p` are spelled differently and this causes a mismatch.

There's one way to workaround this - turn the type member `A` into a type parameter using the [Aux Pattern](http://gigiigig.github.io/posts/2015/09/13/aux-pattern.html),
and then for that type parameter in turn, summon the type information using `Tag` implicit (as described in @ref[Tagless Final Style chapter](basics.md#tagless-final-style))
and summon the constructor using the `ClassConstructor` implicit, example:

```scala mdoc:to-string:reset:invisible
class Path {
  class A
}
```

```scala mdoc:to-string
import distage.{ClassConstructor, GCMode, ModuleDef, Injector, Tag}

object Path {
  type Aux[A0] = Path { type A = A0 }
}

def pathModule[A: Tag: ClassConstructor](p: Path.Aux[A]) = new ModuleDef {
  make[A]
}

val path1 = new Path
val path2 = new Path

Injector()
  .produceRun(pathModule(path1) ++ pathModule(path2)) {
    (p1a: path1.A, p2a: path2.A) =>
      println((p1a, p2a))
  }
```

Now the example works, because the `A` inside `pathModule` is `path1.A` & `path2.A` respectively, the same as it is later
in `produceRun`

### Depending on Locator

Objects can depend on the outer object graph that contains them (@scaladoc[Locator](izumi.distage.model.Locator)), by including a @scaladoc[LocatorRef](izumi.distage.model.recursive.LocatorRef) parameter:

```scala mdoc:reset:to-string
import distage.{ModuleDef, LocatorRef, Injector, GCMode}

class A(
  objects: LocatorRef
) {
  def c = objects.get.get[C]
}
class B
class C

val module = new ModuleDef {
  make[A]
  make[B]
  make[C]
}

val objects = Injector().produce(module, GCMode.NoGC).unsafeGet()

// A took C from the object graph

objects.get[A].c

// this C is the same C as in this `objects` value

val thisC = objects.get[C]
val thatC = objects.get[A].c

assert(thisC == thatC)
```

Locator contains metadata about the plan and the bindings from which it was ultimately created:

```scala mdoc:to-string
import distage.{OrderedPlan, ModuleBase}

// Plan that created this locator (after GC)

val plan: OrderedPlan = objects.plan

// Bindings from which the Plan was built (after GC)

val bindings: ModuleBase = plan.definition
```

The plan and bindings in Locator are saved in the state they were AFTER @ref[Garbage Collection](#garbage-collection) has been performed.
Objects can request the original input via a `PlannerInput` parameter:

```scala mdoc:reset:to-string
import distage.{DIKey, GCMode, ModuleDef, PlannerInput, Injector}

class InjectionInfo(val plannerInput: PlannerInput)

val module = new ModuleDef {
  make[InjectionInfo]
}

val input = PlannerInput(module, GCMode(root = DIKey.get[InjectionInfo]))

val injectionInfo = Injector().produce(input).unsafeGet().get[InjectionInfo]

// the PlannerInput in `InjectionInfo` is the same as `input`

assert(injectionInfo.plannerInput == input)
```

@scaladoc[Bootloader](izumi.distage.model.recursive.Bootloader) is another summonable parameter that contains the above information in aggregate
and lets you create another object graph from the same inputs as the current or with alterations.
