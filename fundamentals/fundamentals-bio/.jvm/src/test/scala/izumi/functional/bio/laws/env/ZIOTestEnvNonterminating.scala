//package izumi.functional.bio.laws.env
//
//import cats.Eq
//import cats.effect.Effect
//import cats.effect.laws.discipline.arbitrary.catsEffectLawsArbitraryForIO
//import cats.effect.laws.util.{TestContext, TestInstances}
//import izumi.functional.bio.{IO2, UnsafeRun2}
//import org.scalacheck.{Arbitrary, Cogen}
//import zio.{Cause, IO, Task, UIO}
//
//trait ZIOTestEnvTerminating extends ZIOTestEnvBase {
//  implicit def eqTask[A](implicit eq: Eq[A], unsafeRun2: UnsafeRun2[IO]): Eq[Task[A]] = {
//    Eq.by(io => unsafeRun2.unsafeRunSync(io).toThrowableEither)
//  }
//
//  implicit def eqCatsIO[A](implicit A: Eq[A]): Eq[cats.effect.IO[A]] = Eq.by(_.attempt.unsafeRunSync())
//}
//
//trait ZIOTestEnvNonterminating extends ZIOTestEnvBase {
//  implicit def eqCauseNothing: Eq[Cause[Nothing]] = Eq.fromUniversalEquals
//
//  implicit def eqCatsIO[A](implicit A: Eq[A], ec: TestContext): Eq[cats.effect.IO[A]] = TestInstances.eqIO
//
//  implicit def eqIO[E: Eq, A: Eq](implicit effect: Effect[Task], tc: TestContext): Eq[IO[E, A]] =
//    Eq.by(_.either)
//
//  implicit def eqTask[A: Eq](implicit effect: Effect[Task], tc: TestContext): Eq[Task[A]] =
//    Eq.by(_.either)
//
//  implicit def eqUIO[A: Eq](implicit effect: Effect[Task], tc: TestContext): Eq[UIO[A]] =
//    Eq.by(uio => effect.toIO(uio.sandbox.either))
//
//}
//
//trait ZIOTestEnvBase extends EqThrowable {
//  implicit def arbCatsIO[A: Arbitrary: Cogen]: Arbitrary[cats.effect.IO[A]] =
//    catsEffectLawsArbitraryForIO
//
//  implicit def arbTask[A](implicit arb: Arbitrary[A]): Arbitrary[Task[A]] = Arbitrary {
//    Arbitrary.arbBool.arbitrary.flatMap {
//      if (_) arb.arbitrary.map(IO2[IO].pure(_))
//      else Arbitrary.arbThrowable.arbitrary.map(IO2[IO].fail(_))
//    }
//  }
//}
