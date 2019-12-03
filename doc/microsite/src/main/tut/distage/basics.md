Basics
======

@@toc { depth=2 }

### Tutorial

Suppose we want to create an abstract `Greeter` component that we want to use without knowing its concrete implementation:

```scala mdoc:reset
trait Greeter {
  def hello(name: String): Unit
}
```

A simple implementation would be:

```scala mdoc
final class PrintGreeter extends Greeter {
  override def hello(name: String) = println(s"Hello $name!") 
}
```

Let's define some more components that depend on a `Greeter`:

```scala mdoc:invisible
def readLine() = { println("kai"); "kai" }
```

```scala mdoc
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

The app above uses `Greeter` and `Byer` to hold a simple conversation with the user.

To actually run the app, we'll have to bind the real implementations of `Greeter` and `Byer`.

```scala mdoc
import distage.{ModuleDef, Injector, GCMode}

object HelloByeModule extends ModuleDef {
  make[Greeter].from[PrintGreeter]
  make[Byer].from[PrintByer]
  make[HelloByeApp]
}
```

Since `HelloByeApp` is not an interface, but a `final class` that implements itself, we don't have to specify an implementation class for it.

Let's launch the app and see what happens:

```scala mdoc
val injector = Injector()

val plan = injector.plan(HelloByeModule, GCMode.NoGC)
val objects = injector.produceUnsafe(plan)

objects.get[HelloByeApp].run()
```

Given a set of bindings, such as `HelloByeModule`, `distage` will lookup the dependencies (constructor or function arguments)
of each implementation and deduce a `plan` to satisfy each dependency using the other implementations in that module.
Once finished, it will happily return the `plan` back to you as a simple datatype.
We can print `HelloByeModule`'s plan while we're at it:

```
println(plan.render)
```

Since `plan` is just a value, we need to interpret it to create the actual object graph –
`Injector`'s `produce` method is the default interpreter. `Injector` contains no logic of its own beyond interpreting
instructions, its output is fully determined by the plan. This makes @ref[debugging](debugging.md#debugging) quite easy.

Given that plans are data, it's possible to @ref[inspect them](debugging.md#pretty-printing-plans), test them or 
@ref[verify them at compile-time](other-features.md#compile-time-checks) before ever running the application.

Objects in `distage` are always created exactly once, even if multiple other objects depend on them - they're `Singletons`.
It's impossible to create non-singletons in `distage`, however it's possible to generate factory classes (@ref[Auto-Factories](#auto-factories))
If you need multiple singleton instances of the same type, you may create `named` instances and disambiguate between them with `@Id` annotation:

```scala mdoc
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

Modules can be combined into larger modules with `++` and `overridenBy` operators.

```scala mdoc
val caps = HelloByeModule.overridenBy(new ModuleDef {
  make[Greeter].from(new Greeter {
    override def hello(name: String) = println(s"HELLO ${name.toUpperCase}")
  })
})

val capsUniverse = injector.produceUnsafe(caps, GCMode.NoGC)

capsUniverse.get[HelloByeApp].run()
```

We've overriden the `Greeter` binding in `HelloByeModule` with an implementation of `Greeter` that prints in ALL CAPS.

### Set Bindings

Set bindings are useful for implementing event listeners, plugins, hooks, http routes, healthchecks, migrations, etc. Everywhere where
you need to gather up a bunch of similar components is probably a good place for a Set Binding.

To define a Set binding use `.many` and `.add` methods in @scaladoc[ModuleDef](izumi.distage.model.definition.ModuleDef)
DSL.


For example, we can gather and serve all the different [http4s](https://http4s.org) routes added in multiple independent modules:

```scala mdoc:silent:reset
// boilerplate
import cats.implicits._
import cats.effect._
import distage._
import org.http4s._
import org.http4s.Uri.uri
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.blaze._

import scala.concurrent.ExecutionContext.Implicits.global

implicit val contextShift = IO.contextShift(global)
implicit val timer = IO.timer(global)
```

```scala mdoc:invisible
// HACK ??? because of mdoc // java.lang.ClassNotFoundException: repl.Session$
// trait ModuleDef extends distage.ModuleDef {
//  make[App4.this.type].from[App4.this.type](App4.this: App4.this.type)
// }
```

```scala mdoc
object HomeRouteModule extends ModuleDef {

  val homeRoute = HttpRoutes.of[IO] { 
    case GET -> Root / "home" => Ok(s"Home page!") 
  }

  many[HttpRoutes[IO]]
    .add(homeRoute)
}
```

We've used `many` method to declare an open `Set` of http routes and then added one HTTP route into it.
When module definitions are combined, `Sets` for the same binding will be merged together.
You can summon a Set Bindings by summoning a scala `Set`, as in `Set[HttpRoutes[IO]]`.

Let's define a new module with another route:

```scala mdoc
object BlogRouteModule extends ModuleDef {

  val blogRoute = HttpRoutes.of[IO] { 
    case GET -> Root / "blog" / post => Ok(s"Blog post ``$post''!") 
  }
  
  many[HttpRoutes[IO]]
    .add(blogRoute)
}
```

Now it's the time to define a `Server` component to serve all the different routes we have:

```scala mdoc
final class HttpServer(routes: Set[HttpRoutes[IO]]) {
  
  val router: HttpApp[IO] = 
    routes.toList.foldK.orNotFound

  val serverResource = 
    BlazeServerBuilder[IO]
      .bindHttp(8080, "localhost")
      .withHttpApp(router)
      .resource
}

object HttpServerModule extends ModuleDef {
  make[HttpServer]
}
```

Now, let's wire all the modules and create the server!

```scala mdoc
val finalModule = Seq(
    HomeRouteModule,
    BlogRouteModule,
    HttpServerModule,
  ).merge

val objects = Injector().produceUnsafe(finalModule, GCMode.NoGC)

val server = objects.get[HttpServer]
```

Check if it works:

```scala mdoc
server.router.run(Request(uri = uri("/home")))
  .flatMap(_.as[String]).unsafeRunSync
```

```scala mdoc
server.router.run(Request(uri = uri("/blog/1")))
  .flatMap(_.as[String]).unsafeRunSync
```

It does!

See also, same concept in [Guice](https://github.com/google/guice/wiki/Multibindings).

### Effect Bindings

Sometimes we need to effectfully create a component or fetch some data and inject it into the object graph during startup (e.g. read a configuration file),
but the resulting component or data does not need to be closed. An example might be a global `Semaphore` that limits the parallelism of an
entire application based on configuration value or a `dummy`/`test double` implementation of some external service made for testing using simple `Ref`s.

In these cases we can use `.fromEffect` to simply bind a value created effectfully.

Example with `ZIO` `Semaphore`:

```scala mdoc:reset-class
import distage._
import distage.config._
import zio._

case class Content(bytes: Array[Byte])

case class UploadConfig(maxParallelUploads: Long)

class UploaderModule extends ModuleDef {
  make[Semaphore].named("upload-limit").fromEffect {
    conf: UploadConfig =>
      Semaphore.make(conf.maxParallelUploads) // : IO[Nothing, Semaphore]
  }

  make[Uploader]

  include(new ConfigModuleDef {
    makeConfig[UploadConfig]("myapp.uploads")
  })
}

class Uploader(limit: Semaphore @Id("upload-limit")) {
  def upload(content: Content): IO[Throwable, Unit] =
    limit.withPermit(upload(content))
}
```

Example with a `Dummy` `KVStore`:

```scala mdoc
trait KVStore[F[_, _]] {
  def put(key: String, value: String): F[Nothing, Unit]
  def get(key: String): F[NoSuchElementException, String]
}

object KVStore {
  def dummy: IO[Nothing, KVStore[IO]] = for {
    ref <- Ref.make(Map.empty[String, String])
    kvStore = new KVStore[IO] {
      def put(key: String, value: String): IO[Nothing, Unit] =
        ref.update(_ + (key -> value)).unit
      
      def get(key: String): IO[NoSuchElementException, String] = 
        for {
          map <- ref.get
          maybeValue = map.get(key)
          res <- maybeValue match {
            case None => 
              IO.fail(new NoSuchElementException(key))
            case Some(value) => 
              IO.succeed(value)
          }
        } yield res
    }
  } yield kvStore
}

val kvStoreModule = new ModuleDef {
  make[KVStore[IO]].fromEffect(KVStore.dummy)
}

new DefaultRuntime{}.unsafeRun {
  Injector().produceF[IO[Throwable, ?]](kvStoreModule, GCMode.NoGC)
    .use {
      objects =>
        val kv = objects.get[KVStore[IO]]
        
        for {
          _ <- kv.put("apple", "pie")
          res <- kv.get("apple")
        } yield res
    }
}
```

You need to use effect-aware `Injector.produceF`/`Injector.produceUnsafeF` methods to use effect bindings.

### Resource Bindings, Lifecycle

You can specify objects' lifecycle by injecting [cats.effect.Resource](https://typelevel.org/cats-effect/datatypes/resource.html),
[zio.ZManaged](https://zio.dev/docs/datatypes/datatypes_managed) or @scaladoc[distage.DIResource](izumi.distage.model.definition.DIResource)
values that specify the allocation and finalization actions for an object.
Resources will be deallocated when the scope of `.use` method on the result object graph ends, this will generally coincide 
with the end of application lifecylce or a test suite.

Example with `cats.effect.Resource`:

```scala mdoc:reset
import distage._
import cats.effect._

class DBConnection
class MessageQueueConnection

val dbResource = Resource.make(
  acquire = IO { println("Connecting to DB!"); new DBConnection }
)(release = _ => IO(println("Disconnecting DB")))

val mqResource = Resource.make(
  acquire = IO { println("Connecting to Message Queue!"); new MessageQueueConnection }
)(release = _ => IO(println("Disconnecting Message Queue")))

class MyApp(db: DBConnection, mq: MessageQueueConnection) {
  val run = IO(println("Hello World!"))
}

def module = new ModuleDef {
  make[DBConnection].fromResource(dbResource)
  make[MessageQueueConnection].fromResource(mqResource)
  addImplicit[Bracket[IO, Throwable]]
  make[MyApp]
}
```

Will produce the following output:

```scala mdoc
// Injector returns a pure DIResource value that describes the creation
// and finalization of the object graph.
// One value can be reused to recreate the same graph multiple times.

val objectGraphResource = Injector().produceF[IO](module, GCMode.NoGC)
objectGraphResource.use {
  objects =>
    objects.get[MyApp].run
}.unsafeRunSync()
```

`DIResource` lifecycle is available without an effect type too, via `DIResource.Simple` and `DIResource.Mutable`:

```scala mdoc:reset
import distage._

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

`DIResource` forms a monad and has the expected `.map`, `.flatMap`, `.evalMap` methods available.
You can convert a `DIResource` into a `cats.effect.Resource` via `.toCats` method.

You need to use resource-aware `Injector.produce`/`Injector.produceF` methods to control lifecycle of the object graph.

### Auto-Traits

distage can instantiate traits and structural types. All unimplemented fields in a trait or a refinement are filled in from the object graph.

This can be used to create ZIO Environment cakes with required dependencies - https://gitter.im/ZIO/Core?at=5dbb06a86570b076740f6db2

Trait implementations are derived at compile-time by @scaladoc[TraitConstructor](izumi.distage.constructors.TraitConstructor) macro
and can be summoned at need. Example:

```scala mdoc
import distage.{DIKey, GCMode, ModuleDef, Injector, ProviderMagnet, Tag}
import izumi.distage.constructors.TraitConstructor
import zio.console.{Console, putStrLn}
import zio.{UIO, URIO, URManaged, ZIO, Ref, Task}

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
def makeHello: URManaged[Console, Hello] = {
  (for {
    _     <- putStrLn("Creating Enterprise Hellower...")
    hello = new Hello { val hello = UIO("Hello") }
  } yield hello).toManaged { _ =>
    putStrLn("Shutting down Enterprise Hellower")
  }
}

def makeWorld: URIO[Console, World] = {
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
  make[World].fromEffect(provideCake(makeWorld.provide(_)))
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

```scala mdoc
import distage._
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
- easy @ref[effectful instantiation](#effect-bindings) and @ref[resource management](#resource-bindings-lifecycle)
- extremely easy & scalable [test](#testkit) context setup due to the above
- multiple different implementations via `@Id` annotation

As an example, let's take [freestyle's tagless example](http://frees.io/docs/core/handlers/#tagless-interpretation)
and make it safer and more flexible by replacing dependencies on global `import`ed implementations from with explicit modules.

First, the program we want to write:

```scala mdoc:reset
import cats._
import cats.implicits._
import distage._

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
    _         <- if (valid) 
                    Interaction[F].tell("awesomesauce!")
                 else 
                    Interaction[F].tell(s"$userInput is not valid")
  } yield ()
}

class Program[F[_]: TagK: Monad] extends ModuleDef {
  make[TaglessProgram[F]]

  addImplicit[Monad[F]]
}
```

@scaladoc[TagK](izumi.fundamentals.reflection.Tags.TagK) is distage's analogue of `TypeTag` for higher-kinded types such as `F[_]`,
it allows preserving type-information at runtime for types that aren't yet known at definition.
You'll need to add a @scaladoc[TagK](izumi.fundamentals.reflection.Tags.TagK) context bound to create a module parameterized by an abstract `F[_]`.
Use @scaladoc[Tag](izumi.fundamentals.reflection.Tags.Tag) to create modules parameterized by non-higher-kinded types.

Interpreters:

```scala mdoc:invisible
import cats.instances.try_.catsStdInstancesForTry
```

```scala mdoc
import scala.util.Try
import cats.instances.all._

def tryValidation = new Validation[Try] {
  def minSize(s: String, n: Int): Try[Boolean] = Try(s.size >= n)
  def hasNumber(s: String): Try[Boolean] = Try(s.exists(c => "0123456789".contains(c)))
}
  
def tryInteraction = new Interaction[Try] {
  def tell(s: String): Try[Unit] = Try(println(s))
  def ask(s: String): Try[String] = Try("This could have been user input 1")
}

object TryInterpreters extends ModuleDef {
  make[Validation[Try]].from(tryValidation)
  make[Interaction[Try]].from(tryInteraction)
}

// combine all modules
val TryProgram = new Program[Try] ++ TryInterpreters

// create object graph
val objects = Injector().produceUnsafe(TryProgram, GCMode.NoGC)

// run
objects.get[TaglessProgram[Try]].program
```

The program module is polymorphic over the eventual effect type, we can easily parameterize it by a different effect:

```scala mdoc
import cats.effect._

def SyncInterpreters[F[_]: TagK](implicit F: Sync[F]) = new ModuleDef {
  make[Validation[F]].from(new Validation[F] {
    def minSize(s: String, n: Int): F[Boolean] = F.delay(s.size >= n)
    def hasNumber(s: String): F[Boolean] = F.delay(s.exists(c => "0123456789".contains(c)))
  })
  make[Interaction[F]].from(new Interaction[F] {
    def tell(s: String): F[Unit] = F.delay(println(s))
    def ask(s: String): F[String] = F.delay("This could have been user input 1")
  })
}

def IOProgram = new Program[IO] ++ SyncInterpreters[IO]
```

We can leave it completely polymorphic:

```scala mdoc
def SyncProgram[F[_]: TagK: Sync] = new Program[F] ++ SyncInterpreters[F]
```

Or choose different interpreters at runtime:

```scala mdoc:invisible
import cats.instances.try_.catsStdInstancesForTry
```

```scala mdoc
def DifferentTryInterpreters = ???
def chooseInterpreters(default: Boolean) = {
  val interpreters = if (default) TryInterpreters else DifferentTryInterpreters
  new Program[Try] ++ interpreters
}
```

Modules can be polymorphic over arbitrary kinds - use `TagKK` to abstract over bifunctors:

```scala mdoc
class BifunctorIOModule[F[_, _]: TagKK] extends ModuleDef 
```

Or use `Tag.auto.T` to abstract over any kind:

```scala mdoc
class MonadTransModule[F[_[_], _]: Tag.auto.T] extends ModuleDef
```

```scala mdoc
class TrifunctorModule[F[_, _, _]: Tag.auto.T] extends ModuleDef
```

```scala mdoc
class EldritchModule[F[+_, -_[_, _], _[_[_, _], _], _]: Tag.auto.T] extends ModuleDef
```

consult @scaladoc[HKTag](izumi.fundamentals.reflection.WithTags#HKTag) docs for more details.

### Cats & ZIO Integration

Cats & ZIO instances and syntax are available automatically without imports, if `cats-core`, `cats-effect` or `zio` are
already dependencies of your project. (Note: distage *won't* bring `cats` or `zio` as a dependency if you don't already use them.
See [No More Orphans](https://blog.7mind.io/no-more-orphans.html) for description of the technique)

@ref[Cats Resource Bindings](basics.md#resource-bindings-lifecycle) will also work out of the box without any magic imports.

Example:

```scala
import cats.implicits._
import cats.effect._
import distage._
import com.example.{DBConnection, AppEntrypoint}

object Main extends IOApp {
  def run(args: List[String]): IO[Unit] = {
    // ModuleDef has a Monoid instance
    val myModules = module1 |+| module2
    
    for {
      // resolveImportsF can effectfully add missing instances to an existing plan
      // (You can create instances effectfully beforehand via `make[_].fromEffect` bindings)
      plan <- myModules.resolveImportsF[IO] {
        case i if i.target == DIKey.get[DBConnection] =>
           DBConnection.create[IO]
      } 
      // `produceF` specifies an Effect to run in; 
      // Effect used in Resource and Effect Bindings 
      // should match the effect in `produceF`
      classes <- Injector().produceF[IO](plan)
      _ <- classes.get[AppEntrypoint].run
    } yield ()
  }
}
```
