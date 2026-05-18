---
out: index.html
---

BIO Hierarchy
=============

**BIO** is a set of typeclasses and datatypes for programming in tagless final style using bifunctor effect types with variance.

Key syntactic features:

1. Universal summoner `F` is a single point of entry for all methods in the hierarchy
2. Import-less syntax. Syntax is automatically available whenever any typeclass from the hierarchy is imported, e.g. immediately after IDE auto-import.

These syntactic features allow you to write in a low ceremony, IDE-friendly and newcomer-friendly style:

```scala mdoc:to-string
import izumi.functional.bio.{F, Monad2, Primitives2}

def adder[F[+_, +_]: Monad2: Primitives2](i: Int): F[Nothing, Int] = {
  for { // map and flatMap extension methods are available automatically by importing Monad2
    ref <- F.mkRef(i) // mkRef method is available from Primitives2
    _   <- ref.update(_ + i)
    res <- ref.get
  } yield res
}
```

Key semantic features:

1. Typed error handling with bifunctor effect types
2. Automatic conversions to equivalent `cats.effect` instances using `import izumi.functional.bio.catz._`
3. No ambiguous implicit errors. It's legal to have both `Monad2` and `Applicative2` as constraints,
   despite the fact that `Monad2` provides an `Applicative2`.
4. Primitive concurrent data structures: `Ref`, `Promise`, `Semaphore`

To use it, add `fundamentals-bio` library:

@@@vars

```scala
libraryDependencies += "io.7mind.izumi" %% "fundamentals-bio" % "$izumi.version$"
```

@@@


If you're using Scala `2.12` you **must** enable `-Ypartial-unification` and either `-Xsource:2.13` or `-Xsource:3` for this library to work correctly:

```scala
// REQUIRED options for Scala 2.12
scalacOptions += "-Ypartial-unification"
scalacOptions += "-Xsource:2.13" // either this
// scalacOptions += "-Xsource:3" // or this
```

Most likely you’ll also need to add [Kind Projector](https://github.com/typelevel/kind-projector) plugin:

```scala
addCompilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full)
```

## Overview

The following graphic shows the current `BIO` hierarchy.

![BIO-relationship-hierarchy](media/bio-relationship-hierarchy.svg)

[(image)](media/bio-relationship-hierarchy.svg)

Auxiliary components:

![algebras](media/algebras.svg)

[(image)](media/algebras.svg)

Raw inheritance hierarchy:

![BIO-inheritance-hierarchy](media/bio-hierarchy.svg)

[(image)](media/bio-hierarchy.svg)

## Syntax, Implicit Punning

All implicit syntax in BIO is available automatically without wildcard imports
with the help of so-called "implicit punning".

Before (cats-effect):

```scala reset:mdoc:to-string
import cats.effect.{Ref, Sync}

// have to import syntax extensions in every file for for-comprehensions to work
import cats.syntax.all._

def adder[F[_]: Sync](i: Int): F[Int] = {
  for {
    ref <- Ref.of(i)
    _   <- ref.update(_ + i)
    res <- ref.get
  } yield res
}
```

After (BIO):

```scala reset:mdoc:to-string
// for-comprehensions work just by auto-importing Monad2 through the IDE
import izumi.functional.bio.{Monad2, Primitives2}

def adder[F[+_, +_]: Monad2: Primitives2](i: Int): F[Nothing, Int] = {
  for {
    ref <- F.mkRef(i) // typing `F.` lets IDE show all available TF methods
    _   <- ref.update(_ + i)
    res <- ref.get
  } yield res
}
```

Note: a `.flatMap` and `.map` methods that enable for-comprehensions are available even though we did not import any syntax implicits using a wildcard import.

The `flatMap` method was added by the implicit punning on the `Monad2` name.
Implicit punning is implemented by eschewing a companion object for a type and replacing it with "companion" implicit conversions with the same name as the type. That way whenever you import the type, you also always import the syntax-providing implicit conversions.

This happens to be a great fit for Tagless Final Style, since nearly all TF code will import the names of the typeclasses used to add them to context bounds on `F` parameter.

Implicit Punning for typeclass syntax relieves the programmer from having to manually import syntax implicits in every file in their codebase.

## cats-effect compatibilty

Import `izumi.functional.bio.catz._` for shim compatibilty with cats-effect. You can run `http4s` and all the other cats-effect libraries with just BIO typeclasses, without requiring any cats-effect typeclasses.

## Data Types

`Ref2`, `Promise2` and `Semaphore2` provide basic concurrent mutable state primitives. They require a `Primitives2` capability to create.
With `Primitiives2[F]` in implicit scope, use `F.mkRef`/`F.mkPromise`/`F.mkSemaphore` respectively. (See also example at top of the page)

`Free` monad, as well as `FreeError` and `FreePanic`  provide building blocks for DSLs when combined with a DSL describing functor.

`Morphism1/2/3` provide unboxed natural transformations for functors with 1,2,3-arity respectively, with `Isomorphism1/2/3` modeling two-way transformations.

## Misc Capabilities

`Clock1/2/3` gives access to current time.

`Entropy1/2/3` models random numbers.

`UnsafeRun2` allows executing effects (it is required for conversion to cats' `ConcurrentEffect` which also allows unsafe running)

## Examples

[distage-example](https://github.com/7mind/distage-example) is a full example application written in Tagless Final Style using BIO typeclasses.

You may also find a video walkthrough of using BIO on Youtube by [DevInsideYou — Tagless Final with BIO](https://www.youtube.com/watch?v=ZdGK1uedAE0&t=580s)

## Removal of trifunctor hierarchy

Since version 1.2.0 the trifunctor hierarchy has been removed, due to the fact that since ZIO version 2.0 they cannot be implemented for ZIO and also because of lack of use.

See details in https://github.com/7mind/izumi/issues/2026
