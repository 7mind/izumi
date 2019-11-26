package izumi.thirdparty.internal.boopickle

import java.nio.ByteBuffer
import java.util.UUID

import scala.collection.mutable
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.reflect.ClassTag

private[izumi] trait Pickler[A] {
  def pickle(obj: A)(implicit state: PickleState): Unit
  def unpickle(implicit state: UnpickleState): A

  def xmap[B](ab: A => B)(ba: B => A): Pickler[B] = {
    val self = this
    new Pickler[B] {
      override def unpickle(implicit state: UnpickleState): B = {
        ab(self.unpickle(state))
      }
      override def pickle(obj: B)(implicit state: PickleState): Unit = {
        self.pickle(ba(obj))
      }
    }
  }
}

/**
  * A Pickler that always returns a constant value.
  *
  * Stores nothing in the pickled output.
  */
private[izumi] final case class ConstPickler[A](a: A) extends Pickler[A] {
  @inline override def pickle(x: A)(implicit s: PickleState) = ()
  @inline override def unpickle(implicit s: UnpickleState) = a
}

private[izumi] trait PicklerHelper {
  protected type P[A] = Pickler[A]

  /**
    * Helper function to write pickled types
    */
  protected def write[A](value: A)(implicit state: PickleState, p: P[A]): Unit = p.pickle(value)(state)

  /**
    * Helper function to unpickle a type
    */
  protected def read[A](implicit state: UnpickleState, u: P[A]): A = u.unpickle
}

private[izumi] object BasicPicklers extends PicklerHelper with XCompatPicklers {

  import Constants._

  val UnitPickler = ConstPickler(())

  object BooleanPickler extends P[Boolean] {
    @inline override def pickle(value: Boolean)(implicit state: PickleState): Unit = state.enc.writeByte(if (value) 1 else 0)
    @inline override def unpickle(implicit state: UnpickleState): Boolean = {
      state.dec.readByte match {
        case 1 => true
        case 0 => false
        case x => throw new IllegalArgumentException(s"Invalid value $x for Boolean")
      }
    }
  }

  object BytePickler extends P[Byte] {
    @inline override def pickle(value: Byte)(implicit state: PickleState): Unit = state.enc.writeByte(value)
    @inline override def unpickle(implicit state: UnpickleState): Byte = state.dec.readByte
  }

  object ShortPickler extends P[Short] {
    @inline override def pickle(value: Short)(implicit state: PickleState): Unit = state.enc.writeShort(value)
    @inline override def unpickle(implicit state: UnpickleState): Short = state.dec.readShort
  }

  object CharPickler extends P[Char] {
    @inline override def pickle(value: Char)(implicit state: PickleState): Unit = state.enc.writeChar(value)
    @inline override def unpickle(implicit state: UnpickleState): Char = state.dec.readChar
  }

  object IntPickler extends P[Int] {
    @inline override def pickle(value: Int)(implicit state: PickleState): Unit = state.enc.writeInt(value)
    @inline override def unpickle(implicit state: UnpickleState): Int = state.dec.readInt
  }

  object LongPickler extends P[Long] {
    @inline override def pickle(value: Long)(implicit state: PickleState): Unit = state.enc.writeLong(value)
    @inline override def unpickle(implicit state: UnpickleState): Long = state.dec.readLong
  }

  object FloatPickler extends P[Float] {
    @inline override def pickle(value: Float)(implicit state: PickleState): Unit = state.enc.writeFloat(value)
    @inline override def unpickle(implicit state: UnpickleState): Float = state.dec.readFloat
  }

  object DoublePickler extends P[Double] {
    @inline override def pickle(value: Double)(implicit state: PickleState): Unit = state.enc.writeDouble(value)
    @inline override def unpickle(implicit state: UnpickleState): Double = state.dec.readDouble
  }

  object ByteBufferPickler extends P[ByteBuffer] {
    @inline override def pickle(bb: ByteBuffer)(implicit state: PickleState): Unit = state.enc.writeByteBuffer(bb)
    @inline override def unpickle(implicit state: UnpickleState): ByteBuffer = state.dec.readByteBuffer
  }

  object BigIntPickler extends P[BigInt] {
    implicit def bp = BytePickler

    @inline override def pickle(value: BigInt)(implicit state: PickleState): Unit = {
      ArrayPickler.pickle(value.toByteArray)
    }
    @inline override def unpickle(implicit state: UnpickleState): BigInt = {
      BigInt(ArrayPickler.unpickle)
    }
  }

  object BigDecimalPickler extends P[BigDecimal] {
    implicit def bp = BytePickler

    @inline override def pickle(value: BigDecimal)(implicit state: PickleState): Unit = {
      state.enc.writeInt(value.scale)
      ArrayPickler.pickle(value.underlying().unscaledValue.toByteArray)
    }
    @inline override def unpickle(implicit state: UnpickleState): BigDecimal = {
      val scale = state.dec.readInt
      val arr = ArrayPickler.unpickle
      BigDecimal(BigInt(arr), scale)
    }
  }

  object StringPickler extends P[String] {
    override def pickle(s: String)(implicit state: PickleState): Unit = {
      state.immutableRefFor(s) match {
        case Some(idx) =>
          state.enc.writeInt(-idx)
        case None =>
          if (s.isEmpty) {
            state.enc.writeInt(0)
          } else {
            state.enc.writeString(s)
            state.addImmutableRef(s)
          }
      }
    }

    override def unpickle(implicit state: UnpickleState): String = {
      val len = state.dec.readInt
      if (len < 0) {
        state.immutableFor[String](-len)
      } else if (len == 0) {
        ""
      } else {
        val s = state.dec.readString(len)
        state.addImmutableRef(s)
        s
      }
    }
  }

  object UUIDPickler extends P[UUID] {
    override def pickle(s: UUID)(implicit state: PickleState): Unit = {
      if (s == null) {
        state.enc.writeRawLong(0)
        state.enc.writeRawLong(0)
        state.enc.writeByte(0)
      } else {
        val msb = s.getMostSignificantBits
        val lsb = s.getLeastSignificantBits
        state.enc.writeRawLong(msb)
        state.enc.writeRawLong(lsb)
        // special encoding for UUID zero, to differentiate from null
        if (msb == 0 && lsb == 0)
          state.enc.writeByte(1)
      }
    }

    @inline override def unpickle(implicit state: UnpickleState): UUID = {
      val msb = state.dec.readRawLong
      val lsb = state.dec.readRawLong

      if (msb == 0 && lsb == 0) {
        val actualUuidByte = state.dec.readByte
        if (actualUuidByte == 0) null else new UUID(0, 0)
      } else
        new UUID(msb, lsb)
    }
  }

  object DurationPickler extends P[Duration] {
    override def pickle(value: Duration)(implicit state: PickleState): Unit = {
      // take care of special Durations
      value match {
        case null =>
          state.enc.writeLongCode(Left(NullObject.toByte))
        case Duration.Inf =>
          state.enc.writeLongCode(Left(DurationInf))
        case Duration.MinusInf =>
          state.enc.writeLongCode(Left(DurationMinusInf))
        case x if x eq Duration.Undefined =>
          state.enc.writeLongCode(Left(DurationUndefined))
        case x =>
          state.enc.writeLongCode(Right(x.toNanos))
      }
    }

    @inline override def unpickle(implicit state: UnpickleState): Duration = {
      state.dec.readLongCode match {
        case Left(NullObject) =>
          null
        case Left(DurationInf) =>
          Duration.Inf
        case Left(DurationMinusInf) =>
          Duration.MinusInf
        case Left(DurationUndefined) =>
          Duration.Undefined
        case Right(value) =>
          Duration.fromNanos(value)
        case Left(_) =>
          null
      }
    }
  }

  def FiniteDurationPickler: P[FiniteDuration] = DurationPickler.asInstanceOf[P[FiniteDuration]]

  def InfiniteDurationPickler: P[Duration.Infinite] = DurationPickler.asInstanceOf[P[Duration.Infinite]]

  def OptionPickler[T: P]: P[Option[T]] = new P[Option[T]] {
    override def pickle(obj: Option[T])(implicit state: PickleState): Unit = {
      obj match {
        case null =>
          state.enc.writeInt(NullObject)
        case Some(x) =>
          state.enc.writeInt(OptionSome.toInt)
          write[T](x)
        case None =>
          // `None` is always encoded as zero
          state.enc.writeInt(OptionNone.toInt)
      }
    }

    override def unpickle(implicit state: UnpickleState): Option[T] = {
      state.dec.readInt match {
        case NullObject =>
          null
        case OptionSome =>
          val o = Some(read[T])
          o
        case OptionNone =>
          None
        case _ =>
          throw new IllegalArgumentException("Invalid coding for Option type")
      }
    }
  }

  def SomePickler[T: P]: P[Some[T]] = OptionPickler[T].asInstanceOf[P[Some[T]]]

  def EitherPickler[T: P, S: P]: P[Either[T, S]] = new P[Either[T, S]] {
    override def pickle(obj: Either[T, S])(implicit state: PickleState): Unit = {
      obj match {
        case null =>
          state.enc.writeInt(NullObject)
        case Left(l) =>
          state.enc.writeInt(EitherLeft.toInt)
          write[T](l)
        case Right(r) =>
          state.enc.writeInt(EitherRight.toInt)
          write[S](r)
      }
    }

    override def unpickle(implicit state: UnpickleState): Either[T, S] = {
      state.dec.readInt match {
        case NullObject =>
          null
        case EitherLeft =>
          Left(read[T])
        case EitherRight =>
          Right(read[S])
        case _ =>
          throw new IllegalArgumentException("Invalid coding for Either type")
      }
    }
  }

  def LeftPickler[T: P, S: P]: P[Left[T, S]] = EitherPickler[T, S].asInstanceOf[P[Left[T, S]]]

  def RightPickler[T: P, S: P]: P[Right[T, S]] = EitherPickler[T, S].asInstanceOf[P[Right[T, S]]]

  /**
    * Specific pickler for Arrays
    *
    * @tparam T Type of values
    * @return
    */
  def ArrayPickler[T: P: ClassTag]: P[Array[T]] = new P[Array[T]] {
    override def pickle(array: Array[T])(implicit state: PickleState): Unit = {
      if (array == null)
        state.enc.writeRawInt(NullRef)
      else {
        // check if this iterable has been pickled already
        implicitly[ClassTag[T]] match {
          // handle specialization
          case ClassTag.Byte =>
            state.enc.writeByteArray(array.asInstanceOf[Array[Byte]])
          case ClassTag.Int =>
            state.enc.writeIntArray(array.asInstanceOf[Array[Int]])
          case ClassTag.Float =>
            state.enc.writeFloatArray(array.asInstanceOf[Array[Float]])
          case ClassTag.Double =>
            state.enc.writeDoubleArray(array.asInstanceOf[Array[Double]])
          case _ =>
            // encode length
            state.enc.writeRawInt(array.length)
            // encode contents
            array.foreach(a => write[T](a))
        }
      }
    }

    override def unpickle(implicit state: UnpickleState): Array[T] = {
      state.dec.readRawInt match {
        case NullRef =>
          null
        case 0 =>
          // empty Array
          Array.empty[T]
        case len =>
          val r = implicitly[ClassTag[T]] match {
            // handle specialization
            case ClassTag.Byte =>
              state.dec.readByteArray(len).asInstanceOf[Array[T]]
            case ClassTag.Int =>
              state.dec.readIntArray(len).asInstanceOf[Array[T]]
            case ClassTag.Float =>
              state.dec.readFloatArray(len).asInstanceOf[Array[T]]
            case ClassTag.Double =>
              // remove padding
              state.dec.readRawInt
              state.dec.readDoubleArray(len).asInstanceOf[Array[T]]
            case _ =>
              val a = new Array[T](len)
              var i = 0
              while (i < len) {
                a(i) = read[T]
                i += 1
              }
              a
          }
          r
      }
    }
  }
}

/**
  * Manage state for a pickling "session".
  *
  * @param enc            Encoder instance to use
  * @param deduplicate    Set to `false` if you want to disable deduplication
  * @param dedupImmutable Set to `false` if you want to disable deduplication of immutable values (like Strings)
  */
private[izumi] final class PickleState(val enc: Encoder, deduplicate: Boolean = true, dedupImmutable: Boolean = true) {

  /**
    * Object reference for pickled objects (use identity for equality comparison)
    *
    * Index 0 is not used
    * Index 1 = null
    * Index 2-n, references to pickled objects
    */
  private[this] var identityRefs: IdentMap = EmptyIdentMap

  @inline def identityRefFor(obj: AnyRef): Option[Int] = {
    if (obj == null)
      Some(1)
    else if (!deduplicate)
      None
    else
      identityRefs(obj)
  }

  @inline def addIdentityRef(obj: AnyRef): Unit = {
    if (deduplicate)
      identityRefs = identityRefs.updated(obj)
  }

  /**
    * Object reference for immutable pickled objects
    */
  private[this] var immutableRefs: mutable.AnyRefMap[AnyRef, Int] = null
  private[this] var immutableIdx = 2

  @inline def immutableRefFor(obj: AnyRef): Option[Int] = {
    if (obj == null)
      Some(1)
    else if (!dedupImmutable)
      None
    else if (immutableRefs != null)
      immutableRefs.get(obj)
    else
      None
  }

  @inline def addImmutableRef(obj: AnyRef): Unit = {
    if (dedupImmutable) {
      if (immutableRefs == null)
        immutableRefs = mutable.AnyRefMap.empty
      immutableRefs.update(obj, immutableIdx)
      immutableIdx += 1
    }
  }

  @inline def pickle[A](value: A)(implicit p: Pickler[A]): PickleState = {
    p.pickle(value)(this)
    this
  }

  def toByteBuffer = enc.asByteBuffer

  def toByteBuffers = enc.asByteBuffers
}

private[izumi] object PickleState {

  /**
    * Provides a default PickleState if none is available implicitly
    *
    * @return
    */
  implicit def pickleStateSpeed: PickleState = new PickleState(new EncoderSize, true, true)
}

/**
  * Manage state for an unpickling "session"
  *
  * @param dec            Decoder instance to use
  * @param deduplicate    Set to `false` if you want to disable deduplication
  * @param dedupImmutable Set to `false` if you want to disable deduplication of immutable values (like Strings)
  */
private[izumi] final class UnpickleState(val dec: Decoder, deduplicate: Boolean = true, dedupImmutable: Boolean = true) {

  /**
    * Object reference for pickled objects (use identity for equality comparison)
    *
    * Index 0 is not used
    * Index 1 = null
    * Index 2-n, references to pickled objects
    */
  private[this] var identityRefs: IdentList = EmptyIdentList

  @noinline def codingError(code: Int): Nothing = {
    throw new IllegalArgumentException(s"Unknown object coding: $code")
  }

  @noinline def identityFor[A <: AnyRef](ref: Int): A = {
    if (ref < 2)
      null.asInstanceOf[A]
    else if (!deduplicate)
      throw new Exception("Deduplication is disabled, but identityFor was called.")
    else
      identityRefs(ref - 2).asInstanceOf[A]
  }

  @inline def addIdentityRef(obj: AnyRef): Unit =
    if (deduplicate)
      identityRefs = identityRefs.updated(obj)

  /**
    * Object reference for immutable pickled objects
    */
  private[this] var immutableRefs: IdentList = EmptyIdentList

  @noinline def immutableFor[A <: AnyRef](ref: Int): A = {
    if (ref < 2)
      null.asInstanceOf[A]
    else if (dedupImmutable)
      immutableRefs(ref - 2).asInstanceOf[A]
    else
      throw new Exception("Deduplication for immutable objects is disabled, but immutableFor was called.")
  }

  @inline def addImmutableRef(obj: AnyRef): Unit = {
    if (dedupImmutable)
      immutableRefs = immutableRefs.updated(obj)
  }

  @inline def unpickle[A](implicit u: Pickler[A]): A = u.unpickle(this)
}

private[izumi] object UnpickleState {

  /**
    * Provides a default UnpickleState if none is available implicitly
    *
    * @return
    */
  implicit def unpickleStateSpeed: ByteBuffer => UnpickleState =
    bytes => new UnpickleState(new DecoderSize(bytes), true, true)

  def apply(bytes: ByteBuffer) = new UnpickleState(new DecoderSize(bytes))

  def apply(decoder: Decoder, deduplicate: Boolean = false, dedupImmutable: Boolean = false) =
    new UnpickleState(decoder, deduplicate)
}
