package izumi.fundamentals.platform.entropy

import izumi.fundamentals.platform.functional.Identity

import scala.collection.generic.CanBuildFrom

trait ScalaEntropy extends Entropy[Identity] {

  protected def random: scala.util.Random

  def shuffle[T, CC[X] <: TraversableOnce[X]](xs: CC[T])(implicit bf: CanBuildFrom[CC[T], T, CC[T]]): CC[T] = {
    random.shuffle(xs)(bf)
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
