package izumi.thirdparty.internal.boopickle

import scala.collection.mutable
import scala.reflect.ClassTag

/**
  * Encodes a class belonging to a type hierarchy. Type is identified by the index in the `picklers` sequence, so care
  * must be taken to ensure picklers are added in the same order.
  */
private[izumi] class CompositePickler[A] extends Pickler[A] {

  import Constants._

  private var picklerClasses = IdentMap.empty
  private val picklers = mutable.ArrayBuffer.empty[(Class[_], Pickler[_])]

  override def pickle(obj: A)(implicit state: PickleState): Unit = {
    if (obj == null) {
      state.enc.writeInt(NullObject)
    } else {
      val clz = obj.getClass
      picklerClasses(clz) match {
        case None =>
          throw new IllegalArgumentException(s"This CompositePickler doesn't know class '${clz.getName}'.")
        case Some(idx) =>
          val pickler = picklers(idx - 2)._2
          state.enc.writeInt(idx - 1)
          pickler.asInstanceOf[Pickler[A]].pickle(obj)
      }
    }
  }

  override def unpickle(implicit state: UnpickleState): A = {
    val idx = state.dec.readInt
    if (idx == NullObject)
      null.asInstanceOf[A]
    else {
      if (idx < 0 || idx > picklers.size)
        throw new IllegalStateException(s"Index $idx is not defined in this CompositePickler")
      picklers(idx - 1)._2.asInstanceOf[Pickler[A]].unpickle
    }
  }

  private def addPickler[B](pickler: Pickler[B], tag: ClassTag[B]): Unit = {
    val clz = tag.runtimeClass
    if (picklerClasses(clz).isDefined)
      throw new IllegalArgumentException(s"Cannot add same class (${clz.getName}) twice to a composite pickler")
    picklerClasses = picklerClasses.updated(clz)
    picklers.append((clz, pickler))
  }

  @noinline def addConcreteType[B <: A](implicit pickler: Pickler[B], tag: ClassTag[B]): CompositePickler[A] = {
    addPickler(pickler, tag)
    this
  }

  @noinline def addTransform[B <: A, C](transformTo: (B) => C, transformFrom: (C) => B)(implicit p: Pickler[C], tag: ClassTag[B]): CompositePickler[A] = {
    val pickler = p.xmap(transformFrom)(transformTo)
    addPickler(pickler, tag)
    this
  }

  @noinline def addException[B <: A with Throwable](ctor: (String) => B)(implicit tag: ClassTag[B]): CompositePickler[A] = {
    import Default.stringPickler
    val pickler = new Pickler[B] {
      override def pickle(ex: B)(implicit state: PickleState): Unit = {
        state.pickle(ex.getMessage)
      }

      override def unpickle(implicit state: UnpickleState): B = {
        ctor(state.unpickle[String])
      }
    }
    addPickler(pickler, tag)
    this
  }

  def join[B <: A](implicit cp: CompositePickler[B]): CompositePickler[A] = {
    picklers.appendAll(cp.picklers)
    picklerClasses = IdentMap.empty
    picklers.foreach(cp => picklerClasses = picklerClasses.updated(cp._1))
    this
  }
}

private[izumi] object CompositePickler {
  def apply[A] = new CompositePickler[A]
}

private[izumi] object ExceptionPickler {
  def empty = CompositePickler[Throwable]
  // generate base exception picklers
  private lazy val basePicklers = CompositePickler[Throwable]
    .addException[Exception](m => new Exception(m))
    .addException[RuntimeException](m => new RuntimeException(m))
    .addException[MatchError](m => new MatchError(m))
    .addException[UninitializedFieldError](m => new UninitializedFieldError(m))
    .addException[NullPointerException](m => new NullPointerException(m))
    .addException[ClassCastException](m => new ClassCastException(m))
    .addException[IndexOutOfBoundsException](m => new IndexOutOfBoundsException(m))
    .addException[ArrayIndexOutOfBoundsException](m => new ArrayIndexOutOfBoundsException(m))
    .addException[StringIndexOutOfBoundsException](m => new StringIndexOutOfBoundsException(m))
    .addException[UnsupportedOperationException](m => new UnsupportedOperationException(m))
    .addException[IllegalArgumentException](m => new IllegalArgumentException(m))
    .addException[IllegalStateException](m => new IllegalStateException(m))
    .addException[NoSuchElementException](m => new NoSuchElementException(m))
    .addException[NumberFormatException](m => new NumberFormatException(m))
    .addException[ArithmeticException](m => new ArithmeticException(m))
    .addException[InterruptedException](m => new InterruptedException(m))

  /**
    * Provides simple (message only) pickling of most common Java/Scala exception types. This can be used
    * as a base for adding custom exception types.
    */
  def base = CompositePickler[Throwable].join(basePicklers)
}
