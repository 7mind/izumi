---
out: index.html
---

distage Staged Dependency Injection
===================================

`distage` is a pragmatic module system for Scala that combines simplicity and reliability of pure FP with extreme late-binding
and flexibility of runtime dependency injection frameworks such as Guice.

`distage` is unopinionated, it's good for structuring applications written in either imperative Scala as in Akka/Play,
or in [Tagless Final Style](#tagless-final-style) or with [ZIO Environment](#zio).

More info:

- Slides: [distage: Purely Functional Staged Dependency Injection](https://www.slideshare.net/7mind/distage-purely-functional-staged-dependency-injection-bonus-faking-kind-polymorphism-in-scala-2)
- @ref[Motivation for DI frameworks](motivation.md)

### Tutorial

Suppose we want to create an abstract `Greeter` component that we want to use without knowing its concrete implementation:

scala mdoc:reset-class

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
def readLine() = "kai"
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
  // make[App0.this.type].from[App0.this.type](App0.this: App0.this.type)
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

What just happened:

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

Since plans are just data, we need to interpret them to create the actual object graph –
`Injector`'s `produce` method provides the default interpreter. Injector contains no logic of its own beyond interpreting
instructions, its output is fully determined by the plan. This makes [debugging](#debugging-introspection-diagnostics-and-hooks) quite easy.

Given that plans are data, it's possible to [verify them at compile-time](#compile-time-checks) or [splice equivalent Scala code](#compile-time-instantiation)
to do the instantiation before ever running the application. When used in that way,
`distage` is a great alternative to compile-time frameworks such as `MacWire` all the while keeping the flexibility to interpret
at runtime when needed. This flexibility allows adding features, such as [Plugins](#plugins) and
[Typesafe Config integration](#config-files) by transforming plans and bindings.

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

We've overriden the `Greeter` binding in `HelloByeModule` with a new implementation of `Greeter` that prints in ALL CAPS.
For simple cases like this we can write implementations in-place.

### ModuleDef Syntax

DSL for defining bindings from interface to implementation

Basic operators:

Polymorphism:


### Set Bindings / Multibindings

Set bindings are useful for implementing event listeners, plugins, hooks, http routes, etc.

To define a Set binding use `.many` and `.add` methods in @scaladoc[ModuleDef](com.github.pshirshov.izumi.distage.model.definition.ModuleDef)
DSL.

For example, we can serve all [http4s](https://http4s.org) routes added in multiple different modules:

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

Now it's the time to define a `Server` component to serve all the routes we've got now:

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

But does it work?

```scala mdoc
server.router.run(Request(uri = uri("/home")))
  .flatMap(_.as[String]).unsafeRunSync
```

```scala mdoc
server.router.run(Request(uri = uri("/blog/1")))
  .flatMap(_.as[String]).unsafeRunSync
```

Great!

See [Guice wiki on Multibindings](https://github.com/google/guice/wiki/Multibindings) for more on the concept.

### Tagless Final Style

distage works well with tagless final style. As an example, let's take [freestyle's tagless example](http://frees.io/docs/core/handlers/#tagless-interpretation)
and make it safer and more flexible by replacing dependencies on global implementations from `import`'s with explicit modules.

Tagless Final is a pattern of definition allowing us to defnie pure programs

- preliminary https://medium.com/@calvin.l.fer/deferring-commitments-tagless-final-704d768f15cb
- more at bwhatever article https://www.beyondthelines.net/programming/introduction-to-tagless-final/

If you're not familiar with tagless final you can skip this section

these are the benefits distage brings as TF driver compared to implicits

- explicit easy overrides
- <s>easy effectful instantiation and resource management</s>
- multiple different implementations via Id
- by contrast, implicit domain will be consistent

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

consult @scaladoc[HKTag](com.github.pshirshov.izumi.fundamentals.reflection.WithTags#HKTag) docs for more details

### Function Bindings

To bind to a function instead of constructor use `.from` method in @scaladoc[ModuleDef](com.github.pshirshov.izumi.distage.model.definition.ModuleDef) DSL:

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

To inject named instances or config values, add annotations to lambda arguments' types:

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

```scala mdoc:invisible
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

For further details, see @scaladoc[ProviderMagnet](com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet)

### Config files

`distage-config` library parses `typesafe-config` into arbitrary case classes or sealed traits and makes them available
for summoning as a class dependency.

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
  config: Config @ConfPath("program.config"),
  primaryProgram: TaglessProgram[F] @Id("primary"),
  differentProgram: TaglessProgram[F] @Id("different") ) {

    val program = if (config.different) differentProgram else primaryProgram
}

class ConfiguredTryProgram[F[_]: TagK: Monad] extends ModuleDef {
  make[ConfiguredProgram[F]]
  make[TaglessProgram[F]].named("primary")
  make[TaglessProgram[F]].named("different")
}
```

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
Injector().produce(app).use {
  _.get[PetStoreController].run
}
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

### Auto-Traits

@@@ warning { title='TODO' }
Sorry, this page is not ready yet
@@@

...

### Auto-Factories

`distage` can automatically create `Factory` classes from suitable traits.
This feature is especially useful for `Akka`.

Given a class `ActorFactory`:

```scala
class UserActor(sessionId: UUID, sessionRepo: SessionRepo)

trait ActorFactory {
  def createActor(sessionId: UUID): UserActor
}
```

And a binding of `ActorFactory` *without* an implementation

```scala
class ActorModule extends ModuleDef {
  make[ActorFactory]
}
```

`distage` will derive and bind the following implementation for `ActorFactory`:

```scala
class ActorFactoryImpl(sessionRepo: SessionRepo) extends ActorFactory {
  override def createActor(sessionId: UUID): UserActor = {
    new UserActor(sessionId, sessionRepo)
  }
}
```

You can use this feature to concisely provide non-singleton semantics for some of your components.

By default, the factory implementation class will be created automatically at runtime.
To create factories at compile-time use `distage-static` module.

### Compile-Time Instantiation

@@@ warning { title='TODO' }
Sorry, this page is not ready yet

Relevant ticket: https://github.com/pshirshov/izumi-r2/issues/453
@@@

WIP

You can participate in this ticket at https://github.com/pshirshov/izumi-r2/issues/453

### Inner Classes and Path-Dependent Types

To instantiate path-dependent types via constructor, their prefix type has to be present in DI object graph:

```scala mdoc:reset
import distage._

trait Path {
  class A
  class B
}

val path = new Path {}

val module = new ModuleDef {
  make[path.A]
  make[path.type].from[path.type](path: path.type)
}
```

The same applies to type projections:

```scala mdoc
val module1 = new ModuleDef {
  make[Path#B]
  make[Path].from(new Path {})
}
```

Provider and instance bindings and also compile-time mode in `distage-static` module do not require the singleton type prefix to be present in DI object graph:

```scala mdoc
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

Implicits for managed classes are injected from the object graph, not from the surrounding lexical scope.
If they were captured from lexical scope at the time of binding definition, then classes would effectively depend on a
specific *implementation* of implicits.
Depending on implementations is unmodular and directly contradicts the idea of using a dedicated module system in the first place,
therefore you're encouraged to wire implicits with their implementations in your of module definitions.

```scala
import cats._
import distage._

abstract class KVStore[F[_]](implicit M: Monad[F]) {
  def fetch(key: String): F[String]
}

val kvstoreModuleBad = new ModuleDef {
  // We DON'T want this import to be necessary here
  // import cats.instances.either._

  make[KVStore[Either[Error, ?]]].from[KVStoreEitherImpl]
}

// Instead, wire implicits explicitly
val kvstoreModuleGood = new ModuleDef {
  // Ok to import here
  import cats.instances.either._
  
  addImplicit[Monad[Either[Error, ?]]]
  
  make[KVStore[Either[Error, ?]]].from[KVStoreEitherImpl]
}
```

Implicits obey the usual lexical scope in user code.

You can participate in this ticket at https://github.com/pshirshov/izumi-r2/issues/230

### Monadic effects instantiation

@@@ warning { title='TODO' }
Sorry, this page is not ready yet

Relevant ticket: https://github.com/pshirshov/izumi-r2/issues/331
@@@

FIXME: WRITE .fromEffect doc

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

### TestKit

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

A garbage collector is included in `distage` by default. GC will remove all bindings that aren't transitive dependencies
of the chosen `GC root` keys from the plan - they will never be instantiated.

To use garbage collector, supply GC roots as an argument to `Injector.produce*` methods:

```scala mdoc:reset
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
```

```scala mdoc:invisible
val HACK_OVERRIDE_module = new ModuleDef {
  make[A].from(new A(_))
  make[B].from(new B)
  make[C].from(new C)
}
```

```scala mdoc:override
val roots = Set[DIKey](DIKey.get[A])

val locator = Injector().produceUnsafe(HACK_OVERRIDE_module, roots = roots)

// A is here
locator.find[A]

// B and C were not created
locator.find[B]
locator.find[C]
```

Class `C` was removed because it neither `B` nor `A` depended on it. It's not present in the `Locator` and the `"C!"` message was never printed.
But, if class `B` were to depend on `C` as in `case class B(c: C)`, it would've been retained, because `A` - the GC root, would depend on `B` which in turns depends on `C`.

GC serves two important purposes:

* It enables faster [tests](#test-kit) by omitting unrequired instantiations and initialization of potentially heavy resources,
* It enables multiple separate applications, aka "[Roles](#roles)" to be hosted within a single `.jar`.

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

NB: Currently a limitation applies to by-names - ALL dependencies on a class engaged in a by-name circular dependency have to be by-name,
otherwise distage will transparently revert to generating proxies.

### Auto-Sets: Collecting Bindings By Predicate

AutoSet @scaladoc[Planner](com.github.pshirshov.izumi.distage.model.Planner) Hooks traverse the class graph and collect all classes matching a predicate.

Using Auto-Sets, one can, for example, collect all `AutoCloseable` classes and `.close()` them after the application has finished work.

Note: it's not generally recommended to construct stateful, effectful or resource-allocating classes with `distage`, a general rule of thumb is:
if a class and its dependencies are stateless and can be replaced by a global `object`, it's ok to inject them with  `distage`. However, an example is given anyway,
as a lot of real applications depend on global resources, such as JDBC connections, `ExecutionContext` thread pools, Akka Systems, etc. that should
be closed properly at exit.

```scala
trait PrintResource(name: String) {
  def start(): Unit = println(s"$name started")
  def stop(): Unit = println(s"$name stopped")
}

class A extends PrintResource("A")
class B(val a: A) extends PrintResource("B")
class C(val b: B) extends PrintResource("C")

val resources = Injector(new BootstraModuleDef {
  many[PlanningHook]
    .add(new AssignableFromAutoSetHook[PrintResource])
}).produce(new ModuleDef {
  make[C]
  make[B]
  make[A]
}).get[Set[PrintResource]]

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

Calling `.foreach` on an auto-set is safe; the actions will be executed in order of dependencies.
Auto-Sets preserve ordering, they use `ListSet` under the hood, unlike user-defined [Sets](#multibindings--set-bindings).
e.g. If `C` depends on `B` depends on `A`, autoset order is: `A, B, C`, to start call: `A, B, C`, to close call: `C, B, A`.
When you use auto-sets for finalization, you **must** `.reverse` the autoset.

Note: Auto-Sets are NOT subject to [Garbage Collection](#using-garbage-collector), they are assembled *after* garbage collection is done,
as such they can't contain garbage by construction.

#### Weak Sets

[Set bindings](#set-bindings--multibindings) can contain *weak* references. References designated as weak will
be retained by [Garbage Collector](#using-garbage-collector) _only_ if there are other references to them except the
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
val roots = Seq(DIKey.get[Set[SetElem]])

// Enable GC
val injector = Injector(gcRoots = roots)

val locator = injector.produce(roots)
// Strong constructed!

assert(locator.get[Set[SetElem]].size == 1)
```

The `Weak` class was not required in any dependency of `Set[SetElem]`, so it was pruned.
The `Strong` class remained, because the reference to it was **strong**, therefore it was counted as a dependency of `Set[SetElem]`

If we change `Strong` to depend on `Weak`, then `Weak` will be retained:

```scala
final class Strong(weak: Weak) {
  println("Strong constructed")
}

assert(locator.get[Set[SetElem]].size == 2)
```

### Debugging, Introspection, Diagnostics and Hooks

You can print the `plan` to get detailed info on what will happen during instantiation. The printout includes file:line info
so your IDE can show you where the binding was defined!

```scala
val plan = Injector().plan(module)

System.err.println(plan)
```

![print-test-plan](media/print-test-plan.png)

You can also query a plan to see the dependencies and reverse dependencies of a specific class and their order of instantiation:

```scala
// Print dependencies
System.err.println(plan.topology.dependencies.tree(DIKey.get[Circular1]))
// Print reverse dependencies
System.err.println(plan.topology.dependees.tree(DIKey.get[Circular1]))
```

![print-dependencies](media/print-dependencies.png)

The printer highlights circular dependencies.

distage also uses some macros to create `TagK`s and [function bindings](#function-bindings),
you can turn on macro debug output during compilation by setting `-Dizumi.distage.debug.macro=true` java property:

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

To import cats integration add `distage-cats` library:

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

### ZIO

## PPER

See @ref[PPER Overview](../pper/00_pper.md)

@@@ index

* [Basics](01_basics.md)
* [Other features](02_other_features.md)
* [Debugging](03_debugging.md)
* [Cookbook](04_cookbook.md)
* [Syntax reference](05_reference.md)
* [Motivation](motivation.md)

@@@
