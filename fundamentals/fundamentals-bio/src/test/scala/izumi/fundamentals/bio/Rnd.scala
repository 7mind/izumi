package izumi.fundamentals.bio

import cats.Eq
import izumi.functional.bio.BIO
import izumi.functional.bio.impl.MiniBIO
import org.scalacheck.Arbitrary

trait Rnd  {
  implicit def arbMiniBIO[A](implicit arb: Arbitrary[A]): Arbitrary[MiniBIO[Throwable, A]] = Arbitrary {
    Arbitrary.arbBool.arbitrary.flatMap {
      if (_)
        arb.arbitrary.map(BIO[MiniBIO].pure(_))
      else
        Arbitrary.arbThrowable.arbitrary.map(BIO[MiniBIO].fail(_))
    }
  }

  implicit def arbMonixBIO[A](implicit arb: Arbitrary[A]): Arbitrary[monix.bio.Task[A]] = Arbitrary {
    Arbitrary.arbBool.arbitrary.flatMap {
      if (_)
        arb.arbitrary.map(BIO[monix.bio.IO].pure(_))
      else
        Arbitrary.arbThrowable.arbitrary.map(BIO[monix.bio.IO].fail(_))
    }
  }

//  implicit def arbZIO[A](implicit arb: Arbitrary[A]): Arbitrary[zio.Task[A]] = Arbitrary {
//    Arbitrary.arbBool.arbitrary.flatMap {
//      if (_)
//        arb.arbitrary.map(BIO[zio.IO].pure(_))
//      else
//        Arbitrary.arbThrowable.arbitrary.map(BIO[zio.IO].fail(_))
//    }
//  }


  implicit def eqMiniBIO[A](implicit eq: Eq[A]): Eq[MiniBIO[Throwable, A]] = Eq.instance {
    (l, r) => l.run() == r.run()
  }

  implicit def eqMonix[A](implicit eq: Eq[A]): Eq[monix.bio.Task[A]] = Eq.instance {
    import monix.execution.Scheduler.Implicits.global
    (l, r) => l.runSyncUnsafe() == r.runSyncUnsafe()
  }


//  implicit def eqZIO[A](implicit  eq: Eq[A]): Eq[zio.Task[A]] = Eq.instance {
//    (l, r) =>
//      val runtime = zio.Runtime.default
//      runtime.unsafeRun(l) == runtime.unsafeRun(r)
//  }

}
