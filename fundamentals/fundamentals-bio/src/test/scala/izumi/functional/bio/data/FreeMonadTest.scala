package izumi.functional.bio.data

import izumi.functional.bio.{BIO, BIORunner, F}
import org.scalatest.wordspec.AnyWordSpec
import zio.IO
import zio.internal.Platform

class FreeMonadTest extends AnyWordSpec {
  import FreeMonadTest._
  val interpreter = new TestFreeInterpreter[IO]
  val syntax = new TestFreeSyntax[IO]
  val simpleExecution: Free[IO, TestFreeChoice, Nothing, Unit] = {
    for {
      res <- syntax.pure(1)
      _ <- syntax.sync(assert(res == 1))
      _ <- syntax.scopeUpdate(_ => 100)
      _ <- syntax.scopeAccess(res => assert(res == 100))
    } yield ()
  }

  "Interpret Free and run it via bio" in {
    val runner = BIORunner.createZIO(Platform.default)
    val interpreted = simpleExecution.execute(0, interpreter)
    runner.unsafeRun(interpreted)
  }
}

object FreeMonadTest {
  sealed trait TestFreeChoice[F[+_, +_], E, A]
  object TestFreeChoice {
    final case class Pure[F[+_, +_], A](execution: A) extends TestFreeChoice[F, Nothing, A]
    sealed trait Sync[F[+_, +_], A] extends TestFreeChoice[F, Nothing, A] { def execution: A }
    object Sync {
      def apply[F[+_, +_], A](exec: => A): Sync[F, A] = new Sync[F, A] {
        override def execution: A = exec
      }
    }
    final case class ScopeUpdate[F[+_, +_], A](update: Int => Int) extends TestFreeChoice[F, Nothing, A]
    final case class ScopeAccess[F[+_, +_], A](execution: Int => A) extends TestFreeChoice[F, Nothing, A]
    implicit val applicative: FreeApplicative[TestFreeChoice] = new FreeApplicative[TestFreeChoice] {
      override def pure[F[+_, +_], A](a: => A): TestFreeChoice[F, Nothing, A] = Pure(a)
    }
  }

  final class TestFreeSyntax[F[+_, +_]] {
    def pure[A](a: A): Free[F, TestFreeChoice, Nothing, A] = Free.lift(TestFreeChoice.Pure(a))
    def sync[A](execution: => A): Free[F, TestFreeChoice, Nothing, A] = Free.lift(TestFreeChoice.Sync(execution))
    def scopeUpdate(update: Int => Int): Free[F, TestFreeChoice, Nothing, Unit] = Free.lift(TestFreeChoice.ScopeUpdate(update))
    def scopeAccess[A](execution: Int => A): Free[F, TestFreeChoice, Nothing, A] = Free.lift(TestFreeChoice.ScopeAccess(execution))
  }

  final class TestFreeInterpreter[F[+_, +_]: BIO] extends FreeInterpreter[F, TestFreeChoice, Int] {
    override def interpret[E, A](scope: Int, s: TestFreeChoice[F, E, A]): F[Nothing, FreeInterpreter.Result[F, TestFreeChoice, Int]] = s match {
      case TestFreeChoice.Pure(execution) =>
        F.pure(FreeInterpreter.Result.Success(scope, execution))
      case sync: TestFreeChoice.Sync[F, A] =>
        F.sync(FreeInterpreter.Result.Success(scope, sync.execution))
      case TestFreeChoice.ScopeAccess(execution) =>
        F.sync(FreeInterpreter.Result.Success(scope, execution(scope)))
      case TestFreeChoice.ScopeUpdate(update) =>
        F.sync(FreeInterpreter.Result.Success(update(scope), ()))
    }
  }
}
