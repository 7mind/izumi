package izumi.functional.bio.env

import cats.Eq
import izumi.functional.bio.IO2
import izumi.functional.bio.impl.MiniBIO
import org.scalacheck.Arbitrary

import scala.util.Try

trait MiniBIOEnv {
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
    new Eq[Try[A]] {
      val optA: Eq[Option[A]] = Eq[Option[A]]
      override def eqv(x: Try[A], y: Try[A]): Boolean =
        if (x.isSuccess) optA.eqv(x.toOption, y.toOption)
        else y.isFailure
    }
}
