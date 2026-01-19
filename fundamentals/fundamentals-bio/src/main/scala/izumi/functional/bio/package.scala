package izumi.functional

import izumi.functional.bio.data.Isomorphism2
import izumi.functional.bio.syntax.Syntax2

/**
  *  Current hierarchy (rendered: [[https://izumi.7mind.io/bio/media/bio-relationship-hierarchy.svg]], use [[https://www.nomnoml.com/]] to render)
  *
  *  {{{
  *  [Functor2]<--[Bifunctor2]
  *  [Bifunctor2]<--[ApplicativeError2]
  *  [Functor2]<--[Applicative2]
  *  [Applicative2]<--[Guarantee2]
  *  [Applicative2]<--[Monad2]
  *  [Guarantee2]<--[ApplicativeError2]
  *  [ApplicativeError2]<--[Error2]
  *  [Error2]<--[WeakTemporal2]
  *  [WeakTemporal2]<--[Temporal2]
  *  [Monad2]<--[Error2]
  *  [Error2]<--[Bracket2]
  *  [Bracket2]<--[Panic2]
  *  [Panic2]<--[IO2]
  *  [IO2]<--[WeakAsync2]
  *  [WeakAsync2]<--[Async2]
  *
  *  [Monad2]<--[Parallel2]
  *  [Parallel2]<--[WeakAsync2]
  *  [Panic2]<--[Concurrent2]
  *  [Parallel2]<--[Concurrent2]
  *  }}}
  *
  *  Auxiliary algebras (rendered: [[https://izumi.7mind.io/bio/media/algebras.svg]]):
  *
  *  {{{
  *  [cats.effect.*]<:--[CatsConversions]
  *
  *  [Fork2]<:--[Fiber2]
  *
  *  [BlockingIO2]
  *
  *  [Primitives2]
  *
  *  [Primitives2]<:--[Ref2]
  *  [Primitives2]<:--[Semaphore2]
  *  [Primitives2]<:--[Promise2]
  *
  *  [PrimitivesM2]
  *  [PrimitivesM2]<:--[RefM2]
  *  [PrimitivesM2]<:--[Mutex2]
  *
  *  [PrimitivesLocal2]
  *  [PrimitivesLocal2]<--[FiberRef2]
  *  [PrimitivesLocal2]<--[FiberLocal2]
  *
  *  [Entropy1]<:--[Entropy2]
  *  [Clock1]<:--[Clock2]
  *
  *  [Scheduler2]
  *
  *  [UnsafeRun2]
  *  }}}
  *
  *  Raw inheritance hierarchy (rendered: [[https://izumi.7mind.io/bio/media/bio-hierarchy.svg]]):
  *
  *  {{{
  *  [Bifunctor2]<--[ApplicativeError2]
  *  [Functor2]<--[Applicative2]
  *  [Applicative2]<--[Guarantee2]
  *  [Applicative2]<--[Monad2]
  *  [Guarantee2]<--[ApplicativeError2]
  *  [ApplicativeError2]<--[Error2]
  *  [Monad2]<--[Error2]
  *  [Error2]<--[Bracket2]
  *  [Bracket2]<--[Panic2]
  *  [Panic2]<--[IO2]
  *
  *  [Parallel2]<--[WeakAsync2]
  *  [Parallel2]<--[Concurrent2]
  *  [Concurrent2]<--[Async2]
  *  [IO2]<--[WeakAsync2]
  *  [WeakAsync2]<--[Async2]
  *
  *  [WeakTemporal2]<--[Temporal2]
  *  }}}
  *
  *  current hierarchy roots:
  *
  *  bifunctor:
  *  - [[Functor2]]
  *  - [[Bifunctor2]]
  *  - [[Parallel2]]
  *  - [[WeakTemporal2]]
  *
  *  standalone:
  *  - [[Fork2]]
  *  - [[BlockingIO2]]
  *  - [[Primitives2]]
  *  - [[PrimitivesM2]]
  *
  *  @note New BIO typeclass checklist:
  *  {{{
  *  [ ] - add syntax to `Syntax2` with the same name as the type
  *  [ ] - add syntax for new root's `InnerF` with the same name as `InnerF` in `Syntax2`
  *  [ ] - add new attachments in `RootInstanceLowPriority*`
  *  [ ] - add conversion from itself to its `InnerF` to `RootInstanceLowPriority*`
  *        (conversions implicit priority: from most specific `InnerF` to least specific)
  *  [ ] - add conversion to equivalent cats typeclass if applicable in `CatsConversions`
  *  [ ] - update hierarchy graph above, re-render SVG
  *  [ ] - add syntax tests in `SyntaxTest`, runtime tests if applicable
  *  }}}
  * @note Real and raw (direct inheritance) hierarchies differ because of implicit ambiguities caused by
  *       inheritance: [[https://typelevel.org/blog/2016/09/30/subtype-typeclasses.html]]
  *
  *       However, since Scala 3.7, the ambiguity problem has been resolved on Scala 3 using inverted given prioritization:
  *       [[https://contributors.scala-lang.org/t/joining-the-dots-on-recent-implicit-prioritization-changes-and-some-scala-history/6814/3]]
  *
  *       So, when or if we drop support for Scala 2, we can revisit the design, remove `InnerF` pattern and make
  *       real and raw hierarchy match.
  */
package object bio extends Syntax2 {

  /**
    * A convenient dependent-typed summoner for BIO hierarchy.
    * Auto-narrows to the most powerful available type class:
    *
    * {{{
    *   import izumi.functional.bio.{F, Temporal2}
    *
    *   def y[F[+_, +_]: Temporal2] = {
    *     F.timeout(5.seconds)(F.unit)
    *   }
    * }}}
    */
  @inline override final def F[F[+_, +_]](implicit F: Functor2[F]): F.type = F

  type TransZio[F[_, _]] = Isomorphism2[F, zio.IO]
  object TransZio {
    @inline def apply[F[_, _]: TransZio]: TransZio[F] = implicitly
  }

  type Ref2[+F[_, _], A] = Ref1[F[Nothing, _], A]
  lazy val Ref2: Ref1.type = Ref1

  type Latch2[+F[+_, +_]] = Promise2[F, Nothing, Unit]
  lazy val Latch2: Promise2.type = Promise2

  type Semaphore2[+F[_, _]] = Semaphore1[F[Nothing, _]]
  lazy val Semaphore2: Semaphore1.type = Semaphore1

  type SyncSafe2[F[_, _]] = SyncSafe1[F[Nothing, _]]
  object SyncSafe2 {
    @inline def apply[F[_, _]: SyncSafe2]: SyncSafe2[F] = implicitly
  }
  type SyncSafe3[F[_, _, _]] = SyncSafe1[F[Any, Nothing, _]]
  object SyncSafe3 {
    @inline def apply[F[_, _, _]: SyncSafe3]: SyncSafe3[F] = implicitly
  }

  type Clock2[F[_, _]] = Clock1[F[Nothing, _]]
  object Clock2 {
    @inline def apply[F[_, _]: Clock2]: Clock2[F] = implicitly
  }
  type Clock3[F[_, _, _]] = Clock1[F[Any, Nothing, _]]
  object Clock3 {
    @inline def apply[F[_, _, _]: Clock3]: Clock3[F] = implicitly
  }

  type Entropy2[F[_, _]] = Entropy1[F[Nothing, _]]
  object Entropy2 {
    @inline def apply[F[_, _]: Entropy2]: Entropy2[F] = implicitly
  }
  type Entropy3[F[_, _, _]] = Entropy1[F[Any, Nothing, _]]
  object Entropy3 {
    @inline def apply[F[_, _, _]: Entropy3]: Entropy3[F] = implicitly
  }

}
