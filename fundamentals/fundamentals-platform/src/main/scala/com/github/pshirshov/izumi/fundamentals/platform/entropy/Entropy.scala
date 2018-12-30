package com.github.pshirshov.izumi.fundamentals.platform.entropy

import java.util.UUID

import com.github.pshirshov.izumi.fundamentals.platform.uuid.UUIDGen

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

  def shuffle[T, CC[X] <: TraversableOnce[X]](xs: CC[T])(implicit bf: CanBuildFrom[CC[T], T, CC[T]]): CC[T]
}

object Entropy {

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

}
