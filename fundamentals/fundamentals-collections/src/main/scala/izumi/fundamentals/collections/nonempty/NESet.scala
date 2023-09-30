package izumi.fundamentals.collections.nonempty

// shameless copypaste from Scalactic

import scala.collection.compat.*
import scala.collection.mutable.{ArrayBuffer, Buffer}
import scala.language.implicitConversions
import scala.reflect.ClassTag

// Can't be a LinearSeq[T] because Builder would be able to create an empty one.
/**
  * A non-empty Set: an ordered, immutable, non-empty collection of elements with <code>LinearSeq</code> performance characteristics.
  *
  * <p>
  * The purpose of <code>NESet</code> is to allow you to express in a type that a <code>Set</code> is non-empty, thereby eliminating the
  * need for (and potential exception from) a run-time check for non-emptiness. For a non-empty sequence with <code>IndexedSeq</code>
  * performance, see <a href="Vector.html"><code>Vector</code></a>.
  * </p>
  *
  * <h2>Constructing <code>NESet</code>s</h2>
  *
  * <p>
  * You can construct a <code>NESet</code> by passing one or more elements to the <code>NESet.apply</code> factory method:
  * </p>
  *
  * <pre class="stHighlight">
  * scala&gt; NESet(1, 2, 3)
  * res0: org.scalactic.anyvals.NESet[Int] = NESet(1, 2, 3)
  * </pre>
  *
  * <p>
  * Alternatively you can <em>cons</em> elements onto the <code>End</code> singleton object, similar to making a <code>Set</code> starting with <code>Nil</code>:
  * </p>
  *
  * <pre class="stHighlight">
  * scala&gt; 1 :: 2 :: 3 :: Nil
  * res0: Set[Int] = Set(1, 2, 3)
  *
  * scala&gt; 1 :: 2 :: 3 :: End
  * res1: org.scalactic.NESet[Int] = NESet(1, 2, 3)
  * </pre>
  *
  * <p>
  * Note that although <code>Nil</code> is a <code>Set[Nothing]</code>, <code>End</code> is
  * not a <code>NESet[Nothing]</code>, because no empty <code>NESet</code> exists. (A non-empty Set is a series
  * of connected links; if you have no links, you have no non-empty Set.)
  * </p>
  *
  * <pre class="stHighlight">
  * scala&gt; val nil: Set[Nothing] = Nil
  * nil: Set[Nothing] = Set()
  *
  * scala&gt; val nada: NESet[Nothing] = End
  * &lt;console&gt;:16: error: type mismatch;
  * found   : org.scalactic.anyvals.End.type
  * required: org.scalactic.anyvals.NESet[Nothing]
  *        val nada: NESet[Nothing] = End
  *                                          ^
  * </pre>
  *
  * <h2>Working with <code>NESet</code>s</h2>
  *
  * <p>
  * <code>NESet</code> does not extend Scala's <code>Seq</code> or <code>Traversable</code> traits because these require that
  * implementations may be empty. For example, if you invoke <code>tail</code> on a <code>Seq</code> that contains just one element,
  * you'll get an empty <code>Seq</code>:
  * </p>
  *
  * <pre class="stREPL">
  * scala&gt; Set(1).tail
  * res6: Set[Int] = Set()
  * </pre>
  *
  * <p>
  * On the other hand, many useful methods exist on <code>Seq</code> that when invoked on a non-empty <code>Seq</code> are guaranteed
  * to not result in an empty <code>Seq</code>. For convenience, <code>NESet</code> defines a method corresponding to every such <code>Seq</code>
  * method. Here are some examples:
  * </p>
  *
  * <pre class="stHighlight">
  * NESet(1, 2, 3).map(_ + 1)                        // Result: NESet(2, 3, 4)
  * NESet(1).map(_ + 1)                              // Result: NESet(2)
  * NESet(1, 2, 3).containsSlice(NESet(2, 3)) // Result: true
  * NESet(1, 2, 3).containsSlice(NESet(3, 4)) // Result: false
  * NESet(-1, -2, 3, 4, 5).minBy(_.abs)              // Result: -1
  * </pre>
  *
  * <p>
  * <code>NESet</code> does <em>not</em> currently define any methods corresponding to <code>Seq</code> methods that could result in
  * an empty <code>Seq</code>. However, an implicit converison from <code>NESet</code> to <code>Set</code>
  * is defined in the <code>NESet</code> companion object that will be applied if you attempt to call one of the missing methods. As a
  * result, you can invoke <code>filter</code> on an <code>NESet</code>, even though <code>filter</code> could result
  * in an empty sequence&mdash;but the result type will be <code>Set</code> instead of <code>NESet</code>:
  * </p>
  *
  * <pre class="stHighlight">
  * NESet(1, 2, 3).filter(_ &lt; 10) // Result: Set(1, 2, 3)
  * NESet(1, 2, 3).filter(_ &gt; 10) // Result: Set()
  * </pre>
  *
  * <p>
  * You can use <code>NESet</code>s in <code>for</code> expressions. The result will be an <code>NESet</code> unless
  * you use a filter (an <code>if</code> clause). Because filters are desugared to invocations of <code>filter</code>, the
  * result type will switch to a <code>Set</code> at that point. Here are some examples:
  * </p>
  *
  * <pre class="stREPL">
  * scala&gt; import org.scalactic.anyvals._
  * import org.scalactic.anyvals._
  *
  * scala&gt; for (i &lt;- NESet(1, 2, 3)) yield i + 1
  * res0: org.scalactic.anyvals.NESet[Int] = NESet(2, 3, 4)
  *
  * scala&gt; for (i &lt;- NESet(1, 2, 3) if i &lt; 10) yield i + 1
  * res1: Set[Int] = Set(2, 3, 4)
  *
  * scala&gt; for {
  *      |   i &lt;- NESet(1, 2, 3)
  *      |   j &lt;- NESet('a', 'b', 'c')
  *      | } yield (i, j)
  * res3: org.scalactic.anyvals.NESet[(Int, Char)] =
  *         NESet((1,a), (1,b), (1,c), (2,a), (2,b), (2,c), (3,a), (3,b), (3,c))
  *
  * scala&gt; for {
  *      |   i &lt;- NESet(1, 2, 3) if i &lt; 10
  *      |   j &lt;- NESet('a', 'b', 'c')
  *      | } yield (i, j)
  * res6: Set[(Int, Char)] =
  *         Set((1,a), (1,b), (1,c), (2,a), (2,b), (2,c), (3,a), (3,b), (3,c))
  * </pre>
  *
  * @tparam T the type of elements contained in this <code>NESet</code>
  */
final class NESet[T] private (val toSet: Set[T]) extends AnyVal {

  /**
    * Returns a new <code>NESet</code> containing the elements of this <code>NESet</code> followed by the elements of the passed <code>NESet</code>.
    * The element type of the resulting <code>NESet</code> is the most specific superclass encompassing the element types of this and the passed <code>NESet</code>.
    *
    * @tparam U the element type of the returned <code>NESet</code>
    * @param other the <code>NESet</code> to append
    * @return a new <code>NESet</code> that contains all the elements of this <code>NESet</code> followed by all elements of <code>other</code>.
    */
  def ++[U >: T](other: NESet[U]): NESet[U] = new NESet(toSet ++ other.toSet)

  /**
    * Returns a new <code>NESet</code> containing the elements of this <code>NESet</code> followed by the elements of the passed <code>Vector</code>.
    * The element type of the resulting <code>NESet</code> is the most specific superclass encompassing the element types of this <code>NESet</code> and the passed <code>Vector</code>.
    *
    * @tparam U the element type of the returned <code>NESet</code>
    * @param other the <code>Vector</code> to append
    * @return a new <code>NESet</code> that contains all the elements of this <code>NESet</code> followed by all elements of <code>other</code>.
    */
  def ++[U >: T](other: Vector[U]): NESet[U] = new NESet(toSet ++ other)

  // TODO: Have I added these extra ++, etc. methods to Vector that take a NESet?

  /**
    * Returns a new <code>NESet</code> containing the elements of this <code>NESet</code> followed by the elements of the passed <code>GenTraversableOnce</code>.
    * The element type of the resulting <code>NESet</code> is the most specific superclass encompassing the element types of this <code>NESet</code>
    * and the passed <code>GenTraversableOnce</code>.
    *
    * @param other the <code>GenTraversableOnce</code> to append
    * @return a new <code>NESet</code> that contains all the elements of this <code>NESet</code> followed by all elements of <code>other</code>.
    */
  def ++(other: IterableOnce[T]): NESet[T] =
    if (other.iterator.isEmpty) this else new NESet(toSet ++ other.iterator.to(Set))

  /**
    * Returns a new <code>NESet</code> with the given element added.
    *
    * @param element the element to add to this <code>NESet</code>
    * @return a new <code>NESet</code> consisting of <code>element</code> and all elements of this <code>NESet</code>.
    */
  def +(element: T): NESet[T] = new NESet(toSet ++ Set(element))

  /**
    * Appends all elements of this <code>NESet</code> to a string builder. The written text will consist of a concatenation of the result of invoking <code>toString</code>
    * on of every element of this <code>NESet</code>, without any separator string.
    *
    * @param sb the string builder to which elements will be appended
    * @return the string builder, <code>sb</code>, to which elements were appended.
    */
  def addString(sb: StringBuilder): StringBuilder = toSet.addString(sb)

  /**
    * Check if an element exists at its index in the <code>NESet</code>.
    *
    * @return <code>true</code> if a element exists in <code>NESet</code> at index <code>idx</code>, where <code>false</code> indicates the element at index <code>idx</code> does not exist.
    */
  def apply(elem: T): Boolean = toSet(elem)

  /**
    * Appends all elements of this <code>NESet</code> to a string builder using a separator string. The written text will consist of a concatenation of the
    * result of invoking <code>toString</code>
    * on of every element of this <code>NESet</code>, separated by the string <code>sep</code>.
    *
    * @param sb the string builder to which elements will be appended
    * @param sep the separator string
    * @return the string builder, <code>sb</code>, to which elements were appended.
    */
  def addString(sb: StringBuilder, sep: String): StringBuilder = toSet.addString(sb, sep)

  /**
    * Appends all elements of this <code>NESet</code> to a string builder using start, end, and separator strings. The written text will consist of a concatenation of
    * the string <code>start</code>; the result of invoking <code>toString</code> on all elements of this <code>NESet</code>,
    * separated by the string <code>sep</code>; and the string <code>end</code>
    *
    * @param sb the string builder to which elements will be appended
    * @param start the starting string
    * @param sep the separator string
    * @param start the ending string
    * @return the string builder, <code>sb</code>, to which elements were appended.
    */
  def addString(sb: StringBuilder, start: String, sep: String, end: String): StringBuilder = toSet.addString(sb, start, sep, end)

  /**
    * Finds the first element of this <code>NESet</code> for which the given partial function is defined, if any, and applies the partial function to it.
    *
    * @param pf the partial function
    * @return an <code>Option</code> containing <code>pf</code> applied to the first element for which it is defined, or <code>None</code> if
    *    the partial function was not defined for any element.
    */
  def collectFirst[U](pf: PartialFunction[T, U]): Option[U] = toSet.collectFirst(pf)

  /**
    * Indicates whether this <code>NESet</code> contains a given value as an element.
    *
    * @param elem the element to look for
    * @return true if this <code>NESet</code> has an element that is equal (as determined by <code>==)</code> to <code>elem</code>, false otherwise.
    */
  def contains(elem: T): Boolean = toSet.contains(elem)

  /**
    * Copies values of this <code>NESet</code> to an array. Fills the given array <code>arr</code> with values of this <code>NESet</code>. Copying
    * will stop once either the end of the current <code>NESet</code> is reached, or the end of the array is reached.
    *
    * @param arr the array to fill
    */
  def copyToArray[U >: T](arr: Array[U]): Unit = {
    toSet.copyToArray(arr)
    ()
  }

  /**
    * Copies values of this <code>NESet</code> to an array. Fills the given array <code>arr</code> with values of this <code>NESet</code>, beginning at
    * index <code>start</code>. Copying will stop once either the end of the current <code>NESet</code> is reached, or the end of the array is reached.
    *
    * @param arr the array to fill
    * @param start the starting index
    */
  def copyToArray[U >: T](arr: Array[U], start: Int): Unit = {
    toSet.copyToArray(arr, start)
    ()
  }

  /**
    * Copies values of this <code>NESet</code> to an array. Fills the given array <code>arr</code> with at most <code>len</code> elements of this <code>NESet</code>, beginning at
    * index <code>start</code>. Copying will stop once either the end of the current <code>NESet</code> is reached, the end of the array is reached, or
    * <code>len</code> elements have been copied.
    *
    * @param arr the array to fill
    * @param start the starting index
    * @param len the maximum number of elements to copy
    */
  def copyToArray[U >: T](arr: Array[U], start: Int, len: Int): Unit = {
    toSet.copyToArray(arr, start, len)
    ()
  }

  /**
    * Copies all elements of this <code>NESet</code> to a buffer.
    *
    * @param buf the buffer to which elements are copied
    */
  def copyToBuffer[U >: T](buf: Buffer[U]): Unit = {
    buf ++= toSet
    ()
  }

  /**
    * Counts the number of elements in this <code>NESet</code> that satisfy a predicate.
    *
    * @param p the predicate used to test elements.
    * @return the number of elements satisfying the predicate <code>p</code>.
    */
  def count(p: T => Boolean): Int = toSet.count(p)

  /**
    * Indicates whether a predicate holds for at least one of the elements of this <code>NESet</code>.
    *
    * @param p the predicate used to test elements.
    * @return <code>true</code> if the given predicate <code>p</code> holds for some of the elements of this <code>NESet</code>, otherwise <code>false</code>.
    */
  def exists(p: T => Boolean): Boolean = toSet.exists(p)

  /**
    * Finds the first element of this <code>NESet</code> that satisfies the given predicate, if any.
    *
    * @param p the predicate used to test elements
    * @return an <code>Some</code> containing the first element in this <code>NESet</code> that satisfies <code>p</code>, or <code>None</code> if none exists.
    */
  def find(p: T => Boolean): Option[T] = toSet.find(p)

  /**
    * Builds a new <code>NESet</code> by applying a function to all elements of this <code>NESet</code> and using the elements of the resulting <code>NESet</code>s.
    *
    * @tparam U the element type of the returned <code>NESet</code>
    * @param f the function to apply to each element.
    * @return a new <code>NESet</code> containing elements obtained by applying the given function <code>f</code> to each element of this <code>NESet</code> and concatenating
    *    the elements of resulting <code>NESet</code>s.
    */
  def flatMap[U](f: T => NESet[U]): NESet[U] = {
    val buf = new ArrayBuffer[U]
    for (ele <- toSet)
      buf ++= f(ele).toSet
    new NESet(buf.toSet)
  }

  /**
    * Converts this <code>NESet</code> of <code>NESet</code>s into a <code>NESet</code>
    * formed by the elements of the nested <code>NESet</code>s.
    *
    * <p>
    * @note You cannot use this <code>flatten</code> method on a <code>NESet</code> that contains a <code>GenTraversableOnce</code>s, because
    * if all the nested <code>GenTraversableOnce</code>s were empty, you'd end up with an empty <code>NESet</code>.
    * </p>
    *
    * @tparm B the type of the elements of each nested <code>NESet</code>
    * @return a new <code>NESet</code> resulting from concatenating all nested <code>NESet</code>s.
    */
  def flatten[B](implicit ev: T <:< NESet[B]): NESet[B] = flatMap(ev)

  /**
    * Folds the elements of this <code>NESet</code> using the specified associative binary operator.
    *
    * <p>
    * The order in which operations are performed on elements is unspecified and may be nondeterministic.
    * </p>
    *
    * @tparam U a type parameter for the binary operator, a supertype of T.
    * @param z a neutral element for the fold operation; may be added to the result an arbitrary number of
    *     times, and must not change the result (<em>e.g.</em>, <code>Nil</code> for Set concatenation,
    *     0 for addition, or 1 for multiplication.)
    * @param op a binary operator that must be associative
    * @return the result of applying fold operator <code>op</code> between all the elements and <code>z</code>
    */
  def fold[U >: T](z: U)(op: (U, U) => U): U = toSet.fold(z)(op)

  /**
    * Applies a binary operator to a start value and all elements of this <code>NESet</code>, going left to right.
    *
    * @tparam B the result type of the binary operator.
    * @param z the start value.
    * @param op the binary operator.
    * @return the result of inserting <code>op</code> between consecutive elements of this <code>NESet</code>, going left to right, with the start value,
    *     <code>z</code>, on the left:
    *
    * <pre>
    * op(...op(op(z, x_1), x_2), ..., x_n)
    * </pre>
    *
    * <p>
    * where x<sub>1</sub>, ..., x<sub>n</sub> are the elements of this <code>NESet</code>.
    * </p>
    */
  def foldLeft[B](z: B)(op: (B, T) => B): B = toSet.foldLeft(z)(op)

  /**
    * Applies a binary operator to all elements of this <code>NESet</code> and a start value, going right to left.
    *
    * @tparam B the result of the binary operator
    * @param z the start value
    * @param op the binary operator
    * @return the result of inserting <code>op</code> between consecutive elements of this <code>NESet</code>, going right to left, with the start value,
    *     <code>z</code>, on the right:
    *
    * <pre>
    * op(x_1, op(x_2, ... op(x_n, z)...))
    * </pre>
    *
    * <p>
    * where x<sub>1</sub>, ..., x<sub>n</sub> are the elements of this <code>NESet</code>.
    * </p>
    */
  def foldRight[B](z: B)(op: (T, B) => B): B = toSet.foldRight(z)(op)

  /**
    * Indicates whether a predicate holds for all elements of this <code>NESet</code>.
    *
    * @param p the predicate used to test elements.
    * @return <code>true</code> if the given predicate <code>p</code> holds for all elements of this <code>NESet</code>, otherwise <code>false</code>.
    */
  def forall(p: T => Boolean): Boolean = toSet.forall(p)

  /**
    * Applies a function <code>f</code> to all elements of this <code>NESet</code>.
    *
    * @param f the function that is applied for its side-effect to every element. The result of function <code>f</code> is discarded.
    */
  def foreach(f: T => Unit): Unit = toSet.foreach(f)

  /**
    * Partitions this <code>NESet</code> into a map of <code>NESet</code>s according to some discriminator function.
    *
    * @tparam K the type of keys returned by the discriminator function.
    * @param f the discriminator function.
    * @return A map from keys to <code>NESet</code>s such that the following invariant holds:
    *
    * <pre>
    * (NESet.toSet partition f)(k) = xs filter (x =&gt; f(x) == k)
    * </pre>
    *
    * <p>
    * That is, every key <code>k</code> is bound to a <code>NESet</code> of those elements <code>x</code> for which <code>f(x)</code> equals <code>k</code>.
    * </p>
    */
  def groupBy[K](f: T => K): Map[K, NESet[T]] = {
    val mapKToSet = toSet.groupBy(f)
    mapKToSet.view.mapValues {
      Set => new NESet(Set)
    }.toMap
  }

  /**
    * Partitions elements into fixed size <code>NESet</code>s.
    *
    * @param size the number of elements per group
    * @return An iterator producing <code>NESet</code>s of size <code>size</code>, except the last will be truncated if the elements don't divide evenly.
    */
  def grouped(size: Int): Iterator[NESet[T]] = {
    val itOfSet = toSet.grouped(size)
    itOfSet.map {
      Set => new NESet(Set)
    }
  }

  /**
    * Returns <code>true</code> to indicate this <code>NESet</code> has a definite size, since all <code>NESet</code>s are strict collections.
    */
  def hasDefiniteSize: Boolean = true

  // override def hashCode: Int = toSet.hashCode

  /**
    * Selects the first element of this <code>NESet</code>.
    *
    * @return the first element of this <code>NESet</code>.
    */
  def head: T = toSet.head

  def tail: Set[T] = toSet.tail

  /**
    * Returns <code>false</code> to indicate this <code>NESet</code>, like all <code>NESet</code>s, is non-empty.
    *
    * @return false
    */
  def isEmpty: Boolean = false

  /**
    * Returns <code>true</code> to indicate this <code>NESet</code>, like all <code>NESet</code>s, can be traversed repeatedly.
    *
    * @return true
    */
  def isTraversableAgain: Boolean = true

  /**
    * Creates and returns a new iterator over all elements contained in this <code>NESet</code>.
    *
    * @return the new iterator
    */
  def iterator: Iterator[T] = toSet.iterator

  /**
    * Selects the last element of this <code>NESet</code>.
    *
    * @return the last element of this <code>NESet</code>.
    */
  def last: T = toSet.last

  /**
    * Builds a new <code>NESet</code> by applying a function to all elements of this <code>NESet</code>.
    *
    * @tparam U the element type of the returned <code>NESet</code>.
    * @param f the function to apply to each element.
    * @return a new <code>NESet</code> resulting from applying the given function <code>f</code> to each element of this <code>NESet</code> and collecting the results.
    */
  def map[U](f: T => U): NESet[U] =
    new NESet(toSet.map(f))

  /**
    * Finds the largest element.
    *
    * @return the largest element of this <code>NESet</code>.
    */
  def max[U >: T](implicit cmp: Ordering[U]): T = toSet.max(cmp)

  /**
    * Finds the largest result after applying the given function to every element.
    *
    * @return the largest result of applying the given function to every element of this <code>NESet</code>.
    */
  def maxBy[U](f: T => U)(implicit cmp: Ordering[U]): T = toSet.maxBy(f)(cmp)

  /**
    * Finds the smallest element.
    *
    * @return the smallest element of this <code>NESet</code>.
    */
  def min[U >: T](implicit cmp: Ordering[U]): T = toSet.min(cmp)

  /**
    * Finds the smallest result after applying the given function to every element.
    *
    * @return the smallest result of applying the given function to every element of this <code>NESet</code>.
    */
  def minBy[U](f: T => U)(implicit cmp: Ordering[U]): T = toSet.minBy(f)(cmp)

  /**
    * Displays all elements of this <code>NESet</code> in a string.
    *
    * @return a string representation of this <code>NESet</code>. In the resulting string, the result of invoking <code>toString</code> on all elements of this
    *     <code>NESet</code> follow each other without any separator string.
    */
  def mkString: String = toSet.mkString

  /**
    * Displays all elements of this <code>NESet</code> in a string using a separator string.
    *
    * @param sep the separator string
    * @return a string representation of this <code>NESet</code>. In the resulting string, the result of invoking <code>toString</code> on all elements of this
    *     <code>NESet</code> are separated by the string <code>sep</code>.
    */
  def mkString(sep: String): String = toSet.mkString(sep)

  /**
    * Displays all elements of this <code>NESet</code> in a string using start, end, and separator strings.
    *
    * @param start the starting string.
    * @param sep the separator string.
    * @param end the ending string.
    * @return a string representation of this <code>NESet</code>. The resulting string begins with the string <code>start</code> and ends with the string
    *     <code>end</code>. Inside, In the resulting string, the result of invoking <code>toString</code> on all elements of this <code>NESet</code> are
    *     separated by the string <code>sep</code>.
    */
  def mkString(start: String, sep: String, end: String): String = toSet.mkString(start, sep, end)

  /**
    * Returns <code>true</code> to indicate this <code>NESet</code>, like all <code>NESet</code>s, is non-empty.
    *
    * @return true
    */
  def nonEmpty: Boolean = true

  /**
    * The result of multiplying all the elements of this <code>NESet</code>.
    *
    * <p>
    * This method can be invoked for any <code>NESet[T]</code> for which an implicit <code>Numeric[T]</code> exists.
    * </p>
    *
    * @return the product of all elements
    */
  def product[U >: T](implicit num: Numeric[U]): U = toSet.product(num)

  /**
    * Reduces the elements of this <code>NESet</code> using the specified associative binary operator.
    *
    * <p>
    * The order in which operations are performed on elements is unspecified and may be nondeterministic.
    * </p>
    *
    * @tparam U a type parameter for the binary operator, a supertype of T.
    * @param op a binary operator that must be associative.
    * @return the result of applying reduce operator <code>op</code> between all the elements of this <code>NESet</code>.
    */
  def reduce[U >: T](op: (U, U) => U): U = toSet.reduce(op)

  /**
    * Applies a binary operator to all elements of this <code>NESet</code>, going left to right.
    *
    * @tparam U the result type of the binary operator.
    * @param op the binary operator.
    * @return the result of inserting <code>op</code> between consecutive elements of this <code>NESet</code>, going left to right:
    *
    * <pre>
    * op(...op(op(x_1, x_2), x_3), ..., x_n)
    * </pre>
    *
    * <p>
    * where x<sub>1</sub>, ..., x<sub>n</sub> are the elements of this <code>NESet</code>.
    * </p>
    */
  def reduceLeft[U >: T](op: (U, T) => U): U = toSet.reduceLeft(op)

  /**
    * Applies a binary operator to all elements of this <code>NESet</code>, going left to right, returning the result in a <code>Some</code>.
    *
    * @tparam U the result type of the binary operator.
    * @param op the binary operator.
    * @return a <code>Some</code> containing the result of <code>reduceLeft(op)</code>
    * </p>
    */
  def reduceLeftOption[U >: T](op: (U, T) => U): Option[U] = toSet.reduceLeftOption(op)

  def reduceOption[U >: T](op: (U, U) => U): Option[U] = toSet.reduceOption(op)

  /**
    * Applies a binary operator to all elements of this <code>NESet</code>, going right to left.
    *
    * @tparam U the result of the binary operator
    * @param op the binary operator
    * @return the result of inserting <code>op</code> between consecutive elements of this <code>NESet</code>, going right to left:
    *
    * <pre>
    * op(x_1, op(x_2, ... op(x_{n-1}, x_n)...))
    * </pre>
    *
    * <p>
    * where x<sub>1</sub>, ..., x<sub>n</sub> are the elements of this <code>NESet</code>.
    * </p>
    */
  def reduceRight[U >: T](op: (T, U) => U): U = toSet.reduceRight(op)

  /**
    * Applies a binary operator to all elements of this <code>NESet</code>, going right to left, returning the result in a <code>Some</code>.
    *
    * @tparam U the result of the binary operator
    * @param op the binary operator
    * @return a <code>Some</code> containing the result of <code>reduceRight(op)</code>
    */
  def reduceRightOption[U >: T](op: (T, U) => U): Option[U] = toSet.reduceRightOption(op)

  /**
    * Checks if the given <code>Iterable</code> contains the same elements in the same order as this <code>NESet</code>.
    *
    * @param that the <code>Iterable</code> with which to compare
    * @return <code>true</code>, if both this <code>NESet</code> and the given <code>Iterable</code> contain the same elements
    *     in the same order, <code>false</code> otherwise.
    */
  def sameElements[U >: T](that: Iterable[U]): Boolean = toSet == that.toSet

  /**
    * Checks if the given <code>Vector</code> contains the same elements in the same order as this <code>NESet</code>.
    *
    * @param that the <code>Vector</code> with which to compare
    * @return <code>true</code>, if both this and the given <code>Vector</code> contain the same elements
    *     in the same order, <code>false</code> otherwise.
    */
  def sameElements[U >: T](that: Vector[U]): Boolean = toSet == that.toSet

  /**
    * Checks if the given <code>NESet</code> contains the same elements in the same order as this <code>NESet</code>.
    *
    * @param that the <code>NESet</code> with which to compare
    * @return <code>true</code>, if both this and the given <code>NESet</code> contain the same elements
    *     in the same order, <code>false</code> otherwise.
    */
  def sameElements[U >: T](that: NESet[U]): Boolean = toSet == that.toSet

  /**
    * Computes a prefix scan of the elements of this <code>NESet</code>.
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
    * NESet(1, 2, 3).scan(0)(_ + _) == NESet(0, 1, 3, 6)
    * NESet(1, 2, 3).scan("z")(_ + _.toString) == NESet("z", "z1", "z12", "z123")
    * </pre>
    *
    * @tparam U a type parameter for the binary operator, a supertype of T, and the type of the resulting <code>NESet</code>.
    * @param z a neutral element for the scan operation; may be added to the result an arbitrary number of
    *     times, and must not change the result (<em>e.g.</em>, <code>Nil</code> for Set concatenation,
    *     0 for addition, or 1 for multiplication.)
    * @param op a binary operator that must be associative
    * @return a new <code>NESet</code> containing the prefix scan of the elements in this <code>NESet</code>
    */
  def scan[U >: T](z: U)(op: (U, U) => U): NESet[U] = new NESet(toSet.scan(z)(op))

  /**
    * Produces a <code>NESet</code> containing cumulative results of applying the operator going left to right.
    *
    * <p>
    * Here are some examples:
    * </p>
    *
    * <pre class="stHighlight">
    * NESet(1, 2, 3).scanLeft(0)(_ + _) == NESet(0, 1, 3, 6)
    * NESet(1, 2, 3).scanLeft("z")(_ + _) == NESet("z", "z1", "z12", "z123")
    * </pre>
    *
    * @tparam B the result type of the binary operator and type of the resulting <code>NESet</code>
    * @param z the start value.
    * @param op the binary operator.
    * @return a new <code>NESet</code> containing the intermediate results of inserting <code>op</code> between consecutive elements of this <code>NESet</code>,
    *     going left to right, with the start value, <code>z</code>, on the left.
    */
  def scanLeft[B](z: B)(op: (B, T) => B): NESet[B] = new NESet(toSet.scanLeft(z)(op))

  /**
    * Produces a <code>NESet</code> containing cumulative results of applying the operator going right to left.
    *
    * <p>
    * Here are some examples:
    * </p>
    *
    * <pre class="stHighlight">
    * NESet(1, 2, 3).scanRight(0)(_ + _) == NESet(6, 5, 3, 0)
    * NESet(1, 2, 3).scanRight("z")(_ + _) == NESet("123z", "23z", "3z", "z")
    * </pre>
    *
    * @tparam B the result of the binary operator and type of the resulting <code>NESet</code>
    * @param z the start value
    * @param op the binary operator
    * @return a new <code>NESet</code> containing the intermediate results of inserting <code>op</code> between consecutive elements of this <code>NESet</code>,
    *     going right to left, with the start value, <code>z</code>, on the right.
    */
  def scanRight[B](z: B)(op: (T, B) => B): NESet[B] = new NESet(toSet.scanRight(z)(op))

  /**
    * Groups elements in fixed size blocks by passing a &ldquo;sliding window&rdquo; over them (as opposed to partitioning them, as is done in grouped.)
    *
    * @param size the number of elements per group
    * @return an iterator producing <code>NESet</code>s of size <code>size</code>, except the last and the only element will be truncated
    *     if there are fewer elements than <code>size</code>.
    */
  def sliding(size: Int): Iterator[NESet[T]] = toSet.sliding(size).map(new NESet(_))

  /**
    * Groups elements in fixed size blocks by passing a &ldquo;sliding window&rdquo; over them (as opposed to partitioning them, as is done in grouped.),
    * moving the sliding window by a given <code>step</code> each time.
    *
    * @param size the number of elements per group
    * @param step the distance between the first elements of successive groups
    * @return an iterator producing <code>NESet</code>s of size <code>size</code>, except the last and the only element will be truncated
    *     if there are fewer elements than <code>size</code>.
    */
  def sliding(size: Int, step: Int): Iterator[NESet[T]] = toSet.sliding(size, step).map(new NESet(_))

  /**
    * The size of this <code>NESet</code>.
    *
    * <p>
    * @note <code>length</code> and <code>size</code> yield the same result, which will be <code>&gt;</code>= 1.
    * </p>
    *
    * @return the number of elements in this <code>NESet</code>.
    */
  def size: Int = toSet.size

  /**
    * Returns <code>"NESet"</code>, the prefix of this object's <code>toString</code> representation.
    *
    * @return the string <code>"NESet"</code>
    */
  def stringPrefix: String = "NESet"

  def subsetOf(that: Set[T]): Boolean = toSet.subsetOf(that)

  /**
    * The result of summing all the elements of this <code>NESet</code>.
    *
    * <p>
    * This method can be invoked for any <code>NESet[T]</code> for which an implicit <code>Numeric[T]</code> exists.
    * </p>
    *
    * @return the sum of all elements
    */
  def sum[U >: T](implicit num: Numeric[U]): U = toSet.sum(num)

  /**
    * Converts this <code>NESet</code> into a collection of type <code>Col</code> by copying all elements.
    *
    * @tparam C1 the collection type to build.
    * @return a new collection containing all elements of this <code>NESet</code>.
    */
  def to[C1](factory: Factory[T, C1]): C1 = factory.fromSpecific(iterator)

  /**
    * Converts this <code>NESet</code> to an array.
    *
    * @return an array containing all elements of this <code>NESet</code>. A <code>ClassTag</code> must be available for the element type of this <code>NESet</code>.
    */
  def toArray[U >: T](implicit classTag: ClassTag[U]): Array[U] = toSet.toArray

  /**
    * Converts this <code>NESet</code> to a <code>Vector</code>.
    *
    * @return a <code>Vector</code> containing all elements of this <code>NESet</code>.
    */
  def toVector: Vector[T] = toSet.toVector

  /**
    * Converts this <code>NESet</code> to a mutable buffer.
    *
    * @return a buffer containing all elements of this <code>NESet</code>.
    */
  def toBuffer[U >: T]: Buffer[U] = toSet.toBuffer

  /**
    * Converts this <code>NESet</code> to an immutable <code>IndexedSeq</code>.
    *
    * @return an immutable <code>IndexedSeq</code> containing all elements of this <code>NESet</code>.
    */
  def toIndexedSeq: scala.collection.immutable.IndexedSeq[T] = toSet.toVector

  /**
    * Converts this <code>NESet</code> to an iterable collection.
    *
    * @return an <code>Iterable</code> containing all elements of this <code>NESet</code>.
    */
  def toIterable: Iterable[T] = toSet

  /**
    * Returns an <code>Iterator</code> over the elements in this <code>NESet</code>.
    *
    * @return an <code>Iterator</code> containing all elements of this <code>NESet</code>.
    */
  def toIterator: Iterator[T] = toSet.iterator

  /**
    * Converts this <code>NESet</code> to a Set.
    *
    * @return a Set containing all elements of this <code>NESet</code>.
    */
  // def toSet: Set[T] = toSet

  /**
    * Converts this <code>NESet</code> to a map.
    *
    * <p>
    * This method is unavailable unless the elements are members of <code>Tuple2</code>, each <code>((K, V))</code> becoming a key-value pair
    * in the map. Duplicate keys will be overwritten by later keys.
    * </p>
    *
    * @return a map of type <code>immutable.Map[K, V]</code> containing all key/value pairs of type <code>(K, V)</code> of this <code>NESet</code>.
    */
  def toMap[K, V](implicit ev: T <:< (K, V)): Map[K, V] = toSet.toMap

  /**
    * Converts this <code>NESet</code> to an immutable <code>IndexedSeq</code>.
    *
    * @return an immutable <code>IndexedSeq</code> containing all elements of this <code>NESet</code>.
    */
  def toSeq: Seq[T] = toSet.toSeq

  /**
    * Converts this <code>NESet</code> to a set.
    *
    * @return a set containing all elements of this <code>NESet</code>.
    */
  def toList: scala.collection.immutable.List[T] = toSet.toList

  /**
    * Returns a string representation of this <code>NESet</code>.
    *
    * @return the string <code>"NESet"</code> followed by the result of invoking <code>toString</code> on
    *   this <code>NESet</code>'s elements, surrounded by parentheses.
    */
  override def toString: String = "NESet(" + toSet.mkString(", ") + ")"

  def transpose[U](implicit ev: T <:< NESet[U]): NESet[NESet[U]] = {
    val asSets = toSet.map(ev)
    val Set = asSets.map(_.toSet).transpose
    new NESet(Set.map(new NESet(_)))
  }

  /**
    * Produces a new <code>NESet</code> that contains all elements of this <code>NESet</code> and also all elements of a given <code>Vector</code>.
    *
    * <p>
    * <code>NESetX</code> <code>union</code> <code>everyY</code> is equivalent to <code>NESetX</code> <code>++</code> <code>everyY</code>.
    * </p>
    *
    * <p>
    * Another way to express this is that <code>NESetX</code> <code>union</code> <code>everyY</code> computes the order-presevring multi-set union
    * of <code>NESetX</code> and <code>everyY</code>. This <code>union</code> method is hence a counter-part of <code>diff</code> and <code>intersect</code> that
    * also work on multi-sets.
    * </p>
    *
    * @param that the <code>Vector</code> to add.
    * @return a new <code>NESet</code> that contains all elements of this <code>NESet</code> followed by all elements of <code>that</code> <code>Vector</code>.
    */
  def union(that: Vector[T]): NESet[T] = new NESet(toSet union that.toSet)

  /**
    * Produces a new <code>NESet</code> that contains all elements of this <code>NESet</code> and also all elements of a given <code>NESet</code>.
    *
    * <p>
    * <code>NESetX</code> <code>union</code> <code>NESetY</code> is equivalent to <code>NESetX</code> <code>++</code> <code>NESetY</code>.
    * </p>
    *
    * <p>
    * Another way to express this is that <code>NESetX</code> <code>union</code> <code>NESetY</code> computes the order-presevring multi-set union
    * of <code>NESetX</code> and <code>NESetY</code>. This <code>union</code> method is hence a counter-part of <code>diff</code> and <code>intersect</code> that
    * also work on multi-sets.
    * </p>
    *
    * @param that the <code>NESet</code> to add.
    * @return a new <code>NESet</code> that contains all elements of this <code>NESet</code> followed by all elements of <code>that</code>.
    */
  def union(that: NESet[T]): NESet[T] = new NESet(toSet union that.toSet)

  /**
    * Produces a new <code>NESet</code> that contains all elements of this <code>NESet</code> and also all elements of a given <code>GenSeq</code>.
    *
    * <p>
    * <code>NESetX</code> <code>union</code> <code>ys</code> is equivalent to <code>NESetX</code> <code>++</code> <code>ys</code>.
    * </p>
    *
    * <p>
    * Another way to express this is that <code>NESetX</code> <code>union</code> <code>ys</code> computes the order-presevring multi-set union
    * of <code>NESetX</code> and <code>ys</code>. This <code>union</code> method is hence a counter-part of <code>diff</code> and <code>intersect</code> that
    * also work on multi-sets.
    * </p>
    *
    * @param that the <code>Set</code> to add.
    * @return a new <code>NESet</code> that contains all elements of this <code>NESet</code> followed by all elements of <code>that</code> <code>GenSeq</code>.
    */
  def union(that: Set[T])(implicit dummyImplicit: DummyImplicit): NESet[T] = new NESet(toSet.union(that))

  /**
    * Converts this <code>NESet</code> of pairs into two <code>NESet</code>s of the first and second half of each pair.
    *
    * @tparam L the type of the first half of the element pairs
    * @tparam R the type of the second half of the element pairs
    * @param asPair an implicit conversion that asserts that the element type of this <code>NESet</code> is a pair.
    * @return a pair of <code>NESet</code>s, containing the first and second half, respectively, of each element pair of this <code>NESet</code>.
    */
  def unzip[L, R](implicit asPair: T => (L, R)): (NESet[L], NESet[R]) = {
    val unzipped = toSet.unzip
    (new NESet(unzipped._1), new NESet(unzipped._2))
  }

  /**
    * Converts this <code>NESet</code> of triples into three <code>NESet</code>s of the first, second, and and third element of each triple.
    *
    * @tparam L the type of the first member of the element triples
    * @tparam M the type of the second member of the element triples
    * @tparam R the type of the third member of the element triples
    * @param asTriple an implicit conversion that asserts that the element type of this <code>NESet</code> is a triple.
    * @return a triple of <code>NESet</code>s, containing the first, second, and third member, respectively, of each element triple of this <code>NESet</code>.
    */
  def unzip3[L, M, R](implicit asTriple: T => (L, M, R)): (NESet[L], NESet[M], NESet[R]) = {
    val unzipped = toSet.unzip3
    (new NESet(unzipped._1), new NESet(unzipped._2), new NESet(unzipped._3))
  }

  /**
    * Returns a <code>NESet</code> formed from this <code>NESet</code> and an iterable collection by combining corresponding
    * elements in pairs. If one of the two collections is shorter than the other, placeholder elements will be used to extend the
    * shorter collection to the length of the longer.
    *
    * @tparm O the type of the second half of the returned pairs
    * @tparm U the type of the first half of the returned pairs
    * @param other the <code>Iterable</code> providing the second half of each result pair
    * @param thisElem the element to be used to fill up the result if this <code>NESet</code> is shorter than <code>that</code> <code>Iterable</code>.
    * @param otherElem the element to be used to fill up the result if <code>that</code> <code>Iterable</code> is shorter than this <code>NESet</code>.
    * @return a new <code>NESet</code> containing pairs consisting of corresponding elements of this <code>NESet</code> and <code>that</code>. The
    *     length of the returned collection is the maximum of the lengths of this <code>NESet</code> and <code>that</code>. If this <code>NESet</code>
    *     is shorter than <code>that</code>, <code>thisElem</code> values are used to pad the result. If <code>that</code> is shorter than this
    *     <code>NESet</code>, <code>thatElem</code> values are used to pad the result.
    */
  def zipAll[O, U >: T](other: collection.Iterable[O], thisElem: U, otherElem: O): NESet[(U, O)] =
    new NESet(toSet.zipAll(other, thisElem, otherElem))

  /**
    * Zips this <code>NESet</code>  with its indices.
    *
    * @return A new <code>NESet</code> containing pairs consisting of all elements of this <code>NESet</code> paired with their index. Indices start at 0.
    */
  def zipWithIndex: NESet[(T, Int)] = new NESet(toSet.zipWithIndex)

  def widen[U >: T]: NESet[U] = new NESet(toSet.toSet[U])
}

/**
  * Companion object for class <code>NESet</code>.
  */
object NESet extends NESetInstances {

  /**
    * Constructs a new <code>NESet</code> given at least one element.
    *
    * @tparam T the type of the element contained in the new <code>NESet</code>
    * @param firstElement the first element (with index 0) contained in this <code>NESet</code>
    * @param otherElements a varargs of zero or more other elements (with index 1, 2, 3, ...) contained in this <code>NESet</code>
    */
  @inline def apply[T](firstElement: T, otherElements: T*): NESet[T] = new NESet(otherElements.toSet + firstElement)

  /**
    * Variable argument extractor for <code>NESet</code>s.
    *
    * @param NESet: the <code>NESet</code> containing the elements to extract
    * @return an <code>Seq</code> containing this <code>NESet</code>s elements, wrapped in a <code>Some</code>
    */
  @inline def unapplySeq[T](NESet: NESet[T]): Some[Seq[T]] = Some(NESet.toSeq)

  /**
    * Optionally construct a <code>NESet</code> containing the elements, if any, of a given <code>Set</code>.
    *
    * @param set the <code>Set</code> with which to construct a <code>NESet</code>
    * @return a <code>NESet</code> containing the elements of the given <code>GenSeq</code>, if non-empty, wrapped in
    *     a <code>Some</code>; else <code>None</code> if the <code>GenSeq</code> is empty
    */
  @inline def from[T](set: scala.collection.immutable.Set[T]): Option[NESet[T]] =
    set.headOption match {
      case None => None
      case Some(_) => Some(new NESet(set))
    }

  @inline def unsafeFrom[T](set: scala.collection.immutable.Set[T]): NESet[T] = {
    require(set.nonEmpty)
    new NESet(set)
  }

  implicit final class OptionOps[A](private val option: Option[NESet[A]]) extends AnyVal {
    @inline def fromNESet: Set[A] = if (option.isEmpty) Set.empty else option.get.toSet
  }

  @inline implicit def asIterable[A](ne: NESet[A]): IterableOnce[A] = ne.toIterable
}
