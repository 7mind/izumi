---
out: index.html
---
distage Staged Dependency Injection
============

distage is a pragmatic module system for Scala that combines safety and clarity of pure FP with late binding, flexibility
and malleability of runtime dependency injection frameworks such as Guice.

### Hello World

This is what Hello World looks like in distage:

```scala
import distage._

class Hello {
  def hello(name: String) = println(s"Hello $name!")
}

object HelloModule extends ModuleDef {
  make[Hello]
}

object Main extends App {
  val injector = Injector()

  val plan = injector.plan(HelloModule)

  val classes: Locator = injector.produce(plan)

  println("What's your name?")
  val name = readLine()
  
  classes.get[Hello].hello(name)
}
```

Let's take a closer look:

```scala
object HelloModule extends ModuleDef {
  make[Hello]
}
```

We define a *Module* for our application. A module specifies *what* classes to instantiate and *how* to instantiate them.

In this case we are using the default instantiation strategy - just calling the constructor.

If a constructor accepts arguments, distage will first instantiate the arguments, then call the constructor. 
All the classes in distage are instantiated exactly once, even if multiple different classes depend on them, in other words
they are `Singletons`.
 
Modules can be combined using `++` and `overridenBy` operators. For example we can join our `HelloModule` with a `ByeModule`:

```scala
object ByeModule extends ModuleDef {
  make[Bye]
}

class Bye {
  def bye(name: String) = println(s"Bye $name!")
}

val helloBye = HelloModule ++ ByeModule
```

And override:

```scala
val uppercaseHello = new Hello { 
  override def hello(name: String) = s"HELLO ${name.toUpperCase}"
}

object UppercaseHelloModule extends ModuleDef {
  make[Hello].from(uppercaseHello)  
}

val uppercaseHelloBye = helloBye overridenBy uppercaseHello 
```

Combining modules with `++` is the main way to assemble your app together! But, if you don't want to list all your modules
in one place, you can use [Plugins](#plugins) to automatically discover all the (marked) modules in your app.

If you choose to combine modules explicitly, distage offers compile-time checks ensuring that your app will start.
See [Static Configurations](#static-configurations) for details.

```scala
object Main extends App {
  val injector = Injector()
  
  val plan = injector.plan(HelloModule)
```

We create an instantation `plan` from the module definition. distage is *staged*, so instead of instantiating our 
definitions right away, distage first builds a pure representation of all the operations it will do and returns it back to us.

This allows us to easily implement additional functionality on top of distage without modifying the library.
Features such as [Plugins](#plugins) and [Configurations](#config-files) are separate libraries, built on 
[transforming modules and plans](#import-materialization)

```scala
  val classes: Locator = injector.produce(plan)

  classes.get[Hello].helloWorld()
```

After we execute the plan we're left a `Locator` that holds all of our app's classes.
We can retrieve the instances by type using the `.get` method

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

### Provider Bindings

To bind to a function instead of constructor use `.from` method in @scaladoc[ModuleDef](com.github.pshirshov.izumi.distage.model.definition.ModuleDef) DSL:

```scala
case class HostPort(host: String, port: Int)

class HttpServer(hostPort: HostPort)

trait HttpServerModule extends ModuleDef {
  make[HttpServer].from {
    hostPort: HostPort => new HttpServer(hostPort.host, hostPort + 1000)
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

### Tagless Final Style with distage

distage has first-class support for tagless final style. Let's see what [freestyle tagless example](http://frees.io/docs/core/handlers/#tagless-interpretation)
looks like in distage:

```scala
class Program[F[_]: TagK: Monad] extends ModuleDef {
  make[TaglessProgram[F]]
}

object TryInterpreters extends ModuleDef {
  make[Validation.Handler[Try]].from(tryValidationHandler)
  make[Interaction.Handler[Try]].from(tryInteractionHandler)
}

// Combine modules into a full program
val TryProgram = new Program[Try] ++ TryInterpreters
```

@scaladoc[TagK](com.github.pshirshov.izumi.fundamentals.reflection.WithTags#TagK) is distage's analogue
of `TypeTag` or `Typeable`, but for higher-kinded types such as `F[_]`.
Don't forget to add a @scaladoc[TagK](com.github.pshirshov.izumi.fundamentals.reflection.WithTags#TagK) when you need to create a module parameterized by an abstract `F[_]`!
You don't need it however, when your module refers to concrete monads such as `Future` or `IO`. If you want
to create a module that's generic in a non-higher kinded parameter, use @scaladoc[Tag](com.github.pshirshov.izumi.fundamentals.reflection.WithTags#Tag).

The rest of the program:

```scala
class TaglessProgram[F[_]: Monad](validation: Validation[F], interaction: Interaction[F]) {
  def program = for {
      userInput <- interaction.ask("Give me something with at least 3 chars and a number on it")
      valid     <- (validation.minSize(userInput, 3), validation.hasNumber(userInput)).mapN(_ && _)
      _         <- if (valid) interaction.tell("awesomesauce!") else interaction.tell(s"$userInput is not valid")
  } yield ()
}

val validationHandler = new Validation.Handler[Try] {
  override def minSize(s: String, n: Int): Try[Boolean] = Try(s.size >= n)
  override def hasNumber(s: String): Try[Boolean] = Try(s.exists(c => "0123456789".contains(c)))
}

val interactionHandler = new Interaction.Handler[Try] {
  override def tell(s: String): Try[Unit] = Try(println(s))
  override def ask(s: String): Try[String] = Try("This could have been user input 1")
}
```

Notice how the program module stays completely polymorphic and abstracted from its eventual interpeter or the monad it
will run in? Want a program in different Monad? No problem:

```scala
val IOProgram = new Program[IO] ++ IOInterpreters
```

Want a program in the **same** Monad, but with different interpreters? No problem either:

```scala
val DifferentTryProgram = new Program[Try] ++ DifferentTryInterpreters
```

distage makes tagless final style easier and safer by making your implicit instances explicit and configurable as
first-class values. It even enforces typeclass coherence by disallowing multiple instances, so one wrong `import` can't
ruin your day. distage doesn't make you choose between OO and FP, it lets you use both without losing neither ease of
configuration and variability of a runtime DI framework, nor parametricity and equational reasoning of pure FP style.

### Config files

We provide first-class integration with `typesafe-config`, rendering a lot of parsing boilerplate unnecessary.

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

If you're not using [sbt-izumi-deps](sbt/00_sbt.md#bills-of-materials) plugin.

Write a config in HOCON format:

```hocon
# resources/application.conf
program {
    config {
        different = true
    }
}
```

Add `ConfigModule` to your injector:

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

class ConfiguredTryProgram[F: TagK: Monad] extends ModuleDef {
  make[ConfiguredProgram[F]]
  make[TaglessProgram[F]].named("primary")
  make[TaglessProgram[F]].named("different")
}
```

### Effectful instantiation

@@@ warning { title='TODO' }
Sorry, this page is not ready yet
@@@

example of explicitly splitting effectful and pure instantiations:

```scala
import distage._
import distage.config._
import com.typesafe.config._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.global

case class DbConf()
case class MsgQueueConf()
case class RegistryConf()

class DBService[F[_]]
class MsgQueueService[F[_]]
class RegistryService[F[_]]

class DomainService[F[_]]
( dbService: DBService[F]
, msgQueueService: MsgQueueService[F]
, registryService: RegistryService[F]
) {
  def run: F[Unit] = ???
}

class ExternalInitializers[F[_]: TagK] extends ModuleDef {
  make[F[DBService[F]]].from { dbConf: DbConf @ConfPath("network-service.db") => ??? }
  make[F[MsgQueueService[F]]].from { msgQueueConf: MsgQueueConf @ConfPath("network-service.msg-queue") => ??? }
  make[F[RegistryService[F]]].from { registryConf: RegistryConf @ConfPath("network-service.registry") => ??? }
}

val injector = Injector(new ConfigModule(AppConfig(ConfigFactory.load())))
val initializers = injector.produce(new ExternalInitializers[Future])

class DomainServices[F[_]: TagK] extends ModuleDef {
  make[DomainService[F]]
}

val main: Future[Unit] = initializers.run {
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

    allServices = injector.produce(externalServicesModule ++ new DomainServices[Future])

    _ <- allServices.get[DomainService[Future]].run
  } yield ()
}

Await.result(main, Duration.Inf)
```

### Auto-Factories & Auto-Traits

...

### Patterns

### Import Materialization

...

### Depending on future values with by-name parameters

...

### Ensuring service boundaries using API modules

...

### Plugins

When rapidly prototyping, the friction of adding new modules can become a burden.
distage plugin extension can alleviate that by automatically picking up all the `Plugin` modules defined in the program.

Note that auto plugins are incompatible with distage [static checks](#static-configurations). Our recommended workflow is 
to start with plugins, then switch to static configurations after the program has been stabilized.

To define a plugin, first add distage-plugins library:

```scala
libraryDependencies += Izumi.R.distage_plugins
```
or

@@@vars
```scala
libraryDependencies += "com.github.pshirshov.izumi.r2" %% "distage-plugins" % "$izumi.version$"
```
@@@

If you're not using [sbt-izumi-deps](sbt/00_sbt.md#bills-of-materials) plugin.

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
    , packagesEnabled = Seq("com.example") // packages to scan
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

Plugins also allow a program to dynamically extend itself by adding new Plugin classes on the classpath at launch time with `java -cp`

### Roles 

...

### Test Kit

### Fixtures and utilities

...

### Static Configurations

...

### Using Garbage Collector to instantiate only classes required for the test

...

### Detailed Feature Overview

### Implicits Injection

...

#### Typeclass Coherence Guarantees

### Compile-Time Checks

...

### Circular Dependencies support

...

#### Automatic Resolution with generated Proxies

#### Manual Resolution with by-name parameters

### Auto-Sets: Collecting Bindings By Predicate

...

#### Weak Sets

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


Macros power `distage-static` module, an alternative backend that doesn't use JVM runtime reflection.

### Extensions and Plan Rewriting – writing a distage extension

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

If you're not using [sbt-izumi-deps](sbt/00_sbt.md#bills-of-materials) plugin.

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

### Freestyle

### Eff

## PPER

See @ref[PPER Overview](../pper/00_pper.md)
