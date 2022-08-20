package izumi.functional.bio

import izumi.functional.bio.DivergenceHelper.{Divergent, Nondivergent}
import izumi.functional.bio.Entropy1.fromImpure
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.uuid.UUIDGen

import java.util.UUID
import scala.annotation.{nowarn, unused}
import scala.collection.compat.*
import scala.collection.generic.CanBuildFrom
import scala.language.implicitConversions
import scala.util.Random

trait Entropy1[F[_]] extends DivergenceHelper {
  def nextBoolean(): F[Boolean]
  def nextInt(): F[Int]
  /** @return 0 to `max` */
  def nextInt(max: Int): F[Int]
  def nextLong(): F[Long]
  /** @return 0L to `max` */
  def nextLong(max: Long): F[Long]
  /** @return 0.0f to 1.0f */
  def nextFloat(): F[Float]
  /** @return 0.0d to 1.0d */
  def nextDouble(): F[Double]
  def nextGaussian(): F[Double]

  def nextBytes(length: Int): F[Array[Byte]]
  def nextPrintableChar(): F[Char]
  def nextString(length: Int): F[String]

  /** @see [[izumi.fundamentals.platform.uuid.UUIDGen]] */
  def nextTimeUUID(): F[UUID]
  def nextUUID(): F[UUID]

  @nowarn("msg=CanBuildFrom")
  def shuffle[T, CC[X] <: IterableOnce[X]](xs: CC[T])(implicit bf: CanBuildFrom[CC[T], T, CC[T]]): F[CC[T]]

  def withSeed(seed: Long): Entropy1[F]
  def setSeed(seed: Long): F[Unit]

  def writeRandomBytes(bytes: Array[Byte]): F[Unit]

  @inline final def widen[G[x] >: F[x]]: Entropy1[G] = this
}

object Entropy1 extends LowPriorityEntropyInstances {
  def apply[F[_]: Entropy1]: Entropy1[F] = implicitly

  def fromImpure[F[_]: SyncSafe1](impureEntropy: Entropy1[Identity]): Entropy1[F] = fromImpureEntropy(impureEntropy, SyncSafe1[F])

  object Standard extends ScalaEntropy {
    override protected def random: Random = scala.util.Random

    override def nextTimeUUID(): UUID = UUIDGen.getTimeUUID()
    override def nextUUID(): UUID = UUID.randomUUID()
  }

  class Deterministic(seed: Long) extends ScalaEntropy {
    override protected val random: Random = new scala.util.Random(seed)

    override def nextTimeUUID(): UUID = UUIDGen.getTimeUUID(math.abs(random.nextLong()))
    override def nextUUID(): UUID = new UUID(random.nextLong(), random.nextLong())
  }

  trait ScalaEntropy extends Entropy1[Identity] {
    protected def random: scala.util.Random

    @nowarn("msg=CanBuildFrom")
    override def shuffle[T, CC[X] <: IterableOnce[X]](xs: CC[T])(implicit bf: CanBuildFrom[CC[T], T, CC[T]]): CC[T] = {
      random.shuffle(xs)
    }

    override def withSeed(seed: Long): Entropy1[Identity] = new Deterministic(seed)

    override def nextBoolean(): Boolean = random.nextBoolean()
    override def nextFloat(): Float = random.nextFloat()
    override def nextDouble(): Double = random.nextDouble()
    override def nextGaussian(): Double = random.nextGaussian()
    override def nextInt(): Int = random.nextInt()
    override def nextInt(max: Int): Int = random.nextInt(max)
    override def nextLong(): Long = random.nextLong()
    override def nextLong(max: Long): Long = {
      if (max <= 0) throw new IllegalArgumentException("bound must be positive")
      math.abs(random.nextLong()) % max
    }
    override def nextPrintableChar(): Char = random.nextPrintableChar()
    override def nextString(length: Int): String = random.nextString(length)
    override def nextBytes(length: Int): Array[Byte] = {
      val bytes = new Array[Byte](0 max length)
      random.nextBytes(bytes)
      bytes
    }
    override def writeRandomBytes(bytes: Array[Byte]): Unit = random.nextBytes(bytes)
    override def setSeed(seed: Long): Unit = random.setSeed(seed)
  }

  @inline implicit final def impureEntropy: Entropy1[Identity] = Standard

  /**
    * Emulate covariance. We're forced to employ these because
    * we can't make Entropy covariant, because covariant implicits
    * are broken (see scalac bug)
    *
    * Safe because `F` appears only in a covariant position
    *
    * @see https://github.com/scala/bug/issues/11427
    */
  @inline implicit final def limitedCovariance2[C[f[_]] <: Entropy1[f], FR[_, _], R0](
    implicit F: C[FR[Nothing, _]] { type Divergence = Nondivergent }
  ): Divergent.Of[C[FR[R0, _]]] = {
    Divergent(F.asInstanceOf[C[FR[R0, _]]])
  }

  @inline implicit final def limitedCovariance3[C[f[_]] <: Entropy1[f], FR[_, _, _], R0, E](
    implicit F: C[FR[Any, Nothing, _]] { type Divergence = Nondivergent }
  ): Divergent.Of[C[FR[R0, E, _]]] = {
    Divergent(F.asInstanceOf[C[FR[R0, E, _]]])
  }

  @inline implicit final def covarianceConversion[F[_], G[_]](entropy: Entropy1[F])(implicit @unused ev: F[Unit] <:< G[Unit]): Entropy1[G] = {
    entropy.asInstanceOf[Entropy1[G]]
  }

}

sealed trait LowPriorityEntropyInstances {

  @inline implicit final def fromImpureEntropy[F[_]](implicit impureEntropy: Entropy1[Identity], F: SyncSafe1[F]): Entropy1[F] = {
    new Entropy1[F] {
      override def nextBoolean(): F[Boolean] = F.syncSafe(impureEntropy.nextBoolean())
      override def nextDouble(): F[Double] = F.syncSafe(impureEntropy.nextDouble())
      override def nextFloat(): F[Float] = F.syncSafe(impureEntropy.nextFloat())
      override def nextGaussian(): F[Double] = F.syncSafe(impureEntropy.nextGaussian())
      override def nextLong(): F[Long] = F.syncSafe(impureEntropy.nextLong())
      override def nextLong(max: Long): F[Long] = F.syncSafe(impureEntropy.nextLong(max))
      override def nextInt(): F[Int] = F.syncSafe(impureEntropy.nextInt())
      override def nextInt(max: Int): F[Int] = F.syncSafe(impureEntropy.nextInt(max))
      override def nextBytes(length: Int): F[Array[Byte]] = F.syncSafe(impureEntropy.nextBytes(length))
      override def nextPrintableChar(): F[Char] = F.syncSafe(impureEntropy.nextPrintableChar())
      override def nextString(length: Int): F[String] = F.syncSafe(impureEntropy.nextString(length))
      override def setSeed(seed: Long): F[Unit] = F.syncSafe(impureEntropy.setSeed(seed))
      override def nextTimeUUID(): F[UUID] = F.syncSafe(impureEntropy.nextTimeUUID())
      override def nextUUID(): F[UUID] = F.syncSafe(impureEntropy.nextUUID())
      override def writeRandomBytes(bytes: Array[Byte]): F[Unit] = F.syncSafe(impureEntropy.writeRandomBytes(bytes))
      override def withSeed(seed: Long): Entropy1[F] = fromImpure(impureEntropy.withSeed(seed))

      @nowarn("msg=CanBuildFrom")
      override def shuffle[T, CC[X] <: IterableOnce[X]](xs: CC[T])(implicit bf: CanBuildFrom[CC[T], T, CC[T]]): F[CC[T]] =
        F.syncSafe(impureEntropy.shuffle[T, CC](xs))
    }
  }

}
