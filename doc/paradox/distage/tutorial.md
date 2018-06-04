Tutorial: DIStage By Example
==================

This tutorial will teach you how to build applications quickly and efficiently using DIStage! DIStage is a library that takes care of _all_ the boring parts of wiring your app together, letting you focus on just the essentials.<br/>
DIStage is *staged*, meaning that it's split into several distinct *stages*, letting you add custom functionality between stages should you need it, yet it keeps a simple API.

We'll start with basic examples and by the end of this tutorial you'll make a fully-fledged Pet Store application, complete with tests and configuration! Hang on tight, and let's start with:

## Defining simple modules

This is what Hello World looks like in DIStage:

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
DIStage will first instantiate the arguments, then call the constructor. All the classes in DIStage are instantiated exactly once,
 even if multiple classes depend on them, in other words they are `Singletons`.
 
Modules can be combined using `++` operator. In DIStage, you'll combine all the modules in your application into one 
one large module representing your application. Don't worry, you won't have to do that manually, if you don't want to: DIStage comes with a mechanism 
to discover all the (specially marked) Modules on the classpath at boot-up time. See [Plugins](#plugins-and-configurations) for details.

However, If you choose to combine your modules manually, DIStage can offer compile-time checks to ensure that all the
dependencies have been wired and that your app will run. See [Static Configurations](#testing-and-using-static-configurations) for details.
Whether you prefer the flexibility of runtime DI or the stability of compile-time DI, DIStage lets you mix and match different modes within one application.

Next line:

```scala
object Main extends App {
  val injector = Injectors.bootstrap()
```

Injector is the entity responsible for wiring our app together. We create an `injector` using default configuration.

```scala
  val plan = injector.plan(HelloModule)
```

We create an instantation `plan` from the module definition. Remember that DIStage is *staged*, instead of instantiating our 
definitions right away, DIStage first builds a pure representation of all the operations it will do and returns it back to us.
This allows us to easily implement additional functionality on top of DIStage without modifying the library. In fact, DIStage's built-in 
functionality such as [Plugins](#plugins-and-configurations) and [Configurations](#plugins-and-configurations) is not hard-wired, but is 
built on this framework of manipulating the `plan`. Plan rewriting also enables the [Import Injection Pattern](#import-injection-pattern) 
that will be especially interesting for the Scalazzi adepts seeking to free their programs of side effects.

```scala
  val classes = injector.produce(plan)

  classes.get[Hello].helloWorld()
```

After we execute the plan we're left a `Locator` that holds all of our app's classes. We can retrieve the class by type using `.get`

This concludes the first chapter. Next, we'll learn how to use multiple and named bindings:

## Using Multiple Bindings

...

## Plugins and Configurations

...

## Testing our App and using the Garbage Collector to instantiate only what's required

...

## Import Injection Pattern
...


## SPI, Hooks and Planning – writing our first DIStage extension

...

## Detailed Feature Overview

### PPER 

...

### Testing and using Static Configurations

WIP
