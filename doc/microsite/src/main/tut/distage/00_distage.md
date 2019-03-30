---
out: index.html
---

distage: Staged Dependency Injection
===================================

`distage` is a pragmatic module system for Scala that combines simplicity and reliability of pure FP with extreme late-binding
and flexibility of runtime dependency injection frameworks such as Guice.

`distage` is unopinionated, it's good for structuring applications written in either imperative Scala as in Akka/Play,
or in pure FP @ref[Tagless Final Style](basics.md#tagless-final-style)

```scala mdoc:invisible
// or with [ZIO Environment](#zio).
```

Further reading:

- Slides: [distage: Purely Functional Staged Dependency Injection](https://www.slideshare.net/7mind/distage-purely-functional-staged-dependency-injection-bonus-faking-kind-polymorphism-in-scala-2)
- @ref[Tutorial](basics.md#tutorial)
- @ref[Config Injection](config_injection.md)
- @ref[Other features](other-features.md)
- @ref[Debugging](debugging.md)
- @ref[Cookbook](cookbook.md)
- @ref[Syntax reference](reference.md)
```scala mdoc:invisible
/*
- @ref[Motivation for DI frameworks](motivation.md)

# Motivation

There's much written on the benefit of Dependency Injection.

Motivation for Dependency Injection frameworks in OOP:

- [Zenject: is this overkill?](https://github.com/svermeulen/Zenject#theory)

- [IoC in 2003](https://paulhammant.com/files/JDJ_2003_12_IoC_Rocks.pdf) - 
  nowadays what the article calls. `distage`
  
- Example: Code sharing

 To extend the example, If your code spans over multiple repositories – for example, your company has `sdk` + 3 dependent apps, and the constructor has been changed in sdk, you will now have to update 4 repositories.
This is clearly _very_ unmodular – 4 applications were broken by an irrelevant implementation detail!
\item IMHO using DI as a concept – accepting modules as arguments, instead of using global objects – implicits included – is a #1 prerequisite for modularity,
while wiring implicitly is a #2 prerequisite – without those you simply can't share modules!

DI solves the problem of modularity in large systems. Not having problems solvable by DI is great. But when systems grow
to encompass several repos it becomes more important. For example, let's assume you have 4 separate repos - 3 apps, 1 shared sdk.
If in these 3 apps you instantiate an sdk class explicitly, and that class e.g. added a new argument, now you have to update
instantiations in all 3 apps. If we think of classes as first-class modules, this is clearly unmodular

Motivation for Dependency Injection frameworks in functional programming:

- [Haskell registry: modules as records of functions](https://github.com/etorreborre/registry/blob/master/doc/motivation.md)
- [Wire Once, Rewire Twice - Dependency Injection in Haskell by etorreborre](https://skillsmatter.com/skillscasts/12299-wire-once-rewire-twice)
- [Dependency Injection and FP in Scala](https://blog.softwaremill.com/what-is-dependency-injection-8c9e7805502f)

Note: despite being a Haskell library, the `registry` DI framework above is *less* principled than `distage` due to not offering
a first-class representation for its operations. That is, registry's flow is `registry(moduleDef) -> program`, same as in Guice's `bindings -> program`,
whereas distage has an additional interpretation stage: `moduleDef -> plan (operations) -> program`.
The sequence of operations in a `plan` fully specifies Injector's behavior ahead of time - it has no branches of its own.
This makes `distage` easily extensible by just rewriting plans before execution, see `distage-config` for an example.
On the contrary, that while `registry` can _trace_ its execution to print an operations sequence post-factum, you can't use the resulting operations as an
input to registry itself. Registry Ops do not define the steps the algorithm will take, only trace, thus failing to achieve
extensibility in that direction.

Registry also lacks Set Bindings and module groups (tags); distage doesn't require manual lifting of arguments (funTo, valTo, etc.) for use with monads

// Haskell-land is a nightmare for anyone concerned with modularity. importing B into A should ALWAYS succeed, A should not be impacted by internal implementation of B, even if B imports A – this can, and SHOULD always be resolved.
// Similarly, modules should not be dependent on application data flow, if a module changes signature as to require a new collaborator, it's users SHOULD not be affected by this change – they should NEVER be impacted by changes in a module's implementation

== 

di as a pattern:

- define programs as collections of components
- trust types
- program orthogonally

- di in .Net image
- softwaremill

what does di framework buy you on top:

- wire implicitly across repository boundaries
- debug easily with plain data plans vs. opaque implicit resolution
- override specific modules in tests or in runtime depending on configuration
- deal with global resources

  = trade offs
  - another set of rules on top of scala
  - although any large program will grow its own organizational structure and 
    distage strives to nullify code changes outside of modules
  // - distage also dumps equivalent scala code

distage improves on others:

- guaranteed termination (no laziness)
- singleton-only semantics (non-singletons are bogus)
- reified plan, the DI program is a transparent, debuggable set of instructions
- compile-time guarantees, if it compiles it will start
- compile-time guarantees that do not lose runtime flexibility
- polymorphic modules (i.e. change your DI program from cats IO to monix or ZIO with one line change – or at runtime)
- natural driver for tagless final and mtl-style classes
  - easily create algebras that depend on resources
  - explicitly specify the implicit domain of your program, shady local imports can't ruin your program anymore
- resource management

=


the big innovations are:

1. reified plan
2. strict execution
3. singleton only semantic
4. gc

--- table ---

> The example about DI should either be removed or completed. It needs to show how to support: 
> a graph with different components of the same type,
> how to easily overload an existing wiring,
> how to deal with effectful instantiation of components

= reified representation
= generate-equivalent code
= polymorphism
= higher-kinded polymorphism
= monadic instantiation
= compile-time safety
= circular dependencies
= multi-bindings
= invasiveness on top of pure DI
= invasiveness on top of TF
= resource-management
= instantiation semantic

### Migrating from Guice

...

### Migrating from MacWire

...

### Integrations

...

### ZIO
*/
```

## PPER

See @ref[PPER Overview](../pper/00_pper.md)

@@@ index

* [Basics](basics.md)
* [Config Injection](config_injection.md)
* [Other features](other-features.md)
* [Debugging](debugging.md)
* [Cookbook](cookbook.md)
* [Syntax reference](reference.md)

@@@
