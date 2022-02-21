package izumi.functional.bio.test

import izumi.functional.bio.Clock1.ClockAccuracy
import izumi.functional.bio.{Clock1, Clock2, Clock3, Entropy1, Entropy2, Entropy3, SyncSafe1, SyncSafe2, SyncSafe3}
import org.scalatest.wordspec.AnyWordSpec

import java.time.{LocalDateTime, OffsetDateTime, ZonedDateTime}
import java.util.UUID
import scala.annotation.nowarn
import scala.annotation.unchecked.uncheckedVariance
import scala.collection.generic.CanBuildFrom

final class ArityConversionTest extends AnyWordSpec {

  "arity conversions" should {

    "not diverge for SyncSafe" in {
      noImplicit[SyncSafe1[Either[String, _]]]
      noImplicit[SyncSafe2[Either]]
      noImplicit[SyncSafe3[EitherR]]
    }

    "work for SyncSafe3" in {
      assert(implicitly[SyncSafe3[EitherRX]] != null)

      assert(implicitly[SyncSafe2[EitherRX[Any, _, _]]] != null)
      assert(implicitly[SyncSafe1[EitherRX[Any, Nothing, _]]] != null)

      assert(implicitly[SyncSafe1[EitherRX[Any, Int, _]]] != null)
      assert(implicitly[SyncSafe1[EitherRX[Any, Any, _]]] != null)

      assert(implicitly[SyncSafe2[EitherRX[Int, _, _]]] != null)
      assert(implicitly[SyncSafe2[EitherRX[Nothing, _, _]]] != null)

      assert(implicitly[SyncSafe1[EitherRX[Nothing, Any, _]]] != null)
      assert(implicitly[SyncSafe1[EitherRX[Nothing, Nothing, _]]] != null)
      assert(implicitly[SyncSafe1[EitherRX[Nothing, Int, _]]] != null)

      assert(implicitly[SyncSafe1[EitherRX[Int, Any, _]]] != null)
      assert(implicitly[SyncSafe1[EitherRX[Int, Nothing, _]]] != null)
      assert(implicitly[SyncSafe1[EitherRX[Int, Int, _]]] != null)
    }

    "work for SyncSafe2" in {
      assert(implicitly[SyncSafe2[EitherX]] != null)

      assert(implicitly[SyncSafe1[EitherX[Nothing, _]]] != null)
      assert(implicitly[SyncSafe1[EitherX[Int, _]]] != null)
      assert(implicitly[SyncSafe1[EitherX[Any, _]]] != null)
    }

    "not diverge for Clock" in {
      noImplicit[Clock1[Either[String, _]]]
      noImplicit[Clock2[Either]]
      noImplicit[Clock3[EitherR]]
    }

    "work for Clock3" in {
      assert(implicitly[Clock3[EitherRX]] != null)

      assert(implicitly[Clock2[EitherRX[Any, _, _]]] != null)
      assert(implicitly[Clock1[EitherRX[Any, Nothing, _]]] != null)

      assert(implicitly[Clock1[EitherRX[Any, Int, _]]] != null)
      assert(implicitly[Clock1[EitherRX[Any, Any, _]]] != null)

      assert(implicitly[Clock2[EitherRX[Int, _, _]]] != null)
      assert(implicitly[Clock2[EitherRX[Nothing, _, _]]] != null)

      assert(implicitly[Clock1[EitherRX[Nothing, Any, _]]] != null)
      assert(implicitly[Clock1[EitherRX[Nothing, Nothing, _]]] != null)
      assert(implicitly[Clock1[EitherRX[Nothing, Int, _]]] != null)

      assert(implicitly[Clock1[EitherRX[Int, Any, _]]] != null)
      assert(implicitly[Clock1[EitherRX[Int, Nothing, _]]] != null)
      assert(implicitly[Clock1[EitherRX[Int, Int, _]]] != null)
    }

    "work for Clock2" in {
      assert(implicitly[Clock2[EitherX]] != null)

      assert(implicitly[Clock1[EitherX[Nothing, _]]] != null)
      assert(implicitly[Clock1[EitherX[Int, _]]] != null)
      assert(implicitly[Clock1[EitherX[Any, _]]] != null)
    }

    "not diverge for Entropy" in {
      noImplicit[Entropy1[Either[String, _]]]
      noImplicit[Entropy2[Either]]
      noImplicit[Entropy3[EitherR]]
    }

    "work for Entropy3" in {
      assert(implicitly[Entropy3[EitherRX]] != null)

      assert(implicitly[Entropy2[EitherRX[Any, _, _]]] != null)
      assert(implicitly[Entropy1[EitherRX[Any, Nothing, _]]] != null)

      assert(implicitly[Entropy1[EitherRX[Any, Int, _]]] != null)
      assert(implicitly[Entropy1[EitherRX[Any, Any, _]]] != null)

      assert(implicitly[Entropy2[EitherRX[Int, _, _]]] != null)
      assert(implicitly[Entropy2[EitherRX[Nothing, _, _]]] != null)

      assert(implicitly[Entropy1[EitherRX[Nothing, Any, _]]] != null)
      assert(implicitly[Entropy1[EitherRX[Nothing, Nothing, _]]] != null)
      assert(implicitly[Entropy1[EitherRX[Nothing, Int, _]]] != null)

      assert(implicitly[Entropy1[EitherRX[Int, Any, _]]] != null)
      assert(implicitly[Entropy1[EitherRX[Int, Nothing, _]]] != null)
      assert(implicitly[Entropy1[EitherRX[Int, Int, _]]] != null)
    }

    "work for Entropy2" in {
      assert(implicitly[Entropy2[EitherX]] != null)

      assert(implicitly[Entropy1[EitherX[Nothing, _]]] != null)
      assert(implicitly[Entropy1[EitherX[Int, _]]] != null)
      assert(implicitly[Entropy1[EitherX[Any, _]]] != null)
    }

  }

  implicit def noImplicit[A](implicit Null: A = null): Boolean = Null == null

  type EitherR[R, E, A] = R => Either[E, A]

  type EitherRX[-R, +E, +A] >: R => Either[E @uncheckedVariance, A @uncheckedVariance]
  implicit val syncSafeEitherRX: SyncSafe3[EitherRX] = new SyncSafe3[EitherRX] {
    override def syncSafe[A](unexceptionalEff: => A): EitherR[Any, Nothing, A] = _ => Right(unexceptionalEff)
  }
  implicit val clockEitherRX: Clock3[EitherRX] = new Clock3[EitherRX] {
    override def epoch: EitherRX[Any, Nothing, Long] = ???
    override def now(accuracy: ClockAccuracy): EitherRX[Any, Nothing, ZonedDateTime] = ???
    override def nowLocal(accuracy: ClockAccuracy): EitherRX[Any, Nothing, LocalDateTime] = ???
    override def nowOffset(accuracy: ClockAccuracy): EitherRX[Any, Nothing, OffsetDateTime] = ???
  }
  @nowarn("msg=CanBuildFrom")
  implicit val entropyEitherRX: Entropy3[EitherRX] = new Entropy3[EitherRX] {
    override def nextBoolean(): EitherRX[Any, Nothing, Boolean] = ???
    override def nextInt(): EitherRX[Any, Nothing, Int] = ???
    override def nextInt(max: Int): EitherRX[Any, Nothing, Int] = ???
    override def nextLong(): EitherRX[Any, Nothing, Long] = ???
    override def nextLong(max: Long): EitherRX[Any, Nothing, Long] = ???
    override def nextFloat(): EitherRX[Any, Nothing, Float] = ???
    override def nextDouble(): EitherRX[Any, Nothing, Double] = ???
    override def nextGaussian(): EitherRX[Any, Nothing, Double] = ???
    override def nextBytes(length: Int): EitherRX[Any, Nothing, Array[Byte]] = ???
    override def nextPrintableChar(): EitherRX[Any, Nothing, Char] = ???
    override def nextString(length: Int): EitherRX[Any, Nothing, String] = ???
    override def nextTimeUUID(): EitherRX[Any, Nothing, UUID] = ???
    override def nextUUID(): EitherRX[Any, Nothing, UUID] = ???
    override def shuffle[T, CC[X] <: IterableOnce[X]](xs: CC[T])(implicit bf: CanBuildFrom[CC[T], T, CC[T]]): EitherRX[Any, Nothing, CC[T]] = ???
    override def withSeed(seed: Long): Entropy3[EitherRX] = ???
    override def setSeed(seed: Long): EitherRX[Any, Nothing, Unit] = ???
    override def writeRandomBytes(bytes: Array[Byte]): EitherRX[Any, Nothing, Unit] = ???
  }

  type EitherX[+E, +A] >: Either[E @uncheckedVariance, A @uncheckedVariance]
  implicit val syncSafeEitherX: SyncSafe2[EitherX] = new SyncSafe2[EitherX] {
    override def syncSafe[A](unexceptionalEff: => A): EitherX[Nothing, A] = Right(unexceptionalEff)
  }
  implicit val clockEitherX: Clock2[EitherX] = new Clock2[EitherX] {
    override def epoch: EitherX[Nothing, Long] = ???
    override def now(accuracy: ClockAccuracy): EitherX[Nothing, ZonedDateTime] = ???
    override def nowLocal(accuracy: ClockAccuracy): EitherX[Nothing, LocalDateTime] = ???
    override def nowOffset(accuracy: ClockAccuracy): EitherX[Nothing, OffsetDateTime] = ???
  }
  @nowarn("msg=CanBuildFrom")
  implicit val entropyEitherX: Entropy2[EitherX] = new Entropy2[EitherX] {
    override def nextBoolean(): EitherX[Nothing, Boolean] = ???
    override def nextInt(): EitherX[Nothing, Int] = ???
    override def nextInt(max: Int): EitherX[Nothing, Int] = ???
    override def nextLong(): EitherX[Nothing, Long] = ???
    override def nextLong(max: Long): EitherX[Nothing, Long] = ???
    override def nextFloat(): EitherX[Nothing, Float] = ???
    override def nextDouble(): EitherX[Nothing, Double] = ???
    override def nextGaussian(): EitherX[Nothing, Double] = ???
    override def nextBytes(length: Int): EitherX[Nothing, Array[Byte]] = ???
    override def nextPrintableChar(): EitherX[Nothing, Char] = ???
    override def nextString(length: Int): EitherX[Nothing, String] = ???
    override def nextTimeUUID(): EitherX[Nothing, UUID] = ???
    override def nextUUID(): EitherX[Nothing, UUID] = ???
    override def shuffle[T, CC[X] <: IterableOnce[X]](xs: CC[T])(implicit bf: CanBuildFrom[CC[T], T, CC[T]]): EitherX[Nothing, CC[T]] = ???
    override def withSeed(seed: Long): Entropy2[EitherX] = ???
    override def setSeed(seed: Long): EitherX[Nothing, Unit] = ???
    override def writeRandomBytes(bytes: Array[Byte]): EitherX[Nothing, Unit] = ???
  }

}
