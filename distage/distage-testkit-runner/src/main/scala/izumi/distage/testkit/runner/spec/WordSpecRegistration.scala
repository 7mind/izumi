package izumi.distage.testkit.runner.spec

import distage.{Functoid, TagK}
import izumi.distage.testkit.runner.TestCancelled
import izumi.distage.testkit.spec.DISyntaxBase
import izumi.functional.quasi.QuasiIO
import izumi.fundamentals.platform.language.{SourceFilePosition, SourceFilePositionMaterializer}

import scala.annotation.unused

private[spec] trait WordSpecRegistration[F[_]] extends DISyntaxBase[F] {
  protected def suite: DistageSpec[F]
  protected def text: String
  protected def position: RegistrationPosition
  protected def testPath: Vector[String]

  override implicit final def tagMonoIO: TagK[F] = suite.tagMonoIO

  infix final def should(body: => Unit): Unit = suite.branch(text, "should", () => body)
  infix final def must(body: => Unit): Unit = suite.branch(text, "must", () => body)
  infix final def can(body: => Unit): Unit = suite.branch(text, "can", () => body)

  infix final def in(function: Functoid[Unit])(implicit pos: SourceFilePositionMaterializer, d1: DummyImplicit, d2: DummyImplicit): Unit = takeAny(function, pos.get)
  infix final def in(value: => Unit)(implicit pos: SourceFilePositionMaterializer, d1: DummyImplicit, d2: DummyImplicit): Unit = takeAny(() => value, pos.get)

  infix final def skip(@unused value: => Any)(implicit pos: SourceFilePositionMaterializer): Unit = {
    takeFunIO[Nothing, QuasiIO[F]](F => F.maybeSuspend(throw new TestCancelled("test skipped")), pos.get)
  }

  override protected final def takeIO[A](function: Functoid[F[A]], pos: SourceFilePosition): Unit = suite.add(testPath, function, position.location, pos)
}
