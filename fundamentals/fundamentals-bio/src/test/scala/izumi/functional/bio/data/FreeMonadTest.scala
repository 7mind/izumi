package izumi.functional.bio.data

import java.util.concurrent.atomic.AtomicReference

import izumi.functional.bio.{BIO, BIORunner, F}
import org.scalatest.wordspec.AnyWordSpec
import zio.IO
import zio.internal.Platform

class FreeMonadTest extends AnyWordSpec {
  import FreeMonadTest._
  val syntax = new TestFreeSyntax[IO]
  val simpleExecution: FreePanic[TestFreeChoice, Nothing, Unit] = {
    for {
      res <- syntax.pure(1)
      _ <- syntax.sync(assert(res == 1))
      _ <- syntax.scopeUpdate(_ => 100)
      _ <- syntax.scopeAccess(res => assert(res == 100))
      _ <- syntax.sync(throw new RuntimeException("Sandbox test")).sandbox.flip
      _ <- syntax
        .sync(0).bracket(_ => syntax.scopeUpdate(_ => 1000)) {
          _ => syntax.fail(new RuntimeException("Bracket test"))
        }.catchAll(_ => syntax.unit)
      _ <- syntax.scopeAccess(res => assert(res == 1000))
    } yield ()
  }
  // tailrec test, just in case
  val nested: FreePanic[TestFreeChoice, Nothing, Unit] = List.fill(100000)(simpleExecution).reduce((f1, f2) => f1.flatMap(_ => f2))

  "Interpret Free and run it via bio" in {
    val runner = BIORunner.createZIO(Platform.default)
    runner.unsafeRun(FreeMonadTest.compiler[IO].flatMap(simpleExecution.foldMap(_)))
    runner.unsafeRun(FreeMonadTest.compiler[IO].flatMap(nested.foldMap(_)))
  }
}

object FreeMonadTest {
  sealed trait TestFreeChoice[+E, +A] {
    def interpret[F[+_, +_]: BIO](scope: AtomicReference[Int]): F[E, A] = TestFreeChoice.interpret[F, E, A](scope)(this)
  }
  object TestFreeChoice {
    final case class Pure[+A](execution: A) extends TestFreeChoice[Nothing, A]
    final case class Fail[+E](error: E) extends TestFreeChoice[E, Nothing]
    final case class Sync[+A](execution: () => A) extends TestFreeChoice[Nothing, A]
    final case class ScopeUpdate(update: Int => Int) extends TestFreeChoice[Nothing, Unit]
    final case class ScopeAccess[+A](execution: Int => A) extends TestFreeChoice[Nothing, A]

    private def interpret[F[+_, +_]: BIO, E, A](scope: AtomicReference[Int])(op: TestFreeChoice[E, A]): F[E, A] = op match {
      case TestFreeChoice.Pure(execution) => F.pure(execution)
      case TestFreeChoice.Fail(err) => F.fail(err)
      case TestFreeChoice.Sync(execution) => F.sync(execution())
      case TestFreeChoice.ScopeUpdate(update) => F.sync(scope.updateAndGet(update(_))).void
      case TestFreeChoice.ScopeAccess(execution) => F.sync(execution(scope.get))
    }
  }

  final class TestFreeSyntax[F[+_, +_]] {
    def pure[A](a: A): FreePanic[TestFreeChoice, Nothing, A] = FreePanic.lift(TestFreeChoice.Pure(a))
    def unit: FreePanic[TestFreeChoice, Nothing, Unit] = FreePanic.lift(TestFreeChoice.Pure(()))
    def fail[E](a: E): FreePanic[TestFreeChoice, E, Nothing] = FreePanic.lift(TestFreeChoice.Fail(a))
    def sync[A](execution: => A): FreePanic[TestFreeChoice, Nothing, A] = FreePanic.lift(TestFreeChoice.Sync(() => execution))
    def scopeUpdate(update: Int => Int): FreePanic[TestFreeChoice, Nothing, Unit] = FreePanic.lift(TestFreeChoice.ScopeUpdate(update))
    def scopeAccess[A](execution: Int => A): FreePanic[TestFreeChoice, Nothing, A] = FreePanic.lift(TestFreeChoice.ScopeAccess(execution))
  }

  def compiler[F[+_, +_]: BIO]: F[Nothing, TestFreeChoice ~>> F] = {
    F.sync {
      val scope = new AtomicReference[Int](0)
      FunctionKK(_.interpret(scope))
    }
  }
}
