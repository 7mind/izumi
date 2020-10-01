package izumi.fundamentals.bio.env

import cats.Eq
import izumi.functional.bio.BIO
import izumi.functional.bio.impl.MiniBIO
import izumi.fundamentals.bio.CatsLawsTestBase
import org.scalacheck.Arbitrary

import scala.util.Try

trait MiniBIOEnv {
  implicit def arbMiniBIO[A](implicit arb: Arbitrary[A]): Arbitrary[MiniBIO[Throwable, A]] = Arbitrary {
    Arbitrary.arbBool.arbitrary.flatMap {
      if (_)
        arb.arbitrary.map(BIO[MiniBIO].pure(_))
      else
        Arbitrary.arbThrowable.arbitrary.map(BIO[MiniBIO].fail(_))
    }
  }


  implicit def eqMiniBIO[A](implicit eq: Eq[A]): Eq[MiniBIO[Throwable, A]] = Eq.instance {
    (l, r) =>
      val tl = Try(MiniBIO.autoRun.autoRunAlways(l))
      val tr = Try(MiniBIO.autoRun.autoRunAlways(r))
      CatsLawsTestBase.equalityTry[A].eqv(tl, tr)
  }
}
