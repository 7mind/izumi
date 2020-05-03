---
out: index.html
---

BIO
===

BIO is a set of typeclasses and algebras for programming in tagless final style using bifunctor or trifunctor effect types with variance.

Key syntactic features:

1. Ergonomic `F` summoner that is a single point of entry to all methods in the hierarchy
2. Import-less syntax. Syntax is available automatically available whenever any typeclass from the hierarchy is imported, e.g. immediately after IDE auto-import.

These syntactic features allow you to write in a low ceremony, IDE-friendly and newcomer-friendly style:

```scala mdoc:to-string
import izumi.functional.bio.{F, BIOMonad, BIOMonadAsk, BIOPrimitives, BIORef3}

def adder[F[+_, +_]: BIOMonad: BIOPrimitives](i: Int): F[Nothing, Int] =
  F.mkRef(0)
   .flatMap(ref => ref.update(_ + i) *> ref.get)

// update ref from the environment and return result
def adderEnv[F[-_, +_, +_]: BIOMonadAsk](i: Int): F[BIORef3[F, Int], Nothing, Int] =
  F.access {
    ref => 
      for {
        _   <- ref.update(_ + i)
        res <- ref.get
      } yield res
  }
```

Key semantic features:

1. Typed error handling with bifunctor effect types
2. Automatic conversions to equivalent `cats.effect` instances in `import izumi.functional.bio.catz._`
3. Automatic adaptation of trifunctor typeclasses to bifunctor typeclasses when required
4. No ambiguous implicit errors. It's legal to have both `BIOMonad3` and `BIOMonadAsk` as constraints,
    despite the fact that `BIOMonadAsk` provides a `BIOMonad3`: `def adderEnv[F[-_, +_, +_]: BIOMonad3: BIOMonadAsk] // would still work`
5. Wrappers for primitive concurrent data structures: `BIORef`, `BIOPromise`, `BIOSemaphore`

To use it, add `fundamentals-bio` library:

@@@vars

```scala
libraryDependencies += "io.7mind.izumi" %% "fundamentals-bio" % "$izumi.version$"
```

Most likely you’ll also need to add [Kind Projector](https://github.com/typelevel/kind-projector) and enable partial unification on Scala versions older than `2.13`:

```scala
addCompilerPlugin("org.typelevel" % "kind-projector" % "0.11.0" cross CrossVersion.full)

// _Required_ option for Scala 2.12, not required on 2.13+
scalacOptions += "-Ypartial-unification"
```


@@@

Overview
--------

The following graphic shows the current BIO relationship hierarchy. Note that all the trifunctor `BIO*3` typeclasses
have bifunctor `BIO*` counterparts.

![BIO-relationship-hierarchy](media/bio-relationship-hierarchy.svg)

[(image)](media/bio-relationship-hierarchy.svg)

Auxiliary algebras:

![algebras](media/algebras.svg)

[(image)](media/algebras.svg)

Raw subtyping hierarchy:

![BIO-inheritance-hierarchy](media/bio-hierarchy.svg)

[(image)](media/bio-hierarchy.svg)
