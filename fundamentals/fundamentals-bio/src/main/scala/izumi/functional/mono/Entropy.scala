package izumi.functional.mono

import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.uuid.UUIDGen

import java.util.UUID
import scala.annotation.nowarn
import scala.collection.compat._
import scala.collection.generic.CanBuildFrom
import scala.util.Random

trait Entropy[+F[_]] {
  def nextBoolean(): F[Boolean]
  def nextInt(): F[Int]
  def nextInt(max: Int): F[Int]
  def nextLong(): F[Long]
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

  def withSeed(seed: Long): Entropy[F]
  def setSeed(seed: Long): F[Unit]

  def writeRandomBytes(bytes: Array[Byte]): F[Unit]
}

object Entropy {
  def apply[F[_]: Entropy]: Entropy[F] = implicitly

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

  def fromImpure[F[_]](impureEntropy: Entropy[Identity])(implicit F: SyncSafe[F]): Entropy[F] = {
    new Entropy[F] {
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
      override def withSeed(seed: Long): Entropy[F] = fromImpure(impureEntropy.withSeed(seed))

      @nowarn("msg=CanBuildFrom")
      override def shuffle[T, CC[X] <: IterableOnce[X]](xs: CC[T])(implicit bf: CanBuildFrom[CC[T], T, CC[T]]): F[CC[T]] =
        F.syncSafe(impureEntropy.shuffle[T, CC](xs))
    }
  }

  trait ScalaEntropy extends Entropy[Identity] {
    protected def random: scala.util.Random

    @nowarn("msg=CanBuildFrom")
    override def shuffle[T, CC[X] <: IterableOnce[X]](xs: CC[T])(implicit bf: CanBuildFrom[CC[T], T, CC[T]]): CC[T] = {
      random.shuffle(xs)
    }

    override def withSeed(seed: Long): Entropy[Identity] = new Deterministic(seed)

    override def nextBoolean(): Boolean = random.nextBoolean()
    override def nextFloat(): Float = random.nextFloat()
    override def nextDouble(): Double = random.nextDouble()
    override def nextGaussian(): Double = random.nextGaussian()
    override def nextInt(): Int = random.nextInt()
    override def nextInt(max: Int): Int = random.nextInt(max)
    override def nextLong(): Long = random.nextLong()
    override def nextLong(max: Long): Long = random.self.nextLong() % max
    override def nextPrintableChar(): Identity[Char] = random.nextPrintableChar()
    override def nextString(length: Int): String = random.nextString(length)
    override def nextBytes(length: Int): Array[Byte] = {
      val bytes = new Array[Byte](0 max length)
      random.nextBytes(bytes)
      bytes
    }
    override def writeRandomBytes(bytes: Array[Byte]): Unit = random.nextBytes(bytes)
    override def setSeed(seed: Long): Identity[Unit] = random.setSeed(seed)
  }

}
