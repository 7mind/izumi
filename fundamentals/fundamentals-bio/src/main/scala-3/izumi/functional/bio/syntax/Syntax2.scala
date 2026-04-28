package izumi.functional.bio.syntax

import izumi.functional.bio.*

import scala.annotation.unused

/**
  * On Scala 3, BIO syntax is expressed exclusively through native `extension`
  * methods, collected into per-typeclass `<Typeclass2>ExtensionMethods` traits
  * (see e.g. [[Monad2ExtensionMethods]]).
  *
  * Three reach mechanisms are used — each targeting a distinct import style
  * the Scala 2 [[Syntax2]] implicit-punning trick served on Scala 2:
  *
  *   1. Each typeclass trait mixes in its own-level `<Typeclass2>ExtensionMethods`,
  *      so a `given Xyz2[F]` in scope carries every applicable extension as a
  *      member via typeclass inheritance (Scala 3 finds them via "visible given
  *      instances"). Covers callers writing abstract `F[+_, +_]: Xyz2`
  *      context-bound code with no imports at all.
  *
  *      Critically, the `<Typeclass2>ExtensionMethods` traits are *standalone* —
  *      none inherits from another. Shared-ancestor extensions (e.g. `orElse`
  *      defined on [[ApplicativeError2ExtensionMethods]]) reach a given `Monad2[F]`
  *      only via the typeclass hierarchy proper, never via a second, overlapping
  *      `<Xyz>2ExtensionMethods`-trait path. This prevents the sibling-typeclass
  *      extension-method ambiguity Scala 3 would otherwise hit when two unrelated
  *      typeclasses (e.g. `Concurrent2` and `Temporal2`) have a common ancestor.
  *
  *   2. The [[Syntax2.ImplicitPuns]] chain publishes one `given <Name>: ExtensionCarrier`
  *      per typeclass name in a *linearized* trait stack
  *      (`ImplicitPuns extends ImplicitPunsWeakTemporal extends … extends ImplicitPunsFunctor`,
  *      mirroring the Scala 2 implicit-conversion chain). Named imports like
  *      `import izumi.functional.bio.Monad2` pull both the typeclass *type*
  *      and the like-named given *term*, putting the umbrella
  *      [[Syntax2.ExtensionCarrier]] in scope — so extension methods become
  *      available without a wildcard import. If the caller imports *several*
  *      sibling typeclass names, the resulting multiple `given` instances all
  *      share the `ExtensionCarrier` type but are declared at different trait
  *      levels, so trait linearization unambiguously picks one winner for each
  *      extension-method lookup.
  *
  *   3. `Syntax2` mixes in every `<Typeclass2>ExtensionMethods` trait and
  *      `package object bio extends Syntax2`. Because Scala 3's `import pkg.*`
  *      wildcard does *not* bring givens (only named imports and
  *      `import pkg.{given, *}` do), mechanism 2 alone does not cover plain
  *      wildcard callers. Inheriting the extension methods onto the package
  *      object makes them lexical package-level members reachable via
  *      `import izumi.functional.bio.*`, closing that gap.
  *
  * This trait also holds the `F` summoner and the `<Typeclass2>[F]: <Typeclass2>[F]`
  * summoner `def`s that replace the typeclass-summoner half of Scala 2 punning.
  */
trait Syntax2
  extends Functor2ExtensionMethods
  with Bifunctor2ExtensionMethods
  with Applicative2ExtensionMethods
  with Guarantee2ExtensionMethods
  with ApplicativeError2ExtensionMethods
  with Monad2ExtensionMethods
  with Error2ExtensionMethods
  with Bracket2ExtensionMethods
  with Panic2ExtensionMethods
  with IO2ExtensionMethods
  with Parallel2ExtensionMethods
  with Concurrent2ExtensionMethods
  with WeakTemporal2ExtensionMethods
  with Temporal2ExtensionMethods
  with Fork2ExtensionMethods
  with Syntax2.ImplicitPuns {

  /**
    * A convenient dependent-typed summoner for BIO hierarchy.
    * Auto-narrows to the most powerful available type class:
    *
    * {{{
    *   import izumi.functional.bio.{F, Temporal2}
    *
    *   def y[F[+_, +_]: Temporal2] = {
    *     F.timeout(5.seconds)(F.forever(F.unit))
    *   }
    * }}}
    */
  def F[F[+_, +_]](implicit F: Functor2[F]): F.type = F

  @inline final def Functor2[F[+_, +_]: Functor2]: Functor2[F] = implicitly
  @inline final def Bifunctor2[F[+_, +_]: Bifunctor2]: Bifunctor2[F] = implicitly
  @inline final def Applicative2[F[+_, +_]: Applicative2]: Applicative2[F] = implicitly
  @inline final def Guarantee2[F[+_, +_]: Guarantee2]: Guarantee2[F] = implicitly
  @inline final def ApplicativeError2[F[+_, +_]: ApplicativeError2]: ApplicativeError2[F] = implicitly
  @inline final def Monad2[F[+_, +_]: Monad2]: Monad2[F] = implicitly
  @inline final def Error2[F[+_, +_]: Error2]: Error2[F] = implicitly
  @inline final def Bracket2[F[+_, +_]: Bracket2]: Bracket2[F] = implicitly
  @inline final def Panic2[F[+_, +_]: Panic2]: Panic2[F] = implicitly
  @inline final def IO2[F[+_, +_]: IO2]: IO2[F] = implicitly

  /**
    * Shorthand for [[IO2#syncThrowable]]
    *
    * {{{
    *   IO2(println("Hello world!"))
    * }}}
    */
  @inline final def IO2[F[+_, +_], A](effect: => A)(using F: IO2[F]): F[Throwable, A] = F.syncThrowable(effect)

  @inline final def Parallel2[F[+_, +_]: Parallel2]: Parallel2[F] = implicitly
  @inline final def ParallelErrorAccumulatingOps2[F[+_, +_]: ParallelErrorAccumulatingOps2]: ParallelErrorAccumulatingOps2[F] = implicitly
  @inline final def Concurrent2[F[+_, +_]: Concurrent2]: Concurrent2[F] = implicitly
  @inline final def WeakAsync2[F[+_, +_]: WeakAsync2]: WeakAsync2[F] = implicitly
  @inline final def Async2[F[+_, +_]: Async2]: Async2[F] = implicitly
  @inline final def Fork2[F[+_, +_]: Fork2]: Fork2[F] = implicitly
  @inline final def WeakTemporal2[F[+_, +_]: WeakTemporal2]: WeakTemporal2[F] = implicitly
  @inline final def Temporal2[F[+_, +_]: Temporal2]: Temporal2[F] = implicitly
}

object Syntax2 {

  final class ClockAccessor[F[+_, +_]](@unused private val dummy: Boolean = false) extends AnyVal {
    def clock(implicit clock: Clock2[F]): clock.type = clock
  }

  final class EntropyAccessor[F[+_, +_]](@unused private val dummy: Boolean = false) extends AnyVal {
    def entropy(implicit entropy: Entropy2[F]): entropy.type = entropy
  }

  /**
    * Umbrella carrier type mixing every standalone `<Xyz>2ExtensionMethods`
    * trait. Used as the *type* of every named-import pun given below — so
    * whichever typeclass name the caller imports, the resulting in-scope
    * given carries the full extension-method surface of BIO. Extensions still
    * self-filter by their own `using F: <Typeclass>[F]` context parameter, so
    * a given `ExtensionCarrier` in scope doesn't mean `ZIO.never.timeout(...)`
    * compiles — it still needs `Temporal2[ZIO]` derivable from the implicit scope.
    */
  trait ExtensionCarrier
    extends Functor2ExtensionMethods
    with Bifunctor2ExtensionMethods
    with Applicative2ExtensionMethods
    with Guarantee2ExtensionMethods
    with ApplicativeError2ExtensionMethods
    with Monad2ExtensionMethods
    with Error2ExtensionMethods
    with Bracket2ExtensionMethods
    with Panic2ExtensionMethods
    with IO2ExtensionMethods
    with Parallel2ExtensionMethods
    with Concurrent2ExtensionMethods
    with WeakTemporal2ExtensionMethods
    with Temporal2ExtensionMethods
    with Fork2ExtensionMethods

  object ExtensionCarrier extends ExtensionCarrier

  /**
    * Scala 3 "implicit punning" carriers, laid out as a linearized trait chain
    * (deepest = highest priority, following Scala 2 `Syntax2.ImplicitPuns*`).
    *
    * When a caller imports *one* typeclass name, its lone `given` carries the
    * full [[ExtensionCarrier]] surface — no ambiguity. When a caller imports
    * *several* sibling typeclass names, the multiple same-typed `given`s in
    * scope are resolved by Scala 3's trait-linearization priority: the
    * deepest-declared given wins. Each extension method resolves to exactly
    * one givens' copy, so no "ambiguous extension method" error can arise
    * from this source.
    *
    * The underlying witness is a single object ([[ExtensionCarrier]]); every
    * level aliases it under the appropriate typeclass name. This makes the
    * pun cheap — importing many names adds no allocation.
    */
  trait ImplicitPunsFunctor {
    given Functor2: ExtensionCarrier = ExtensionCarrier
  }
  trait ImplicitPunsBifunctor extends ImplicitPunsFunctor {
    given Bifunctor2: ExtensionCarrier = ExtensionCarrier
  }
  trait ImplicitPunsApplicative extends ImplicitPunsBifunctor {
    given Applicative2: ExtensionCarrier = ExtensionCarrier
  }
  trait ImplicitPunsGuarantee extends ImplicitPunsApplicative {
    given Guarantee2: ExtensionCarrier = ExtensionCarrier
  }
  trait ImplicitPunsApplicativeError extends ImplicitPunsGuarantee {
    given ApplicativeError2: ExtensionCarrier = ExtensionCarrier
  }
  trait ImplicitPunsMonad extends ImplicitPunsApplicativeError {
    given Monad2: ExtensionCarrier = ExtensionCarrier
  }
  trait ImplicitPunsError extends ImplicitPunsMonad {
    given Error2: ExtensionCarrier = ExtensionCarrier
  }
  trait ImplicitPunsBracket extends ImplicitPunsError {
    given Bracket2: ExtensionCarrier = ExtensionCarrier
  }
  trait ImplicitPunsPanic extends ImplicitPunsBracket {
    given Panic2: ExtensionCarrier = ExtensionCarrier
  }
  trait ImplicitPunsIO extends ImplicitPunsPanic {
    given IO2: ExtensionCarrier = ExtensionCarrier
  }
  trait ImplicitPunsParallel extends ImplicitPunsIO {
    given Parallel2: ExtensionCarrier = ExtensionCarrier
  }
  trait ImplicitPunsParallelErrorAccumulatingOps extends ImplicitPunsParallel {
    given ParallelErrorAccumulatingOps2: ExtensionCarrier = ExtensionCarrier
  }
  trait ImplicitPunsConcurrent extends ImplicitPunsParallelErrorAccumulatingOps {
    given Concurrent2: ExtensionCarrier = ExtensionCarrier
  }
  trait ImplicitPunsFork extends ImplicitPunsConcurrent {
    given Fork2: ExtensionCarrier = ExtensionCarrier
  }
  trait ImplicitPunsWeakTemporal extends ImplicitPunsFork {
    given WeakTemporal2: ExtensionCarrier = ExtensionCarrier
  }
  trait ImplicitPuns extends ImplicitPunsWeakTemporal {
    given Temporal2: ExtensionCarrier = ExtensionCarrier
  }

}
