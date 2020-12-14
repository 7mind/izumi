package izumi.functional.bio.laws.env

import cats.Eq
import izumi.functional.bio.IO2
import izumi.functional.bio.impl.MiniBIO
import org.scalacheck.Arbitrary

import scala.util.Try

trait MiniBIOEnv extends EqThrowable {
  implicit def arbMiniBIO[A](implicit arb: Arbitrary[A]): Arbitrary[MiniBIO[Throwable, A]] = Arbitrary {
    Arbitrary.arbBool.arbitrary.flatMap {
      if (_) arb.arbitrary.map(IO2[MiniBIO].pure(_))
      else Arbitrary.arbThrowable.arbitrary.map(IO2[MiniBIO].fail(_))
    }
  }

  implicit def eqMiniBIO[A](implicit eq: Eq[A]): Eq[MiniBIO[Throwable, A]] = Eq.instance {
    (l, r) =>
      val tl = Try(MiniBIO.autoRun.autoRunAlways(l))
      val tr = Try(MiniBIO.autoRun.autoRunAlways(r))
      equalityTry[A].eqv(tl, tr)
  }

  private[this] def equalityTry[A: Eq]: Eq[Try[A]] =
    Eq.by(_.toEither)
}
