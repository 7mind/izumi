package com.github.pshirshov.izumi.fundamentals.platform.entropy

import java.util.UUID

import com.github.pshirshov.izumi.fundamentals.platform.uuid.UUIDGen

import scala.collection.generic.CanBuildFrom
import scala.util.Random

trait Entropy {
  def nextBoolean(): Boolean

  def nextBytes(bytes: Array[Byte]): Unit

  def nextDouble(): Double

  def nextFloat(): Float

  def nextGaussian(): Double

  def nextLong(): Long

  def nextInt(max: Int): Int

  def nextInt(): Int

  def nextTimeUUID(): UUID

  def nextUUID(): UUID

  def shuffle[T, CC[X] <: TraversableOnce[X]](xs: CC[T])(implicit bf: CanBuildFrom[CC[T], T, CC[T]]): CC[T]
}

object Entropy {

  object Standard extends ScalaEntropy {
    override protected def random: Random = scala.util.Random

    override def nextTimeUUID(): UUID = UUIDGen.getTimeUUID

    override def nextUUID(): UUID = UUID.randomUUID()
  }

  class Determenistic(seed: Int) extends ScalaEntropy {
    override protected def random: Random = new scala.util.Random(seed)

    override def nextTimeUUID(): UUID = UUIDGen.getTimeUUID(math.abs(random.nextLong()))

    override def nextUUID(): UUID = new UUID(random.nextLong(), random.nextLong())
  }

}
