Overview
============

@@toc { depth=2 }

### Quick Start

#### Dependencies

Add the `distage-core` library:

@@dependency[sbt,Maven,Gradle] {
  group="io.7mind.izumi"
  artifact="distage-core_2.13"
  version="$izumi.version$"
}


#### Hello World example

Suppose we have an abstract `Greeter` component, and some other components that depend on it:

```scala mdoc:reset:invisible:to-string
var counter = 0
val names = Array("izumi", "kai", "Pavel")
def HACK_OVERRIDE_getStrLn = zio.ZIO {
  val n = names(counter % names.length)
  counter += 1
  println(s"> $n")
  n
}
```

```scala mdoc:override:to-string
import zio.RIO
import zio.console.{Console, getStrLn, putStrLn}

trait Greeter {
  def hello(name: String): RIO[Console, Unit]
}

final class PrintGreeter extends Greeter {
  override def hello(name: String) = 
    putStrLn(s"Hello $name!") 
}

trait Byer {
  def bye(name: String): RIO[Console, Unit]
}

final class PrintByer extends Byer {  
  override def bye(name: String) = 
    putStrLn(s"Bye $name!")
}

final class HelloByeApp(
  greeter: Greeter, 
  byer: Byer,
) {
  def run: RIO[Console, Unit] = {
    for {
      _    <- putStrLn("What's your name?") 
      name <- HACK_OVERRIDE_getStrLn
      _    <- greeter.hello(name)
      _    <- byer.bye(name) 
    } yield ()
  }
}
```

To actually run the `HelloByeApp`, we have to wire implementations of `Greeter` and `Byer` into it.
We will not do it directly. First we'll only declare the component interfaces we have, and the implementations we want for them:

```scala mdoc:to-string
import distage.ModuleDef

def HelloByeModule = new ModuleDef {
  make[Greeter].from[PrintGreeter]
  make[Byer].from[PrintByer]
  make[HelloByeApp] // `.from` is not required for concrete classes 
}
```

`ModuleDef` merely contains a description of the desired object graph, let's transform that high-level description into an
actionable series of steps - an @scaladoc[OrderedPlan](izumi.distage.model.plan.OrderedPlan), a datatype we can
@ref[inspect](debugging.md#pretty-printing-plans), @ref[test](debugging.md#testing-plans) or @ref[verify at compile-time](distage-framework.md#compile-time-checks) – without having to actually create objects or execute effects.

```scala mdoc:to-string
import distage.{Activation, Injector, Roots}

val injector = Injector[RIO[Console, *]]()

val plan = injector.plan(HelloByeModule, Activation.empty, Roots.target[HelloByeApp])
```

The series of steps must be executed to produce the object graph.

`Injector.produce` will interpret the steps into a @ref[`Lifecycle`](basics.md#resource-bindings-lifecycle) value holding the lifecycle of the object graph:

```scala mdoc:to-string
import zio.Runtime.default.unsafeRun

// Interpret into a Lifecycle value

val resource = injector.produce(plan)

// Use the object graph:
// After `.use` exits, all objects will be deallocated,
// and all allocated resources will be freed.

val effect = resource.use {
  objects =>
    objects.get[HelloByeApp].run
}

// Run the resulting program

unsafeRun(effect)
```

`distage` always creates components exactly once, even if multiple other objects depend on them. Coming from other DI frameworks, you may think of it as if there's only a "Singleton" scope. It's impossible to create non-singletons in `distage`.

#### Named instances

If you need multiple singleton instances of the same type, you may create "named" instances and disambiguate between them using @scaladoc[`@distage.Id`](izumi.distage.model.definition.Id) annotation. (`javax.inject.Named` is also supported)

```scala mdoc:silent
import distage.Id

def negateByer(otherByer: Byer): Byer = {
  new Byer {
    def bye(name: String) =   
     otherByer.bye(s"NOT-$name")
  }
}

new ModuleDef {
  make[Byer].named("byer-1").from[PrintByer]
  make[Byer].named("byer-2").from {
    otherByer: Byer @Id("byer-1") =>
      negateByer(otherByer)
  }
}
```


You can use `make[_].annotateParameter` method instead of an annotation, to attach a name component to an existing constructor:

```scala mdoc:silent
new ModuleDef {
  // same binding as above
  make[Byer].named("byer-2")
    .from(negateByer(_))
    .annotateParameter[Byer]("byer-1")
}
```

You can also abstract over annotations using type aliases or string constants:

```scala mdoc:to-string
object Ids {
  final val byer1Id = "byer-1"
  type Byer1 = Byer @Id(byer1Id)
}
```

#### Non-singleton components

To create new non-singleton instances you must use explicit factories. `distage`'s @ref[Auto-Factories](#auto-factories) can generate implementations for your factories, removing the associated boilerplate.

While Auto-Factories may remove the boilerplate of passing the singleton parts of the graph to your new non-singleton components along with dynamic arguments,
if you absolutely must wire a new non-trivial subgraph in a dynamic scope you'll need to run `Injector` again in your scope. 

You may use `Injector.inherit` to obtain access to your outer object graph in your new sub-graph. It's safe to run `Injector` multiple times in nested scopes, as it's extremely fast, not least due to total absence of runtime reflection. See @ref[Injector Inheritance](advanced-features.md#injector-inheritance)

#### Real-world example

Check out [`distage-example`](https://github.com/7mind/distage-example) sample project for a complete example built using `distage`, @ref[bifunctor tagless final](../bio/00_bio.md), `http4s`, `doobie` and `zio` libraries.

It shows how to write an idiomatic `distage`-style from scratch and how to:

- write tests using @ref[`distage-testkit`](distage-testkit.md)
- setup portable test environments using @ref[`distage-framework-docker`](distage-framework-docker.md)
- create @ref[role-based applications](distage-framework.md#roles)
- enable @ref[compile-time checks](distage-framework.md) for fast-feedback on wiring errors

```scala mdoc:invisible
/**
add to distage-example

- how to setup graalvm native image with distage
- how to debug dump graphs and render to graphviz [Actually, we have a GUI component now, can we show em there???]
*/
```

### Activation Axis

You can choose between different implementations of a component using "Activation axis":

```scala mdoc:to-string
import distage.{Axis, Activation, ModuleDef, Injector}

class AllCapsGreeter extends Greeter {
  def hello(name: String) = 
    putStrLn(s"HELLO ${name.toUpperCase}")
}

// declare a configuration axis for our components

object Style extends Axis {
  case object AllCaps extends AxisValueDef
  case object Normal extends AxisValueDef
}

// Declare a module with several implementations of Greeter
// but in different environments

def TwoImplsModule = new ModuleDef {
  make[Greeter].tagged(Style.Normal)
    .from[PrintGreeter]
  
  make[Greeter].tagged(Style.AllCaps)
    .from[AllCapsGreeter]
}

// Combine previous `HelloByeModule` with our new module
// While overriding `make[Greeter]` bindings from the first module 

def CombinedModule = HelloByeModule overriddenBy TwoImplsModule

// Choose component configuration when making an Injector:

unsafeRun {
  Injector()
    .produceGet[HelloByeApp](CombinedModule, Activation(Style -> Style.AllCaps))
    .use(_.run)
}

// Check that result changes with a different configuration:

unsafeRun {
  Injector()
    .produceGet[HelloByeApp](CombinedModule, Activation(Style -> Style.Normal))
    .use(_.run)
}
```

@scaladoc[distage.StandardAxis](izumi.distage.model.definition.StandardAxis$) contains bundled Axes for back-end development: 

- @scaladoc[Repo](izumi.distage.model.definition.StandardAxis$$Repo$) axis, with `Prod`/`Dummy` choices, describes any entities which may store and persist state or "repositories". e.g. databases, message queues, KV storages, file systems, etc. Those may typically have both in-memory `Dummy` implementations and heavyweight `Prod` implementations using external databases.
  
- @scaladoc[Mode](izumi.distage.model.definition.StandardAxis$$Mode$) axis, with `Prod`/`Test` choices, describes a generic choice between production and test implementations of a component.
  
- @scaladoc[World](izumi.distage.model.definition.StandardAxis$$World$) axis, with `Real`/`Mock` choices, describes third-party integrations which are not controlled by the application and provided "as is". e.g. Facebook API, Google API, etc. those may contact a `Real` external integration or a `Mock` one with predefined responses.
  
- @scaladoc[Scene](izumi.distage.model.definition.StandardAxis$$Scene$) axis with `Managed`/`Provided` choices, describes whether external services required by the application should be set-up on the fly by an orchestrator library such as @ref[`distage-framework-docker`](distage-framework-docker.md) (`Scene.Managed`), or whether the application should try to connect to external services as if they already exist in the environment (`Scene.Provided`). 
  We call a set of external services required by the application a `Scene`, etymology being that the running external services required by the application are like a "scene" that the "theatre staff" (the orchestrator) must prepare for the "actor" (the application) to enter.

In `distage-framework`'s @scaladoc[RoleAppMain](izumi.distage.roles.RoleAppMain), you can choose axes using the `-u` command-line parameter:

```
./launcher -u repo:dummy -u env:prod app1
```

In `distage-testkit`, choose axes using @scaladoc[TestConfig](izumi.distage.testkit.TestConfig):

```scala mdoc:to-string
import distage.StandardAxis.Repo
import izumi.distage.testkit.TestConfig
import izumi.distage.testkit.scalatest.DistageBIOSpecScalatest

class AxisTest extends DistageBIOSpecScalatest[zio.IO] {

  // choose implementations `.tagged` as `Repo.Dummy` over those tagged `Repo.Prod`
  override def config: TestConfig = super.config.copy(
    activation = Activation(Repo -> Repo.Dummy)
  )
  
}
```

#### Multi-dimensionality

There may be many configuration axes in an application and components can specify multiple axis choices at once:

```scala mdoc:to-string
import distage.StandardAxis.Mode

class TestPrintGreeter extends Greeter {
  def hello(name: String) = 
    putStrLn(s"Test 1 2, hello $name")
}

// declare 3 possible implementations

def TestModule = new ModuleDef {
  make[Greeter].tagged(Style.Normal, Mode.Prod).from[PrintGreeter]
  make[Greeter].tagged(Style.Normal, Mode.Test).from[TestPrintGreeter]
  make[Greeter].tagged(Style.AllCaps).from[AllCapsGreeter]
}

def runWith(activation: Activation) = unsafeRun {
  Injector().produceRun(TestModule, activation) {
    greeter: Greeter => greeter.hello("$USERNAME")
  }
}

// Production Normal Greeter

runWith(Activation(Style -> Style.Normal, Mode -> Mode.Prod))

// Test Normal Greeter

runWith(Activation(Style -> Style.Normal, Mode -> Mode.Test))

// Both Production and Test Caps Greeters are the same:

runWith(Activation(Style -> Style.AllCaps, Mode -> Mode.Prod))

runWith(Activation(Style -> Style.AllCaps, Mode -> Mode.Test))
```

### Resource Bindings, Lifecycle

You can specify object lifecycle by injecting [cats.effect.Resource](https://typelevel.org/cats-effect/datatypes/resource.html),
[zio.ZManaged](https://zio.dev/docs/datatypes/datatypes_managed) or @scaladoc[distage.Lifecycle](izumi.distage.model.definition.Lifecycle)
values that specify the allocation and finalization actions for an object.

Injector itself only returns a `Lifecycle` value that can be used to create and finalize the object graph, this value is
pure and can be reused multiple times. A `Lifecycle` is consumed using its `.use` method, the function passed to `use` will
receive an allocated resource and when the function exits the resource will be deallocated. 

Example with `cats.effect.Resource`:

```scala mdoc:reset:to-string
import distage.{Roots, ModuleDef, Injector}
import cats.effect.{Resource, IO}

class DBConnection
class MessageQueueConnection

val dbResource = Resource.make(
  acquire = IO { 
    println("Connecting to DB!")
    new DBConnection 
})(release = _ => IO(println("Disconnecting DB")))

val mqResource = Resource.make(
  acquire = IO {
   println("Connecting to Message Queue!")
   new MessageQueueConnection 
})(release = _ => IO(println("Disconnecting Message Queue")))

class MyApp(db: DBConnection, mq: MessageQueueConnection) {
  val run = IO(println("Hello World!"))
}

def module = new ModuleDef {
  make[DBConnection].fromResource(dbResource)
  make[MessageQueueConnection].fromResource(mqResource)
  make[MyApp]
}
```

Will produce the following output:

```scala mdoc:to-string
import distage.DIKey

val objectGraphResource = Injector[IO]().produce(module, Roots(root = DIKey[MyApp]))

objectGraphResource
  .use(_.get[MyApp].run)
  .unsafeRunSync()
```

Lifecycle management with `Lifecycle` is also available without an effect type, via `Lifecycle.Simple` and `Lifecycle.Mutable`:

```scala mdoc:reset:to-string
import distage.{Lifecycle, ModuleDef, Injector}

class Init {
  var initialized = false
}

class InitResource extends Lifecycle.Simple[Init] {
  override def acquire = {
    val init = new Init
    init.initialized = true
    init
  }
  override def release(init: Init) = {
    init.initialized = false
  }
}

def module = new ModuleDef {
  make[Init].fromResource[InitResource]
}

val closedInit = Injector()
  .produceGet[Init](module)
  .use {
    init =>
      println(init.initialized)
      init
}

println(closedInit.initialized)
```

`Lifecycle` forms a monad and has the expected `.map`, `.flatMap`, `.evalMap`, `.mapK` methods.

You can convert between a `Lifecycle` and `cats.effect.Resource` via `Lifecycle#toCats`/`Lifecycle.fromCats` methods, 
and between a `Lifecycle` and `zio.ZManaged` via `Lifecycle#toZIO`/`Lifecycle.fromZIO` methods.

#### Inheritance helpers

The following helpers allow defining `Lifecycle` sub-classes using expression-like syntax:

- @scaladoc[Lifecycle.Of](izumi.distage.model.definition.Lifecycle$$Of)
- @scaladoc[Lifecycle.OfInner](izumi.distage.model.definition.Lifecycle$$OfInner)
- @scaladoc[Lifecycle.OfCats](izumi.distage.model.definition.Lifecycle$$OfCats)
- @scaladoc[Lifecycle.OfZIO](izumi.distage.model.definition.Lifecycle$$OfZIO)
- @scaladoc[Lifecycle.LiftF](izumi.distage.model.definition.Lifecycle$$LiftF)
- @scaladoc[Lifecycle.Make](izumi.distage.model.definition.Lifecycle$$Make)
- @scaladoc[Lifecycle.Make_](izumi.distage.model.definition.Lifecycle$$Make_)
- @scaladoc[Lifecycle.MakePair](izumi.distage.model.definition.Lifecycle$$MakePair)
- @scaladoc[Lifecycle.FromAutoCloseable](izumi.distage.model.definition.Lifecycle$$FromAutoCloseable)
- @scaladoc[Lifecycle.SelfOf](izumi.distage.model.definition.Lifecycle$$SelfOf)
- @scaladoc[Lifecycle.MutableOf](izumi.distage.model.definition.Lifecycle$$MutableOf)

The main reason to employ them is to workaround a limitation in Scala 2's eta-expansion whereby when converting a method to a function value,
Scala would always try to fulfill implicit parameters eagerly instead of making them parameters in the function value,
this limitation makes it harder to inject implicits using `distage`.
However, if instead of eta-expanding manually as in `make[A].fromResource(A.resource[F] _)`,
you use `distage`'s type-based constructor syntax: `make[A].fromResource[A.Resource[F]]`,
this limitation is lifted, injecting the implicit parameters of class `A.Resource` from
the object graph instead of summoning them in-place.
Therefore, you can convert an expression based resource-constructor such as:

```scala mdoc:reset:to-string
import distage.Lifecycle, cats.Monad

class A

object A {

  def resource[F[_]: Monad]: Lifecycle[F, A] =
    Lifecycle.pure[F, A](new A)
    
}
```

Into class-based form:

```scala mdoc:reset:to-string
import distage.Lifecycle, cats.Monad

class A

object A {

  final class Resource[F[_]: Monad]
    extends Lifecycle.Of(
      Lifecycle.pure[F, A](new A)
    )
    
}
```

And inject successfully using `make[A].fromResource[A.Resource[F]]` syntax of @scaladoc[ModuleDefDSL](izumi.distage.model.definition.dsl.ModuleDefDSL).

The following helpers ease defining `Lifecycle` sub-classes using traditional inheritance where `acquire`/`release` parts are defined as methods:

- @scaladoc[Lifecycle.Basic](izumi.distage.model.definition.Lifecycle$$Basic)
- @scaladoc[Lifecycle.Simple](izumi.distage.model.definition.Lifecycle$$Simple)
- @scaladoc[Lifecycle.Mutable](izumi.distage.model.definition.Lifecycle$$Mutable)
- @scaladoc[Lifecycle.MutableNoClose](izumi.distage.model.definition.Lifecycle$$MutableNoClose)
- @scaladoc[Lifecycle.Self](izumi.distage.model.definition.Lifecycle$$Self)
- @scaladoc[Lifecycle.SelfNoClose](izumi.distage.model.definition.Lifecycle$$SelfNoClose)
- @scaladoc[Lifecycle.NoClose](izumi.distage.model.definition.Lifecycle$$NoClose)

### Set Bindings

Set bindings are useful for implementing listeners, plugins, hooks, http routes, healthchecks, migrations, etc.
Everywhere where a collection of components is required, a Set Binding is appropriate.

To define a Set binding use `.many` and `.add` methods of the @scaladoc[ModuleDef](izumi.distage.model.definition.ModuleDef) DSL.

As an example, we may declare multiple command handlers and use them to interpret user input in a REPL


```scala mdoc:reset:to-string
import distage.ModuleDef

final case class CommandHandler(
  handle: PartialFunction[String, String]
)

val additionHandler = CommandHandler {
  case s"$x + $y" => s"${x.toInt + y.toInt}"
}

object AdditionModule extends ModuleDef {
  many[CommandHandler]
    .add(additionHandler)
}
```

We've used `many` method to declare an open `Set` of command handlers and then added one handler to it.

When module definitions are combined, elements for the same type of `Set` will be merged together into a larger set.

You can summon a Set binding by summoning a scala `Set`, as in `Set[CommandHandler]`.

Let's define a new module with another handler:

```scala mdoc:to-string
val subtractionHandler = CommandHandler { 
  case s"$x - $y" => s"${x.toInt - y.toInt}" 
}

object SubtractionModule extends ModuleDef {  
  many[CommandHandler]
    .add(subtractionHandler)
}
```

Let's create a command-line application using our command handlers:

```scala mdoc:invisible:to-string
implicit final class InjRun_NO_IDENTITY_TYPE_IN_OUTPUT(inj: distage.Injector[izumi.fundamentals.platform.functional.Identity]) {
  def HACK_OVERRIDE_produceRun[A](m: distage.Module)(p: distage.Functoid[A]): A = inj.produceRun[A](m)(p)
}
implicit final class LifecycleUnsafeGet_NO_IDENTITY_TYPE_IN_OUTPUT[A](lifecycle: distage.Lifecycle[izumi.fundamentals.platform.functional.Identity, A]) {
  def HACK_OVERRIDE_unsafeGet(): A = lifecycle.unsafeGet()
}
```

```scala mdoc:override:to-string
import distage.Injector

trait App {
  def interpret(input: String): String
}
object App {
  final class Impl(
    handlers: Set[CommandHandler]
  ) extends App {
    override def interpret(input: String): String = {
      handlers.map(_.handle).reduce(_ orElse _).lift(input) match {
        case Some(answer) => s"ANSWER: $answer"
        case None         => "?"
      }
    }
  }
}

object AppModule extends ModuleDef {
  // include all the previous module definitions
  include(AdditionModule)
  include(SubtractionModule)
  
  // add a help handler
  many[CommandHandler].add(CommandHandler {
    case "help" => "Please input an arithmetic expression!" 
  })

  // bind App
  make[App].from[App.Impl]
}

// wire the graph and get the app

val app = Injector().produceGet[App](AppModule).HACK_OVERRIDE_unsafeGet()

// check how it works

app.interpret("1 + 5")

app.interpret("7 - 11")

app.interpret("1 / 3")

app.interpret("help")
```

If we rewire the app without `SubtractionModule`, it will expectedly lose the ability to subtract:

```scala mdoc:override:to-string
Injector().HACK_OVERRIDE_produceRun(AppModule -- SubtractionModule.keys) {
  app: App =>
    app.interpret("10 - 1")
}
```

Further reading:

- Guice calls the same concept ["Multibindings"](https://github.com/google/guice/wiki/Multibindings).

### Effect Bindings

Sometimes we want to effectfully create a component, but the resulting component or data does not need to be deallocated.
An example might be a global `Semaphore` to limit the parallelism of the entire application based on configuration,
or a test implementation of some service made with `Ref`s.

In these cases we can use `.fromEffect` to create a value using an effectful constructor.

Example with a `Ref`-based Tagless Final `KVStore`:

```scala mdoc:reset:to-string
import distage.{ModuleDef, Injector}
import izumi.functional.bio.{BIOError, Primitives2, F}
import zio.{Task, IO}

trait KVStore[F[_, _]] {
  def get(key: String): F[NoSuchElementException, String]
  def put(key: String, value: String): F[Nothing, Unit]
}

def dummyKVStore[F[+_, +_]: BIOError: Primitives2]: F[Nothing, KVStore[F]] = {
  for {
    ref <- F.mkRef(Map.empty[String, String])
  } yield new KVStore[F] {
    def put(key: String, value: String): F[Nothing, Unit] = {
      ref.update_(_ + (key -> value))
    }
  
    def get(key: String): F[NoSuchElementException, String] = {
      for {
        map <- ref.get
        res <- map.get(key) match {
          case Some(value) => F.pure(value)
          case None        => F.fail(new NoSuchElementException(key))
        }
      } yield res
    }
  }
}

def kvStoreModule = new ModuleDef {
  make[KVStore[IO]].fromEffect(dummyKVStore[IO])
}

val io = Injector[Task]()
  .produceRun[String](kvStoreModule) {
    kv: KVStore[IO] =>
      for {
        _    <- kv.put("apple", "pie")
        res1 <- kv.get("apple")
        _    <- kv.put("apple", "ipad")
        res2 <- kv.get("apple")
      } yield res1 + res2
  }

zio.Runtime.default.unsafeRun(io)
```

You need to specify your effect type when constructing `Injector`, as in `Injector[F]()`, to use effect bindings in chosen `F[_]`.

### ZIO Has Bindings

You can inject into ZIO Environment using `make[_].fromHas` syntax for `ZLayer`, `ZManaged`, `ZIO` or any `F[_, _, _]: Local3`:

```scala mdoc:reset:invisible
class Dep1
class Dep2
class Arg1
class Arg2

class X
object X extends X {
  def apply(a: Arg1, b: Arg2, d: Dep1): X = X
}
```

```scala mdoc:to-string
import zio._
import distage._

def zioEnvCtor: URIO[Has[Dep1] with Has[Dep2], X] = ZIO.succeed(X)
def zmanagedEnvCtor: URManaged[Has[Dep1] with Has[Dep2], X] = ZManaged.succeed(X)
def zlayerEnvCtor: URLayer[Has[Dep1] with Has[Dep2], Has[X]] = ZLayer.succeed(X)

def module1 = new ModuleDef {
  make[X].fromHas(zioEnvCtor)
  // or
  make[X].fromHas(zmanagedEnvCtor)
  // or
  make[X].fromHas(zlayerEnvCtor)
}
```

You can also mix environment and parameter dependencies at the same time in one constructor:

```scala mdoc:to-string
def zioArgEnvCtor(a: Arg1, b: Arg2): URLayer[Has[Dep1], Has[X]] = ZLayer.fromService(dep1 => X(a, b, dep1))

def module2 = new ModuleDef {
  make[X].fromHas(zioArgEnvCtor _)
}
```

`zio.Has` values are derived at compile-time by @scaladoc[HasConstructor](izumi.distage.constructors.HasConstructor) macro and can be summoned at need. 

Example:

```scala mdoc:reset:to-string
import distage.{ModuleDef, Injector}
import zio.console.{putStrLn, Console}
import zio.{UIO, URIO, Ref, Task, Has}

trait Hello {
  def hello: UIO[String]
}
trait World {
  def world: UIO[String]
}

// Environment forwarders that allow
// using service functions from everywhere

val hello: URIO[Has[Hello], String] = URIO.accessM(_.get.hello)

val world: URIO[Has[World], String] = URIO.accessM(_.get.world)

// service implementations

val makeHello = {
  (for {
    _     <- putStrLn("Creating Enterprise Hellower...")
    hello = new Hello { val hello = UIO("Hello") }
  } yield hello).toManaged { _ =>
    putStrLn("Shutting down Enterprise Hellower")
  }
}

val makeWorld = {
  for {
    counter <- Ref.make(0)
  } yield new World {
    val world = counter.get.map(c => if (c < 1) "World" else "THE World")
  }
}

// the main function

val turboFunctionalHelloWorld: URIO[Has[Hello] with Has[World] with Has[Console.Service], Unit] = {
  for {
    hello <- hello
    world <- world
    _     <- putStrLn(s"$hello $world")
  } yield ()
}

def module = new ModuleDef {
  make[Hello].fromHas(makeHello)
  make[World].fromHas(makeWorld)
  make[Console.Service].fromHas(Console.live)
  make[Unit].fromHas(turboFunctionalHelloWorld)
}

val main = Injector[Task]()
  .produceRun[Unit](module)((_: Unit) => Task.unit)

zio.Runtime.default.unsafeRun(main)
```

#### Converting ZIO environment dependencies to parameters

Any ZIO Service that requires an environment can be turned into a service without an environment dependency by providing
the dependency in each method using `.provide`.

This pattern can be generalized by implementing an instance of `cats.Contravariant` (or `cats.tagless.FunctorK`) for your services 
and using it to turn environment dependencies into constructor parameters.

In that way ZIO Environment can be used uniformly
for declaration of dependencies, but the dependencies used inside the service do not leak to other services calling it.
See: https://gitter.im/ZIO/Core?at=5dbb06a86570b076740f6db2

Example:

```scala mdoc:reset:to-string
import cats.Contravariant
import distage.{Injector, ModuleDef, Functoid, Tag, TagK, HasConstructor}
import zio.{Task, UIO, URIO, Has}

trait Dependee[-R] {
  def x(y: String): URIO[R, Int]
}
trait Depender[-R] {
  def y: URIO[R, String]
}
implicit val contra1: Contravariant[Dependee] = new Contravariant[Dependee] {
  def contramap[A, B](fa: Dependee[A])(f: B => A): Dependee[B] = new Dependee[B] { def x(y: String) = fa.x(y).provideSome(f) }
}
implicit val contra2: Contravariant[Depender] = new Contravariant[Depender] {
  def contramap[A, B](fa: Depender[A])(f: B => A): Depender[B] = new Depender[B] { def y = fa.y.provideSome(f) }
}

type DependeeR = Has[Dependee[Any]]
type DependerR = Has[Depender[Any]]
object dependee extends Dependee[DependeeR] { 
  def x(y: String) = URIO.accessM(_.get.x(y))
}
object depender extends Depender[DependerR] { 
  def y = URIO.accessM(_.get.y) 
}

// cycle
object dependerImpl extends Depender[DependeeR] {
  def y: URIO[DependeeR, String] = dependee.x("hello").map(_.toString)
}
object dependeeImpl extends Dependee[DependerR] {
  def x(y: String): URIO[DependerR, Int] = {
    if (y == "hello") UIO(5) 
    else depender.y.map(y.length + _.length)
  }
}

/** Fulfill the environment dependencies of a service from the object graph */
def fullfill[R: Tag: HasConstructor, M[_]: TagK: Contravariant](service: M[R]): Functoid[M[Any]] = {
  HasConstructor[R]
    .map(depsCakeR => Contravariant[M].contramap(service)(_ => depsCakeR))
}

def module = new ModuleDef {
  make[Depender[Any]].from(fullfill(dependerImpl))
  make[Dependee[Any]].from(fullfill(dependeeImpl))
}

Injector()
  .produceRun(module) {
    HasConstructor[DependeeR].map {
      (for {
        r <- dependee.x("zxc")
        _ <- Task(println(s"result: $r"))
      } yield ()).provide(_)
    }
  }.fold(_ => 1, _ => 0)
```

### Auto-Traits

distage can instantiate traits and structural types. All unimplemented fields in a trait, or a refinement are filled in from the object graph.

Trait implementations are derived at compile-time by @scaladoc[TraitConstructor](izumi.distage.constructors.TraitConstructor) macro
and can be summoned at need. 

If a suitable trait is specified as an implementation class for a binding, `TraitConstructor` will be used automatically:

Example:

```scala mdoc:reset-object:to-string
import distage.{ModuleDef, Id, Injector}

trait Trait1 {
  def a: Int @Id("a")
}
trait Trait2 {
  def b: Int @Id("b")
}

/** All methods in this trait are implemented,
  * so a constructor for it will be generated
  * even though it's not a class */
trait Pluser {
  def plus(a: Int, b: Int) = a + b
}

trait PlusedInt {
  def result(): Int
}
object PlusedInt {

  /**
    * Besides the dependency on `Pluser`,
    * this class defines 2 more dependencies
    * to be injected from the object graph:
    *
    * `def a: Int @Id("a")` and
    * `def b: Int @Id("b")`
    * 
    * When an abstract type is declared as an implementation,
    * its no-argument abstract defs & vals are considered as
    * dependency parameters by TraitConstructor. (empty-parens and
    * parameterized methods are not considered parameters)
    *
    * Here, using an abstract class directly as an implementation
    * lets us avoid writing a lengthier constructor, like this one:
    * 
    * {{{
    *   final class Impl(
    *     pluser: Pluser,
    *     override val a: Int @Id("a"),
    *     override val b: Int @Id("b"),
    *   ) extends PlusedInt with Trait1 with Trait2
    * }}}
    */
  abstract class Impl(
    pluser: Pluser
  ) extends PlusedInt
    with Trait1
    with Trait2 {
    override def result(): Int = {
      pluser.plus(a, b)
    }
  }

}

def module = new ModuleDef {
  make[Int].named("a").from(1)
  make[Int].named("b").from(2)
  make[Pluser]
  make[PlusedInt].from[PlusedInt.Impl]
}

Injector().produceRun(module) {
  plusedInt: PlusedInt => 
    plusedInt.result()
}
```

#### @impl annotation

Abstract classes or traits without obvious concrete subclasses
may hinder the readability of a codebase, to mitigate that you may use an optional @scaladoc[@impl](izumi.distage.model.definition.impl) 
documenting annotation to aid the reader in understanding your intention.

```scala mdoc:to-string
import distage.impl

@impl abstract class Impl(
  pluser: Pluser
) extends PlusedInt with Trait1 with Trait2 {
  override def result(): Int = {
    pluser.plus(a, b)
  }
}
```

#### Avoiding constructors even further

When overriding behavior of a class, you may avoid writing a repeat of its constructor in your sub-class by inheriting
it with a trait instead. Example:

```scala mdoc:to-string
/**
  * Note how we avoid writing a call to the super-constructor 
  * of `PlusedInt.Impl`, such as:
  *
  * {{{
  *   abstract class OverridenPlusedIntImpl(
  *     pluser: Pluser
  *   ) extends PlusedInt.Impl(pluser)
  * }}}
  *
  * Which would be unavoidable with class-to-class inheritance.
  * Using trait-to-class inheritance we avoid writing any boilerplate
  * besides the overrides we want to apply to the class. 
  */
@impl trait OverridenPlusedIntImpl extends PlusedInt.Impl {
 override def result(): Int = {
   super.result() * 10
 }
}

Injector().produceRun(module overriddenBy new ModuleDef {
  make[PlusedInt].from[OverridenPlusedIntImpl]
}) {
  plusedInt: PlusedInt => 
    plusedInt.result()
}
```

### Auto-Factories

`distage` can instantiate 'factory' classes from suitable traits. This feature is especially useful with `Akka`.
All unimplemented methods _with parameters_ in a trait will be filled by factory methods:

Given a class `ActorFactory`:

```scala mdoc:reset:to-string
import distage.ModuleDef
import java.util.UUID

class SessionStorage

class UserActor(sessionId: UUID, sessionStorage: SessionStorage)

trait ActorFactory {
  // UserActor will be created as follows:
  //   sessionId argument is provided by the user
  //   sessionStorage argument is wired from the object graph
  def createActor(sessionId: UUID): UserActor
}
```

And a binding of `ActorFactory` *without* an implementation

```scala mdoc:to-string
class ActorModule extends ModuleDef {
  make[ActorFactory]
}
```

`distage` will derive and bind the following implementation for `ActorFactory`:

```scala mdoc:to-string
class ActorFactoryImpl(sessionStorage: SessionStorage) extends ActorFactory {
  override def createActor(sessionId: UUID): UserActor = {
    new UserActor(sessionId, sessionStorage)
  }
}
```

#### @With annotation

`@With` annotation can be used to specify the implementation class, to avoid leaking the implementation type in factory method result:

```scala mdoc:reset:to-string
import distage.{ModuleDef, Injector, With}

trait Actor { 
  def receive(msg: Any): Unit
}

object Actor {
  trait Factory {
    def newActor(id: String): Actor @With[Actor.Impl]
  }

  final class Impl(id: String, config: Actor.Configuration) extends Actor {
    def receive(msg: Any) = {
      val response = s"Actor `$id` received a message: $msg"
      println(if (config.allCaps) response.toUpperCase else response)
    }
  }

  final case class Configuration(allCaps: Boolean)
}

def factoryModule = new ModuleDef {
  make[Actor.Factory]
  make[Actor.Configuration].from(Actor.Configuration(allCaps = false))
}

Injector()
  .produceGet[Actor.Factory](factoryModule)
  .use(_.newActor("Martin Odersky").receive("ping"))
```

You can use this feature to concisely provide non-Singleton semantics for some of your components.

Factory implementations are derived at compile-time by
@scaladoc[FactoryConstructor](izumi.distage.constructors.FactoryConstructor) macro
and can be summoned at need.

### Tagless Final Style

Tagless Final is one of the popular patterns for structuring purely-functional applications.

Brief introduction to tagless final:

- [Deferring Commitments: Tagless Final](https://medium.com/@calvin.l.fer/deferring-commitments-tagless-final-704d768f15cb)
- [Introduction to Tagless Final](https://www.beyondthelines.net/programming/introduction-to-tagless-final/)

Advantages of `distage` as a driver for TF compared to implicits:

- easy explicit overrides
- easy @ref[effectful instantiation](basics.md#effect-bindings) and @ref[resource management](basics.md#resource-bindings-lifecycle)
- extremely easy & scalable @ref[test](distage-testkit.md#testkit) context setup due to the above
- multiple different implementations for a type using disambiguation by `@Id`

For example, let's take [freestyle's tagless example](http://frees.io/docs/core/handlers/#tagless-interpretation)
and make it safer and more flexible by replacing dependencies on global `import`ed implementations from with explicit modules.

First, the program we want to write:

```scala mdoc:reset:to-string
import cats.Monad
import cats.effect.{Sync, IO}
import cats.syntax.all._
import distage.{Roots, Module, ModuleDef, Injector, Tag, TagK, TagKK}

trait Validation[F[_]] {
  def minSize(s: String, n: Int): F[Boolean]
  def hasNumber(s: String): F[Boolean]
}
object Validation {
  def apply[F[_]: Validation]: Validation[F] = implicitly
}

trait Interaction[F[_]] {
  def tell(msg: String): F[Unit]
  def ask(prompt: String): F[String]
}
object Interaction {
  def apply[F[_]: Interaction]: Interaction[F] = implicitly
}

class TaglessProgram[F[_]: Monad: Validation: Interaction] {
  def program: F[Unit] = for {
    userInput <- Interaction[F].ask("Give me something with at least 3 chars and a number on it")
    valid     <- (Validation[F].minSize(userInput, 3), Validation[F].hasNumber(userInput)).mapN(_ && _)
    _         <- if (valid) Interaction[F].tell("awesomesauce!")
                 else       Interaction[F].tell(s"$userInput is not valid")
  } yield ()
}

def ProgramModule[F[_]: TagK: Monad] = new ModuleDef {
  make[TaglessProgram[F]]
}
```

@scaladoc[TagK](izumi.reflect.TagK) is `distage`'s analogue of `TypeTag` for higher-kinded types such as `F[_]`,
it allows preserving type-information at runtime for type parameters.
You'll need to add a @scaladoc[TagK](izumi.reflect.TagK) context bound to create a module parameterized by an abstract `F[_]`.
To parameterize by non-higher-kinded types, use just @scaladoc[Tag](izumi.reflect.Tag).

Now the interpreters for `Validation` and `Interaction`:

```scala mdoc:to-string
final class SyncValidation[F[_]](implicit F: Sync[F]) extends Validation[F] {
  def minSize(s: String, n: Int): F[Boolean] = F.delay(s.size >= n)
  def hasNumber(s: String): F[Boolean]       = F.delay(s.exists(c => "0123456789".contains(c)))
}
  
final class SyncInteraction[F[_]](implicit F: Sync[F]) extends Interaction[F] {
  def tell(s: String): F[Unit]  = F.delay(println(s))
  def ask(s: String): F[String] = F.delay("This could have been user input 1")
}

def SyncInterpreters[F[_]: TagK: Sync] = {
  new ModuleDef {
    make[Validation[F]].from[SyncValidation[F]]
    make[Interaction[F]].from[SyncInteraction[F]]
  }
}

// combine all modules

def SyncProgram[F[_]: TagK: Sync] = ProgramModule[F] ++ SyncInterpreters[F]

// create object graph Lifecycle

val objectsLifecycle = Injector[IO]().produce(SyncProgram[IO], Roots.Everything)

// run

objectsResource.use(_.get[TaglessProgram[IO]].program).unsafeRunSync()
```

#### Out-of-the-box typeclass instances

Note: we did not have to include either `Monad[IO]` or `Sync[IO]` instances for our program to run,
despite the fact that `SyncInteraction` depends on `Sync[F]`.

This is because `distage` includes all the relevant typeclass instances for the provided effect type by default, using implicits.
In general whenever `cats-effect` is on the classpath and your effect type has `cats-effect` instances, they will be included, this is valid for `ZIO`, `cats.effect.IO`, `monix`, `monix-bio` and any other type with cats-effect instances.
For ZIO, `monix-bio` and any other implementors of @ref[BIO](../bio/00_bio.md), instances of bifunctor @ref[BIO](../bio/00_bio.md) effect hierarchy will also be included.
See @scaladoc[`DefaultModule`](izumi.distage.modules.DefaultModule) for more details.

#### Effect-type polymorphism

The program module is polymorphic over effect type. It can be instantiated by a different effect:

```scala mdoc:to-string
import zio.interop.catz._
import zio.Task

val ZIOProgram = ProgramModule[Task] ++ SyncInterpreters[Task]
```

We may even choose different interpreters at runtime:

```scala mdoc:to-string
import zio.RIO
import zio.console.{Console, getStrLn, putStrLn}
import distage.Activation

object RealInteractionZIO extends Interaction[RIO[Console, ?]] {
  def tell(s: String): RIO[Console, Unit]  = putStrLn(s)
  def ask(s: String): RIO[Console, String] = putStrLn(s) *> getStrLn
}

def RealInterpretersZIO = {
  SyncInterpreters[RIO[Console, ?]] overriddenBy new ModuleDef {
    make[Interaction[RIO[Console, ?]]].from(RealInteractionZIO)
  }
}

def chooseInterpreters(isDummy: Boolean) = {
  val interpreters = if (isDummy) SyncInterpreters[RIO[Console, ?]]
                     else         RealInterpretersZIO
  def module = ProgramModule[RIO[Console, ?]] ++ interpreters
  
  Injector[RIO[Console, ?]]()
    .produceGet[TaglessProgram[RIO[Console, ?]]](module, Activation.empty)
}

// execute

chooseInterpreters(true)
```

#### Kind polymorphism

Modules can be polymorphic over arbitrary kinds - use `TagKK` to abstract over bifunctors:

```scala mdoc:to-string
class BifunctorIOModule[F[_, _]: TagKK] extends ModuleDef 
```

Or use `Tag.auto.T` to abstract over any kind:

```scala mdoc:to-string
class MonadTransModule[F[_[_], _]: Tag.auto.T] extends ModuleDef
```

```scala mdoc:to-string
class TrifunctorModule[F[_, _, _]: Tag.auto.T] extends ModuleDef
```

```scala mdoc:to-string
class EldritchModule[F[+_, -_[_, _], _[_[_, _], _], _]: Tag.auto.T] extends ModuleDef
```

consult [izumi.reflect.HKTag](https://javadoc.io/doc/dev.zio/izumi-reflect_2.13/latest/izumi/reflect/HKTag.html) docs for more details.

### Cats & ZIO Integration

Cats & ZIO instances and syntax are available automatically in `distage-core`, without wildcard imports, if your project depends on `cats-core`, `cats-effect` or `zio`.
However, distage *won't* bring in `cats` or `zio` as dependencies if you don't already depend on them.
(see [No More Orphans](https://blog.7mind.io/no-more-orphans.html) blog post for details on how that works)

@ref[Cats Resource & ZIO ZManaged Bindings](basics.md#resource-bindings-lifecycle) also work out of the box without any magic imports.

All relevant typeclass instances for chosen effect type, such as `ConcurrentEffect[F]`, are @ref[included by default](basics.md#out-of-the-box-typeclass-instances) (overridable by user bindings)

Example:

```scala mdoc:reset:invisible:to-string
import distage.Module
class DBConnection
object DBConnection {
  def create[F[_]]: F[DBConnection] = ???
}
def ProgramModule[F[_]]: Module = Module.empty
def SyncInterpreters[F[_]]: Module = Module.empty
```

```scala mdoc:to-string
import cats.syntax.semigroup._
import cats.effect.{ExitCode, IO, IOApp}
import distage.{DIKey, Roots, Injector}

trait AppEntrypoint {
  def run: IO[Unit]
}

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    
    // `distage.Module` has a Monoid instance

    val myModules = ProgramModule[IO] |+| SyncInterpreters[IO]

    val plan = Injector().plan(myModules, Roots.target[AppEntrypoint])

    for {
      // resolveImportsF can effectfully add missing instances to an existing plan
      // (You can also create instances effectfully inside `ModuleDef` via `make[_].fromEffect` bindings)

      newPlan <- plan.resolveImportsF[IO] {
        case i if i.target == DIKey[DBConnection] =>
           DBConnection.create[IO]
      }

      // Effects used in Resource and Effect Bindings 
      // should match the effect `F[_]` in `Injector[F]()`

      _ <- Injector[IO]().produce(newPlan).use {
        classes =>
          classes.get[AppEntrypoint].run
      }
    } yield ExitCode.Success
  }
}
```