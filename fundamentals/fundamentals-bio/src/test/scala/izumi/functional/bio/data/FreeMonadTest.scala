package izumi.functional.bio.data

import java.util.concurrent.atomic.AtomicReference

import izumi.functional.bio.{BIO, BIORunner, F}
import org.scalatest.wordspec.AnyWordSpec
import zio.IO
import zio.internal.Platform

class FreeMonadTest extends AnyWordSpec {
  import FreeMonadTest._
  val syntax = new TestFreeSyntax[IO]
  val simpleExecution: Free[TestFreeChoice, Nothing, Unit] = {
    for {
      res <- syntax.pure(1)
      _ <- syntax.sync(assert(res == 1))
      _ <- syntax.scopeUpdate(_ => 100)
      _ <- syntax.scopeAccess(res => assert(res == 100))
      _ <- syntax.sync(throw new RuntimeException("Sandbox test")).sandbox
      _ <- syntax
        .sync(0).bracket(_ => syntax.scopeUpdate(_ => 1000)) {
          _ => syntax.fail(new RuntimeException("Bracket test"))
        }.catchAll(_ => syntax.unit)
      _ <- syntax.scopeAccess(res => assert(res == 1000))
    } yield ()
  }
  // tailrec test, just in case
  val nested: Free[TestFreeChoice, Nothing, Unit] = List.fill(100000)(simpleExecution).reduce((f1,f2) => f1.flatMap(_ => f2))

  "Interpret Free and run it via bio" in {
    val runner = BIORunner.createZIO(Platform.default)
    runner.unsafeRun(simpleExecution.foldMap(FreeMonadTest.compiler[IO]))
    runner.unsafeRun(nested.foldMap(FreeMonadTest.compiler[IO]))
  }
}

object FreeMonadTest {
  sealed trait TestFreeChoice[E, A]
  object TestFreeChoice {
    final case class Pure[A](execution: A) extends TestFreeChoice[Nothing, A]
    final case class Fail[E](error: E) extends TestFreeChoice[E, Nothing]
    sealed trait Sync[A] extends TestFreeChoice[Nothing, A] { def execution: A }
    object Sync {
      def apply[F[+_, +_], A](exec: => A): Sync[A] = new Sync[A] {
        override def execution: A = exec
      }
    }
    final case class ScopeUpdate[A](update: Int => Int) extends TestFreeChoice[Nothing, A]
    final case class ScopeAccess[A](execution: Int => A) extends TestFreeChoice[Nothing, A]
  }

  final class TestFreeSyntax[F[+_, +_]] {
    def pure[A](a: A): Free[TestFreeChoice, Nothing, A] = Free.lift(TestFreeChoice.Pure(a))
    def unit: Free[TestFreeChoice, Nothing, Unit] = Free.lift(TestFreeChoice.Pure(()))
    def fail[E](a: E): Free[TestFreeChoice, E, Nothing] = Free.lift(TestFreeChoice.Fail(a))
    def sync[A](execution: => A): Free[TestFreeChoice, Nothing, A] = Free.lift(TestFreeChoice.Sync(execution))
    def scopeUpdate(update: Int => Int): Free[TestFreeChoice, Nothing, Unit] = Free.lift(TestFreeChoice.ScopeUpdate(update))
    def scopeAccess[A](execution: Int => A): Free[TestFreeChoice, Nothing, A] = Free.lift(TestFreeChoice.ScopeAccess(execution))
  }

  def compiler[F[+_, +_]: BIO]: TestFreeChoice ~>> F = new (TestFreeChoice ~>> F) {
    val scope = new AtomicReference[Int](0)
    override def apply[E, A](fa: TestFreeChoice[E, A]): F[E, A] = {
      fa match {
        case TestFreeChoice.Pure(execution) => F.pure(execution)
        case TestFreeChoice.Fail(err) => F.fail(err)
        case sync: TestFreeChoice.Sync[_] => F.sync(sync.execution)
        case TestFreeChoice.ScopeUpdate(update) => F.sync(scope.updateAndGet(update(_))).void
        case TestFreeChoice.ScopeAccess(execution) => F.sync(execution(scope.get))
      }
    }
  }
}
