# Tutorial: DiStage By Example

DiStage is a modern dependency injection framework for modern Scala, like Scala itself, DiStage seeks to combine the best practices of FP with the best practices of OOP, to form its own, unique paradigm of development.

Taking pervasive type safety, composition and separation of declaration and execution from FP, and modularity, scalability and late binding from OOP,
DiStage brings together a fusion that retains safety and clarity of pure FP without sacrificing full runtime flexibility and configurability of traditional 
runtime dependency injection frameworks such as Guice.

This tutorial will teach you how to build applications quickly and efficiently using DiStage!</br>
DiStage is *staged*, meaning that it's split into several distinct *stages*, like a multi-pass compiler. DiStage lets you add custom functionality between stages should you require it, yet it keeps a simple surface API.

We'll start off with basic examples and by the end of this tutorial you'll make a fully-fledged Pet Store application, complete with tests and configuration.

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
to discover all the (specially marked) Modules on the classpath at boot-up time. See [Plugins](#plugins-and-configurations) for details.

However, If you choose to combine your modules manually, DiStage can offer compile-time checks to ensure that all the
dependencies have been wired and that your app will run. See [Static Configurations](#testing-and-using-static-configurations) for details.
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
functionality such as [Plugins](#plugins-and-configurations) and [Configurations](#plugins-and-configurations) is not hard-wired, but is 
built on this framework of manipulating the `plan`. Plan rewriting also enables the [Import Injection Pattern](#import-injection-pattern) 
that will be especially interesting for the Scalazzi adepts seeking to free their programs of side effects.

```scala
  val classes = injector.produce(plan)

  classes.get[Hello].helloWorld()
```

After we execute the plan we're left a `Locator` that holds all of our app's classes. We can retrieve the class by type using `.get`

This concludes the first chapter. Next, we'll learn how to use multiple and named bindings:

### Using Multiple Bindings (Multibindings, Set Bindings)

...

### Tagless Final Style with DiStage

...

### Plugins and Configurations

...

## Patterns

### Ensuring service boundaries using API modules

...

### Roles, not microservices 

...

### Import Injection Pattern

...

## Test Kit

### Fixtures and utilities

...

### Static Configurations

...

### Using Garbage Collector to instantiate only classes required for the test

...

## Detailed Feature Overview

### Circular Dependencies support

...

### PPER

See [PPER Overview](../pper/00_pper.md)

### SPI, Hooks and Plan Rewriting – writing our first DiStage extension

...
