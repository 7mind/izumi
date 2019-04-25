
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
import distage.{ModuleDef, Injector}

object HelloByeModule extends ModuleDef {
  make[Greeter].from[PrintGreeter]
  make[Byer].from[PrintByer]
  make[HelloByeApp]
}
```

Since `HelloByeApp` is not an interface, but a `final class` that implements itself, we don't have to specify an implementation class for it.

Let's launch the app and see what happens:

```scala mdoc:invisible
// A hack because `tut` REPL fails to create classes via reflection when defined in REPL for some reason..
// Also, we want to override HelloByeApp to not use stdin
object HACK_OVERRIDE_HelloByeModule extends ModuleDef {
  make[Greeter].from(new PrintGreeter)
  make[Byer].from(new PrintByer)
  make[HelloByeApp].from(new HelloByeApp(_, _))
}
```

```scala mdoc:override:silent
val injector = Injector()

val plan = injector.plan(HACK_OVERRIDE_HelloByeModule)
val objects = injector.produceUnsafe(plan)

val app = objects.get[HelloByeApp]
```

```scala mdoc
app.run()
```

Given a set of bindings, such as `HelloByeModule`, `distage` will lookup the dependencies (constructor or function arguments)
of each implementation and deduce a `plan` to satisfy each dependency using the other implementations in that module.
Once finished, it will happily return the `plan` back to you as a simple datatype.
We can print `HelloByeModule`'s plan while we're at it:

```scala mdoc:invisible
val HACK_OVERRIDE_plan = injector.plan(HelloByeModule)
```

```scala mdoc:override
println(HACK_OVERRIDE_plan.render)
```

Since `plan` is just a piece of data, we need to interpret it to create the actual object graph –
`Injector`'s `produce` method is the default interpreter. `Injector` contains no logic of its own beyond interpreting
instructions, its output is fully determined by the plan. This makes @ref[debugging](debugging.md#debugging) quite easy.

Given that plans are data, it's possible to @ref[verify them at compile-time](other-features.md#compile-time-checks) or @ref[splice equivalent Scala code](other-features.md#compile-time-instantiation)
to do the instantiation before ever running the application. When used in that way,
`distage` is a great alternative to compile-time frameworks such as `MacWire` all the while keeping the flexibility to interpret
at runtime when needed. This flexibility in interpretation allows adding features, such as @ref[Plugins](other-features.md#plugins) and
@ref[Typesafe Config integration](config_injection.md#config-injection) by transforming plans and bindings.

Note: classes in `distage` are always created exactly once, even if many different classes depend on them - they're `Singletons`.
Non-singleton semantics are not available, however you can create multiple `named` instances and disambiguate between them with `@Id` annotation:

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

You can also create factory classes to help you mint new non-singleton instances. [Auto-Factories](#auto-factories) can reduce boilerplate involved in doing this.

Modules can be combined into larger modules with `++` and `overridenBy` operators. Let's use `overridenBy` to greet in ALL CAPS:

```scala mdoc:override:silent
val caps = HACK_OVERRIDE_HelloByeModule.overridenBy(new ModuleDef {
  make[Greeter].from(new Greeter {
    override def hello(name: String) = println(s"HELLO ${name.toUpperCase}")
  })
})

val capsUniverse = injector.produceUnsafe(caps)
```

```scala mdoc
capsUniverse.get[HelloByeApp].run()
```

We've overriden the `Greeter` binding in `HelloByeModule` with an implementation of `Greeter` that prints in ALL CAPS.
For simple cases like this we can write implementations right inside the module.

### Function Bindings

To bind to a function instead of a class constructor use `.from` method in @scaladoc[ModuleDef](com.github.pshirshov.izumi.distage.model.definition.ModuleDef) DSL:

```scala mdoc:reset
import distage._

case class HostPort(host: String, port: Int)

class HttpServer(hostPort: HostPort)

object HttpServerModule extends ModuleDef {
  make[HttpServer].from {
    hostPort: HostPort =>
      val modifiedPort = hostPort.port + 1000
      new HttpServer(hostPort.copy(port = modifiedPort))
  }
}
```

To inject named instances or @ref[config values](config_injection.md#config-injection), add annotations such as `@Id` and `@ConfPath` to lambda arguments' types:

```scala mdoc
import distage.config._

object HostPortModule extends ModuleDef {
  make[HostPort].named("default").from(HostPort("localhost", 8080))
  make[HostPort].from {
    (maybeConfigHostPort: Option[HostPort] @ConfPath("http"),
     defaultHostPort: HostPort @Id("default")) =>
      maybeConfigHostPort.getOrElse(defaultHostPort)
  }
}
```

Given a `Locator` we can retrieve instances by type, call methods on them or summon them with a function:

```scala mdoc:invisible:reset
import distage._

class Hello {
  def apply(name: String): Unit = println(s"Hello $name")
}

class Bye {
  def apply(name: String): Unit = println(s"Bye $name")
}

object HelloByeModule extends ModuleDef {
  make[Hello].from(new Hello)
  make[Bye].from(new Bye)
}
```

```scala mdoc
import scala.util.Random

val objects = Injector().produceUnsafe(HelloByeModule)

objects.run {
  (hello: Hello, bye: Bye) =>
    val names = Array("Snow", "Marisa", "Shelby")
    val rnd = Random.nextInt(3)
    println(s"Random index: $rnd")
    hello(names(rnd))
    bye(names(rnd))
}
```

```scala mdoc
objects.runOption { i: Int => i + 10 } match {
  case None => println("There is no Int in the object graph!")
  case Some(i) => println(s"Int is $i")
}
```

consult @scaladoc[ProviderMagnet](com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet) docs for more details.

### Set Bindings

Set bindings are useful for implementing event listeners, plugins, hooks, http routes, healthchecks, migrations, etc. Everywhere where
you need to gather up a bunch of similar components is probably a good place for a Set Binding.

To define a Set binding use `.many` and `.add` methods in @scaladoc[ModuleDef](com.github.pshirshov.izumi.distage.model.definition.ModuleDef)
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

```scala mdoc:invisible
// A hack because `tut` REPL fails to create classes via reflection when classes are defined in REPL...
object HACK_OVERRIDE_HttpServerModule extends ModuleDef {
  make[HttpServer].from(new HttpServer(_))
}
```

```scala mdoc:override:silent
val finalModule = Seq(
    HomeRouteModule,
    BlogRouteModule,
    HACK_OVERRIDE_HttpServerModule,
  ).merge

val objects = Injector().produceUnsafe(finalModule)

val server = objects.get[HttpServer]
```

Let's check if it works:

```scala mdoc
server.router.run(Request(uri = uri("/home")))
  .flatMap(_.as[String]).unsafeRunSync
```

```scala mdoc
server.router.run(Request(uri = uri("/blog/1")))
  .flatMap(_.as[String]).unsafeRunSync
```

Fantastic!

consult [Guice wiki on Multibindings](https://github.com/google/guice/wiki/Multibindings) for more details about the concept.

### Injecting Implicits

@@@ warning { title='TODO' }
Sorry, this page is not ready yet

Relevant ticket: https://github.com/7mind/izumi/issues/230
@@@

Implicits are managed like any other class. To make them available for summoning, declare them in a module:

```scala
import cats.Monad
import distage._
import scalaz.zio.IO
import scalaz.zio.interop.catz._

object IOMonad extends ModuleDef {
  addImplicit[Monad[IO[Throwable, ?]]]
  // same as make[Monad[IO[Throwable, ?]]].from(implicitly[Monad[IO[Throwable, ?]]])
}
```

Implicits for managed classes are injected from the object graph, NOT from the surrounding lexical scope.
If they were captured from lexical scope inside `ModuleDef`, then classes would effectively depend on specific 
*implementations* of implicits available in scope at `ModuleDef` definition point.
Depending on implementations is unmodular! We want to late-bind implicit dependencies same as any other dependencies,
therefore you must specify implementations for implicits in `ModuleDef`.

```scala
import cats._
import distage._

trait KVStore[F[_]] {
  def fetch(key: String): F[String]
}

final class KVStoreEitherImpl(implicit F: MonadError[Either[Error, ?], Error]) extends KVStore[Either[Error, ?]] {
  def fetch(key: String) = F.raiseError(new Error(s"no value for key $key!"))
}

val kvstoreModuleBad = new ModuleDef {
  // We DON'T want this import to be necessary here
  // import cats.instances.either._

  make[KVStore[Either[Error, ?]]].from[KVStoreEitherImpl]
}

// Instead, wire implicits explicitly
val kvstoreModuleGood = new ModuleDef {

  make[KVStore[Either[Error, ?]]].from[KVStoreEitherImpl]
  
  // Ok to import here
  import cats.instances.either._
  
  // add the implicit dependency into the object graph
  addImplicit[MonadError[Either[Error, ?], Error]]
  
}
```

Implicits obey the usual lexical scope in user code.

You can participate in this ticket at https://github.com/7mind/izumi/issues/230

### Resource Bindings & Lifecycle

Lifecycle is supported via Resource bindings.
You can inject any [cats.effect.Resource](https://typelevel.org/cats-effect/datatypes/resource.html) into the object graph.
You can also inject @scaladoc[DIResource](com.github.pshirshov.izumi.distage.model.definition.DIResource) classes.
Global resources will be deallocated when the app or the test ends.

Note that lifecycle control via `DIResource` is available in non-FP applications as well via inheritance from `DIResource.Simple` and `DIResource.Mutable`.

Example with `cats` Resource:

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

```scala mdoc:invisible
// cats conversions cause a reflection exception in mdoc
val HACK_OVERRIDE_dbResource = DIResource.make(
  acquire = IO { println("Connecting to DB!"); new DBConnection }
)(release = _ => IO(println("Disconnecting DB")))

val HACK_OVERRIDE_mqResource = DIResource.make(
  acquire = IO { println("Connecting to Message Queue!"); new MessageQueueConnection }
)(release = _ => IO(println("Disconnecting Message Queue")))

val HACK_OVERRIDE_module = new ModuleDef {
  make[DBConnection].fromResource(HACK_OVERRIDE_dbResource)
  make[MessageQueueConnection].fromResource(HACK_OVERRIDE_mqResource)
  addImplicit[Bracket[IO, Throwable]]
  make[MyApp].from(new MyApp(_, _))
}
```

Will produce the following output:

```scala mdoc:override
Injector().produceF[IO](HACK_OVERRIDE_module).use {
  objects =>
    objects.get[MyApp].run
}.unsafeRunSync()
```

Example with `DIResource.Simple`:

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
```

```scala mdoc:invisible
val HACK_OVERRIDE_module = new ModuleDef {
  make[Init].fromResource(new InitResource)
}
```

```scala mdoc:override
val closedInit = Injector().produce(HACK_OVERRIDE_module).use {
  objects =>
    val init = objects.get[Init] 
    println(init.initialized)
    init
}
println(closedInit.initialized)
```

`DIResource` forms a monad and has the expected `.map`, `.flatMap`, `.evalMap` methods available. You can convert a `DIResource` into a `cats.effect.Resource` via `.toCats` method.

You need to use resource-aware `Injector.produce`/`Injector.produceF` methods to control lifecycle of the object graph.

### Effect Bindings

Sometimes we need to effectfully create a component or fetch some data and inject it into the object graph during startup (e.g. read a configuration file),
but the resulting component or data does not need to be closed. An example might be a global `Semaphore` that limits the parallelism of an
entire application based on configuration value or a `dummy`/`test double` implementation of some external service made for testing using simple `Ref`s.

In these cases we can use `.fromEffect` to simply bind a value created effectfully.

Example with `ZIO` `Semaphore`:

```scala mdoc:reset-class
import distage._
import distage.config._
import scalaz.zio._

case class Content(bytes: Array[Byte])

case class UploadConfig(maxParallelUploads: Long)

class UploaderModule extends ModuleDef {
  make[Semaphore].named("upload-limit").fromEffect {
    conf: UploadConfig @ConfPath("myapp.uploads") =>
      Semaphore.make(conf.maxParallelUploads)
  }
  
  make[Uploader]
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
        ref.update(_ + (key -> value)).void
      
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

new RTS{}.unsafeRun {
  Injector().produceF[IO[Throwable, ?]](kvStoreModule)
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

### Tagless Final Style

Tagless Final is one of the popular patterns for structuring purely-functional applications. If you're not familiar with tagless final you can skip this section.

Brief introduction to tagless final:

- [Deferring Commitments: Tagless Final](https://medium.com/@calvin.l.fer/deferring-commitments-tagless-final-704d768f15cb)
- [Introduction to Tagless Final](https://www.beyondthelines.net/programming/introduction-to-tagless-final/)

Advantages of `distage` as a driver for TF compared to implicits:

- easy explicit overrides
- easy @ref[effectful instantiation](#effect-bindings) and @ref[resource management](#resource-bindings--lifecycle)
- extremely easy & scalable [test](#testkit) context setup due to the above
- multiple different implementations via `@Id` annotation

As an example, let's take [freestyle's tagless example](http://frees.io/docs/core/handlers/#tagless-interpretation)
and make it safer and more flexible by replacing dependencies on global `import`ed implementations from with explicit modules.

First, the program we want to write:

```scala mdoc:reset:silent
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

@scaladoc[TagK](com.github.pshirshov.izumi.fundamentals.reflection.WithTags#TagK) is distage's analogue of `TypeTag` for higher-kinded types such as `F[_]`,
it allows preserving type-information at runtime for types that aren't yet known at definition.
You'll need to add a @scaladoc[TagK](com.github.pshirshov.izumi.fundamentals.reflection.WithTags#TagK) context bound to create a module parameterized by an abstract `F[_]`.
Use @scaladoc[Tag](com.github.pshirshov.izumi.fundamentals.reflection.WithTags#Tag) to create modules parameterized by non-higher-kinded types.

Interpreters:

```scala mdoc:invisible
class HACK_OVERRIDE_Program[F[_]: TagK: Monad] extends ModuleDef {
  make[TaglessProgram[F]].from(new TaglessProgram()(_: Monad[F], _: Validation[F], _: Interaction[F]))

  addImplicit[Monad[F]]
}
```

```scala mdoc:invisible
import cats.instances.try_.catsStdInstancesForTry
```

```scala mdoc:override
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
val TryProgram = new HACK_OVERRIDE_Program[Try] ++ TryInterpreters

// create object graph
val objects = Injector().produceUnsafe(TryProgram)

// run
objects.get[TaglessProgram[Try]].program
```

The program module is polymorphic over its eventual monad, we can easily parameterize it with a different monad:

```scala mdoc:override
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

def IOProgram = new HACK_OVERRIDE_Program[IO] ++ SyncInterpreters[IO]
```

We can leave it completely polymorphic as well:

```scala mdoc:override
def SyncProgram[F[_]: TagK: Sync] = new HACK_OVERRIDE_Program[F] ++ SyncInterpreters[F]
```

Or choose different interpreters at runtime:

```scala mdoc:invisible
import cats.instances.try_.catsStdInstancesForTry
```

```scala mdoc:override
def DifferentTryInterpreters = ???
def chooseInterpreters(default: Boolean) = {
  val interpreters = if (default) TryInterpreters else DifferentTryInterpreters
  new HACK_OVERRIDE_Program[Try] ++ interpreters
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

consult @scaladoc[HKTag](com.github.pshirshov.izumi.fundamentals.reflection.WithTags#HKTag) docs for more details.

### Testkit

`distage-testkit` module provides integration with `scalatest`:

```scala
libraryDependencies += Izumi.R.distage_testkit
```

or

@@@vars

```scala
libraryDependencies += "com.github.pshirshov.izumi.r2" %% "distage-plugins" % "$izumi.version$"
```

@@@

If you're not using @ref[sbt-izumi-deps](../sbt/00_sbt.md#bills-of-materials) plugin.

Example usage:

```scala mdoc
/*
TODO: update doc
import distage._
import com.github.pshirshov.izumi.distage.testkit.legacy.DistageSpec

class TestClass {
  def hello: String = "Hello World!"
}

class Test extends DistageSpec {
  override protected def makeBindings: ModuleBase = new ModuleDef {
    make[TestClass]
  }

  "TestClass" should {

    "Say hello" in di {
      testClass: TestClass =>
        assert(testClass.hello == "Hello World!")
    }

  }
}
*/
```
