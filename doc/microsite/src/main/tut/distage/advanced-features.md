# Advanced Features

@@toc { depth=2 }

### Dependency Pruning

`distage` performs pruning of all unused bindings by default.
When you configure a set of "root" keys -
either explicitly by passing @scaladoc[Roots](izumi.distage.model.plan.Roots)
or implicitly by using @scaladoc[Injector#produceRun](izumi.distage.model.Injector#produceRun) or @scaladoc[Injector#produceGet](izumi.distage.model.Injector#produceGet) methods, `distage` will remove all bindings that aren't required to create the supplied roots – these bindings will be thrown out and not even considered, much less executed.

Pruning serves two important purposes:

* It enables faster @ref[tests](distage-testkit.md) by omitting unused instantiations and allocations of potentially heavy resources,
* It enables multiple independent applications, aka "@ref[Roles](distage-framework.md#roles)" to be hosted within a single binary.

Example:

```scala mdoc:reset:to-string
import distage.{Roots, ModuleDef, Injector}

class A(b: B) {
  println("A!")
}
class B() {
  println("B!")
}
class C() {
  println("C!")
}

def module = new ModuleDef {
  make[A]
  make[B]
  make[C]
}

// create an object graph from description in `module`
// with `A` as the GC root

val objects = Injector().produce(module, Roots.target[A]).unsafeGet()

// A and B are in the object graph

objects.find[A]

objects.find[B]

// C is missing

objects.find[C]
```

Class `C` was removed because neither `B` nor `A` depended on it. It's neither present in the `Locator` nor the `"C!"` message from it's constructor was ever printed.

If you add `c: C` parameter to `class B` , like `class B(c: C)` - then `C` _will_ be instantiated,
because `A` - the "root", will now depend on `B`, and `B` will depend on `C`.

```scala mdoc:reset-object:invisible:to-string
import distage._

class A(b: B) {
  println("A!")
}
class C() {
  println("C!")
}

def module = new ModuleDef {
  make[A]
  make[B]
  make[C]
}
```

```scala mdoc:to-string
class B(c: C) {
  println("B!")
}

val objects = Injector().produce(module, Roots.target[A]).unsafeGet()

objects.find[C]
```

### Circular Dependencies Support

`distage` automatically resolves arbitrary circular dependencies, including self-references:

```scala mdoc:reset-object:to-string
import distage.{DIKey, Roots, ModuleDef, Injector}

class A(val b: B)
class B(val a: A)
class C(val c: C)

def module = new ModuleDef {
  make[A]
  make[B]
  make[C]
}

val objects = Injector().produce(module, Roots(DIKey[A], DIKey[C])).unsafeGet()

objects.get[A] eq objects.get[B].a

objects.get[B] eq objects.get[A].b

objects.get[C] eq objects.get[C].c
```

#### Automatic Resolution with generated proxies

The above strategy depends on `distage-core-proxy-bytebuddy` module and is enabled by default.

If you want to disable it, use `NoProxies` bootstrap configuration:

```scala mdoc:to-string
Injector.NoProxies()
```

Proxies are not supported on Scala.js.

#### Manual Resolution with by-name parameters

Most cycles can be resolved without proxies, using By-Name parameters:

```scala mdoc:reset:to-string
import distage.{DIKey, Roots, ModuleDef, Injector}

class A(b0: => B) {
  def b: B = b0
}

class B(a0: => A) {
  def a: A = a0
}

class C(self: => C) {
  def c: C = self
}

def module = new ModuleDef {
  make[A]
  make[B]
  make[C]
}

// disable proxies and execute the module

val locator = Injector.NoProxies()
  .produce(module, Roots(DIKey[A], DIKey[C]))
  .unsafeGet()

assert(locator.get[A].b eq locator.get[B])

assert(locator.get[B].a eq locator.get[A])

assert(locator.get[C].c eq locator.get[C])
```

The proxy generation via `bytebuddy` is enabled by default, because in scenarios with extreme late-binding cycles can emerge unexpectedly,
out of control of the origin module.

Note: Currently a limitation applies to by-names - ALL dependencies of a class engaged in a by-name circular dependency must
be by-name, otherwise `distage` will revert to generating proxies.

### Weak Sets

@ref[Set bindings](basics.md#set-bindings) can contain *weak* references. References designated as weak will
be retained *only* if there are *other* dependencies on the referred bindings, *NOT* if there's a dependency only on the entire Set.

Example:

```scala mdoc:reset-object:to-string
import distage.{Roots, ModuleDef, Injector}

sealed trait Elem

final case class Strong() extends Elem {
  println("Strong constructed")
}

final case class Weak() extends Elem {
  println("Weak constructed")
}

def module = new ModuleDef {
  make[Strong]
  make[Weak]

  many[Elem]
    .ref[Strong]
    .weak[Weak]
}

// Designate Set[Elem] as the garbage collection root,
// everything that Set[Elem] does not strongly depend on will be garbage collected
// and will not be constructed.

val roots = Roots.target[Set[Elem]]

val objects = Injector().produce(module, roots).unsafeGet()

// Strong is around

objects.find[Strong]

// Weak is not

objects.find[Weak]

// There's only Strong in the Set

objects.get[Set[Elem]]
```

The `Weak` class was not required by any dependency of `Set[Elem]`, so it was pruned.
The `Strong` class remained, because the reference to it was **strong**, so it was counted as a dependency of `Set[Elem]`.

If we change `Strong` to depend on the `Weak`, then `Weak` will be retained:

```scala mdoc:reset-object:invisible:to-string
import distage.{Roots, ModuleDef, Injector}

sealed trait Elem

final class Weak extends Elem {
  println("Weak constructed")
}

def module = new ModuleDef {
  make[Strong]
  make[Weak]

  many[Elem]
    .ref[Strong]
    .weak[Weak]
}

val roots = Roots.target[Set[Elem]]
```

```scala mdoc:to-string
final class Strong(weak: Weak) extends Elem {
  println("Strong constructed")
}

val objects = Injector().produce(module, roots).unsafeGet()

// Weak is around

objects.find[Weak]

// both Strong and Weak are in the Set

objects.get[Set[Elem]]
```

### Auto-Sets

Auto-Set @scaladoc[PlanningHooks](izumi.distage.model.planning.PlanningHook) can traverse the plan and collect all bindings with *implementation types* that are `_ <: T` into a `Set[T]` set binding available for summoning. Filters, in addition to filtering by subtype, may be passed to @scaladoc[AutoSetHook](izumi.distage.planning.AutoSetHook) or to @scaladoc[AutoSetModule.register](izumi.distage.planning.AutoSetModule#register).

```scala mdoc:reset:to-string
import distage.{AutoSetModule, BootstrapModule, ModuleDef, Injector, Identity}

class PrintService(
  name: String
) {
  def start(): Unit = println(s"$name started")
}

trait A
class AImpl extends PrintService("A") with A
class B(val a: A) extends PrintService("B")
class C(val b: B) extends PrintService("C")

def bootstrapModule: BootstrapModule = new AutoSetModule {
  register[PrintService](weak = false)
}

def appModule = new ModuleDef {
  make[A].from[AImpl]
  make[B]
  make[C]
}

val services: Set[PrintService] = Injector[Identity](bootstrapOverrides = Seq(bootstrapModule))
  .produceGet[Set[PrintService]](appModule)
  .unsafeGet()

require(services.size == 3)

services.foreach(_.start())
```

Calling `.foreach` on an auto-set is safe: the actions will be executed in dependency order, Auto-Sets preserve ordering, unlike user-defined @ref[Sets](basics.md#set-bindings) e.g. When `C` depends on `B` depends on `A`, autoset order is: `A, B, C`, to start call: `A, B, C`, to close call: `C, B, A`.  When using an auto-set for finalization, you must `.reverse` the autoset.

The `weak` parameter controls whether Auto-Set should depend weakly or strongly on the bindings it matches. If `weak = true`, auto-set will only contain bindings that are retained by another GC root.

Example:

```scala mdoc:to-string
def weakAutoSetModule: BootstrapModule = new AutoSetModule {
  register[PrintService](weak = true)
}

val servicesDepsOfB: Set[PrintService] = Injector[Identity](bootstrapOverrides = Seq(weakAutoSetModule))
  .produceRun(appModule) {
    (_: B, set: Set[PrintService]) =>
      set
  }

require(servicesDepsOfB.size == 2)

servicesDepsOfB.foreach(_.start())

val servicesDepsOfNothing: Set[PrintService] = Injector[Identity](bootstrapOverrides = Seq(weakAutoSetModule))
  .produceGet[Set[PrintService]](appModule)
  .unsafeGet()

require(servicesDepsOfNothing.isEmpty)
```

Further reading:

- MacWire calls the same concept ["Multi Wiring"](https://github.com/softwaremill/macwire?tab=readme-ov-file#multi-wiring-wireset-and-wirelist)

### Depending on Locator

Objects can depend on the outer object graph that contains them (@scaladoc[Locator](izumi.distage.model.Locator)), by including a @scaladoc[LocatorRef](izumi.distage.model.recursive.LocatorRef) parameter:

```scala mdoc:reset:to-string
import distage.{DIKey, ModuleDef, LocatorRef, Injector, Roots}

class A(
  objects: LocatorRef
) {
  def c = objects.get.get[C]
}
class B
class C

def module = new ModuleDef {
  make[A]
  make[B]
  make[C]
}

val objects = Injector().produce(module, Roots(DIKey[A], DIKey[B], DIKey[C])).unsafeGet()

// A took C from the object graph

objects.get[A].c

// this C is the same C as in this `objects` value

val thisC = objects.get[C]

val thatC = objects.get[A].c

assert(thisC == thatC)
```

Locator contains metadata about the plan, and the bindings from which it was ultimately created:

```scala mdoc:to-string
import distage.{Plan, ModuleBase}

// Plan that created this locator

val plan: Plan = objects.plan

// Bindings from which the Plan was built (after GC)

val bindings: ModuleBase = plan.definition
```

Directly depending on `Locator` is a low-level API. If you only need to wire a subgraph within a larger object graph,
you may be able to do this using a high-level @ref[Subcontexts](basics.md#subcontexts) API.

#### Injector inheritance

You may run a new planning cycle, inheriting the instances from an existing `Locator` into your new object subgraph:

```scala mdoc:to-string
val childInjector = Injector.inherit(objects)

class Printer(a: A, b: B, c: C) {
  def printEm(): Unit =
    println(s"I've got A=$a, B=$b, C=$c, all here!")
}

childInjector.produceRun(new ModuleDef { make[Printer] }) {
  (_: Printer).printEm()
}
```

It's safe, performance-wise, to run `Injector` to create nested graphs – `Injector` is extremely fast.

#### Bootloader

The plan and bindings in Locator are saved in the state they were AFTER @ref[Garbage Collection](#dependency-pruning) has been performed.
Objects can request the original input via a `PlannerInput` parameter:

```scala mdoc:reset:to-string
import distage.{Roots, ModuleDef, PlannerInput, Injector, Activation}

class InjectionInfo(val plannerInput: PlannerInput)

def module = new ModuleDef {
  make[InjectionInfo]
}

val input = PlannerInput(module, Roots.target[InjectionInfo], Activation.empty)

val injectionInfo = Injector().produce(input).unsafeGet().get[InjectionInfo]

// the PlannerInput in `InjectionInfo` is the same as `input`

assert(injectionInfo.plannerInput == input)
```

@scaladoc[Bootloader](izumi.distage.model.recursive.Bootloader) is another summonable parameter that contains the above information in aggregate and lets you create another object graph from the same inputs as the current or with alterations.


### Inner Classes and Path-Dependent Types

Path-dependent types with a value prefix will be instantiated normally:

```scala mdoc:reset:to-string
import distage.{Roots, ModuleDef, Injector}

class Path {
  class A
}
val path = new Path

def module = new ModuleDef {
  make[path.A]
}

Injector()
  .produce(module, Roots.Everything)
  .use(_.get[path.A])
```

Since version `0.10`, support for types with a non-value prefix (type projections) [has been dropped](https://github.com/7mind/izumi/issues/764).

However, there's a gotcha with value prefixes, when seen by distage they're based on the **literal variable name** of the prefix,
not the full type information available to the compiler, therefore the following usage, a simple rename, will fail:

```scala mdoc:to-string
def pathModule(p: Path) = new ModuleDef {
  make[p.A]
}

val path1 = new Path
val path2 = new Path
```

```scala mdoc:invisible:to-string
import scala.util.Try
```

```scala mdoc:to-string
Try {
  Injector().produceRun(pathModule(path1) ++ pathModule(path2)) {
    (p1a: path1.A, p2a: path2.A) =>
      println((p1a, p2a))
  }
}.isFailure
```

This will fail because while `path1.A` and `p.A` inside `new ModuleDef` are the same type as far as *Scala* is concerned, the variables
`path1` & `p` are spelled differently and this causes a mismatch in `distage`.

There's one way to workaround this - turn the type member `A` into a type parameter using the [Aux Pattern](http://gigiigig.github.io/posts/2015/09/13/aux-pattern.html), and then for that type parameter in turn, summon the type information using `Tag` implicit (as described in @ref[Tagless Final Style chapter](basics.md#tagless-final-style)) and summon the constructor using the `ClassConstructor` implicit, example:

```scala mdoc:to-string:reset:invisible
class Path {
  class Inner

  type A = Inner
}
```

```scala mdoc:to-string
import distage.{ClassConstructor, ModuleDef, Injector, Tag}

object Path {
  type Aux[A0] = Path { type A = A0 }
}

def pathModule[A: Tag: ClassConstructor](p: Path.Aux[A]) = new ModuleDef {
  make[A]
}

val path1 = new Path
val path2 = new Path

Injector().produceRun(pathModule(path1) ++ pathModule(path2)) {
  (p1a: path1.A, p2a: path2.A) =>
    println((p1a, p2a))
}
```

Now the example works, because the `A` type inside `pathModule(path1)` is `path1.A` and for `pathModule(path2)` it's `path2.A`, which matches their subsequent spelling in `(p1a: path1.A, p2a: path2.A) =>` in `produceRun`

### Locator-private bindings

You may use locator-private bindings, which will not be exposed in`Locator` produced by the interpreter.

If the instance is locator-private, it cannot be seen by inherited locators.
There are 3 locator privacy (@scaladoc[LocatorPrivacy](izumi.distage.model.definition.LocatorPrivacy)) modes:
- PublicByDefault
- PrivateByDefault
- PublicRoots

#### Public By Default
All the bindings are public, unless explicitly marked as `confined`
This is default behaviour.

```scala mdoc:to-string:reset
import distage.{Injector, LocatorPrivacy, ModuleDef, PlannerInput}

class A
class B
class C

def module = new ModuleDef {
  make[A]
  make[B].confined
}
val input = PlannerInput.everything(module).withLocatorPrivacy(LocatorPrivacy.PublicByDefault)
val mainLocator = Injector().produce(Injector().planUnsafe(input)).unsafeGet()

val inheritedInjector = Injector.inherit(mainLocator)

def module2 = new ModuleDef {
  make[C]
}
val input2 = PlannerInput.everything(module2)
val inheritedLocator = inheritedInjector.produce(inheritedInjector.planUnsafe(input2)).unsafeGet()

assert(inheritedLocator.find[A].nonEmpty)
assert(inheritedLocator.find[C].nonEmpty)
// inherited and new binding are available

assert(inheritedLocator.find[B].isEmpty)
// but confined binding is not
```

#### Private By Default
All the bindings are private, unless explicitly marked as `exposed`

```scala mdoc:to-string:reset
import distage.{Injector, LocatorPrivacy, ModuleDef, PlannerInput}

class A
class B
class C

def module = new ModuleDef {
  make[A]
  make[B].exposed
}
val input = PlannerInput.everything(module).withLocatorPrivacy(LocatorPrivacy.PrivateByDefault)

val mainLocator = Injector().produce(Injector().planUnsafe(input)).unsafeGet()

val inheritedInjector = Injector.inherit(mainLocator)

def module2 = new ModuleDef {
  make[C]
}
val input2 = PlannerInput.everything(module2)
val inheritedLocator = inheritedInjector.produce(inheritedInjector.planUnsafe(input2)).unsafeGet()

assert(inheritedLocator.find[A].isEmpty)
// binding is not available

assert(inheritedLocator.find[B].nonEmpty)
assert(inheritedLocator.find[C].nonEmpty)
// but new and 'exposed' are
```

#### Public Roots
Only planning GC roots are public
This is default behaviour for bootstrap injectors.
All bootstrap bindings, which need to be available in inherited locators, must be explicitly marked as `exposed`

```scala mdoc:to-string:reset
import distage.{Injector, LocatorPrivacy, ModuleDef, PlannerInput}

class A
class B
class C

def module = new ModuleDef {
  make[A]
  make[B]
}
val input = PlannerInput.target[A](module).withLocatorPrivacy(LocatorPrivacy.PublicRoots)

val mainLocator = Injector().produce(Injector().planUnsafe(input)).unsafeGet()

val inheritedInjector = Injector.inherit(mainLocator)

def module2 = new ModuleDef {
  make[C]
}
val input2 = PlannerInput.everything(module2)
val inheritedLocator = inheritedInjector.produce(inheritedInjector.planUnsafe(input2)).unsafeGet()

assert(inheritedLocator.find[A].nonEmpty)
// targeted binding is available

assert(inheritedLocator.find[B].isEmpty)
// but unrelated one is not
```
