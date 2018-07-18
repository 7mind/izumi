---
out: index.html
---
DiStage Staged Dependency Injection
============

DiStage is a modern dependency injection framework for Scala. Like Scala itself, DiStage seeks to combine the best practices of FP with the best practices of OOP.

Combining type safety, ease composition and separation of declaration from execution from FP, and late binding, modularity and scalability from OOP,
DiStage brings together a fusion that retains safety and clarity of pure FP without sacrificing full runtime flexibility and configurability of traditional 
runtime dependency injection frameworks such as Guice.

## Tutorial: DiStage By Example

This tutorial will teach you how to build applications quickly and efficiently using DiStage!</br>
DiStage is *staged*, meaning that it's split into several distinct *stages*, like a multi-pass compiler. DiStage lets you add custom functionality between stages should you require it, yet it keeps a simple surface API.

We'll start off with basic examples and by the end of this tutorial you'll make a fully-fledged Pet Store application, complete with tests and configuration!

### Defining simple modules

This is what Hello World looks like in DiStage:

```scala
import com.github.pshirshov.izumi.distage.Injectors
import com.github.pshirshov.izumi.distage.model.definition.ModuleDef

class Hello {
  def helloWorld() = println("Hello World!")
}

object HelloModule extends ModuleDef {
  make[Hello]
}

object Main extends App {
  val injector = Injectors.bootstrap()
  
  val plan = injector.plan(HelloModule)
  
  val classes = injector.produce(plan)

  classes.get[Hello].helloWorld()
}
```

Let's review the new concepts line by line:

```scala
object HelloModules extends ModuleDef {
  make[Hello]
}
```

We define a *Module* for our application. A module specifies *what* classes to instantiate and *how* to instantiate them.

In this case we are using the default instantiation strategy - just calling the constructor, so we don't have to specify anything.

The default way to instantiate a class is to call its constructor. If the constructor accepts arguments, 
DiStage will first instantiate the arguments, then call the constructor. All the classes in DiStage are instantiated exactly once,
 even if multiple classes depend on them, in other words they are `Singletons`.
 
Modules can be combined using `++` operator. In DiStage, you'll combine all the modules in your application into one 
one large module representing your application. Don't worry, you won't have to do that manually, if you don't want to: DiStage comes with a mechanism 
to discover all the (specially marked) Modules on the classpath at boot-up time. See [Plugins](#plugins) for details.

However, If you choose to combine your modules manually, DiStage can offer compile-time checks to ensure that all the
dependencies have been wired and that your app will run. See [Static Configurations](#static-configurations) for details.
Whether you prefer the flexibility of runtime DI or the stability of compile-time DI, DiStage lets you mix and match different modes within one application.

Next line:

```scala
object Main extends App {
  val injector = Injectors.bootstrap()
```

Injector is the entity responsible for wiring our app together. We create an `injector` using default configuration.

```scala
  val plan = injector.plan(HelloModule)
```

We create an instantation `plan` from the module definition. Remember that DiStage is *staged*, instead of instantiating our 
definitions right away, DiStage first builds a pure representation of all the operations it will do and returns it back to us.
This allows us to easily implement additional functionality on top of DiStage without modifying the library. In fact, DiStage's built-in 
functionality such as [Plugins](#plugins) and [Configurations](#config-files) is not hard-wired, but is 
built on this framework of manipulating the `plan`. Plan rewriting also enables the [Import Injection Pattern](#import-injection-pattern) 
that will be especially interesting for the Scalazzi adepts seeking to free their programs of side effects.

```scala
  val classes = injector.produce(plan)

  classes.get[Hello].helloWorld()
```

After we execute the plan we're left a `Locator` that holds all of our app's classes. We can retrieve the class by type using `.get`

This concludes the first chapter. Next, we'll learn how to use multiple and named bindings:


// TODO blakdfg
distage is non-invasive and unopinionated, it tries to get out of the way of programmer as much as possible. As a consequence it does not use annotations

### Multiple Bindings (Multibindings, Set Bindings)

Multibindings
    *
    *
    * Note: The method Multibinder.newSetBinder(binder, type) can be confusing. This operation creates a new binder,
    * but doesn't override any existing bindings. A binder created this way contributes to the existing Set of
    * implementations for that type. It would create a new set only if one is not already bound.

Also see [Guice wiki on Multibindings](https://github.com/google/guice/wiki/Multibindings).

### Tagless Final Style with DiStage

...

### Config files

...

### Auto-Factories & Auto-Traits

...

## Patterns

### Import Injection Pattern

...

### Depending on future values with by-name parameters

...

### Ensuring service boundaries using API modules

...

### Plugins

Sometimes, when rapidly prototyping, the additional friction of adding new modules into the system can disrupt developer's flow.
Distage plugin system can automatically pickup all modules defined in the program and by doing that reduce friction of adding new modules.

To define a plugin, first add distage-plugins library:

@@@vars
```scala
libraryDependencies += "com.github.pshirshov.izumi.r2" %% "distage-plugins " % "$izumi.version$"
```
@@@

Create a module extending the `PluginDef` trait instead of `ModuleDef`:

```scala
trait PetStorePlugin extends PluginDef {
  make[PetRepository]
  make[PetStoreService]
  make[PetStoreController]
}
```

At your app entry point add a plugin loader:

```scala
val pluginLoader = new PluginLoaderDefaultImpl(
  PluginConfig(debug = false, packagesEnabled = Seq("com.example"), packagesDisabled = Seq.empty))
)

val appModules = pluginLoader.load()
val app = appModules.merge
```

Launch as normal with the loaded modules:

```scala
val injector = Injectors.bootstrap()
injector.run(app)
```

Plugins also allow a program to dynamically extend itself by adding new Plugin classes into the classpath which will be picked up at runtime.

### Roles, not microservices 

...

## Test Kit

### Fixtures and utilities

...

### Static Configurations

...

### Using Garbage Collector to instantiate only classes required for the test

...

## Detailed Feature Overview

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

### Provider (Function) Bindings

...

### Debugging, Introspection, Diagnostics and Hooks

To see macro debug output during compilation, set `-Dizumi.distage.debug.macro=true` java property:

```bash
sbt -Dizumi.distage.debug.macro=true compile
```

Macros are currently used to create `TagK`s and [provider bindings](#provider-(function)-bindings).

They also power `distage-static` module, an alternative backend that doesn't use JVM runtime reflection. 

TODO ...

### Extensions and Plan Rewriting – writing our first DiStage extension

...

## Migrating from Guice

...

## Migrating from MacWire

...

## Integrations

...

### Cats

### Scalaz

### Freestyle

### Eff

## PPER

See @ref[PPER Overview](../pper/00_pper.md)
