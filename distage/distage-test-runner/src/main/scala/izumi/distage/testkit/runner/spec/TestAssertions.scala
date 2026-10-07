package izumi.distage.testkit.runner.spec

import izumi.distage.testkit.runner.TestCancelled
import izumi.fundamentals.assertions.*

import scala.reflect.ClassTag
import scala.language.implicitConversions

final class ReferenceIdentityOps[A](private val value: A) extends AnyVal {
  def ne(other: AnyRef): Boolean = value.asInstanceOf[AnyRef] ne other
}

trait TestAssertions extends Assertions with FrontendAssertions {
  implicit final def referenceIdentityOps[A](value: A): ReferenceIdentityOps[A] = new ReferenceIdentityOps(value)

  final def succeed: Assertion = ()

  final def fail(): Nothing = fail("Test failed")

  final def fail(message: String): Nothing = throw failure(message)

  final def cancel(message: String): Nothing = throw new TestCancelled(message)

  final def assume(condition: Boolean): Unit = {
    if (!condition) cancel("Assumption failed")
  }

  final def assume(condition: Boolean, clue: => Any): Unit = {
    if (!condition) cancel(s"Assumption failed: $clue")
  }

  final def fail(cause: Throwable): Nothing = {
    val assertion = failure(Option(cause.getMessage).getOrElse(cause.getClass.getName))
    val _ = assertion.initCause(cause)
    throw assertion
  }

  final def intercept[A <: Throwable: ClassTag](body: => Any): A = {
    val expected = implicitly[ClassTag[A]].runtimeClass
    val thrown = try { val _ = body; None } catch { case cause: Throwable => Some(cause) }
    thrown match {
      case Some(cause) if expected.isInstance(cause) => cause.asInstanceOf[A]
      case Some(cause) =>
        val assertion = failure(s"Expected ${expected.getName}, received ${cause.getClass.getName}")
        val _ = assertion.initCause(cause)
        throw assertion
      case None => fail(s"Expected ${expected.getName}, but no exception was thrown")
    }
  }

  final def assertThrows[A <: Throwable: ClassTag](body: => Any): Assertion = {
    val _ = intercept[A](body)
    ()
  }

  private def failure(message: String): AssertionFailure = new AssertionFailure(
    AssertionDiagnostic(ExpressionSource(SourceIdentity.Virtual("test-assertion"), SourceSpan.Unavailable, CompiledText.Available(message)), Vector.empty),
    AssertionContext.standard,
  )
}
