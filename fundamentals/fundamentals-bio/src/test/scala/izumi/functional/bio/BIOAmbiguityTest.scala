package izumi.functional.bio

import org.scalatest.wordspec.AnyWordSpec
import izumi.fundamentals.platform.language.IzScala
import org.scalatest.exceptions.TestFailedException

class BIOAmbiguityTest extends AnyWordSpec {

  def worksOnlyOnScala3(test: => Any): Unit = {
    if (IzScala.scalaRelease.major == 2) {
      intercept[TestFailedException](test)
    } else {
      test
    }
    ()
  }

  "On Scala 2 + 3" should {

    "NOT compile Monad2 + Guarantee2 without explicit Applicative2" in {
      assertDoesNotCompile("""
        def test[F[+_, +_]: Monad2: Guarantee2]: Any = {
          implicitly[Applicative2[F]]
        }
      """)
    }

    "NOT compile ApplicativeError2 + Monad2 without explicit Functor2" in {
      assertDoesNotCompile("""
        def test[F[+_, +_]: ApplicativeError2: Monad2]: Any = {
          implicitly[Functor2[F]]
        }
      """)
    }

    "WeakAsync2 + Concurrent2 causes ambiguity for Parallel2" in {
      assertDoesNotCompile("""
        def test[F[+_, +_]: WeakAsync2: Concurrent2]: Any = {
          implicitly[Parallel2[F]]
        }
      """)
    }

    "compile with InnerF pattern preventing ambiguity" in {
      assertCompiles("""
        def test[F[+_, +_]: ApplicativeError2: Bifunctor2]: Any = {
          implicitly[Functor2[F]]
        }
      """)
    }
  }

  "On Scala 3, with 3.7+ given prioritization" should {

    "compile Monad2 + Guarantee2 with explicit Applicative2 parent" in {
      worksOnlyOnScala3 {
        assertCompiles("""
        def test[F[+_, +_]: Monad2: Guarantee2: Applicative2]: Any = {
          implicitly[Applicative2[F]]
        }
      """)
      }
    }

    "compile ApplicativeError2 + Monad2 with explicit Functor2 parent" in {
      worksOnlyOnScala3 {
        assertCompiles("""
        def test[F[+_, +_]: ApplicativeError2: Monad2: Functor2]: Any = {
          implicitly[Functor2[F]]
        }
      """)
      }
    }

    "compile WeakAsync2 + Concurrent2 Parallel2 with explicit Parallel2 parent" in {
      worksOnlyOnScala3 {
        assertCompiles("""
        def test[F[+_, +_]: Parallel2: WeakAsync2: Concurrent2]: Any = {
          implicitly[Parallel2[F]]
        }
      """)
      }
    }

    "NOT work with old implicit syntax even with explicit parent" in {
      assertDoesNotCompile("""
        def test[F[+_, +_]](implicit M: Monad2[F], G: Guarantee2[F], A: Applicative2[F]): Any = {
          implicitly[Applicative2[F]]
        }
      """)
    }

  }

}
