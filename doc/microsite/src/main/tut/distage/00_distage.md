---
out: index.html
---
distage Staged Dependency Injection
============

`distage` is a pragmatic module system for Scala that combines safety and reliability of pure FP with late binding and flexibility
of runtime dependency injection frameworks such as Guice.

### Hello World

Let's start with a simple Hello app:

```scala
import distage._

class Hello {
  def hello(name: String) = println(s"Hello $name!")
}

object HelloModule extends ModuleDef {
  make[Hello]
}

object Main extends App {
  val injector: Injector = Injector()
  val plan: OrderedPlan  = injector.plan(HelloModule)
  val classes: Locator   = injector.produce(plan)

  println("What's your name?")
  val name = readLine()
  
  classes.get[Hello].hello(name)
}
```

We'll break it down line-by-line:

```scala
object HelloModule extends ModuleDef {
  make[Hello]
}
```

Here we define a *Module* for our application. A module specifies *what* classes to instantiate and *how* to instantiate them.

By default, `distage` will just call the constructor.

If a class's constructor requires arguments, distage will first instantiate the arguments, then call the constructor with its dependencies fulfilled. 
All classes in distage are instantiated exactly once, even if multiple different classes depend on them, i.e. they're `Singletons`.
 
A module can be combined with another to produce a larger module via `++` and `overridenBy` operators. 
We can create a `ByeModule` and join it with our `HelloModule`:

```scala
object ByeModule extends ModuleDef {
  make[Bye]
}

class Bye {
  def bye(name: String) = println(s"Bye $name!")
}

val helloBye: Module = HelloModule ++ ByeModule
```

We can override the implementation of `Hello` class by overriding our `HelloModule` with an alternative implementation:

```scala
val uppercaseHello: Hello = 
  new Hello { 
    def hello(name: String) = s"HELLO ${name.toUpperCase}"
  }

object UppercaseHelloModule extends ModuleDef {
  make[Hello].from(uppercaseHello)  
}

val uppercaseHelloBye: Module = helloBye overridenBy uppercaseHello 
```

Combining modules with `++` is the main way to assemble your app together. For scenarios requiring extreme late-binding across multiple modules,
you can use the [Plugins](#plugins) mechanism to automatically discover modules from the classpath.

`distage` can check at compile-time if your app is wired correctly, If the modules typecheck, the app is guaranteed to start.
See [Compile-Time Checks](#compile-time-checks) for more details.

Let's go back to the code:

```scala
object Main extends App {
  val injector: Injector = Injector()
  val plan: OrderedPlan  = injector.plan(HelloModule)
```

Here we create an instantation `plan` from the module definition. distage is *staged*, so instead of instantiating our 
definitions right away, distage first builds a pure representation of all the operations it will do and returns it back to us.

This allows us to easily implement additional functionality on top of distage without modifying the library.
Features such as [Plugins](#plugins) and [Configurations](#config-files) are separate libraries, built on 
[transforming modules and plans](#import-materialization)

```scala
  val classes: Locator   = injector.produce(plan)

  println("What's your name?")
  val name = readLine()
  
  classes.get[Hello].hello(name)
```

Executing the plan yields us a `Locator`, that holds all the instantiated classes. This is where `distage` ends, and app's main logic begins.

Given a `Locator` we can retrieve instances by type, call methods on them or summon them with a function:

```scala
import scala.util.Random

classes.run {
  (hello: Hello, bye: Bye) =>
    val names = Array("Snow", "Marisa", "Shelby")
    val rnd = Random.nextInt(3)
    hello(names(rnd))
    bye(names(rnd))
}
```

```scala
classes.runOption {
  i: Int => i + 10
}.fold(println("I thought I had Int!"))(println(_))
```

### Provider Bindings

To bind a function instead of constructor use `.from` method in @scaladoc[ModuleDef](com.github.pshirshov.izumi.distage.model.definition.ModuleDef) DSL:

```scala
case class HostPort(host: String, port: Int)

class HttpServer(hostPort: HostPort)

trait HttpServerModule extends ModuleDef {
  make[HttpServer].from {
    hostPort: HostPort =>
      val modifiedPort = hostPort.port + 1000
      new HttpServer(hostPort.host, modifiedPort)
  }
}
```

To inject named instances or config values, add annotations to lambda arguments' types:

```scala
trait HostPortModule extends ModuleDef {
  make[HostPort].from {
    (configHost: String @ConfPath("http.host"), configPort: Int @ConfPath("http.port")) =>
      HostPort(configHost, configPort)
  }
}
```

For further details, see scaladoc for @scaladoc[ProviderMagnet](com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet)

### Multibindings / Set Bindings

Multibindings are useful for implementing event listeners, plugins, hooks, http routes, etc.

To define a multibinding use `.many` and `.add` methods in @scaladoc[ModuleDef](com.github.pshirshov.izumi.distage.model.definition.ModuleDef)
DSL:

```scala
import cats.effect._, org.http4s._, org.http4s.dsl.io._
import scala.concurrent.ExecutionContext.Implicits.global
import distage._

object HomeRouteModule extends ModuleDef {
  many[HttpRoutes[IO]].add {
    HttpRoutes.of[IO] { 
      case GET -> Root / "home" => Ok(s"Home page!") 
    }
  }
}
```

Multibindings defined in different modules will be merged together into a single Set.
You can summon a multibinding by type `Set[_]`:

```scala
import cats.implicits._, import org.http4s.server.blaze._, import org.http4s.implicits._

object BlogRouteModule extends ModuleDef {
  many[HttpRoutes[IO]].add {
    HttpRoutes.of[IO] { 
      case GET -> Root / "blog" / post => Ok("Blog post ``$post''!") 
    }
  }
}

class HttpServer(routes: Set[HttpRoutes[IO]]) {
  val router = routes.foldK

  def serve = BlazeBuilder[IO]
    .bindHttp(8080, "localhost")
    .mountService(router, "/")
    .start
}

val context = Injector().produce(HomeRouteModule ++ BlogRouteModule)
val server = context.get[HttpServer]

val testRouter = server.router.orNotFound

testRouter.run(Request[IO](uri = uri("/home"))).flatMap(_.as[String]).unsafeRunSync
// Home page!

testRouter.run(Request[IO](uri = uri("/blog/1"))).flatMap(_.as[String]).unsafeRunSync
// Blog post ``1''!
```

For further detail see [Guice wiki on Multibindings](https://github.com/google/guice/wiki/Multibindings).

### Tagless Final Style with distage

distage works well with tagless final style. As an example, let's take [freestyle's tagless example](http://frees.io/docs/core/handlers/#tagless-interpretation)
and make it safer and more flexible by replacing fragile wiring `import ._`'s with explicit modules.

First, the program we want to write:

```scala
import cats._
import cats.implicits._
import cats.tagless._

import distage._

class Program[F[_]: TagK: Monad] extends ModuleDef {
  make[TaglessProgram[F]]

  addImplicit[Monad[F]]
}

@finalAlg
trait Validation[F[_]] {
  def minSize(s: String, n: Int): F[Boolean]
  def hasNumber(s: String): F[Boolean]
}

@finalAlg
trait Interaction[F[_]] {
  def tell(msg: String): F[Unit]
  def ask(prompt: String): F[String]
}

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
```

@scaladoc[TagK](com.github.pshirshov.izumi.fundamentals.reflection.WithTags#TagK) is distage's analogue of `TypeTag` for higher-kinded types such as `F[_]`,
it allows preserving type-information at runtime for types that aren't yet known at definition.
You'll need to add a @scaladoc[TagK](com.github.pshirshov.izumi.fundamentals.reflection.WithTags#TagK) context bound to create a module parameterized by an abstract `F[_]`.
Use @scaladoc[Tag](com.github.pshirshov.izumi.fundamentals.reflection.WithTags#Tag) to create modules parameterized by non-higher-kineded types.

Interpreters:

```scala
import scala.util.Try

object TryInterpreters extends ModuleDef {
  make[Validation.Handler[Try]].from(tryValidationHandler)
  make[Interaction.Handler[Try]].from(tryInteractionHandler)
  
  def tryValidationHandler = new Validation.Handler[Try] {
    override def minSize(s: String, n: Int): Try[Boolean] = Try(s.size >= n)
    override def hasNumber(s: String): Try[Boolean] = Try(s.exists(c => "0123456789".contains(c)))
  }
  
  def tryInteractionHandler = new Interaction.Handler[Try] {
    override def tell(s: String): Try[Unit] = Try(println(s))
    override def ask(s: String): Try[String] = Try("This could have been user input 1")
  }
}

object App extends App {
  import cats.instances.try_._
  // Combine modules into a full program
  val TryProgram: Module = new Program[Try] ++ TryInterpreters
  // run
  Injector().produce(TryProgram).get[TaglessProgram[Try]]
    .program
}
```

The program module is polymorphic and abstracted from its eventual monad, we can easily parameterize it with a different monad:

```scala
import cats.effect.IO

def IOInterpreters = ???
val IOProgram = new Program[IO] ++ IOInterpreters
```

We can leave it polymorphic as well: 

```scala
import cats.effect.Sync

def SyncInterpreters[F[_]: Sync] = ???
def SyncProgram[F[_]: Sync] = new Program[F] ++ SyncInterpreters[F]
```

Or choose different interpreters at runtime:

```scala
def chooseInterpreters(default: Boolean) = new Program[Try] ++ (if (default) TryInterpreters else DifferentTryInterpreters)
```

Modules can abstract over arbitrary kinds - use `TagKK` to abstract over bifunctors:

```scala
class BIOModule[F[_, _]: TagKK] extends ModuleDef 
```

Use `Tag.auto.T` to abstract polymorphically over any kind:

```scala
class MonadTransModule[F[_[_], _]: Tag.auto.T] extends ModuleDef
```

```scala
class TrifunctorModule[F[_, _, _]: Tag.auto.T] extends ModuleDef
```

```scala
class EldritchModule[F[_, _[_, _], _[_[_, _], _], _, _[_[_[_]]], _, _]: Tag.auto.T] extends ModuleDef
```

consult @scaladoc[HKTag](com.github.pshirshov.izumi.fundamentals.reflection.WithTags.HKTag) docs for more details

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
Injector().produce(app).get[PetStoreController].run
```

Plugins also allow a program to extend itself at runtime by adding new `Plugin` classes to the classpath via `java -cp`

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

You can participate in this ticket at https://github.com/pshirshov/izumi-r2/issues/51

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

### Compile-Time Instantiation

@@@ warning { title='TODO' }
Sorry, this page is not ready yet
@@@

...


### Config files

`distage-config` library parses `typesafe-config` into arbitrary case classes or sealed traits and makes them available for summoning as a class dependency.

To use it, add `distage-config` library:

```scala
libraryDependencies += Izumi.R.distage_config
```
or

@@@vars
```scala
libraryDependencies += "com.github.pshirshov.izumi.r2" %% "distage-config" % "$izumi.version$"
```
@@@

If you're not using @ref[sbt-izumi-deps](../sbt/00_sbt.md#bills-of-materials) plugin.

Write a config in HOCON format:

```hocon
# resources/application.conf
program {
    config {
        different = true
    }
}
```

Add `ConfigModule` into your injector:

```scala
import distage.config._
import com.typesafe.config.ConfigFactory

val config = ConfigFactory.load()

val injector = Injector(new ConfigModule(AppConfig(config)))
```

Now you can automatically parse config entries into case classes and can summon them from any class:

```scala
final case class Config(different: Boolean)

class ConfiguredTaglessProgram[F](
  @ConfPath("program.config") config: Config,
  @Id("primary") primaryProgram: TaglessProgram[F],
  @Id("different") differentProgram: TaglessProgram[F]) {

    val program = if (config.different) differentProgram else primaryProgram
}

class ConfiguredTryProgram[F[_]: TagK: Monad] extends ModuleDef {
  make[ConfiguredProgram[F]]
  make[TaglessProgram[F]].named("primary")
  make[TaglessProgram[F]].named("different")
}
```

### Inner Classes and Path-Dependent Types

To instantiate path-dependent types via constructor, their prefix type has to be present in DI context:

```scala

trait Path {
  class A
  class B
}

val path = new Path {}

val module = new ModuleDef {
  make[path.A]
  make[path.type].from(path.type: path.type)
}
```

The same applies to type projections:

```scala
val module1 = new ModuleDef {
  make[Path#B]
  make[Path].from(new Path {})
}
```

Provider and instance bindings and also compile-time mode in `distage-static` module do not require the singleton type prefix to be present in DI context:

```scala
val module2 = new ModuleDef {
  make[Path#B].from {
    val path = new Path {}
    new path.B
  }
}
```

### Implicits Injection

@@@ warning { title='TODO' }
Sorry, this page is not ready yet

Relevant ticket: https://github.com/pshirshov/izumi-r2/issues/230
@@@

Implicits are managed like any other class, to make them available for summoning, declare them inside a module:

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

Implicits for wired classes are injected from DI context, not from surrounding lexical scope.
If they were captured from lexical scope when the module is defined, then any binding of a class that depends on an
implicit, would have to import an *implementation* of that implicit. 
Depending on implementations is unmodular and directly contradicts the idea of using a dedicated module system
in the first place:
 
```scala
import cats._
import distage._

class KVStore[F[_]: Monad, V] {
  def fetch(key: String): F[V]
}

val kvstoreModule = new ModuleDef {
  // We DON'T want this import to be necessary
  // import cats.instances.either._

  make[KVStore[Either[Error, ?]].from[KVStoreEitherImpl]
}

// Instead, specify implicits explicitly
val eitherMonadModule = new ModuleDef {
  // Ok to import here
  import cats.instances.either._
  
  addImplicit[Monad[Either[Error, ?]]]
}

val all = kvstoreMOdule ++ eitherMonadModule
```

Implicits obey the usual lexical scope outside of modules managed by `distage`.

You can participate in this ticket at https://github.com/pshirshov/izumi-r2/issues/230

### Monadic effects instantiation

@@@ warning { title='TODO' }
Sorry, this page is not ready yet

Relevant ticket: https://github.com/pshirshov/izumi-r2/issues/331
@@@

Example of explicitly splitting effectful and pure instantiations:

```scala
import cats._
import cats.implicits._
import distage._
import distage.config._
import com.typesafe.config.ConfigFactory

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.global

case class DbConf()
case class MsgQueueConf()
case class RegistryConf()

class DBService[F[_]]
class MsgQueueService[F[_]]
class RegistryService[F[_]]

class DomainService[F[_]: Monad]
( dbService: DBService[F]
, msgQueueService: MsgQueueService[F]
, registryService: RegistryService[F]
) {
  def run: F[Unit] = Monad[F].unit
}

class ExternalInitializers[F[_]: TagK: Monad] extends ModuleDef {
  make[F[DBService[F]]].from { 
    dbConf: DbConf @ConfPath("network-service.db") => new DBService[F]().pure[F] 
  }
  make[F[MsgQueueService[F]]].from { 
    msgQueueConf: MsgQueueConf @ConfPath("network-service.msg-queue") => new MsgQueueService[F]().pure[F] 
  }
  make[F[RegistryService[F]]].from { 
    registryConf: RegistryConf @ConfPath("network-service.registry") => new RegistryService[F]().pure[F]
  }
}

val injector = Injector(new ConfigModule(AppConfig(ConfigFactory.load())))
val monadicActionsLocator = injector.produce(new ExternalInitializers[Future])

val main: Future[Unit] = monadicActionsLocator.run {
  ( dbF: Future[DBService[Future]]
  , msgF: Future[MsgQueueService[Future]]
  , regF: Future[RegistryService[Future]]
  ) => for {
    db <- dbF
    msg <- msgF
    reg <- regF

    externalServicesModule = new ModuleDef {
      make[DBService[Future]].from(db)
      make[MsgQueueService[Future]].from(msg)
      make[RegistryService[Future]].from(reg)
    }

    domainServiceModule = new ModuleDef {
      make[DomainService[Future]]
    }

    allServices = injector.produce(externalServicesModule ++ domainServiceModule)

    _ <- allServices.get[DomainService[Future]].run
  } yield ()
}

Await.result(main, Duration.Inf)
```

Side-effecting instantiation is not recommended in general – ideally, resources and lifetimes should be managed outside of `distage`. A rule of thumb is:
if a class and its dependencies are stateless, and can be replaced by a global `object`, it's ok to inject them with  `distage`.
However, `distage` does provide helpers to help manage lifecycle, see: [Auto-Sets, closing all AutoCloseables](#auto-sets-collecting-bindings-by-predicate)

You can participate in this ticket at https://github.com/pshirshov/izumi-r2/issues/331

### Auto-Traits & Auto-Factories

@@@ warning { title='TODO' }
Sorry, this page is not ready yet
@@@

...

### Import Materialization

@@@ warning { title='TODO' }
Sorry, this page is not ready yet
@@@

...

### Depending on Locator

Classes can depend on the Locator:

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

It's recommended to avoid this if possible, doing so is often a sign of broken application design.

### Ensuring service boundaries using API modules

...

### Roles 

@@@ warning { title='TODO' }
Sorry, this page is not ready yet
@@@

"Roles" are a pattern of multi-tenant applications, in which multiple separate microservices all reside within a single `.jar`.
This strategy helps cut down development, maintenance and operations costs associated with maintaining fully separate code bases and binaries.
The apps that should be launched are chosen by command-line parameters: `./launcher app1 app2 app3`. When launching less apps
than are available in the launcher - `./launcher app1`, redundant components will be collected by the [garbage collector](#using-garbage-collector)
and won't be started.

Roles: a viable alternative to Microservices:

https://github.com/7mind/slides/blob/master/02-roles/target/roles.pdf

### Test Kit

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

```scala
import distage._
import com.github.pshirshov.izumi.distage.testkit.DistageSpec

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
```

### Using Garbage Collector

A garbage collector is included in `distage`, but has to be enabled explicitly:

```scala
import distage._

class Main

// Designate `Main` class as the garbage collection root
val roots = Seq(DIKey.get[Main])

// Enable GC
val gcModule = new TracingGCModule(roots)
val injector = Injector(gcModule)
```

GC will remove all bindings that aren't transitive dependencies of the chosen `GC root` keys from the plan - they will never be instantiated.

In the following example:

```scala
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

val roots = Seq(DIKey.get[A])

val locator = Injector(new TracingGCModule(roots)).produce(module)

locator.find[A]
// res0: Option[A] = Some(A(B()))
locator.find[B]
// res1: Option[B] = Some(B())
locator.find[C]
// res2: Option[C] = None
```

Class `C` is removed because it wasn't dependent on by classes `B` or `A`. It's not present in the `Locator` and the `"C!"` message was never printed.
If class `B` were to depend on `C` as in `case class B(c: C)`, it would've been retained, because `A` which is the GC root, would've depended on `B` which in turns depends on `C`.

GC serves two important purposes:
* it enables faster [tests](#test-kit) by omitting unneeded instantiations,
* and it enables multiple separate applications, "[Roles](#roles)" to be hosted within a single `.jar`.

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

### Auto-Sets: Collecting Bindings By Predicate

AutoSet @scaladoc[Planner](com.github.pshirshov.izumi.distage.model.Planner) Hooks traverse the class graph and collect all classes matching a predicate.

Using Auto-Sets, one can, for example, collect all `AutoCloseable` classes and `.close()` them after the application has finished work.

Note: it's not generally recommended to construct stateful, effectful or resource-allocating classes with `distage`, a general rule of thumb is:
if a class and its dependencies are stateless and can be replaced by a global `object`, it's ok to inject them with  `distage`. However, an example is given anyway,
as a lot of real applications depend on global resources, such as JDBC connections, `ExecutionContext` thread pools, Akka Systems, etc. that should 
be closed properly at exit.

```scala
import java.util.concurrent.{ExecutorService, Executors}

import com.github.pshirshov.izumi.distage.model.planning.PlanningHook
import com.github.pshirshov.izumi.distage.planning.AssignableFromAutoSetHook
import distage._

val threadPoolModule = new ModuleDef {
  make[ExecutorService].from(Executors.newWorkStealingPool())
  make[ExecutionContext].from {
    es: ExecutorService =>
      ExecutionContext.fromExecutorService(es)
  }
}

// A hook that collects every instance of a subtype of a given type
val collectAllExecutorServicesHook = new AssignableFromAutoSetHook[ExecutorService]

val bootstrapModule = new BootstrapModuleDef {
  many[PlanningHook]
    .add(collectAllExecutorServicesHook)
}

val injector = Injector(bootstrapModule)

val locator = injector.produce(threadPoolModule)

try {
  // run the app
  ???
} finally {
  // when done, close thread pools
  val allExecutors = locator.get[Set[ExecutorService]]
  // Auto-set return ordered Set, in order of dependency
  // To start resources, execute actions in order
  // To close resources, execute actions in *reverse* order
  allExecutors.reverse.foreach(_.shutdownNow())
}
```

Auto-Sets preserve ordering, they use `ListSet` under the hood, unlike user-defined [Sets](#multibindings--set-bindings).
Calling `.foreach` on an auto-set is safe; the actions will be executed in order of dependencies:
i.e. if C depends on B depends on A, autoset order is: `A, B, C`, to start: `A -> B -> C`, to close: `C -> B -> A`
When you use auto-sets for finalization, you **must** `.reverse` the autoset

```scala
trait PrintResource(name: String) {
  def start(): Unit = println(s"$name started")
  def stop(): Unit = println(s"$name stopped")
}
class A extends Resource("A")
class B(val a: A) extends Resource("B")
class C(val b: B) extends Resource("C")

val resources = Injector(new BootstraModuleDef {
  many[PlanningHook]
    .add(new AssignableFromAutoSetHook[Resource])
}).produce(new ModuleDef {
  make[C]
  make[B]
  make[A]
}).get[Set[Resource]]

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

NB: Auto-Sets are NOT subject to [Garbage Collection](#using-garbage-collector), they are assembled *after* garbage collection is done,
as such they can't contain garbage by construction.

#### Weak Sets

Sets (aka [Multibindings](#multibindings--set-bindings)) can contain *weak* references. Elements designated as weak will not
be retained by the [Garbage Collector](#using-garbage-collector) if they are not
referenced outside of the set.

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
val roots = Seq(DIKey.get[Set[SetElem]])

// Enable GC
val gcModule = new TracingGCModule(roots)
val injector = Injector(gcModule)

val locator = injector.produce(roots)
// Strong constructed!

assert(locator.get[Set[SetElem]].size == 1) 
```

The `Weak` class was not required in any dependency of `Set[SetElem]`, so it was pruned.
The `Strong` class remained, because the reference to it was **strong**, therefore it was counted as a dependency of `Set[SetElem]`

Had we changed `Strong` to depend on the `Weak`, the `Weak` would be retained:

```scala
final class Strong(weak: Weak) {
  println("Strong constructed")
}

assert(locator.get[Set[SetElem]].size == 2)
```

### Debugging, Introspection, Diagnostics and Hooks

You can print a `plan` to get detailed info on what will happen during instantiation. The printout includes file:line info 
so your IDE can show you where the binding was defined! 

```scala
System.err.println(plan: OrderedPlan)
```

![print-test-plan](media/print-test-plan.png)

You can also query a plan to see the dependencies and reverse dependencies of a class and their instantiation:

```scala
// Print dependencies
System.err.println(plan.topology.dependencies.tree(DIKey.get[Circular1]))
// Print reverse dependencies
System.err.println(plan.topology.dependees.tree(DIKey.get[Circular1]))
```

![print-dependencies](media/print-dependencies.png)

The printer highlights circular dependencies.

Distage also uses some macros, macros are currently used to create `TagK`s and [provider bindings](#provider-bindings).
If you think they've gone awry, you can turn macro debug output during compilation by setting `-Dizumi.distage.debug.macro=true` java property:

```bash
sbt -Dizumi.distage.debug.macro=true compile
```

Macros power `distage-static` module, an alternative backend that does not use JVM runtime reflection to instantiate classes and auto-traits.

### Extensions and Plan Rewriting - writing a distage extension

...

### Migrating from Guice

...

### Migrating from MacWire

...

### Integrations

...

### Cats

To import cats integration add distage-cats library:

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
      classes <- Injector().produceIO[IO](plan) // produceIO is now available
      _ <- classes.get[AppEntrypoint].run
    } yield ()
  }
}
```

### Scalaz

### ZIO

### Freestyle

### Eff

## PPER

See @ref[PPER Overview](../pper/00_pper.md)
