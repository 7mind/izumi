package izumi.thirdparty.internal.boopickle

import java.nio.{ByteBuffer, ByteOrder}
import java.util.UUID

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.language.experimental.macros
import scala.reflect.ClassTag
import scala.util.Try

private[izumi] trait BasicImplicitPicklers extends PicklerHelper with XCompatImplicitPicklers {
  implicit def unitPickler: ConstPickler[Unit] = BasicPicklers.UnitPickler
  implicit def booleanPickler: P[Boolean] = BasicPicklers.BooleanPickler
  implicit def bytePickler: P[Byte] = BasicPicklers.BytePickler
  implicit def shortPickler: P[Short] = BasicPicklers.ShortPickler
  implicit def charPickler: P[Char] = BasicPicklers.CharPickler
  implicit def intPickler: P[Int] = BasicPicklers.IntPickler
  implicit def longPickler: P[Long] = BasicPicklers.LongPickler
  implicit def floatPickler: P[Float] = BasicPicklers.FloatPickler
  implicit def doublePickler: P[Double] = BasicPicklers.DoublePickler
  implicit def byteBufferPickler: P[ByteBuffer] = BasicPicklers.ByteBufferPickler
  implicit def stringPickler: P[String] = BasicPicklers.StringPickler

  // less frequently used picklers are initializes lazily to enable DCE
  implicit lazy val bigIntPickler: P[BigInt] = BasicPicklers.BigIntPickler
  implicit lazy val bigDecimalPickler: P[BigDecimal] = BasicPicklers.BigDecimalPickler
  implicit lazy val UUIDPickler: P[UUID] = BasicPicklers.UUIDPickler
  implicit lazy val durationPickler: P[Duration] = BasicPicklers.DurationPickler
  implicit lazy val finiteDurationPickler: P[FiniteDuration] = BasicPicklers.FiniteDurationPickler
  implicit lazy val infiniteDurationPickler: P[Duration.Infinite] = BasicPicklers.InfiniteDurationPickler

  implicit def optionPickler[T: P]: P[Option[T]] = BasicPicklers.OptionPickler[T]
  implicit def somePickler[T: P]: P[Some[T]] = BasicPicklers.SomePickler[T]
  implicit def eitherPickler[T: P, S: P]: P[Either[T, S]] = BasicPicklers.EitherPickler[T, S]
  implicit def leftPickler[T: P, S: P]: P[Left[T, S]] = BasicPicklers.LeftPickler[T, S]
  implicit def rightPickler[T: P, S: P]: P[Right[T, S]] = BasicPicklers.RightPickler[T, S]
  implicit def arrayPickler[T: P: ClassTag]: P[Array[T]] = BasicPicklers.ArrayPickler[T]
}

private[izumi] trait TransformPicklers {

  /**
    * Create a transforming pickler that takes an object of type `A` and transforms it into `B`, which is then pickled.
    * Similarly a `B` is unpickled and then transformed back into `A`.
    *
    * This allows for easy creation of picklers for (relatively) simple classes. For example
    * {{{
    *   // transform Date into Long and back
    *   implicit val datePickler = transformPickler((t: Long) => new java.util.Date(t))(_.getTime)
    * }}}
    *
    * Note that parameters are in reversed order.
    *
    * @param transformFrom Function that takes `B` and transforms it into `A`
    * @param transformTo   Function that takes `A` and transforms it into `B`
    * @tparam A Type of the original object
    * @tparam B Type for the object used for pickling
    */
  def transformPickler[A, B](transformFrom: (B) => A)(transformTo: (A) => B)(implicit p: Pickler[B]): Pickler[A] = {
    p.xmap(transformFrom)(transformTo)
  }
}

private[izumi] trait MaterializePicklerFallback {
  implicit def generatePickler[T]: Pickler[T] = macro PicklerMaterializersImpl.materializePickler[T]
}

private[izumi] object PickleImpl {
  def apply[A](value: A)(implicit state: PickleState, p: Pickler[A]): PickleState = {
    p.pickle(value)(state)
    state
  }

  def intoBytes[A](value: A)(implicit state: PickleState, p: Pickler[A]): ByteBuffer = {
    apply(value).toByteBuffer
  }

  def intoByteBuffers[A](value: A)(implicit state: PickleState, p: Pickler[A]): Iterable[ByteBuffer] = {
    apply(value).toByteBuffers
  }
}

private[izumi] object UnpickleImpl {
  def apply[A](implicit u: Pickler[A]) = UnpickledCurry(u)

  private[izumi] case class UnpickledCurry[A](u: Pickler[A]) {
    def apply(implicit state: UnpickleState): A = u.unpickle(state)

    def fromBytes(bytes: ByteBuffer)(implicit buildState: ByteBuffer => UnpickleState): A = {
      // keep original byte order
      val origByteOrder = bytes.order()
      // but decode as little-endian
      val result = u.unpickle(buildState(bytes.order(ByteOrder.LITTLE_ENDIAN)))
      bytes.order(origByteOrder)
      result
    }

    def tryFromBytes(bytes: ByteBuffer)(implicit buildState: ByteBuffer => UnpickleState): Try[A] = Try(fromBytes(bytes))

    def fromState(state: UnpickleState): A = u.unpickle(state)
  }

}

private[izumi] trait Base {
  type Pickler[A] = _root_.izumi.thirdparty.internal.boopickle.Pickler[A]
  def Pickle: PickleImpl.type = _root_.izumi.thirdparty.internal.boopickle.PickleImpl
  type PickleState = _root_.izumi.thirdparty.internal.boopickle.PickleState
  def Unpickle: UnpickleImpl.type = _root_.izumi.thirdparty.internal.boopickle.UnpickleImpl
  type UnpickleState = _root_.izumi.thirdparty.internal.boopickle.UnpickleState

  def compositePickler[A <: AnyRef]: CompositePickler[A] = CompositePickler[A]

  def exceptionPickler: CompositePickler[Throwable] = ExceptionPickler.base
}

private[izumi] object SpeedOriented {

  /**
    * Provides a default PickleState if none is available implicitly
    *
    * @return
    */
  implicit def pickleStateSpeed: PickleState = new PickleState(new EncoderSpeed, false, false)

  /**
    * Provides a default UnpickleState if none is available implicitly
    *
    * @return
    */
  implicit def unpickleStateSpeed: ByteBuffer => UnpickleState =
    bytes => new UnpickleState(new DecoderSpeed(bytes), false, false)
}

/**
  * Provides basic implicit picklers including macro support for case classes
  */
private[izumi] object Default extends Base with BasicImplicitPicklers with TransformPicklers with TuplePicklers with MaterializePicklerFallback

/**
  * Provides basic implicit picklers without macro support for case classes
  */
private[izumi] object DefaultBasic extends Base with BasicImplicitPicklers with TransformPicklers with TuplePicklers {

  def generatePickler[T]: Pickler[T] = macro PicklerMaterializersImpl.materializePickler[T]
}
