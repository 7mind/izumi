package izumi.distage.impl

import izumi.functional.bio.impl.BioEither
import izumi.functional.bio.{Applicative2, ApplicativeError2, Async2, Bifunctor2, BlockingIO2, Bracket2, Concurrent2, Error2, Fork2, Functor2, Guarantee2, IO2, Monad2, Panic2, Parallel2, Primitives2, PrimitivesM2, Temporal2}
import org.scalatest.wordspec.AnyWordSpec

class OptionalDependencyTest213 extends AnyWordSpec {

  "Perform exhaustive search for BIO and not find instances for ZIO / monix-bio when they're not on classpath" in {
    final class optSearch2[C[_[+_, +_]]] { def find[F[+_, +_]](implicit a: C[F] = null.asInstanceOf[C[F]]): C[F] = a }

    assert(new optSearch2[IO2].find == null)

    assert(new optSearch2[Functor2].find == BioEither)
    assert(new optSearch2[Applicative2].find == BioEither)
    assert(new optSearch2[Monad2].find == BioEither)
    assert(new optSearch2[Bifunctor2].find == BioEither)
    assert(new optSearch2[Guarantee2].find == BioEither)
    assert(new optSearch2[ApplicativeError2].find == BioEither)
    assert(new optSearch2[Error2].find == BioEither)
    assert(new optSearch2[Bracket2].find == null)
    assert(new optSearch2[Panic2].find == null)
    assert(new optSearch2[Parallel2].find == null)
    assert(new optSearch2[IO2].find == null)
    assert(new optSearch2[Async2].find == null)
    assert(new optSearch2[Temporal2].find == null)
    assert(new optSearch2[Concurrent2].find == null)

    assert(new optSearch2[Fork2].find == null)
    assert(new optSearch2[Primitives2].find == null)
    assert(new optSearch2[PrimitivesM2].find == null)
    assert(new optSearch2[BlockingIO2].find == null)
  }

}
