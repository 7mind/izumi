# Problem statement

Izumi Project library ecosystem strives to be compatible with a wide variety of effect types that may (or may not) be used by its users.

This led to the creation of QuasiIO @fundamentals/fundamentals-bio/src/main/scala/izumi/functional/quasi family of compatibility typeclasses that extract a subset of operations that Izumi Project libraries need from other effect types. The existence of Quasi* typeclass hierarchy is a maintenance burden on the developers and due to its monofunctor nature (previously thought required to support the most common denominator effect types) it prevents usage of typed errors within the Izumi Project libraries themselves. Moreover, due to QuasiIO's direct support for `Identity`, Izumi itself can't use a purely functional style - all maintainers must be aware that the `F` in `F[_]: QuasiIO` is not a lawful monad and operations on it must be carefully suspended manually.

Following an experiment at conversion of monofunctor effect types to bifunctors (https://github.com/7mind/izumi/pull/1766), we have decided to solve our compatibility problem in a different way. By lifting monofunctor effect types to bifunctors we will get rid of the entire `Quasi*` hierarchy and allow usage of typed errors and purely functional style within Izumi Project library code. However, we also want the usability for monofunctor effect type users not to degrade following that refactoring.

# Background

BIO bifunctor typeclass hierarchy is isomorphic to Cats Effect monofunctor typeclass hierarchy. This is currently witnessed only partially by the existence of BIO to CE conversions in fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/CatsConversions.scala, but the opposite conversions, from CE to BIO are missing. As part of the following design, we will add them.

# Design of the solution

There are two parts to the design:

- Conversion of monofunctor typeclasses to bifunctor typeclasses
- Transparent bifunctorization/debifunctorization for monofunctor effect types at the entry points to Izumi Project libraries.

## Conversion design

We provide conversion typeclasses from Cats Effect to BIO, of form:

```
implicit def MonadToBIO[F[_]](implicit Monad: Monad[F]): Monad2[Bifunctorized[F, +_, +_]]

/* etc for all Cats-Effect -> BIO correspondences */
```

Where `Bifunctorized` is an opaque type wrapper that "converts" a monofunctor effect type to a bifunctor effect type.

```
object Bifunctorized {
  ...
  type Bifunctorized[F[_], +E, +A] // abstract type aka newtype aka pre-Scala 3 opaque type
}
```

The conversions shall use an 'error-submerging'/'submarine error handling' technique shown in Prior Art, to add an ability to a monofunctor effect type to distinguish between typed errors (`Exit.Error`) and untyped errors (defect, `Exit.Termination`). TypedError fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/TypedError.scala exists which converts typed errors into Throwables, but it lacks one detail - we don't want it to be possible for TypedErrors from different `Bifunctorized` effect types to be intermixed together through the Typed error channel. We should add a new type instead that discriminates error origin using `TagK[F]` of the original monofunctor effect type. Note: submarine error handling implies discrimination based on the unique handler, to mimic algebraic effect semantics, which I believe is not necessary for our purpose, type discrimination alone is sufficient.

### Conversion of effect values

In order to provide an ability to use an existing monofunctor effect value with bifunctor methods, the following automatic bijection implicit conversions shall be provided:

```scala
object Bifunctorized {
  ...
  def bifunctorize(f: F[A]): Bifunctorized[F, Throwable, A]

  def debifunctorize(f: Bifunctorized[F, Throwable, A]): F[A]

  implicit def bifunctorizeConversion(f: F[A]): Bifunctorized[F, Throwable, A] = bifunctorize(f)
  implicit def debifunctorizeConversion(f: Bifunctorized[F, Throwable, A]): F[A] = debifunctorize(f)
  
  implicit final class BifunctorizedSyntax[F[_]](private val f: Bifunctorized[F, Throwable, A]) extends AnyVal {
    def toMonofunctor: F[A] = debifunctorize(f)
  }
  
  ...provide Syntax2 conversions in Bifunctorized companion to make BIO syntax available on any Bifunctorized value...
}
```

Where in order to make the untyped Throwable error embedded into the monofunctor `F` effect type manipulable via e.g. `Error2#catchAll` and other typed error BIO hierarchy methods, the Throwable error must be Submerged, converted into a typed error during `bifunctorize`.

In `debifunctorize`, a typed error must be de-Submerged, unwrapped, as its expected to be in order for monofunctor's native methods to work with it.

Note: where the bifunctorized effect value is a bifunctor already, such as `bifunctorize(Left(new Throwable()))`, no submerging should happen.

## Transparent bifunctorization at seams

The Izumi Project external interface seams that previously accepted monofunctor effect types `F[_]`, such as distage/distage-core/src/main/scala/izumi/distage/model/Injector.scala

```scala
def apply[F[_]: QuasiIO: ...](...): Injector[F]
```

Shall now accept bifunctor effect types `F[+_, +_]`:

```scala
def apply[F[+_, +_]: IO2: ...](...): Injector[F]
```

With an overload for monofunctors:

```scala
def apply[F[_]: ...](...)(implicit F: IO2[Bifunctorized[F, +_, +_]]): Injector[Bifunctorized[F, +_, +_]]
```

The BIO syntax on Bifunctorized shall allow users to manipulate their formerly monofunctor effect values in bifunctor way, the implicit conversion `Bifunctorized.bifunctorizeConversion` shall ease the pain of passing in monofunctor values and `.toMonofunctor` method allows return back to monofunctor world.

# Goals

1. Bifunctorized effect types must pass cats laws suites using CatsConversions instances for their Bifunctorized forms. That is, a CE->BIO->CE conversion must come at no loss of correctness with respect to cats effect laws.
2. Bifunctorized Submerged errors are discriminated by TagK[F] of its monofunctor. Terminates/defects use monofunctor's raw Throwable
3. distage's Injector, bio's Lifecycle and logstage's LogIO entrypoints are transparently bifunctorized/de-bifunctorized for monofunctors. `Identity` is special-cased and goes through a bifunctorization/debifunctorization cycle to `MiniBIO` and back, transparently to the user.
4. Bifunctorization should be a no-op for real bifunctors, that is, `bifunctorize(f: ZIO[ArbiraryEnv, Throwable, A]) eq f` should hold. There should be no error submerging performed for effect types that already support typed errors.
5. No More Orphans trick keeps working, users are not forced to have cats on their classpath, test distage/distage-extension-config/.jvm/src/test/scala/izumi/distage/impl/OptionalDependencyTest.scala keeps passing.
6. Quasi* typeclasses are deleted and BIO Hierarchy typeclasess are used everywhere the former were used.
7. The project compiles and all tests pass on Scala 2.13, Scala 3 and Scala 2.12.

# Prior art

- https://github.com/7mind/izumi/pull/1766 - sketch of bifunctorization for arbitrary Cats-Effect compatible monofunctor effect types
- https://github.com/typelevel/cats-mtl/pull/619 - prior art implementation of 'Submerge' error handling in monofunctor world. Their implementation tags errors by unique instance to implement an effects-and-handlers (algebraic effects) semantic where only a handler associated with the effect region can catch errors introduced by that region's throw. We're not looking for that - in fact, we want error handlers for the same monofunctor effect type to be compatible - but we don't want errors from **different** effect types to be compatible. e.g. a Bifunctorized[cats.effect.IO] type should not be able to be fooled into thinking that a Bifunctorized[scala.util.Try]'s typed error is its own typed error.

Fetch the diffs of prior art pull requests and save them for reference first. If you fail to do that, fail fast and revert to user.
