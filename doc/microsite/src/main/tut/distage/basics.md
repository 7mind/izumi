Basics
======

@@toc { depth=2 }

### Quick Start

Suppose we have an abstract `Greeter` component and some other components that depend on it:

```scala mdoc:reset:invisible:to-string
var counter = 0
val names = Array("izumi", "kai", "Pavel")
def readLine() = {
  val n = names(counter % names.length)
  counter += 1
  println(s"> $n")
  n
}
```

```scala mdoc:to-string
import distage.{ModuleDef, Injector, GCMode}

trait Greeter {
  def hello(name: String): Unit
}

final class PrintGreeter extends Greeter {
  override def hello(name: String) = println(s"Hello $name!") 
}

trait Byer {
  def bye(name: String): Unit
}

final class PrintByer extends Byer {  
  override def bye(name: String) = println(s"Bye $name!")
}

final class HelloByeApp(greeter: Greeter, byer: Byer) {
  def run(): Unit = {
    println("What's your name?")
    val name = readLine()
    
    greeter.hello(name)
    byer.bye(name)
  }
}
```

To actually run the `HelloByeApp`, we have to wire implementations of `Greeter` and `Byer` into it.
We will not do it directly. First we'll only declare the component interfaces we have and the implementations we want for them:

```scala mdoc:to-string
val HelloByeModule = new ModuleDef {
  make[Greeter].from[PrintGreeter]
  make[Byer].from[PrintByer]
  make[HelloByeApp] // `.from` is not required for concrete classes 
}
```

`ModuleDef` merely contains a description of the desired object graph, let's transform that high-level description into an
actionable series of steps - an @scaladoc[OrderedPlan](izumi.distage.model.plan.OrderedPlan), a datatype we can
@ref[inspect](debugging.md#pretty-printing-plans), @ref[test](debugging.md#testing-plans) or @ref[verify at compile-time](distage-framework.md#compile-time-checks) – without actually creating any objects or executing any effects.

```scala mdoc:to-string
val plan = Injector().plan(HelloByeModule, GCMode.NoGC)
```

The series of steps must be executed to produce the object graph. `Injector.produce` will interpret the steps into a @ref[Resource](basics.md#resource-bindings-lifecycle) value, that holds the lifecycle of the object graph:

```scala mdoc:to-string
// Interpret into DIResource

val resource = Injector().produce(plan)

// Use the object graph:
// After `.use` exits, all objects will be deallocated,
// and all allocated resources will be freed.

resource.use {
  objects =>
    objects.get[HelloByeApp].run()
}
```

`distage` always creates components exactly once, even if multiple other objects depend on them. There is only a "Singleton" scope.
It's impossible to create non-singletons in `distage`.
If you need multiple singleton instances of the same type, you can create `named` instances and disambiguate between them using `@Id` annotation. 

```scala mdoc:to-string
import distage.Id

new ModuleDef {
  make[Byer].named("byer-1").from[PrintByer]
  make[Byer].named("byer-2").from {
    otherByer: Byer @Id("byer-1") =>
      new Byer {
        def bye(name: String) = otherByer.bye(s"NOT-$name")
      }
  }
}
```

You can abstract over annotations with type aliases or with string constants:

```scala mdoc:to-string
object Ids {
  final val byer1Id = "byer-1"
  type Byer1 = Byer1 @Id(byer1Id)
}
```

For true non-singleton semantics, you must create explicit factory classes, or generate them (see @ref[Auto-Factories](#auto-factories))

### Activation Axis

You can choose between different implementations of a component using `Axis` tags:

```scala mdoc:to-string
import distage.{Axis, Activation, ModuleDef, Injector, GCMode}

class AllCapsGreeter extends Greeter {
  def hello(name: String) = println(s"HELLO ${name.toUpperCase}")
}

// declare the configuration axis for our components

object Style extends Axis {
  case object AllCaps extends AxisValueDef
  case object Normal extends AxisValueDef
}

// Declare a module with several implementations of Greeter
// but in different environments

val TwoImplsModule = new ModuleDef {
  make[Greeter].tagged(Style.Normal)
    .from[PrintGreeter]
  
  make[Greeter].tagged(Style.AllCaps)
    .from[AllCapsGreeter]
}

// Combine previous `HelloByeModule` with our new module
// While overriding `make[Greeter]` bindings from the first module 

val CombinedModule = HelloByeModule overridenBy TwoImplsModule

// Choose component configuration when making an Injector:

val capsInjector = Injector(Activation(Style -> Style.AllCaps))

// Check the result:

capsInjector
  .produce(CombinedModule, GCMode.NoGC)
  .use(_.get[HelloByeApp].run())

// Check that result changes with a different configuration:

Injector(Activation(Style -> Style.Normal))
  .produce(CombinedModule, GCMode.NoGC)
  .use(_.get[HelloByeApp].run())
```

In @scaladoc[distage.StandardAxis](izumi.distage.model.definition.StandardAxis) there are three example Axes for back-end development: `Repo.Prod/Dummy`, `Env.Prod/Test` & `ExternalApi.Prod/Mock`  

In `distage-framework`'s @scaladoc[RoleAppLauncher](izumi.distage.roles.RoleAppLauncher), you can choose axes using the `-u` command-line parameter:

```
./launcher -u repo:dummy app1
```

In `distage-testkit`, specify axes via @scaladoc[TestConfig](izumi.distage.testkit.TestConfig):

```scala mdoc:to-string
import distage.StandardAxis.Repo
import izumi.distage.testkit.TestConfig
import izumi.distage.testkit.scalatest.DistageBIOSpecScalatest

class AxisTest extends DistageBIOSpecScalatest[zio.IO] {
  override protected def config: TestConfig = super.config.copy(
    // choose implementations tagged `Repo.Dummy` when multiple implementations with `Repo.*` tags are available
    activation = Activation(Repo -> Repo.Dummy)
  )
}
```

### Resource Bindings, Lifecycle

You can specify object lifecycle by injecting [cats.effect.Resource](https://typelevel.org/cats-effect/datatypes/resource.html),
[zio.ZManaged](https://zio.dev/docs/datatypes/datatypes_managed) or @scaladoc[distage.DIResource](izumi.distage.model.definition.DIResource)
values that specify the allocation and finalization actions for an object.

Injector itself only returns a DIResource value that can be used to create and finalize the object graph, this value is
pure and can be reused multiple times. A DIResource is consumed using its `.use` method, the function passed to `use` will
receive an allocated resource and when the function exits the resource will be deallocated. 

Example with `cats.effect.Resource`:

```scala mdoc:reset:to-string
import distage.{GCMode, ModuleDef, Injector}
import cats.effect.{Bracket, Resource, IO}

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

val module = new ModuleDef {
  make[DBConnection].fromResource(dbResource)
  make[MessageQueueConnection].fromResource(mqResource)
  addImplicit[Bracket[IO, Throwable]]
  make[MyApp]
}
```

Will produce the following output:

```scala mdoc:to-string
val objectGraphResource = Injector().produceF[IO](module, GCMode.NoGC)

objectGraphResource
  .use(_.get[MyApp].run)
  .unsafeRunSync()
```

Lifecycle management `DIResource` is also available without an effect type, via `DIResource.Simple` and `DIResource.Mutable`:

```scala mdoc:reset:to-string
import distage.{DIResource, GCMode, ModuleDef, Injector}

class Init {
  var initialized = false
}

class InitResource extends DIResource.Simple[Init] {
  override def acquire = {
    val init = new Init
    init.initialized = true
    init
  }
  override def release(init: Init) = {
    init.initialized = false
  }
}

val module = new ModuleDef {
  make[Init].fromResource[InitResource]
}

val closedInit = Injector().produce(module, GCMode.NoGC).use {
  objects =>
    val init = objects.get[Init] 
    println(init.initialized)
    init
}

println(closedInit.initialized)
```

`DIResource` forms a monad and has the expected `.map`, `.flatMap`, `.evalMap`, `.mapK` methods.

You can convert between `DIResource` and `cats.effect.Resource` via `.toCats`/`.fromCats` methods, and between
`zio.ZManaged` via `.toZIO`/`.fromZIO`.

You need to use resource-aware `Injector.produce`/`Injector.produceF`, instead of `produceUnsafe` to be able to deallocate the object graph.

### Set Bindings

Set bindings are useful for implementing listeners, plugins, hooks, http routes, healthchecks, migrations, etc.
Everywhere where a collection of components is required, a Set Binding is appropriate.

To define a Set binding use `.many` and `.add` methods of the @scaladoc[ModuleDef](izumi.distage.model.definition.ModuleDef) DSL.

For example, we may declare many [http4s](https://http4s.org) routes and serve them all from a central router:

```scala mdoc:silent:reset:to-string
// import boilerplate
import cats.implicits._
import cats.effect.{Bracket, IO, Resource}
import distage.{GCMode, ModuleDef, Injector}
import org.http4s._
import org.http4s.server.Server
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext.Implicits.global

implicit val contextShift = IO.contextShift(global)
implicit val timer = IO.timer(global)
```

```scala mdoc:to-string
val homeRoute = HttpRoutes.of[IO] { 
  case GET -> Root / "home" => Ok(s"Home page!") 
}

object HomeRouteModule extends ModuleDef {
  many[HttpRoutes[IO]]
    .add(homeRoute)
}
```

We've used `many` method to declare an open `Set` of http routes and then added one HTTP route into it.
When module definitions are combined, `Sets` for the same binding will be merged together.
You can summon a Set Bindings by summoning a scala `Set`, as in `Set[HttpRoutes[IO]]`.

Let's define a new module with another route:

```scala mdoc:to-string
val blogRoute = HttpRoutes.of[IO] { 
  case GET -> Root / "blog" / post => Ok(s"Blog post ``$post''!") 
}

object BlogRouteModule extends ModuleDef {  
  many[HttpRoutes[IO]]
    .add(blogRoute)
}
```

Now it's the time to define a `Server` component to serve all the different routes we have:

```scala mdoc:to-string
def makeHttp4sServer(routes: Set[HttpRoutes[IO]]): Resource[IO, Server[IO]] = {
  // create a top-level router by combining all the routes
  val router: HttpApp[IO] = routes.toList.foldK.orNotFound

  // return a Resource value that will setup an http4s server 
  BlazeServerBuilder[IO]
    .bindHttp(8080, "localhost")
    .withHttpApp(router)
    .resource
}

object HttpServerModule extends ModuleDef {
  make[Server[IO]].fromResource(makeHttp4sServer _)
  make[Client[IO]].fromResource(BlazeClientBuilder[IO](global).resource)
  addImplicit[Bracket[IO, Throwable]] // required for cats `Resource` in `fromResource`
}

// join all the module definitions
val finalModule = Seq(
  HomeRouteModule,
  BlogRouteModule,
  HttpServerModule,
).merge

// wire the graph
val objects = Injector().produceUnsafeF[IO](finalModule, GCMode.NoGC).unsafeRunSync()

val server = objects.get[Server[IO]]
val client = objects.get[Client[IO]]
```

Check if it works:

```scala mdoc:to-string
// check home page
client.expect[String]("http://localhost:8080/home").unsafeRunSync()

// check blog page
client.expect[String]("http://localhost:8080/blog/1").unsafeRunSync()
```

```scala mdoc:invisible:to-string
// shut down http4s server
objects.finalizers[IO].toList.traverse_(_.effect()).unsafeRunSync()
```

Further reading: the same concept is called [Multibindings](https://github.com/google/guice/wiki/Multibindings) in Guice.

### Effect Bindings

Sometimes we want to effectfully create a component, but the resulting component or data does not need to be deallocated.
An example might be a global `Semaphore` to limit the parallelism of the entire application based on configuration,
or a test implementation of some service made with `Ref`s.

In these cases we can use `.fromEffect` to create a value using an effectful constructor.

Example with a `Ref`-based Tagless Final `KVStore`:

```scala mdoc:reset:to-string
import distage.{GCMode, ModuleDef, Injector}
import izumi.functional.bio.{BIOMonadError, BIOPrimitives, F}
import zio.{Task, IO}

trait KVStore[F[_, _]] {
  def get(key: String): F[NoSuchElementException, String]
  def put(key: String, value: String): F[Nothing, Unit]
}

def dummyKVStore[F[+_, +_]: BIOMonadError: BIOPrimitives]: F[Nothing, KVStore[F]] = {
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

val kvStoreModule = new ModuleDef {
  make[KVStore[IO]].fromEffect(dummyKVStore[IO])
}

val io = Injector()
  .produceF[Task](kvStoreModule, GCMode.NoGC)
  .use {
    objects =>
      val kv = objects.get[KVStore[IO]]
      
      for {
        _    <- kv.put("apple", "pie")
        res1 <- kv.get("apple")
        _    <- kv.put("apple", "ipad")
        res2 <- kv.get("apple")
      } yield res1 + res2
  }

new zio.DefaultRuntime{}.unsafeRun(io)
```

You need to use effect-aware `Injector.produceF`/`Injector.produceUnsafeF` methods to use effect bindings.

### Auto-Traits

distage can instantiate traits and structural types. All unimplemented fields in a trait or a refinement are filled in from the object graph.

This can be used to create ZIO Environment cakes with required dependencies - https://gitter.im/ZIO/Core?at=5dbb06a86570b076740f6db2

Trait implementations are derived at compile-time by @scaladoc[TraitConstructor](izumi.distage.constructors.TraitConstructor) macro
and can be summoned at need. Example:

```scala mdoc:reset:to-string
import distage.{DIKey, GCMode, ModuleDef, Injector, ProviderMagnet, Tag}
import izumi.distage.constructors.TraitConstructor
import zio.console.{Console, putStrLn}
import zio.{UIO, URIO, ZIO, Ref, Task}

trait Hello {
  def hello: UIO[String]
}
trait World {
  def world: UIO[String]
}

// Environment forwarders that allow
// using service functions from everywhere

val hello: URIO[{def hello: Hello}, String] = ZIO.accessM(_.hello.hello)

val world: URIO[{def world: World}, String] = ZIO.accessM(_.world.world)

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

val turboFunctionalHelloWorld = {
  for {
    hello <- hello
    world <- world
    _     <- putStrLn(s"$hello $world")
  } yield ()
}

// a generic function that creates an `R` trait where all fields are populated from the object graph

def provideCake[R: TraitConstructor, A: Tag](fn: R => A): ProviderMagnet[A] = {
  TraitConstructor[R].provider.map(fn)
}

val definition = new ModuleDef {
  make[Hello].fromResource(provideCake(makeHello.provide(_)))
  make[World].fromEffect(makeWorld)
  make[Console.Service[Any]].fromValue(Console.Live.console)
  make[UIO[Unit]].from(provideCake(turboFunctionalHelloWorld.provide))
}

val main = Injector()
  .produceF[Task](definition, GCMode(DIKey.get[UIO[Unit]]))
  .use(_.get[UIO[Unit]])

new zio.DefaultRuntime{}.unsafeRun(main)
```

### Auto-Factories

`distage` can instantiate 'factory' classes from suitable traits. This feature is especially useful with `Akka`.
All unimplemented methods _with parameters_ in a trait will be filled by factory methods:

Given a class `ActorFactory`:

```scala mdoc:to-string
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

`@With` annotation can be used to specify the implementation class, when the factory result is abstract:

```scala mdoc:to-string:reset
import distage.{ModuleDef, Injector, With}

trait Actor { 
  def receive(msg: Any): Unit
}

object Actor {
  trait Factory {
    def newActor(id: String): Actor @With[Actor.Impl]
  }

  final class Impl(id: String, config: Actor.Configuration) extends Actor {
    def receive(msg: String) = {
      val response = s"Actor `$id` received a message: $msg"
      println(if (config.allCaps) response.toUpperCase else response)
    }
  }

  final case class Configuration(allCaps: Boolean)
}

val factoryModule = new ModuleDef {
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

Tagless Final is one of the popular patterns for structuring purely-functional applications. If you're not familiar with tagless final you can skip this section.

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
import cats.effect.{ExitCode, Sync, IO}
import cats.syntax.all._
import distage.{GCMode, Module, ModuleDef, Injector, Tag, TagK, TagKK}

trait Validation[F[_]] {
  def minSize(s: String, n: Int): F[Boolean]
  def hasNumber(s: String): F[Boolean]
}
def Validation[F[_]: Validation]: Validation[F] = implicitly

trait Interaction[F[_]] {
  def tell(msg: String): F[Unit]
  def ask(prompt: String): F[String]
}
def Interaction[F[_]: Interaction]: Interaction[F] = implicitly

class TaglessProgram[F[_]: Monad: Validation: Interaction] {
  def program: F[Unit] = for {
    userInput <- Interaction[F].ask("Give me something with at least 3 chars and a number on it")
    valid     <- (Validation[F].minSize(userInput, 3), Validation[F].hasNumber(userInput)).mapN(_ && _)
    _         <- if (valid) Interaction[F].tell("awesomesauce!")
                 else       Interaction[F].tell(s"$userInput is not valid")
  } yield ()
}

def ProgramModule[F[_]: TagK: Monad]: Module = new ModuleDef {
  make[TaglessProgram[F]]
  addImplicit[Monad[F]]
}
```

@scaladoc[TagK](izumi.fundamentals.reflection.Tags.TagK) is distage's analogue of `TypeTag` for higher-kinded types such as `F[_]`,
it allows preserving type-information at runtime for type parameters.
You'll need to add a @scaladoc[TagK](izumi.fundamentals.reflection.Tags.TagK) context bound to create a module parameterized by an abstract `F[_]`.
To parameterize by non-higher-kinded types, use just @scaladoc[Tag](izumi.fundamentals.reflection.Tags.Tag).

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
    addImplicit[Sync[F]]
  }
}

// combine all modules

def SyncProgram[F[_]: TagK: Sync] = ProgramModule[F] ++ SyncInterpreters[F]

// create object graph Resource

val objectsResource = Injector().produceF[IO](SyncProgram[IO], GCMode.NoGC)

// run

objectsResource.use(_.get[TaglessProgram[IO]].program).unsafeRunSync()
```

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

object RealInteractionZIO extends Interaction[RIO[Console, ?]] {
  def tell(s: String): RIO[Console, Unit]  = putStrLn(s)
  def ask(s: String): RIO[Console, String] = putStrLn(s) *> getStrLn
}

val RealInterpretersZIO = {
  SyncInterpreters[RIO[Console, ?]] overridenBy new ModuleDef {
    make[Interaction[RIO[Console, ?]]].from(RealInteractionZIO)
  }
}

def chooseInterpreters(isDummy: Boolean) = {
  val interpreters = if (isDummy) SyncInterpreters[RIO[Console, ?]]
                     else         RealInterpretersZIO
  val module = ProgramModule[RIO[Console, ?]] ++ interpreters
  Injector().produceGetF[RIO[Console, ?], TaglessProgram[RIO[Console, ?]]](module)
}

// execute

chooseInterpreters(true)
```

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

consult @scaladoc[HKTag](izumi.fundamentals.reflection.WithTags#HKTag) docs for more details.

### Cats & ZIO Integration

Cats & ZIO instances and syntax are available automatically without imports, if `cats-core`, `cats-effect` or `zio` are
already dependencies of your project. (Note: distage *won't* bring `cats` or `zio` as a dependency if you don't already use them.
See [No More Orphans](https://blog.7mind.io/no-more-orphans.html) for description of the technique)

@ref[Cats Resource Bindings](basics.md#resource-bindings-lifecycle) will also work out of the box without any magic imports.

Example:

```scala mdoc:invisible:to-string
class DBConnection
object DBConnection {
  def create[F[_]]: F[DBConnection] = ???
}
```

```scala mdoc:to-string
import cats.effect.IOApp
import distage.DIKey

trait AppEntrypoint {
  def run: IO[Unit]
}

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] = {
    // ModuleDef has a Monoid instance
    val myModules = ProgramModule[IO] |+| SyncInterpreters[IO]
    val plan = Injector().plan(myModules, GCMode(DIKey.get[AppEntrypoint]))

    for {
      // resolveImportsF can effectfully add missing instances to an existing plan
      // (You can also create instances effectfully inside `ModuleDef` via `make[_].fromEffect` bindings)
      newPlan <- plan.resolveImportsF[IO] {
        case i if i.target == DIKey.get[DBConnection] =>
           DBConnection.create[IO]
      } 
      // `produceF` specifies an Effect to run in.
      // Effects used in Resource and Effect Bindings 
      // should match the effect in `produceF`
      _ <- Injector().produceF[IO](newPlan).use {
        classes =>
          classes.get[AppEntrypoint].run
      }
    } yield ExitCode.Success
  }
}
```
