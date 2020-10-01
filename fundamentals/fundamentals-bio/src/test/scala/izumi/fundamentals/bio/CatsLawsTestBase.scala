package izumi.fundamentals.bio

import cats.Eq
import cats.effect.laws.util.TestInstances
import cats.instances.option._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.prop.Configuration
import org.typelevel.discipline.scalatest.FunSuiteDiscipline

import scala.util.Try

trait CatsLawsTestBase extends AnyFunSuite with FunSuiteDiscipline with TestInstances with Configuration

object CatsLawsTestBase {
  implicit def equalityTry[A: Eq]: Eq[Try[A]] =
    new Eq[Try[A]] {
      import TestInstances.eqThrowable
      val optA = implicitly[Eq[Option[A]]]
      val optT = implicitly[Eq[Option[Throwable]]]

      def eqv(x: Try[A], y: Try[A]): Boolean =
        if (x.isSuccess) optA.eqv(x.toOption, y.toOption)
        else y.isFailure
    }
}
