package izumi.functional.mono

import java.util.UUID

import com.github.ghik.silencer.silent
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.uuid.UUIDGen

import scala.collection.compat._
import scala.collection.generic.CanBuildFrom
import scala.util.Random

trait Entropy[+F[_]] {
  def nextBoolean(): F[Boolean]
  def nextBytes(bytes: Array[Byte]): F[Unit]
  def nextDouble(): F[Double]
  def nextFloat(): F[Float]
  def nextGaussian(): F[Double]
  def nextLong(): F[Long]
  def nextInt(max: Int): F[Int]
  def nextInt(): F[Int]
  def nextTimeUUID(): F[UUID]
  def nextUUID(): F[UUID]

  @silent("CanBuildFrom")
  def shuffle[T, CC[X] <: IterableOnce[X]](xs: CC[T])(implicit bf: CanBuildFrom[CC[T], T, CC[T]]): F[CC[T]]
}

object Entropy {
  def apply[F[_]: Entropy]: Entropy[F] = implicitly

  object Standard extends ScalaEntropy {
    override protected def random: Random = scala.util.Random

    override def nextTimeUUID(): UUID = UUIDGen.getTimeUUID()
    override def nextUUID(): UUID = UUID.randomUUID()
  }

  class Deterministic(seed: Int) extends ScalaEntropy {
    override protected def random: Random = new scala.util.Random(seed)

    override def nextTimeUUID(): UUID = UUIDGen.getTimeUUID(math.abs(random.nextLong()))
    override def nextUUID(): UUID = new UUID(random.nextLong(), random.nextLong())
  }

  def fromImpure[F[_]](impureEntropy: Entropy[Identity])(implicit F: SyncSafe[F]): Entropy[F] = {
    new Entropy[F[?]] {
      override def nextBoolean(): F[Boolean] = F.syncSafe(impureEntropy.nextBoolean())
      override def nextBytes(bytes: Array[Byte]): F[Unit] = F.syncSafe(impureEntropy.nextBytes(bytes))
      override def nextDouble(): F[Double] = F.syncSafe(impureEntropy.nextDouble())
      override def nextFloat(): F[Float] = F.syncSafe(impureEntropy.nextFloat())
      override def nextGaussian(): F[Double] = F.syncSafe(impureEntropy.nextGaussian())
      override def nextLong(): F[Long] = F.syncSafe(impureEntropy.nextLong())
      override def nextInt(max: Int): F[Int] = F.syncSafe(impureEntropy.nextInt(max))
      override def nextInt(): F[Int] = F.syncSafe(impureEntropy.nextInt())
      override def nextTimeUUID(): F[UUID] = F.syncSafe(impureEntropy.nextTimeUUID())
      override def nextUUID(): F[UUID] = F.syncSafe(impureEntropy.nextUUID())

      @silent("CanBuildFrom")
      override def shuffle[T, CC[X] <: IterableOnce[X]](xs: CC[T])(implicit bf: CanBuildFrom[CC[T], T, CC[T]]): F[CC[T]] =
        F.syncSafe(impureEntropy.shuffle[T, CC](xs))
    }
  }

  trait ScalaEntropy extends Entropy[Identity] {
    protected def random: scala.util.Random

    @silent("CanBuildFrom")
    def shuffle[T, CC[X] <: IterableOnce[X]](xs: CC[T])(implicit bf: CanBuildFrom[CC[T], T, CC[T]]): CC[T] = {
      random.shuffle(xs)
    }

    override def nextInt(max: Int): Int = random.nextInt(max)
    override def nextBoolean(): Boolean = random.nextBoolean()
    override def nextBytes(bytes: Array[Byte]): Unit = random.nextBytes(bytes)
    override def nextDouble(): Double = random.nextDouble()
    override def nextFloat(): Float = random.nextFloat()
    override def nextGaussian(): Double = random.nextGaussian()
    override def nextLong(): Long = random.nextLong()
    override def nextInt(): Int = random.nextInt()
  }

}
