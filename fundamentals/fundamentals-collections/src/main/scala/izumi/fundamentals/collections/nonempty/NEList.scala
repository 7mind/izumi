package izumi.fundamentals.collections.nonempty

// shameless copypaste from Scalactic

import scala.collection.compat.*
import scala.collection.mutable.{ArrayBuffer, Buffer}
import scala.collection.{Iterable, Seq, mutable}
import scala.language.implicitConversions
import scala.reflect.ClassTag

final class NEList[+T] private (val toList: List[T]) extends AnyVal {

  /**
    * Returns a new <code>NEList</code> containing the elements of this <code>NEList</code> followed by the elements of the passed <code>NEList</code>.
    * The element type of the resulting <code>NEList</code> is the most specific superclass encompassing the element types of this and the passed <code>NEList</code>.
    *
    * @tparam U the element type of the returned <code>NEList</code>
    * @param other the <code>NEList</code> to append
    * @return a new <code>NEList</code> that contains all the elements of this <code>NEList</code> followed by all elements of <code>other</code>.
    */
  def ++[U >: T](other: NEList[U]): NEList[U] = new NEList(toList ++ other.toList)

  /**
    * Returns a new <code>NEList</code> containing the elements of this <code>NEList</code> followed by the elements of the passed <code>Vector</code>.
    * The element type of the resulting <code>NEList</code> is the most specific superclass encompassing the element types of this <code>NEList</code> and the passed <code>Vector</code>.
    *
    * @tparam U the element type of the returned <code>NEList</code>
    * @param other the <code>Vector</code> to append
    * @return a new <code>NEList</code> that contains all the elements of this <code>NEList</code> followed by all elements of <code>other</code>.
    */
  def ++[U >: T](other: Vector[U]): NEList[U] = new NEList(toList ++ other)

  // TODO: Have I added these extra ++, etc. methods to Vector that take a NEList?

  /**
    * Returns a new <code>NEList</code> containing the elements of this <code>NEList</code> followed by the elements of the passed <code>GenTraversableOnce</code>.
    * The element type of the resulting <code>NEList</code> is the most specific superclass encompassing the element types of this <code>NEList</code>
    * and the passed <code>GenTraversableOnce</code>.
    *
    * @tparam U the element type of the returned <code>NEList</code>
    * @param other the <code>GenTraversableOnce</code> to append
    * @return a new <code>NEList</code> that contains all the elements of this <code>NEList</code> followed by all elements of <code>other</code>.
    */
  def ++[U >: T](other: IterableOnce[U]): NEList[U] = {
    val it = other.iterator
    if (it.isEmpty) this else new NEList(toList ++ it)
  }

  @inline def ++:[U >: T](other: NEList[U]): NEList[U] = other ::: this
  @inline def ++:[U >: T](other: Vector[U]): NEList[U] = other ::: this
  @inline def ++:[U >: T](other: IterableOnce[U]): NEList[U] = other ::: this

  /**
    * Returns a new <code>NEList</code> with the given element prepended.
    *
    * <p>
    * Note that :-ending operators are right associative. A mnemonic for <code>+:</code> <em>vs.</em> <code>:+</code> is: the COLon goes on the COLlection side.
    * </p>
    *
    * @param element the element to prepend to this <code>NEList</code>
    * @return a new <code>NEList</code> consisting of <code>element</code> followed by all elements of this <code>NEList</code>.
    */
  def +:[U >: T](element: U): NEList[U] = new NEList(element :: toList)

  /**
    * Adds an element to the beginning of this <code>NEList</code>.
    *
    * <p>
    * Note that :-ending operators are right associative. A mnemonic for <code>+:</code> <em>vs.</em> <code>:+</code> is: the COLon goes on the COLlection side.
    * </p>
    *
    * @param element the element to prepend to this <code>NEList</code>
    * @return a <code>NEList</code> that contains <code>element</code> as first element and that continues with this <code>NEList</code>.
    */
  def ::[U >: T](element: U): NEList[U] = new NEList(element :: toList)

  /**
    * Returns a new <code>NEList</code> containing the elements of this <code>NEList</code> followed by the elements of the passed <code>NEList</code>.
    * The element type of the resulting <code>NEList</code> is the most specific superclass encompassing the element types of this and the passed <code>NEList</code>.
    *
    * @tparam U the element type of the returned <code>NEList</code>
    * @param other the <code>NEList</code> to append
    * @return a new <code>NEList</code> that contains all the elements of this <code>NEList</code> followed by all elements of <code>other</code>.
    */
  def :::[U >: T](other: NEList[U]): NEList[U] = new NEList(other.toList ::: toList)

  /**
    * Returns a new <code>NEList</code> containing the elements of this <code>NEList</code> followed by the elements of the passed <code>Vector</code>.
    * The element type of the resulting <code>NEList</code> is the most specific superclass encompassing the element types of this and the passed <code>Vector</code>.
    *
    * @tparam U the element type of the returned <code>NEList</code>
    * @param other the <code>Vector</code> to append
    * @return a new <code>NEList</code> that contains all the elements of this <code>NEList</code> followed by all elements of <code>other</code>.
    */
  def :::[U >: T](other: Vector[U]): NEList[U] = new NEList(other.toList ::: toList)

  /**
    * Returns a new <code>NEList</code> containing the elements of this <code>NEList</code> followed by the elements of the passed <code>GenTraversableOnce</code>.
    * The element type of the resulting <code>NEList</code> is the most specific superclass encompassing the element types of this <code>NEList</code>
    * and the passed <code>GenTraversableOnce</code>.
    *
    * @tparam U the element type of the returned <code>NEList</code>
    * @param other the <code>GenTraversableOnce</code> to append
    * @return a new <code>NEList</code> that contains all the elements of this <code>NEList</code> followed by all elements of <code>other</code>.
    */
  def :::[U >: T](other: IterableOnce[U]): NEList[U] = {
    val it = other.iterator
    if (it.isEmpty) this else new NEList(it.toList ::: toList)
  }

  /**
    * Returns a new <code>NEList</code> with the given element appended.
    *
    * <p>
    * Note a mnemonic for <code>+:</code> <em>vs.</em> <code>:+</code> is: the COLon goes on the COLlection side.
    * </p>
    *
    * @param element the element to append to this <code>NEList</code>
    * @return a new <code>NEList</code> consisting of all elements of this <code>NEList</code> followed by <code>element</code>.
    */
  def :+[U >: T](element: U): NEList[U] = new NEList(toList :+ element)

  /**
    * Appends all elements of this <code>NEList</code> to a string builder. The written text will consist of a concatenation of the result of invoking <code>toString</code>
    * on of every element of this <code>NEList</code>, without any separator string.
    *
    * @param sb the string builder to which elements will be appended
    * @return the string builder, <code>sb</code>, to which elements were appended.
    */
  def addString(sb: StringBuilder): StringBuilder = toList.addString(sb)

  /**
    * Appends all elements of this <code>NEList</code> to a string builder using a separator string. The written text will consist of a concatenation of the
    * result of invoking <code>toString</code>
    * on of every element of this <code>NEList</code>, separated by the string <code>sep</code>.
    *
    * @param sb the string builder to which elements will be appended
    * @param sep the separator string
    * @return the string builder, <code>sb</code>, to which elements were appended.
    */
  def addString(sb: StringBuilder, sep: String): StringBuilder = toList.addString(sb, sep)

  /**
    * Appends all elements of this <code>NEList</code> to a string builder using start, end, and separator strings. The written text will consist of a concatenation of
    * the string <code>start</code>; the result of invoking <code>toString</code> on all elements of this <code>NEList</code>,
    * separated by the string <code>sep</code>; and the string <code>end</code>
    *
    * @param sb the string builder to which elements will be appended
    * @param start the starting string
    * @param sep the separator string
    * @param start the ending string
    * @return the string builder, <code>sb</code>, to which elements were appended.
    */
  def addString(sb: StringBuilder, start: String, sep: String, end: String): StringBuilder = toList.addString(sb, start, sep, end)

  /**
    * Selects an element by its index in the <code>NEList</code>.
    *
    * @return the element of this <code>NEList</code> at index <code>idx</code>, where 0 indicates the first element.
    */
  def apply(idx: Int): T = toList(idx)

  /**
    * Finds the first element of this <code>NEList</code> for which the given partial function is defined, if any, and applies the partial function to it.
    *
    * @param pf the partial function
    * @return an <code>Option</code> containing <code>pf</code> applied to the first element for which it is defined, or <code>None</code> if
    *    the partial function was not defined for any element.
    */
  def collectFirst[U](pf: PartialFunction[T, U]): Option[U] = toList.collectFirst(pf)

  /**
    * Indicates whether this <code>NEList</code> contains a given value as an element.
    *
    * @param elem the element to look for
    * @return true if this <code>NEList</code> has an element that is equal (as determined by <code>==)</code> to <code>elem</code>, false otherwise.
    */
  infix def contains(elem: Any): Boolean = toList.contains(elem)

  /**
    * Indicates whether this <code>NEList</code> contains a given <code>Seq</code> as a slice.
    *
    * @param that the <code>Seq</code> slice to look for
    * @return true if this <code>NEList</code> contains a slice with the same elements as <code>that</code>, otherwise <code>false</code>.
    */
  def containsSlice[U >: T](that: Seq[U]): Boolean = toList.containsSlice(that)

  /**
    * Indicates whether this <code>NEList</code> contains a given <code>Vector</code> as a slice.
    *
    * @param that the <code>Vector</code> slice to look for
    * @return true if this <code>NEList</code> contains a slice with the same elements as <code>that</code>, otherwise <code>false</code>.
    */
  def containsSlice[U >: T](that: Vector[U]): Boolean = toList.containsSlice(that)

  /**
    * Indicates whether this <code>NEList</code> contains a given <code>NEList</code> as a slice.
    *
    * @param that the <code>NEList</code> slice to look for
    * @return true if this <code>NEList</code> contains a slice with the same elements as <code>that</code>, otherwise <code>false</code>.
    */
  def containsSlice[U >: T](that: NEList[U]): Boolean = toList.containsSlice(that.toList)

  /**
    * Copies values of this <code>NEList</code> to an array. Fills the given array <code>arr</code> with values of this <code>NEList</code>. Copying
    * will stop once either the end of the current <code>NEList</code> is reached, or the end of the array is reached.
    *
    * @param arr the array to fill
    */
  def copyToArray[U >: T](arr: Array[U]): Unit = {
    toList.copyToArray(arr)
    ()
  }

  /**
    * Copies values of this <code>NEList</code> to an array. Fills the given array <code>arr</code> with values of this <code>NEList</code>, beginning at
    * index <code>start</code>. Copying will stop once either the end of the current <code>NEList</code> is reached, or the end of the array is reached.
    *
    * @param arr the array to fill
    * @param start the starting index
    */
  def copyToArray[U >: T](arr: Array[U], start: Int): Unit = {
    toList.copyToArray(arr, start)
    ()
  }

  /**
    * Copies values of this <code>NEList</code> to an array. Fills the given array <code>arr</code> with at most <code>len</code> elements of this <code>NEList</code>, beginning at
    * index <code>start</code>. Copying will stop once either the end of the current <code>NEList</code> is reached, the end of the array is reached, or
    * <code>len</code> elements have been copied.
    *
    * @param arr the array to fill
    * @param start the starting index
    * @param len the maximum number of elements to copy
    */
  def copyToArray[U >: T](arr: Array[U], start: Int, len: Int): Unit = {
    toList.copyToArray(arr, start, len)
    ()
  }

  /**
    * Copies all elements of this <code>NEList</code> to a buffer.
    *
    * @param buf the buffer to which elements are copied
    */
  def copyToBuffer[U >: T](buf: Buffer[U]): Unit = {
    buf ++= toList
    // toList.copyToBuffer(buf)
  }

  /**
    * Indicates whether every element of this <code>NEList</code> relates to the corresponding element of a given <code>Seq</code> by satisfying a given predicate.
    *
    * @tparam B the type of the elements of <code>that</code>
    * @param that the <code>Seq</code> to compare for correspondence
    * @param p the predicate, which relates elements from this <code>NEList</code> and the passed <code>Seq</code>
    * @return true if this <code>NEList</code> and the passed <code>Seq</code> have the same length and <code>p(x, y)</code> is <code>true</code>
    *     for all corresponding elements <code>x</code> of this <code>NEList</code> and <code>y</code> of that, otherwise <code>false</code>.
    */
  def corresponds[B](that: Seq[B])(p: (T, B) => Boolean): Boolean = toList.corresponds(that)(p)

  /**
    * Indicates whether every element of this <code>NEList</code> relates to the corresponding element of a given <code>Vector</code> by satisfying a given predicate.
    *
    * @tparam B the type of the elements of <code>that</code>
    * @param that the <code>Vector</code> to compare for correspondence
    * @param p the predicate, which relates elements from this <code>NEList</code> and the passed <code>Vector</code>
    * @return true if this <code>NEList</code> and the passed <code>Vector</code> have the same length and <code>p(x, y)</code> is <code>true</code>
    *     for all corresponding elements <code>x</code> of this <code>NEList</code> and <code>y</code> of that, otherwise <code>false</code>.
    */
  def corresponds[B](that: Vector[B])(p: (T, B) => Boolean): Boolean = toList.corresponds(that)(p)

  /**
    * Indicates whether every element of this <code>NEList</code> relates to the corresponding element of a given <code>NEList</code> by satisfying a given predicate.
    *
    * @tparam B the type of the elements of <code>that</code>
    * @param that the <code>NEList</code> to compare for correspondence
    * @param p the predicate, which relates elements from this and the passed <code>NEList</code>
    * @return true if this and the passed <code>NEList</code> have the same length and <code>p(x, y)</code> is <code>true</code>
    *     for all corresponding elements <code>x</code> of this <code>NEList</code> and <code>y</code> of that, otherwise <code>false</code>.
    */
  def corresponds[B](that: NEList[B])(p: (T, B) => Boolean): Boolean = toList.corresponds(that.toList)(p)

  /**
    * Counts the number of elements in this <code>NEList</code> that satisfy a predicate.
    *
    * @param p the predicate used to test elements.
    * @return the number of elements satisfying the predicate <code>p</code>.
    */
  def count(p: T => Boolean): Int = toList.count(p)

  /**
    * Builds a new <code>NEList</code> from this <code>NEList</code> without any duplicate elements.
    *
    * @return A new <code>NEList</code> that contains the first occurrence of every element of this <code>NEList</code>.
    */
  def distinct: NEList[T] = new NEList(toList.distinct)

  /**
    * Indicates whether this <code>NEList</code> ends with the given <code>Seq</code>.
    *
    * @param that the sequence to test
    * @return <code>true</code> if this <code>NEList</code> has <code>that</code> as a suffix, <code>false</code> otherwise.
    */
  def endsWith[B](that: Seq[B]): Boolean = toList.endsWith[Any](that)

  /**
    * Indicates whether this <code>NEList</code> ends with the given <code>Vector</code>.
    *
    * @param that the <code>Vector</code> to test
    * @return <code>true</code> if this <code>NEList</code> has <code>that</code> as a suffix, <code>false</code> otherwise.
    */
  def endsWith[B](that: Vector[B]): Boolean = toList.endsWith[Any](that)

  // TODO: Search for that: Vector in here and add a that: NEList in Vector.
  /**
    * Indicates whether this <code>NEList</code> ends with the given <code>NEList</code>.
    *
    * @param that the <code>NEList</code> to test
    * @return <code>true</code> if this <code>NEList</code> has <code>that</code> as a suffix, <code>false</code> otherwise.
    */
  def endsWith[B](that: NEList[B]): Boolean = toList.endsWith[Any](that.toList)

  /*
    override def equals(o: Any): Boolean =
      o match {
        case NEList: NEList[?] => toList == NEList.toList
        case _ => false
      }
   */

  /**
    * Indicates whether a predicate holds for at least one of the elements of this <code>NEList</code>.
    *
    * @param the predicate used to test elements.
    * @return <code>true</code> if the given predicate <code>p</code> holds for some of the elements of this <code>NEList</code>, otherwise <code>false</code>.
    */
  def exists(p: T => Boolean): Boolean = toList.exists(p)

  /**
    * Finds the first element of this <code>NEList</code> that satisfies the given predicate, if any.
    *
    * @param p the predicate used to test elements
    * @return an <code>Some</code> containing the first element in this <code>NEList</code> that satisfies <code>p</code>, or <code>None</code> if none exists.
    */
  def find(p: T => Boolean): Option[T] = toList.find(p)

  /**
    * Builds a new <code>NEList</code> by applying a function to all elements of this <code>NEList</code> and using the elements of the resulting <code>NEList</code>s.
    *
    * @tparam U the element type of the returned <code>NEList</code>
    * @param f the function to apply to each element.
    * @return a new <code>NEList</code> containing elements obtained by applying the given function <code>f</code> to each element of this <code>NEList</code> and concatenating
    *    the elements of resulting <code>NEList</code>s.
    */
  def flatMap[U](f: T => NEList[U]): NEList[U] = {
    val buf = new ArrayBuffer[U]
    for (ele <- toList)
      buf ++= f(ele).toList
    new NEList(buf.toList)
  }

  /**
    * Converts this <code>NEList</code> of <code>NEList</code>s into a <code>NEList</code>
    * formed by the elements of the nested <code>NEList</code>s.
    *
    * <p>
    * @note You cannot use this <code>flatten</code> method on a <code>NEList</code> that contains a <code>GenTraversableOnce</code>s, because
    * if all the nested <code>GenTraversableOnce</code>s were empty, you'd end up with an empty <code>NEList</code>.
    * </p>
    *
    * @tparm B the type of the elements of each nested <code>NEList</code>
    * @return a new <code>NEList</code> resulting from concatenating all nested <code>NEList</code>s.
    */
  def flatten[B](implicit ev: T <:< NEList[B]): NEList[B] = flatMap(ev)

  /**
    * Folds the elements of this <code>NEList</code> using the specified associative binary operator.
    *
    * <p>
    * The order in which operations are performed on elements is unspecified and may be nondeterministic.
    * </p>
    *
    * @tparam U a type parameter for the binary operator, a supertype of T.
    * @param z a neutral element for the fold operation; may be added to the result an arbitrary number of
    *     times, and must not change the result (<em>e.g.</em>, <code>Nil</code> for list concatenation,
    *     0 for addition, or 1 for multiplication.)
    * @param op a binary operator that must be associative
    * @return the result of applying fold operator <code>op</code> between all the elements and <code>z</code>
    */
  def fold[U >: T](z: U)(op: (U, U) => U): U = toList.fold(z)(op)

  /**
    * Applies a binary operator to a start value and all elements of this <code>NEList</code>, going left to right.
    *
    * @tparam B the result type of the binary operator.
    * @param z the start value.
    * @param op the binary operator.
    * @return the result of inserting <code>op</code> between consecutive elements of this <code>NEList</code>, going left to right, with the start value,
    *     <code>z</code>, on the left:
    *
    * <pre>
    * op(...op(op(z, x_1), x_2), ..., x_n)
    * </pre>
    *
    * <p>
    * where x<sub>1</sub>, ..., x<sub>n</sub> are the elements of this <code>NEList</code>.
    * </p>
    */
  def foldLeft[B](z: B)(op: (B, T) => B): B = toList.foldLeft(z)(op)

  /**
    * Applies a binary operator to all elements of this <code>NEList</code> and a start value, going right to left.
    *
    * @tparam B the result of the binary operator
    * @param z the start value
    * @param op the binary operator
    * @return the result of inserting <code>op</code> between consecutive elements of this <code>NEList</code>, going right to left, with the start value,
    *     <code>z</code>, on the right:
    *
    * <pre>
    * op(x_1, op(x_2, ... op(x_n, z)...))
    * </pre>
    *
    * <p>
    * where x<sub>1</sub>, ..., x<sub>n</sub> are the elements of this <code>NEList</code>.
    * </p>
    */
  def foldRight[B](z: B)(op: (T, B) => B): B = toList.foldRight(z)(op)

  /**
    * Indicates whether a predicate holds for all elements of this <code>NEList</code>.
    *
    * @param p the predicate used to test elements.
    * @return <code>true</code> if the given predicate <code>p</code> holds for all elements of this <code>NEList</code>, otherwise <code>false</code>.
    */
  def forall(p: T => Boolean): Boolean = toList.forall(p)

  /**
    * Applies a function <code>f</code> to all elements of this <code>NEList</code>.
    *
    * @param f the function that is applied for its side-effect to every element. The result of function <code>f</code> is discarded.
    */
  def foreach(f: T => Unit): Unit = toList.foreach(f)

  /**
    * Partitions this <code>NEList</code> into a map of <code>NEList</code>s according to some discriminator function.
    *
    * @tparam K the type of keys returned by the discriminator function.
    * @param f the discriminator function.
    * @return A map from keys to <code>NEList</code>s such that the following invariant holds:
    *
    * <pre>
    * (NEList.toList partition f)(k) = xs filter (x =&gt; f(x) == k)
    * </pre>
    *
    * <p>
    * That is, every key <code>k</code> is bound to a <code>NEList</code> of those elements <code>x</code> for which <code>f(x)</code> equals <code>k</code>.
    * </p>
    */
  def groupBy[K](f: T => K): Map[K, NEList[T]] = {
    val mapKToList = toList.groupBy(f)
    mapKToList.view.mapValues {
      list => new NEList(list)
    }.toMap
  }

  /**
    * Partitions elements into fixed size <code>NEList</code>s.
    *
    * @param size the number of elements per group
    * @return An iterator producing <code>NEList</code>s of size <code>size</code>, except the last will be truncated if the elements don't divide evenly.
    */
  def grouped(size: Int): Iterator[NEList[T]] = {
    val itOfList = toList.grouped(size)
    itOfList.map {
      list => new NEList(list)
    }
  }

  /**
    * Returns <code>true</code> to indicate this <code>NEList</code> has a definite size, since all <code>NEList</code>s are strict collections.
    */
  def hasDefiniteSize: Boolean = true

  // override def hashCode: Int = toList.hashCode

  /**
    * Selects the first element of this <code>NEList</code>.
    *
    * @return the first element of this <code>NEList</code>.
    */
  def head: T = toList.head

  def tail: List[T] = toList.tail

  /**
    * Finds index of first occurrence of some value in this <code>NEList</code>.
    *
    * @param elem the element value to search for.
    * @return the index of the first element of this <code>NEList</code> that is equal (as determined by <code>==</code>) to <code>elem</code>,
    *     or <code>-1</code>, if none exists.
    */
  def indexOf[U >: T](elem: U): Int = toList.indexOf(elem, 0)

  /**
    * Finds index of first occurrence of some value in this <code>NEList</code> after or at some start index.
    *
    * @param elem the element value to search for.
    * @param from the start index
    * @return the index <code>&gt;=</code> <code>from</code> of the first element of this <code>NEList</code> that is equal (as determined by <code>==</code>) to <code>elem</code>,
    *     or <code>-1</code>, if none exists.
    */
  def indexOf[U >: T](elem: U, from: Int): Int = toList.indexOf(elem, from)

  /**
    * Finds first index where this <code>NEList</code> contains a given <code>Seq</code> as a slice.
    *
    * @param that the <code>Seq</code> defining the slice to look for
    * @return the first index at which the elements of this <code>NEList</code> starting at that index match the elements of
    *     <code>Seq</code> <code>that</code>, or <code>-1</code> of no such subsequence exists.
    */
  def indexOfSlice[U >: T](that: Seq[U]): Int = toList.indexOfSlice(that)

  /**
    * Finds first index after or at a start index where this <code>NEList</code> contains a given <code>Seq</code> as a slice.
    *
    * @param that the <code>Seq</code> defining the slice to look for
    * @param from the start index
    * @return the first index <code>&gt;=</code> <code>from</code> at which the elements of this <code>NEList</code> starting at that index match the elements of
    *     <code>Seq</code> <code>that</code>, or <code>-1</code> of no such subsequence exists.
    */
  def indexOfSlice[U >: T](that: Seq[U], from: Int): Int = toList.indexOfSlice(that, from)

  /**
    * Finds first index where this <code>NEList</code> contains a given <code>Vector</code> as a slice.
    *
    * @param that the <code>Vector</code> defining the slice to look for
    * @return the first index such that the elements of this <code>NEList</code> starting at this index match the elements of
    *     <code>Vector</code> <code>that</code>, or <code>-1</code> of no such subsequence exists.
    */
  def indexOfSlice[U >: T](that: Vector[U]): Int = toList.indexOfSlice(that)

  /**
    * Finds first index where this <code>NEList</code> contains a given <code>NEList</code> as a slice.
    *
    * @param that the <code>NEList</code> defining the slice to look for
    * @return the first index such that the elements of this <code>NEList</code> starting at this index match the elements of
    *     <code>NEList</code> <code>that</code>, or <code>-1</code> of no such subsequence exists.
    */
  def indexOfSlice[U >: T](that: NEList[U]): Int = toList.indexOfSlice(that.toList)

  /**
    * Finds first index after or at a start index where this <code>NEList</code> contains a given <code>Vector</code> as a slice.
    *
    * @param that the <code>Vector</code> defining the slice to look for
    * @param from the start index
    * @return the first index <code>&gt;=</code> <code>from</code> such that the elements of this <code>NEList</code> starting at this index match the elements of
    *     <code>Vector</code> <code>that</code>, or <code>-1</code> of no such subsequence exists.
    */
  def indexOfSlice[U >: T](that: Vector[U], from: Int): Int = toList.indexOfSlice(that, from)

  /**
    * Finds first index after or at a start index where this <code>NEList</code> contains a given <code>NEList</code> as a slice.
    *
    * @param that the <code>NEList</code> defining the slice to look for
    * @param from the start index
    * @return the first index <code>&gt;=</code> <code>from</code> such that the elements of this <code>NEList</code> starting at this index match the elements of
    *     <code>NEList</code> <code>that</code>, or <code>-1</code> of no such subsequence exists.
    */
  def indexOfSlice[U >: T](that: NEList[U], from: Int): Int = toList.indexOfSlice(that.toList, from)

  /**
    * Finds index of the first element satisfying some predicate.
    *
    * @param p the predicate used to test elements.
    * @return the index of the first element of this <code>NEList</code> that satisfies the predicate <code>p</code>,
    *     or <code>-1</code>, if none exists.
    */
  def indexWhere(p: T => Boolean): Int = toList.indexWhere(p)

  /**
    * Finds index of the first element satisfying some predicate after or at some start index.
    *
    * @param p the predicate used to test elements.
    * @param from the start index
    * @return the index <code>&gt;=</code> <code>from</code> of the first element of this <code>NEList</code> that satisfies the predicate <code>p</code>,
    *     or <code>-1</code>, if none exists.
    */
  def indexWhere(p: T => Boolean, from: Int): Int = toList.indexWhere(p, from)

  /**
    * Produces the range of all indices of this <code>NEList</code>.
    *
    * @return a <code>Range</code> value from <code>0</code> to one less than the length of this <code>NEList</code>.
    */
  def indices: Range = toList.indices

  /**
    * Tests whether this <code>NEList</code> contains given index.
    *
    * @param idx the index to test
    * @return true if this <code>NEList</code> contains an element at position <code>idx</code>, <code>false</code> otherwise.
    */
  def isDefinedAt(idx: Int): Boolean = toList.isDefinedAt(idx)

  /**
    * Returns <code>false</code> to indicate this <code>NEList</code>, like all <code>NEList</code>s, is non-empty.
    *
    * @return false
    */
  def isEmpty: Boolean = false

  /**
    * Returns <code>true</code> to indicate this <code>NEList</code>, like all <code>NEList</code>s, can be traversed repeatedly.
    *
    * @return true
    */
  def isTraversableAgain: Boolean = true

  /**
    * Creates and returns a new iterator over all elements contained in this <code>NEList</code>.
    *
    * @return the new iterator
    */
  def iterator: Iterator[T] = toList.iterator

  /**
    * Selects the last element of this <code>NEList</code>.
    *
    * @return the last element of this <code>NEList</code>.
    */
  def last: T = toList.last

  /**
    * Finds the index of the last occurrence of some value in this <code>NEList</code>.
    *
    * @param elem the element value to search for.
    * @return the index of the last element of this <code>NEList</code> that is equal (as determined by <code>==</code>) to <code>elem</code>,
    *     or <code>-1</code>, if none exists.
    */
  def lastIndexOf[U >: T](elem: U): Int = toList.lastIndexOf(elem)

  /**
    * Finds the index of the last occurrence of some value in this <code>NEList</code> before or at a given <code>end</code> index.
    *
    * @param elem the element value to search for.
    * @param end the end index.
    * @return the index <code>&gt;=</code> <code>end</code> of the last element of this <code>NEList</code> that is equal (as determined by <code>==</code>)
    *     to <code>elem</code>, or <code>-1</code>, if none exists.
    */
  def lastIndexOf[U >: T](elem: U, end: Int): Int = toList.lastIndexOf(elem, end)

  /**
    * Finds the last index where this <code>NEList</code> contains a given <code>Seq</code> as a slice.
    *
    * @param that the <code>Seq</code> defining the slice to look for
    * @return the last index at which the elements of this <code>NEList</code> starting at that index match the elements of
    *    <code>Seq</code> <code>that</code>, or <code>-1</code> of no such subsequence exists.
    */
  def lastIndexOfSlice[U >: T](that: Seq[U]): Int = toList.lastIndexOfSlice(that)

  /**
    * Finds the last index before or at a given end index where this <code>NEList</code> contains a given <code>Seq</code> as a slice.
    *
    * @param that the <code>Seq</code> defining the slice to look for
    * @param end the end index
    * @return the last index <code>&gt;=</code> <code>end</code> at which the elements of this <code>NEList</code> starting at that index match the elements of
    *    <code>Seq</code> <code>that</code>, or <code>-1</code> of no such subsequence exists.
    */
  def lastIndexOfSlice[U >: T](that: Seq[U], end: Int): Int = toList.lastIndexOfSlice(that, end)

  /**
    * Finds the last index where this <code>NEList</code> contains a given <code>Vector</code> as a slice.
    *
    * @param that the <code>Vector</code> defining the slice to look for
    * @return the last index at which the elements of this <code>NEList</code> starting at that index match the elements of
    *    <code>Vector</code> <code>that</code>, or <code>-1</code> of no such subsequence exists.
    */
  def lastIndexOfSlice[U >: T](that: Vector[U]): Int = toList.lastIndexOfSlice(that)

  /**
    * Finds the last index where this <code>NEList</code> contains a given <code>NEList</code> as a slice.
    *
    * @param that the <code>NEList</code> defining the slice to look for
    * @return the last index at which the elements of this <code>NEList</code> starting at that index match the elements of
    *    <code>NEList</code> <code>that</code>, or <code>-1</code> of no such subsequence exists.
    */
  def lastIndexOfSlice[U >: T](that: NEList[U]): Int = toList.lastIndexOfSlice(that.toList)

  /**
    * Finds the last index before or at a given end index where this <code>NEList</code> contains a given <code>Vector</code> as a slice.
    *
    * @param that the <code>Vector</code> defining the slice to look for
    * @param end the end index
    * @return the last index <code>&gt;=</code> <code>end</code> at which the elements of this <code>NEList</code> starting at that index match the elements of
    *    <code>Vector</code> <code>that</code>, or <code>-1</code> of no such subsequence exists.
    */
  def lastIndexOfSlice[U >: T](that: Vector[U], end: Int): Int = toList.lastIndexOfSlice(that, end)

  /**
    * Finds the last index before or at a given end index where this <code>NEList</code> contains a given <code>NEList</code> as a slice.
    *
    * @param that the <code>NEList</code> defining the slice to look for
    * @param end the end index
    * @return the last index <code>&gt;=</code> <code>end</code> at which the elements of this <code>NEList</code> starting at that index match the elements of
    *    <code>NEList</code> <code>that</code>, or <code>-1</code> of no such subsequence exists.
    */
  def lastIndexOfSlice[U >: T](that: NEList[U], end: Int): Int = toList.lastIndexOfSlice(that.toList, end)

  /**
    * Finds index of last element satisfying some predicate.
    *
    * @param p the predicate used to test elements.
    * @return the index of the last element of this <code>NEList</code> that satisfies the predicate <code>p</code>, or <code>-1</code>, if none exists.
    */
  def lastIndexWhere(p: T => Boolean): Int = toList.lastIndexWhere(p)

  /**
    * Finds index of last element satisfying some predicate before or at given end index.
    *
    * @param p the predicate used to test elements.
    * @param end the end index
    * @return the index <code>&gt;=</code> <code>end</code> of the last element of this <code>NEList</code> that satisfies the predicate <code>p</code>,
    *     or <code>-1</code>, if none exists.
    */
  def lastIndexWhere(p: T => Boolean, end: Int): Int = toList.lastIndexWhere(p, end)

  /**
    * The length of this <code>NEList</code>.
    *
    * <p>
    * @note <code>length</code> and <code>size</code> yield the same result, which will be <code>&gt;</code>= 1.
    * </p>
    *
    * @return the number of elements in this <code>NEList</code>.
    */
  def length: Int = toList.length

  /**
    * Compares the length of this <code>NEList</code> to a test value.
    *
    * @param len the test value that gets compared with the length.
    * @return a value <code>x</code> where
    *
    * <pre>
    * x &lt; 0 if this.length &lt; len
    * x == 0 if this.length == len
    * x &gt; 0 if this.length &gt; len
    * </pre>
    */
  def lengthCompare(len: Int): Int = toList.lengthCompare(len)

  /**
    * Builds a new <code>NEList</code> by applying a function to all elements of this <code>NEList</code>.
    *
    * @tparam U the element type of the returned <code>NEList</code>.
    * @param f the function to apply to each element.
    * @return a new <code>NEList</code> resulting from applying the given function <code>f</code> to each element of this <code>NEList</code> and collecting the results.
    */
  def map[U](f: T => U): NEList[U] =
    new NEList(toList.map(f))

  /**
    * Finds the largest element.
    *
    * @return the largest element of this <code>NEList</code>.
    */
  def max[U >: T](implicit cmp: Ordering[U]): T = toList.max(cmp)

  /**
    * Finds the largest result after applying the given function to every element.
    *
    * @return the largest result of applying the given function to every element of this <code>NEList</code>.
    */
  def maxBy[U](f: T => U)(implicit cmp: Ordering[U]): T = toList.maxBy(f)(cmp)

  /**
    * Finds the smallest element.
    *
    * @return the smallest element of this <code>NEList</code>.
    */
  def min[U >: T](implicit cmp: Ordering[U]): T = toList.min(cmp)

  /**
    * Finds the smallest result after applying the given function to every element.
    *
    * @return the smallest result of applying the given function to every element of this <code>NEList</code>.
    */
  def minBy[U](f: T => U)(implicit cmp: Ordering[U]): T = toList.minBy(f)(cmp)

  /**
    * Displays all elements of this <code>NEList</code> in a string.
    *
    * @return a string representation of this <code>NEList</code>. In the resulting string, the result of invoking <code>toString</code> on all elements of this
    *     <code>NEList</code> follow each other without any separator string.
    */
  def mkString: String = toList.mkString

  /**
    * Displays all elements of this <code>NEList</code> in a string using a separator string.
    *
    * @param sep the separator string
    * @return a string representation of this <code>NEList</code>. In the resulting string, the result of invoking <code>toString</code> on all elements of this
    *     <code>NEList</code> are separated by the string <code>sep</code>.
    */
  def mkString(sep: String): String = toList.mkString(sep)

  /**
    * Displays all elements of this <code>NEList</code> in a string using start, end, and separator strings.
    *
    * @param start the starting string.
    * @param sep the separator string.
    * @param end the ending string.
    * @return a string representation of this <code>NEList</code>. The resulting string begins with the string <code>start</code> and ends with the string
    *     <code>end</code>. Inside, In the resulting string, the result of invoking <code>toString</code> on all elements of this <code>NEList</code> are
    *     separated by the string <code>sep</code>.
    */
  def mkString(start: String, sep: String, end: String): String = toList.mkString(start, sep, end)

  /**
    * Returns <code>true</code> to indicate this <code>NEList</code>, like all <code>NEList</code>s, is non-empty.
    *
    * @return true
    */
  def nonEmpty: Boolean = true

  /**
    * A copy of this <code>NEList</code> with an element value appended until a given target length is reached.
    *
    * @param len the target length
    * @param elem he padding value
    * @return a new <code>NEList</code> consisting of all elements of this <code>NEList</code> followed by the minimal number of occurrences
    *     of <code>elem</code> so that the resulting <code>NEList</code> has a length of at least <code>len</code>.
    */
  def padTo[U >: T](len: Int, elem: U): NEList[U] =
    new NEList(toList.padTo(len, elem))

  /**
    * Produces a new <code>NEList</code> where a slice of elements in this <code>NEList</code> is replaced by another <code>NEList</code>
    *
    * @param from the index of the first replaced element
    * @param that the <code>NEList</code> whose elements should replace a slice in this <code>NEList</code>
    * @param replaced the number of elements to drop in the original <code>NEList</code>
    */
  def patch[U >: T](from: Int, that: NEList[U], replaced: Int): NEList[U] =
    new NEList(toList.patch(from, that.toVector, replaced))

  /**
    * Iterates over distinct permutations.
    *
    * <p>
    * Here's an example:
    * </p>
    *
    * <pre class="stHighlight">
    * NEList('a', 'b', 'b').permutations.toList = List(NEList(a, b, b), NEList(b, a, b), NEList(b, b, a))
    * </pre>
    *
    * @return an iterator that traverses the distinct permutations of this <code>NEList</code>.
    */
  def permutations: Iterator[NEList[T]] = {
    val it = toList.permutations
    it map {
      list => new NEList(list)
    }
  }

  /**
    * Returns the length of the longest prefix whose elements all satisfy some predicate.
    *
    * @param p the predicate used to test elements.
    * @return the length of the longest prefix of this <code>NEList</code> such that every element
    *     of the segment satisfies the predicate <code>p</code>.
    */
  def prefixLength(p: T => Boolean): Int = toList.segmentLength(p, 0)

  /**
    * The result of multiplying all the elements of this <code>NEList</code>.
    *
    * <p>
    * This method can be invoked for any <code>NEList[T]</code> for which an implicit <code>Numeric[T]</code> exists.
    * </p>
    *
    * @return the product of all elements
    */
  def product[U >: T](implicit num: Numeric[U]): U = toList.product(num)

  /**
    * Reduces the elements of this <code>NEList</code> using the specified associative binary operator.
    *
    * <p>
    * The order in which operations are performed on elements is unspecified and may be nondeterministic.
    * </p>
    *
    * @tparam U a type parameter for the binary operator, a supertype of T.
    * @param op a binary operator that must be associative.
    * @return the result of applying reduce operator <code>op</code> between all the elements of this <code>NEList</code>.
    */
  def reduce[U >: T](op: (U, U) => U): U = toList.reduce(op)

  /**
    * Applies a binary operator to all elements of this <code>NEList</code>, going left to right.
    *
    * @tparam U the result type of the binary operator.
    * @param op the binary operator.
    * @return the result of inserting <code>op</code> between consecutive elements of this <code>NEList</code>, going left to right:
    *
    * <pre>
    * op(...op(op(x_1, x_2), x_3), ..., x_n)
    * </pre>
    *
    * <p>
    * where x<sub>1</sub>, ..., x<sub>n</sub> are the elements of this <code>NEList</code>.
    * </p>
    */
  def reduceLeft[U >: T](op: (U, T) => U): U = toList.reduceLeft(op)

  /**
    * Applies a binary operator to all elements of this <code>NEList</code>, going left to right, returning the result in a <code>Some</code>.
    *
    * @tparam U the result type of the binary operator.
    * @param op the binary operator.
    * @return a <code>Some</code> containing the result of <code>reduceLeft(op)</code>
    * </p>
    */
  def reduceLeftOption[U >: T](op: (U, T) => U): Option[U] = toList.reduceLeftOption(op)

  def reduceOption[U >: T](op: (U, U) => U): Option[U] = toList.reduceOption(op)

  /**
    * Applies a binary operator to all elements of this <code>NEList</code>, going right to left.
    *
    * @tparam U the result of the binary operator
    * @param op the binary operator
    * @return the result of inserting <code>op</code> between consecutive elements of this <code>NEList</code>, going right to left:
    *
    * <pre>
    * op(x_1, op(x_2, ... op(x_{n-1}, x_n)...))
    * </pre>
    *
    * <p>
    * where x<sub>1</sub>, ..., x<sub>n</sub> are the elements of this <code>NEList</code>.
    * </p>
    */
  def reduceRight[U >: T](op: (T, U) => U): U = toList.reduceRight(op)

  /**
    * Applies a binary operator to all elements of this <code>NEList</code>, going right to left, returning the result in a <code>Some</code>.
    *
    * @tparam U the result of the binary operator
    * @param op the binary operator
    * @return a <code>Some</code> containing the result of <code>reduceRight(op)</code>
    */
  def reduceRightOption[U >: T](op: (T, U) => U): Option[U] = toList.reduceRightOption(op)

  /**
    * Returns new <code>NEList</code> with elements in reverse order.
    *
    * @return a new <code>NEList</code> with all elements of this <code>NEList</code> in reversed order.
    */
  def reverse: NEList[T] =
    new NEList(toList.reverse)

  /**
    * An iterator yielding elements in reverse order.
    *
    * <p>
    * @note <code>NEList.reverseIterator</code> is the same as <code>NEList.reverse.iterator</code>, but might be more efficient.
    * </p>
    *
    * @return an iterator yielding the elements of this <code>NEList</code> in reversed order
    */
  def reverseIterator: Iterator[T] = toList.reverseIterator

  /**
    * Builds a new <code>NEList</code> by applying a function to all elements of this <code>NEList</code> and collecting the results in reverse order.
    *
    * <p>
    * @note <code>NEList.reverseMap(f)</code> is the same as <code>NEList.reverse.map(f)</code>, but might be more efficient.
    * </p>
    *
    * @tparam U the element type of the returned <code>NEList</code>.
    * @param f the function to apply to each element.
    * @return a new <code>NEList</code> resulting from applying the given function <code>f</code> to each element of this <code>NEList</code>
    *     and collecting the results in reverse order.
    */
  def reverseMap[U](f: T => U): NEList[U] =
    new NEList(toList.reverseIterator.map(f).toList)

  /**
    * Checks if the given <code>Iterable</code> contains the same elements in the same order as this <code>NEList</code>.
    *
    * @param that the <code>Iterable</code> with which to compare
    * @return <code>true</code>, if both this <code>NEList</code> and the given <code>Iterable</code> contain the same elements
    *     in the same order, <code>false</code> otherwise.
    */
  def sameElements[U >: T](that: Iterable[U]): Boolean = toList.sameElements(that)

  /**
    * Checks if the given <code>Vector</code> contains the same elements in the same order as this <code>NEList</code>.
    *
    * @param that the <code>Vector</code> with which to compare
    * @return <code>true</code>, if both this and the given <code>Vector</code> contain the same elements
    *     in the same order, <code>false</code> otherwise.
    */
  def sameElements[U >: T](that: Vector[U]): Boolean = toList.sameElements(that)

  /**
    * Checks if the given <code>NEList</code> contains the same elements in the same order as this <code>NEList</code>.
    *
    * @param that the <code>NEList</code> with which to compare
    * @return <code>true</code>, if both this and the given <code>NEList</code> contain the same elements
    *     in the same order, <code>false</code> otherwise.
    */
  def sameElements[U >: T](that: NEList[U]): Boolean = toList.sameElements(that.toList)

  /**
    * Computes a prefix scan of the elements of this <code>NEList</code>.
    *
    * <p>
    * @note The neutral element z may be applied more than once.
    * </p>
    *
    * <p>
    * Here are some examples:
    * </p>
    *
    * <pre class="stHighlight">
    * NEList(1, 2, 3).scan(0)(_ + _) == NEList(0, 1, 3, 6)
    * NEList(1, 2, 3).scan("z")(_ + _.toString) == NEList("z", "z1", "z12", "z123")
    * </pre>
    *
    * @tparam U a type parameter for the binary operator, a supertype of T, and the type of the resulting <code>NEList</code>.
    * @param z a neutral element for the scan operation; may be added to the result an arbitrary number of
    *     times, and must not change the result (<em>e.g.</em>, <code>Nil</code> for list concatenation,
    *     0 for addition, or 1 for multiplication.)
    * @param op a binary operator that must be associative
    * @return a new <code>NEList</code> containing the prefix scan of the elements in this <code>NEList</code>
    */
  def scan[U >: T](z: U)(op: (U, U) => U): NEList[U] = new NEList(toList.scan(z)(op))

  /**
    * Produces a <code>NEList</code> containing cumulative results of applying the operator going left to right.
    *
    * <p>
    * Here are some examples:
    * </p>
    *
    * <pre class="stHighlight">
    * NEList(1, 2, 3).scanLeft(0)(_ + _) == NEList(0, 1, 3, 6)
    * NEList(1, 2, 3).scanLeft("z")(_ + _) == NEList("z", "z1", "z12", "z123")
    * </pre>
    *
    * @tparam B the result type of the binary operator and type of the resulting <code>NEList</code>
    * @param z the start value.
    * @param op the binary operator.
    * @return a new <code>NEList</code> containing the intermediate results of inserting <code>op</code> between consecutive elements of this <code>NEList</code>,
    *     going left to right, with the start value, <code>z</code>, on the left.
    */
  def scanLeft[B](z: B)(op: (B, T) => B): NEList[B] = new NEList(toList.scanLeft(z)(op))

  /**
    * Produces a <code>NEList</code> containing cumulative results of applying the operator going right to left.
    *
    * <p>
    * Here are some examples:
    * </p>
    *
    * <pre class="stHighlight">
    * NEList(1, 2, 3).scanRight(0)(_ + _) == NEList(6, 5, 3, 0)
    * NEList(1, 2, 3).scanRight("z")(_ + _) == NEList("123z", "23z", "3z", "z")
    * </pre>
    *
    * @tparam B the result of the binary operator and type of the resulting <code>NEList</code>
    * @param z the start value
    * @param op the binary operator
    * @return a new <code>NEList</code> containing the intermediate results of inserting <code>op</code> between consecutive elements of this <code>NEList</code>,
    *     going right to left, with the start value, <code>z</code>, on the right.
    */
  def scanRight[B](z: B)(op: (T, B) => B): NEList[B] = new NEList(toList.scanRight(z)(op))

  /**
    * Computes length of longest segment whose elements all satisfy some predicate.
    *
    * @param p the predicate used to test elements.
    * @param from the index where the search starts.
    * @param the length of the longest segment of this <code>NEList</code> starting from index <code>from</code> such that every element of the
    *     segment satisfies the predicate <code>p</code>.
    */
  def segmentLength(p: T => Boolean, from: Int): Int = toList.segmentLength(p, from)

  /**
    * Groups elements in fixed size blocks by passing a &ldquo;sliding window&rdquo; over them (as opposed to partitioning them, as is done in grouped.)
    *
    * @param size the number of elements per group
    * @return an iterator producing <code>NEList</code>s of size <code>size</code>, except the last and the only element will be truncated
    *     if there are fewer elements than <code>size</code>.
    */
  def sliding(size: Int): Iterator[NEList[T]] = toList.sliding(size).map(new NEList(_))

  /**
    * Groups elements in fixed size blocks by passing a &ldquo;sliding window&rdquo; over them (as opposed to partitioning them, as is done in grouped.),
    * moving the sliding window by a given <code>step</code> each time.
    *
    * @param size the number of elements per group
    * @param step the distance between the first elements of successive groups
    * @return an iterator producing <code>NEList</code>s of size <code>size</code>, except the last and the only element will be truncated
    *     if there are fewer elements than <code>size</code>.
    */
  def sliding(size: Int, step: Int): Iterator[NEList[T]] = toList.sliding(size, step).map(new NEList(_))

  /**
    * The size of this <code>NEList</code>.
    *
    * <p>
    * @note <code>length</code> and <code>size</code> yield the same result, which will be <code>&gt;</code>= 1.
    * </p>
    *
    * @return the number of elements in this <code>NEList</code>.
    */
  def size: Int = toList.size

  /**
    * Sorts this <code>NEList</code> according to the <code>Ordering</code> of the result of applying the given function to every element.
    *
    * @tparam U the target type of the transformation <code>f</code>, and the type where the <code>Ordering</code> <code>ord</code> is defined.
    * @param f the transformation function mapping elements to some other domain <code>U</code>.
    * @param ord the ordering assumed on domain <code>U</code>.
    * @return a <code>NEList</code> consisting of the elements of this <code>NEList</code> sorted according to the <code>Ordering</code> where
    *    <code>x &lt; y if ord.lt(f(x), f(y))</code>.
    */
  def sortBy[U](f: T => U)(implicit ord: Ordering[U]): NEList[T] = new NEList(toList.sortBy(f))

  /**
    * Sorts this <code>NEList</code> according to a comparison function.
    *
    * <p>
    * The sort is stable. That is, elements that are equal (as determined by <code>lt</code>) appear in the same order in the
    * sorted <code>NEList</code> as in the original.
    * </p>
    *
    * @param the comparison function that tests whether its first argument precedes its second argument in the desired ordering.
    * @return a <code>NEList</code> consisting of the elements of this <code>NEList</code> sorted according to the comparison function <code>lt</code>.
    */
  def sortWith(lt: (T, T) => Boolean): NEList[T] = new NEList(toList.sortWith(lt))

  /**
    * Sorts this <code>NEList</code> according to an <code>Ordering</code>.
    *
    * <p>
    * The sort is stable. That is, elements that are equal (as determined by <code>lt</code>) appear in the same order in the
    * sorted <code>NEList</code> as in the original.
    * </p>
    *
    * @param ord the <code>Ordering</code> to be used to compare elements.
    * @param the comparison function that tests whether its first argument precedes its second argument in the desired ordering.
    * @return a <code>NEList</code> consisting of the elements of this <code>NEList</code> sorted according to the comparison function <code>lt</code>.
    */
  def sorted[U >: T](implicit ord: Ordering[U]): NEList[U] = new NEList(toList.sorted(ord))

  /**
    * Indicates whether this <code>NEList</code> starts with the given <code>Seq</code>.
    *
    * @param that the <code>Seq</code> slice to look for in this <code>NEList</code>
    * @return <code>true</code> if this <code>NEList</code> has <code>that</code> as a prefix, <code>false</code> otherwise.
    */
  def startsWith[B](that: Seq[B]): Boolean = toList.startsWith[Any](that)

  /**
    * Indicates whether this <code>NEList</code> starts with the given <code>Seq</code> at the given index.
    *
    * @param that the <code>Seq</code> slice to look for in this <code>NEList</code>
    * @param offset the index at which this <code>NEList</code> is searched.
    * @return <code>true</code> if this <code>NEList</code> has <code>that</code> as a slice at the index <code>offset</code>, <code>false</code> otherwise.
    */
  def startsWith[B](that: Seq[B], offset: Int): Boolean = toList.startsWith[Any](that, offset)

  /**
    * Indicates whether this <code>NEList</code> starts with the given <code>Vector</code>.
    *
    * @param that the <code>Vector</code> to test
    * @return <code>true</code> if this collection has <code>that</code> as a prefix, <code>false</code> otherwise.
    */
  def startsWith[B](that: Vector[B]): Boolean = toList.startsWith[Any](that)

  /**
    * Indicates whether this <code>NEList</code> starts with the given <code>NEList</code>.
    *
    * @param that the <code>NEList</code> to test
    * @return <code>true</code> if this collection has <code>that</code> as a prefix, <code>false</code> otherwise.
    */
  def startsWith[B](that: NEList[B]): Boolean = toList.startsWith[Any](that.toList)

  /**
    * Indicates whether this <code>NEList</code> starts with the given <code>Vector</code> at the given index.
    *
    * @param that the <code>Vector</code> slice to look for in this <code>NEList</code>
    * @param offset the index at which this <code>NEList</code> is searched.
    * @return <code>true</code> if this <code>NEList</code> has <code>that</code> as a slice at the index <code>offset</code>, <code>false</code> otherwise.
    */
  def startsWith[B](that: Vector[B], offset: Int): Boolean = toList.startsWith[Any](that, offset)

  /**
    * Indicates whether this <code>NEList</code> starts with the given <code>NEList</code> at the given index.
    *
    * @param that the <code>NEList</code> slice to look for in this <code>NEList</code>
    * @param offset the index at which this <code>NEList</code> is searched.
    * @return <code>true</code> if this <code>NEList</code> has <code>that</code> as a slice at the index <code>offset</code>, <code>false</code> otherwise.
    */
  def startsWith[B](that: NEList[B], offset: Int): Boolean = toList.startsWith[Any](that.toList, offset)

  /**
    * Returns <code>"NEList"</code>, the prefix of this object's <code>toString</code> representation.
    *
    * @return the string <code>"NEList"</code>
    */
  def stringPrefix: String = "NEList"

  /**
    * The result of summing all the elements of this <code>NEList</code>.
    *
    * <p>
    * This method can be invoked for any <code>NEList[T]</code> for which an implicit <code>Numeric[T]</code> exists.
    * </p>
    *
    * @return the sum of all elements
    */
  def sum[U >: T](implicit num: Numeric[U]): U = toList.sum(num)

  // import scala.collection.compat._

  /**
    * Converts this <code>NEList</code> into a collection of type <code>Col</code> by copying all elements.
    *
    * @tparam C1 the collection type to build.
    * @return a new collection containing all elements of this <code>NEList</code>.
    */
  def to[C1](factory: Factory[T, C1]): C1 = factory.fromSpecific(iterator)

  /**
    * Converts this <code>NEList</code> to an array.
    *
    * @return an array containing all elements of this <code>NEList</code>. A <code>ClassTag</code> must be available for the element type of this <code>NEList</code>.
    */
  def toArray[U >: T](implicit classTag: ClassTag[U]): Array[U] = toList.toArray

  /**
    * Converts this <code>NEList</code> to a <code>Vector</code>.
    *
    * @return a <code>Vector</code> containing all elements of this <code>NEList</code>.
    */
  def toVector: Vector[T] = toList.toVector

  /**
    * Converts this <code>NEList</code> to a mutable buffer.
    *
    * @return a buffer containing all elements of this <code>NEList</code>.
    */
  def toBuffer[U >: T]: mutable.Buffer[U] = toList.toBuffer

  /**
    * Converts this <code>NEList</code> to an immutable <code>IndexedSeq</code>.
    *
    * @return an immutable <code>IndexedSeq</code> containing all elements of this <code>NEList</code>.
    */
  def toIndexedSeq: collection.immutable.IndexedSeq[T] = toList.toVector

  /**
    * Converts this <code>NEList</code> to an iterable collection.
    *
    * @return an <code>Iterable</code> containing all elements of this <code>NEList</code>.
    */
  def toIterable: Iterable[T] = toList

  /**
    * Returns an <code>Iterator</code> over the elements in this <code>NEList</code>.
    *
    * @return an <code>Iterator</code> containing all elements of this <code>NEList</code>.
    */
  def toIterator: Iterator[T] = toList.iterator

  /**
    * Converts this <code>NEList</code> to a list.
    *
    * @return a list containing all elements of this <code>NEList</code>.
    */
  // def toList: List[T] = toList

  /**
    * Converts this <code>NEList</code> to a map.
    *
    * <p>
    * This method is unavailable unless the elements are members of <code>Tuple2</code>, each <code>((K, V))</code> becoming a key-value pair
    * in the map. Duplicate keys will be overwritten by later keys.
    * </p>
    *
    * @return a map of type <code>immutable.Map[K, V]</code> containing all key/value pairs of type <code>(K, V)</code> of this <code>NEList</code>.
    */
  def toMap[K, V](implicit ev: T <:< (K, V)): Map[K, V] = toList.toMap

  /**
    * Converts this <code>NEList</code> to an immutable <code>IndexedSeq</code>.
    *
    * @return an immutable <code>IndexedSeq</code> containing all elements of this <code>NEList</code>.
    */
  def toSeq: collection.immutable.Seq[T] = toList

  /**
    * Converts this <code>NEList</code> to a set.
    *
    * @return a set containing all elements of this <code>NEList</code>.
    */
  def toSet[U >: T]: Set[U] = toList.toSet

  /**
    * Returns a string representation of this <code>NEList</code>.
    *
    * @return the string <code>"NEList"</code> followed by the result of invoking <code>toString</code> on
    *   this <code>NEList</code>'s elements, surrounded by parentheses.
    */
  override def toString: String = "NEList(" + toList.mkString(", ") + ")"

  def transpose[U](implicit ev: T <:< NEList[U]): NEList[NEList[U]] = {
    val asLists = toList.map(ev)
    val list = asLists.map(_.toList).transpose
    new NEList(list.map(new NEList(_)))
  }

  /**
    * Produces a new <code>NEList</code> that contains all elements of this <code>NEList</code> and also all elements of a given <code>Vector</code>.
    *
    * <p>
    * <code>NEListX</code> <code>union</code> <code>everyY</code> is equivalent to <code>NEListX</code> <code>++</code> <code>everyY</code>.
    * </p>
    *
    * <p>
    * Another way to express this is that <code>NEListX</code> <code>union</code> <code>everyY</code> computes the order-presevring multi-set union
    * of <code>NEListX</code> and <code>everyY</code>. This <code>union</code> method is hence a counter-part of <code>diff</code> and <code>intersect</code> that
    * also work on multi-sets.
    * </p>
    *
    * @param that the <code>Vector</code> to add.
    * @return a new <code>NEList</code> that contains all elements of this <code>NEList</code> followed by all elements of <code>that</code> <code>Vector</code>.
    */
  def union[U >: T](that: Vector[U]): NEList[U] = new NEList(toList ++ that)

  /**
    * Produces a new <code>NEList</code> that contains all elements of this <code>NEList</code> and also all elements of a given <code>NEList</code>.
    *
    * <p>
    * <code>NEListX</code> <code>union</code> <code>NEListY</code> is equivalent to <code>NEListX</code> <code>++</code> <code>NEListY</code>.
    * </p>
    *
    * <p>
    * Another way to express this is that <code>NEListX</code> <code>union</code> <code>NEListY</code> computes the order-presevring multi-set union
    * of <code>NEListX</code> and <code>NEListY</code>. This <code>union</code> method is hence a counter-part of <code>diff</code> and <code>intersect</code> that
    * also work on multi-sets.
    * </p>
    *
    * @param that the <code>NEList</code> to add.
    * @return a new <code>NEList</code> that contains all elements of this <code>NEList</code> followed by all elements of <code>that</code>.
    */
  def union[U >: T](that: NEList[U]): NEList[U] = new NEList(toList ++ that.toList)

  /**
    * Produces a new <code>NEList</code> that contains all elements of this <code>NEList</code> and also all elements of a given <code>Seq</code>.
    *
    * <p>
    * <code>NEListX</code> <code>union</code> <code>ys</code> is equivalent to <code>NEListX</code> <code>++</code> <code>ys</code>.
    * </p>
    *
    * <p>
    * Another way to express this is that <code>NEListX</code> <code>union</code> <code>ys</code> computes the order-presevring multi-set union
    * of <code>NEListX</code> and <code>ys</code>. This <code>union</code> method is hence a counter-part of <code>diff</code> and <code>intersect</code> that
    * also work on multi-sets.
    * </p>
    *
    * @param that the <code>Seq</code> to add.
    * @return a new <code>NEList</code> that contains all elements of this <code>NEList</code> followed by all elements of <code>that</code> <code>Seq</code>.
    */
  def union[U >: T](that: Seq[U]): NEList[U] = new NEList(toList ++ that)

  /**
    * Converts this <code>NEList</code> of pairs into two <code>NEList</code>s of the first and second half of each pair.
    *
    * @tparam L the type of the first half of the element pairs
    * @tparam R the type of the second half of the element pairs
    * @param asPair an implicit conversion that asserts that the element type of this <code>NEList</code> is a pair.
    * @return a pair of <code>NEList</code>s, containing the first and second half, respectively, of each element pair of this <code>NEList</code>.
    */
  def unzip[L, R](implicit asPair: T => (L, R)): (NEList[L], NEList[R]) = {
    val unzipped = toList.unzip
    (new NEList(unzipped._1), new NEList(unzipped._2))
  }

  /**
    * Converts this <code>NEList</code> of triples into three <code>NEList</code>s of the first, second, and and third element of each triple.
    *
    * @tparam L the type of the first member of the element triples
    * @tparam R the type of the second member of the element triples
    * @tparam R the type of the third member of the element triples
    * @param asTriple an implicit conversion that asserts that the element type of this <code>NEList</code> is a triple.
    * @return a triple of <code>NEList</code>s, containing the first, second, and third member, respectively, of each element triple of this <code>NEList</code>.
    */
  def unzip3[L, M, R](implicit asTriple: T => (L, M, R)): (NEList[L], NEList[M], NEList[R]) = {
    val unzipped = toList.unzip3
    (new NEList(unzipped._1), new NEList(unzipped._2), new NEList(unzipped._3))
  }

  /**
    * A copy of this <code>NEList</code> with one single replaced element.
    *
    * @param idx the position of the replacement
    * @param elem the replacing element
    * @throws IndexOutOfBoundsException if the passed index is greater than or equal to the length of this <code>NEList</code>
    * @return a copy of this <code>NEList</code> with the element at position <code>idx</code> replaced by <code>elem</code>.
    */
  def updated[U >: T](idx: Int, elem: U): NEList[U] =
    try new NEList(toList.updated(idx, elem))
    catch { case _: UnsupportedOperationException => throw new IndexOutOfBoundsException(idx.toString) } // This is needed for 2.10 support. Can drop after.
  // Because 2.11 throws IndexOutOfBoundsException.

  /**
    * Returns a <code>NEList</code> formed from this <code>NEList</code> and an iterable collection by combining corresponding
    * elements in pairs. If one of the two collections is shorter than the other, placeholder elements will be used to extend the
    * shorter collection to the length of the longer.
    *
    * @tparm O the type of the second half of the returned pairs
    * @tparm U the type of the first half of the returned pairs
    * @param other the <code>Iterable</code> providing the second half of each result pair
    * @param thisElem the element to be used to fill up the result if this <code>NEList</code> is shorter than <code>that</code> <code>Iterable</code>.
    * @param thatElem the element to be used to fill up the result if <code>that</code> <code>Iterable</code> is shorter than this <code>NEList</code>.
    * @return a new <code>NEList</code> containing pairs consisting of corresponding elements of this <code>NEList</code> and <code>that</code>. The
    *     length of the returned collection is the maximum of the lengths of this <code>NEList</code> and <code>that</code>. If this <code>NEList</code>
    *     is shorter than <code>that</code>, <code>thisElem</code> values are used to pad the result. If <code>that</code> is shorter than this
    *     <code>NEList</code>, <code>thatElem</code> values are used to pad the result.
    */
  def zipAll[O, U >: T](other: collection.Iterable[O], thisElem: U, otherElem: O): NEList[(U, O)] =
    new NEList(toList.zipAll(other, thisElem, otherElem))

  /**
    * Zips this <code>NEList</code>  with its indices.
    *
    * @return A new <code>NEList</code> containing pairs consisting of all elements of this <code>NEList</code> paired with their index. Indices start at 0.
    */
  def zipWithIndex: NEList[(T, Int)] = new NEList(toList.zipWithIndex)
}

/**
  * Companion object for class <code>NEList</code>.
  */
object NEList extends NEListInstances {

  /**
    * Constructs a new <code>NEList</code> of one element
    */
  @inline def apply[T](singleElement: T): NEList[T] = new NEList[T](singleElement :: Nil)

  /**
    * Constructs a new <code>NEList</code> given at least two elements.
    *
    * @tparam T the type of the element contained in the new <code>NEList</code>
    * @param firstElement the first element (with index 0) contained in this <code>NEList</code>
    * @param otherElements a varargs of zero or more other elements (with index 1, 2, 3, ...) contained in this <code>NEList</code>
    */
  @inline def apply[T](firstElement: T, secondElement: T, otherElements: T*): NEList[T] = new NEList(firstElement :: secondElement :: otherElements.toList)

  /**
    * Constructs a new <code>NEList</code> given at least one element.
    */
  @inline def apply[T](firstElement: T, otherElements: Iterable[T]): NEList[T] = new NEList(firstElement :: otherElements.toList)

  /**
    * Variable argument extractor for <code>NEList</code>s.
    *
    * @param NEList: the <code>NEList</code> containing the elements to extract
    * @return an <code>Seq</code> containing this <code>NEList</code>s elements, wrapped in a <code>Some</code>
    */
  @inline def unapplySeq[T](NEList: NEList[T]): Some[Seq[T]] = Some(NEList.toList)

  /**
    * Optionally construct a <code>NEList</code> containing the elements, if any, of a given <code>List</code>.
    *
    * @param list the <code>List</code> with which to construct a <code>NEList</code>
    * @return a <code>NEList</code> containing the elements of the given <code>List</code>, if non-empty, wrapped in
    *     a <code>Some</code>; else <code>None</code> if the <code>List</code> is empty
    */
  @inline def from[T](list: List[T]): Option[NEList[T]] = {
    if (list.isEmpty) None else Some(new NEList(list))
  }

  /**
    * Optionally construct a <code>NEList</code> containing the elements, if any, of a given <code>Seq</code>.
    *
    * @param seq the <code>Seq</code> with which to construct a <code>NEList</code>
    * @return a <code>NEList</code> containing the elements of the given <code>Seq</code>, if non-empty, wrapped in
    *     a <code>Some</code>; else <code>None</code> if the <code>Seq</code> is empty
    */
  @inline def from[T](seq: Iterable[T]): Option[NEList[T]] = {
    from(seq.toList)
  }

  @inline def from[T](iterableOnce: IterableOnce[T]): Option[NEList[T]] = {
    from(iterableOnce.iterator.toList)
  }

  @inline def unsafeFrom[T](list: List[T]): NEList[T] = {
    require(list.nonEmpty)
    new NEList(list)
  }

  @inline implicit def asIterable[A](nel: NEList[A]): IterableOnce[A] = nel.toIterable

  implicit final class OptionOps[+A](private val option: Option[NEList[A]]) extends AnyVal {
    @inline def fromNEList: List[A] = if (option.isEmpty) Nil else option.get.toList
  }
}
