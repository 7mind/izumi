package izumi.fundamentals.collections.nonempty

// shameless copypaste from Scalactic

import scala.collection.compat.Factory
import scala.collection.mutable.{ArrayBuffer, Buffer}
import scala.collection.{Iterable, Seq}
import scala.language.implicitConversions
import scala.reflect.ClassTag
import scala.collection.compat.*

// Can't be a LinearSeq[T] because Builder would be able to create an empty one.
/**
  * A non-empty list: an ordered, immutable, non-empty collection of elements with <code>LinearSeq</code> performance characteristics.
  *
  * <p>
  * The purpose of <code>NEString</code> is to allow you to express in a type that a <code>String</code> is non-empty, thereby eliminating the
  * need for (and potential exception from) a run-time check for non-emptiness. For a non-empty sequence with <code>IndexedSeq</code>
  * performance, see <a href="Vector.html"><code>Vector</code></a>.
  * </p>
  *
  * <h2>Constructing <code>NEString</code>s</h2>
  *
  * <p>
  * You can construct a <code>NEString</code> by passing one or more elements to the <code>NEString.apply</code> factory method:
  * </p>
  *
  * <pre class="stHighlight">
  * scala&gt; NEString(1, 2, 3)
  * res0: org.scalactic.anyvals.NEString[Int] = NEString(1, 2, 3)
  * </pre>
  *
  * <p>
  * Alternatively you can <em>cons</em> elements onto the <code>End</code> singleton object, similar to making a <code>String</code> starting with <code>Nil</code>:
  * </p>
  *
  * <pre class="stHighlight">
  * scala&gt; 1 :: 2 :: 3 :: Nil
  * res0: String[Int] = String(1, 2, 3)
  *
  * scala&gt; 1 :: 2 :: 3 :: End
  * res1: org.scalactic.NEString[Int] = NEString(1, 2, 3)
  * </pre>
  *
  * <p>
  * Note that although <code>Nil</code> is a <code>String[Nothing]</code>, <code>End</code> is
  * not a <code>NEString[Nothing]</code>, because no empty <code>NEString</code> exists. (A non-empty list is a series
  * of connected links; if you have no links, you have no non-empty list.)
  * </p>
  *
  * <pre class="stHighlight">
  * scala&gt; val nil: String[Nothing] = Nil
  * nil: String[Nothing] = String()
  *
  * scala&gt; val nada: NEString[Nothing] = End
  * &lt;console&gt;:16: error: type mismatch;
  * found   : org.scalactic.anyvals.End.type
  * required: org.scalactic.anyvals.NEString[Nothing]
  *        val nada: NEString[Nothing] = End
  *                                          ^
  * </pre>
  *
  * <h2>Working with <code>NEString</code>s</h2>
  *
  * <p>
  * <code>NEString</code> does not extend Scala's <code>Seq</code> or <code>Traversable</code> traits because these require that
  * implementations may be empty. For example, if you invoke <code>tail</code> on a <code>Seq</code> that contains just one element,
  * you'll get an empty <code>Seq</code>:
  * </p>
  *
  * <pre class="stREPL">
  * scala&gt; String(1).tail
  * res6: String[Int] = String()
  * </pre>
  *
  * <p>
  * On the other hand, many useful methods exist on <code>Seq</code> that when invoked on a non-empty <code>Seq</code> are guaranteed
  * to not result in an empty <code>Seq</code>. For convenience, <code>NEString</code> defines a method corresponding to every such <code>Seq</code>
  * method. Here are some examples:
  * </p>
  *
  * <pre class="stHighlight">
  * NEString(1, 2, 3).map(_ + 1)                        // Result: NEString(2, 3, 4)
  * NEString(1).map(_ + 1)                              // Result: NEString(2)
  * NEString(1, 2, 3).containsSlice(NEString(2, 3)) // Result: true
  * NEString(1, 2, 3).containsSlice(NEString(3, 4)) // Result: false
  * NEString(-1, -2, 3, 4, 5).minBy(_.abs)              // Result: -1
  * </pre>
  *
  * <p>
  * <code>NEString</code> does <em>not</em> currently define any methods corresponding to <code>Seq</code> methods that could result in
  * an empty <code>Seq</code>. However, an implicit converison from <code>NEString</code> to <code>String</code>
  * is defined in the <code>NEString</code> companion object that will be applied if you attempt to call one of the missing methods. As a
  * result, you can invoke <code>filter</code> on an <code>NEString</code>, even though <code>filter</code> could result
  * in an empty sequence&mdash;but the result type will be <code>String</code> instead of <code>NEString</code>:
  * </p>
  *
  * <pre class="stHighlight">
  * NEString(1, 2, 3).filter(_ &lt; 10) // Result: String(1, 2, 3)
  * NEString(1, 2, 3).filter(_ &gt; 10) // Result: String()
  * </pre>
  *
  * <p>
  * You can use <code>NEString</code>s in <code>for</code> expressions. The result will be an <code>NEString</code> unless
  * you use a filter (an <code>if</code> clause). Because filters are desugared to invocations of <code>filter</code>, the
  * result type will switch to a <code>String</code> at that point. Here are some examples:
  * </p>
  *
  * <pre class="stREPL">
  * scala&gt; import org.scalactic.anyvals._
  * import org.scalactic.anyvals._
  *
  * scala&gt; for (i &lt;- NEString(1, 2, 3)) yield i + 1
  * res0: org.scalactic.anyvals.NEString[Int] = NEString(2, 3, 4)
  *
  * scala&gt; for (i &lt;- NEString(1, 2, 3) if i &lt; 10) yield i + 1
  * res1: String[Int] = String(2, 3, 4)
  *
  * scala&gt; for {
  *      |   i &lt;- NEString(1, 2, 3)
  *      |   j &lt;- NEString('a', 'b', 'c')
  *      | } yield (i, j)
  * res3: org.scalactic.anyvals.NEString[(Int, Char)] =
  *         NEString((1,a), (1,b), (1,c), (2,a), (2,b), (2,c), (3,a), (3,b), (3,c))
  *
  * scala&gt; for {
  *      |   i &lt;- NEString(1, 2, 3) if i &lt; 10
  *      |   j &lt;- NEString('a', 'b', 'c')
  *      | } yield (i, j)
  * res6: String[(Int, Char)] =
  *         String((1,a), (1,b), (1,c), (2,a), (2,b), (2,c), (3,a), (3,b), (3,c))
  * </pre>
  */
final class NEString private (val theString: String) extends AnyVal {

  /**
    * Returns a new <code>NEString</code> containing this <code>NEString</code> followed by the passed <code>NEString</code>.
    *
    * @param other the <code>NEString</code> to append
    * @return a new <code>NEString</code> that contains this <code>NEString</code> followed by <code>other</code>.
    */
  def ++(other: NEString): NEString = new NEString(theString ++ other.theString)

  /**
    * Returns a new <code>NEString</code> containing this <code>NEString</code> followed by the characters of the passed <code>Vector</code>.
    *
    * @param other the <code>Vector</code> of <code>Char</code> to append
    * @return a new <code>NEString</code> that contains this <code>NEString</code> followed by all characters of <code>other</code>.
    */
  def ++(other: Vector[Char]): NEString = new NEString(theString ++ other.mkString)

  // TODO: Have I added these extra ++, etc. methods to Vector that take a NEString?

  /**
    * Returns a new <code>NEString</code> containing this <code>NEString</code> followed by the characters of the passed <code>TraversableOnce</code>.
    *
    * @param other the <code>TraversableOnce</code> of <code>Char</code> to append
    * @return a new <code>NEString</code> that contains this <code>NEString</code> followed by all characters of <code>other</code>.
    */
  def ++(other: IterableOnce[Char]): NEString =
    if (other.iterator.isEmpty) this else new NEString(theString ++ other.iterator.mkString)

  /**
    * Returns a new <code>NEString</code> with the given character prepended.
    *
    * <p>
    * Note that :-ending operators are right associative. A mnemonic for <code>+:</code> <em>vs.</em> <code>:+</code> is: the COLon goes on the COLlection side.
    * </p>
    *
    * @param c the character to prepend to this <code>NEString</code>
    * @return a new <code>NEString</code> consisting of <code>c</code> followed by all characters of this <code>NEString</code>.
    */
  def +:(c: Char): NEString = new NEString(c +: theString)

  /**
    * Returns a new <code>NEString</code> with the given character appended.
    *
    * <p>
    * Note a mnemonic for <code>+:</code> <em>vs.</em> <code>:+</code> is: the COLon goes on the COLlection side.
    * </p>
    *
    * @param c the character to append to this <code>NEString</code>
    * @return a new <code>NEString</code> consisting of all characters of this <code>NEString</code> followed by the given <code>c</code>.
    */
  def :+(c: Char): NEString = new NEString(theString :+ c)

  /**
    * Appends all characters of this <code>NEString</code> to a string builder. The written text will consist of a concatenation of the result of invoking <code>toString</code>
    * on of every element of this <code>NEString</code>, without any separator string.
    *
    * @param sb the string builder to which characters will be appended
    * @return the string builder, <code>sb</code>, to which elements were appended.
    */
  def addString(sb: StringBuilder): StringBuilder = theString.addString(sb)

  /**
    * Appends all characters of this <code>NEString</code> to a string builder using a separator string. The written text will consist of a concatenation of the
    * result of invoking <code>toString</code>
    * on of every character of this <code>NEString</code>, separated by the string <code>sep</code>.
    *
    * @param sb the string builder to which characters will be appended
    * @param sep the separator string
    * @return the string builder, <code>sb</code>, to which characters were appended.
    */
  def addString(sb: StringBuilder, sep: String): StringBuilder = theString.addString(sb, sep)

  /**
    * Appends all characters of this <code>NEString</code> to a string builder using start, end, and separator strings. The written text will consist of a concatenation of
    * the string <code>start</code>; the result of invoking <code>toString</code> on all characters of this <code>NEString</code>,
    * separated by the string <code>sep</code>; and the string <code>end</code>
    *
    * @param sb the string builder to which characters will be appended
    * @param start the starting string
    * @param sep the separator string
    * @param end the ending string
    * @return the string builder, <code>sb</code>, to which characters were appended.
    */
  def addString(sb: StringBuilder, start: String, sep: String, end: String): StringBuilder = theString.addString(sb, start, sep, end)

  /**
    * Selects a character by its index in the <code>NEString</code>.
    *
    * @return the character of this <code>NEString</code> at index <code>idx</code>, where 0 indicates the first element.
    */
  def apply(idx: Int): Char = theString(idx)

  /**
    * Gets a character by its index in the <code>NEString</code>.
    *
    * @return the character of this <code>NEString</code> at index <code>idx</code>, where 0 indicates the first element.
    */
  def charAt(idx: Int): Char = theString.charAt(idx)

  /**
    * Finds the first character of this <code>NEString</code> for which the given partial function is defined, if any, and applies the partial function to it.
    *
    * @param pf the partial function
    * @return an <code>Option</code> containing <code>pf</code> applied to the first character for which it is defined, or <code>None</code> if
    *    the partial function was not defined for any character.
    */
  def collectFirst[U](pf: PartialFunction[Char, U]): Option[U] = theString.collectFirst(pf)

  /**
    * Indicates whether this <code>NEString</code> contains a given value as an character.
    *
    * @param c the element to look for
    * @return true if this <code>NEString</code> has an character that is equal (as determined by <code>==)</code> to <code>c</code>, false otherwise.
    */
  infix def contains(c: Char): Boolean = theString.contains(c)

  /**
    * Indicates whether this <code>NEString</code> contains a given <code>Seq</code> of characters as a slice.
    *
    * @param that the <code>Seq</code> character slice to look for
    * @return true if this <code>NEString</code> contains a slice with the same characters as <code>that</code>, otherwise <code>false</code>.
    */
  def containsSlice(that: Seq[Char]): Boolean = theString.containsSlice(that)

  /**
    * Indicates whether this <code>NEString</code> contains a given <code>Vector</code> of character as a slice.
    *
    * @param that the <code>Vector</code> character slice to look for
    * @return true if this <code>NEString</code> contains a character slice with the same characters as <code>that</code>, otherwise <code>false</code>.
    */
  def containsSlice(that: Vector[Char]): Boolean = theString.containsSlice(that)

  /**
    * Indicates whether this <code>NEString</code> contains a given <code>NEString</code> as a slice.
    *
    * @param that the <code>NEString</code> slice to look for
    * @return true if this <code>NEString</code> contains a slice with the same characters as <code>that</code>, otherwise <code>false</code>.
    */
  def containsSlice(that: NEString): Boolean = theString.containsSlice(that.theString)

  /**
    * Copies characters of this <code>NEString</code> to an array. Fills the given array <code>arr</code> with characters of this <code>NEString</code>. Copying
    * will stop once either the end of the current <code>NEString</code> is reached, or the end of the array is reached.
    *
    * @param arr the array to fill
    */
  def copyToArray(arr: Array[Char]): Unit = {
    theString.copyToArray(arr, 0)
    ()
  }

  /**
    * Copies characters of this <code>NEString</code> to an array. Fills the given array <code>arr</code> with characters of this <code>NEString</code>, beginning at
    * index <code>start</code>. Copying will stop once either the end of the current <code>NEString</code> is reached, or the end of the array is reached.
    *
    * @param arr the array to fill
    * @param start the starting index
    */
  def copyToArray(arr: Array[Char], start: Int): Unit = {
    theString.copyToArray(arr, start)
    ()
  }

  /**
    * Copies characters of this <code>NEString</code> to an array. Fills the given array <code>arr</code> with at most <code>len</code> characters of this <code>NEString</code>, beginning at
    * index <code>start</code>. Copying will stop once either the end of the current <code>NEString</code> is reached, the end of the array is reached, or
    * <code>len</code> elements have been copied.
    *
    * @param arr the array to fill
    * @param start the starting index
    * @param len the maximum number of elements to copy
    */
  def copyToArray(arr: Array[Char], start: Int, len: Int): Unit = {
    theString.copyToArray(arr, start, len)
    ()
  }

  /**
    * Copies all characters of this <code>NEString</code> to a buffer.
    *
    * @param buf the buffer to which characters are copied
    */
  def copyToBuffer(buf: Buffer[Char]): Unit = {
    buf ++= theString
    ()
  }

  /**
    * Indicates whether every character of this <code>NEString</code> relates to the corresponding element of a given <code>Seq</code> by satisfying a given predicate.
    *
    * @tparam B the type of the elements of <code>that</code>
    * @param that the <code>Seq</code> to compare for correspondence
    * @param p the predicate, which relates elements from this <code>NEString</code> and the passed <code>Seq</code>
    * @return true if this <code>NEString</code> and the passed <code>Seq</code> have the same length and <code>p(x, y)</code> is <code>true</code>
    *     for all corresponding elements <code>x</code> of this <code>NEString</code> and <code>y</code> of that, otherwise <code>false</code>.
    */
  def corresponds[B](that: Seq[B])(p: (Char, B) => Boolean): Boolean = theString.corresponds(that)(p)

  /**
    * Indicates whether every character of this <code>NEString</code> relates to the corresponding element of a given <code>Vector</code> by satisfying a given predicate.
    *
    * @tparam B the type of the elements of <code>that</code>
    * @param that the <code>Vector</code> to compare for correspondence
    * @param p the predicate, which relates elements from this <code>NEString</code> and the passed <code>Vector</code>
    * @return true if this <code>NEString</code> and the passed <code>Vector</code> have the same length and <code>p(x, y)</code> is <code>true</code>
    *     for all corresponding elements <code>x</code> of this <code>NEString</code> and <code>y</code> of that, otherwise <code>false</code>.
    */
  def corresponds[B](that: Vector[B])(p: (Char, B) => Boolean): Boolean = theString.corresponds(that)(p)

  /**
    * Indicates whether every character of this <code>NEString</code> relates to the corresponding character of a given <code>NEString</code> by satisfying a given predicate.
    *
    * @param that the <code>NEString</code> to compare for correspondence
    * @param p the predicate, which relates elements from this and the passed <code>NEString</code>
    * @return true if this and the passed <code>NEString</code> have the same length and <code>p(x, y)</code> is <code>true</code>
    *     for all corresponding characters <code>x</code> of this <code>NEString</code> and <code>y</code> of that, otherwise <code>false</code>.
    */
  def corresponds(that: NEString)(p: (Char, Char) => Boolean): Boolean = theString.corresponds(that.theString)(p)

  /**
    * Counts the number of characters in this <code>NEString</code> that satisfy a predicate.
    *
    * @param p the predicate used to test characters.
    * @return the number of characters satisfying the predicate <code>p</code>.
    */
  def count(p: Char => Boolean): Int = theString.count(p)

  /**
    * Builds a new <code>NEString</code> from this <code>NEString</code> without any duplicate characters.
    *
    * @return A new <code>NEString</code> that contains the first occurrence of every character of this <code>NEString</code>.
    */
  def distinct: NEString = new NEString(theString.iterator.toSeq.distinct.mkString)

  /**
    * Indicates whether this <code>NEString</code> ends with the given <code>Seq</code> of Char.
    *
    * @param that the sequence to test
    * @return <code>true</code> if this <code>NEString</code> has <code>that</code> as a suffix, <code>false</code> otherwise.
    */
  def endsWith[B](that: Seq[B]): Boolean = theString.toSeq.endsWith[Any](that)

  /**
    * Indicates whether this <code>NEString</code> ends with the given <code>Vector</code>.
    *
    * @param that the <code>Vector</code> to test
    * @return <code>true</code> if this <code>NEString</code> has <code>that</code> as a suffix, <code>false</code> otherwise.
    */
  def endsWith[B](that: Vector[B]): Boolean = theString.toSeq.endsWith[Any](that)

  // TODO: Search for that: Vector in here and add a that: NEString in Vector.
  /**
    * Indicates whether this <code>NEString</code> ends with the given <code>NEString</code>.
    *
    * @param that the <code>NEString</code> to test
    * @return <code>true</code> if this <code>NEString</code> has <code>that</code> as a suffix, <code>false</code> otherwise.
    */
  def endsWith(that: NEString): Boolean = theString.toSeq.endsWith[Any](that.theString)

  /*
    override def equals(o: Any): Boolean =
      o match {
        case NEString: NEString[?] => toString == NEString.toString
        case _ => false
      }
   */

  /**
    * Indicates whether a predicate holds for at least one of the characters of this <code>NEString</code>.
    *
    * @param p the predicate used to test characters.
    * @return <code>true</code> if the given predicate <code>p</code> holds for some of the elements of this <code>NEString</code>, otherwise <code>false</code>.
    */
  def exists(p: Char => Boolean): Boolean = theString.exists(p)

  /**
    * Finds the first character of this <code>NEString</code> that satisfies the given predicate, if any.
    *
    * @param p the predicate used to test characters
    * @return an <code>Some</code> containing the first character in this <code>NEString</code> that satisfies <code>p</code>, or <code>None</code> if none exists.
    */
  def find(p: Char => Boolean): Option[Char] = theString.find(p)

  /**
    * Builds a new <code>NEString</code> by applying a function to all characters of this <code>NEString</code> and using the characters of the resulting <code>NEString</code>s.
    *
    * @param f the function to apply to each character.
    * @return a new <code>NEString</code> containing characters obtained by applying the given function <code>f</code> to each character of this <code>NEString</code> and concatenating
    *    the characters of resulting <code>NEString</code>s.
    */
  def flatMap(f: Char => NEString): NEString = {
    val buf = new ArrayBuffer[Char]
    for (c <- theString)
      buf ++= f(c).theString
    new NEString(buf.mkString)
  }

  /**
    * Folds the characters of this <code>NEString</code> using the specified associative binary operator.
    *
    * <p>
    * The order in which operations are performed on characters is unspecified and may be nondeterministic.
    * </p>
    *
    * @param z a neutral character for the fold operation; may be added to the result an arbitrary number of
    *     times, and must not change the result (<em>e.g.</em>, <code>Nil</code> for list concatenation,
    *     0 for addition, or 1 for multiplication.)
    * @param op a binary operator that must be associative
    * @return the result of applying fold operator <code>op</code> between all the elements and <code>z</code>
    */
  def fold(z: Char)(op: (Char, Char) => Char): Char = theString.fold(z)(op)

  /**
    * Applies a binary operator to a start value and all characters of this <code>NEString</code>, going left to right.
    *
    * @tparam B the result type of the binary operator.
    * @param z the start value.
    * @param op the binary operator.
    * @return the result of inserting <code>op</code> between consecutive characters of this <code>NEString</code>, going left to right, with the start value,
    *     <code>z</code>, on the left:
    *
    * <pre>
    * op(...op(op(z, x_1), x_2), ..., x_n)
    * </pre>
    *
    * <p>
    * where x<sub>1</sub>, ..., x<sub>n</sub> are the elements of this <code>NEString</code>.
    * </p>
    */
  def foldLeft[B](z: B)(op: (B, Char) => B): B = theString.foldLeft(z)(op)

  /**
    * Applies a binary operator to all characters of this <code>NEString</code> and a start value, going right to left.
    *
    * @tparam B the result of the binary operator
    * @param z the start value
    * @param op the binary operator
    * @return the result of inserting <code>op</code> between consecutive characters of this <code>NEString</code>, going right to left, with the start value,
    *     <code>z</code>, on the right:
    *
    * <pre>
    * op(x_1, op(x_2, ... op(x_n, z)...))
    * </pre>
    *
    * <p>
    * where x<sub>1</sub>, ..., x<sub>n</sub> are the elements of this <code>NEString</code>.
    * </p>
    */
  def foldRight[B](z: B)(op: (Char, B) => B): B = theString.foldRight(z)(op)

  /**
    * Indicates whether a predicate holds for all characters of this <code>NEString</code>.
    *
    * @param p the predicate used to test characters.
    * @return <code>true</code> if the given predicate <code>p</code> holds for all characters of this <code>NEString</code>, otherwise <code>false</code>.
    */
  def forall(p: Char => Boolean): Boolean = theString.forall(p)

  /**
    * Applies a function <code>f</code> to all characters of this <code>NEString</code>.
    *
    * @param f the function that is applied for its side-effect to every character. The result of function <code>f</code> is discarded.
    */
  def foreach(f: Char => Unit): Unit = theString.foreach(f)

  /**
    * Partitions this <code>NEString</code> into a map of <code>NEString</code>s according to some discriminator function.
    *
    * @tparam K the type of keys returned by the discriminator function.
    * @param f the discriminator function.
    * @return A map from keys to <code>NEString</code>s such that the following invariant holds:
    *
    * <pre>
    * (NEString.toString partition f)(k) = xs filter (x =&gt; f(x) == k)
    * </pre>
    *
    * <p>
    * That is, every key <code>k</code> is bound to a <code>NEString</code> of those elements <code>x</code> for which <code>f(x)</code> equals <code>k</code>.
    * </p>
    */
  def groupBy[K](f: Char => K): Map[K, NEString] = {
    val mapKToString = theString.iterator.toSeq.groupBy(f)
    mapKToString.view.mapValues {
      list => new NEString(list.mkString)
    }.toMap
  }

  /**
    * Partitions characters into fixed size <code>NEString</code>s.
    *
    * @param size the number of characters per group
    * @return An iterator producing <code>NEString</code>s of size <code>size</code>, except the last will be truncated if the characters don't divide evenly.
    */
  def grouped(size: Int): Iterator[NEString] = {
    if (size > 0) {
      val itOfString = theString.grouped(size)
      itOfString.map {
        list => new NEString(list)
      }
    } else
      throw new IllegalArgumentException(s"Invalid size: $size")
  }

  /**
    * Returns <code>true</code> to indicate this <code>NEString</code> has a definite size, since all <code>NEString</code>s are strict collections.
    */
  def hasDefiniteSize: Boolean = true

  // override def hashCode: Int = toString.hashCode

  /**
    * Selects the first character of this <code>NEString</code>.
    *
    * @return the first character of this <code>NEString</code>.
    */
  def head: Char = theString.head

  def tail: String = theString.tail

  /**
    * Finds index of first occurrence of some value in this <code>NEString</code>.
    *
    * @param c the character value to search for.
    * @return the index of the first character of this <code>NEString</code> that is equal (as determined by <code>==</code>) to <code>c</code>,
    *     or <code>-1</code>, if none exists.
    */
  def indexOf(c: Char): Int = theString.indexOf(c.toInt, 0)

  /**
    * Finds index of first occurrence of some value in this <code>NEString</code> after or at some start index.
    *
    * @param c the character value to search for.
    * @param from the start index
    * @return the index <code>&gt;=</code> <code>from</code> of the first element of this <code>NEString</code> that is equal (as determined by <code>==</code>) to <code>elem</code>,
    *     or <code>-1</code>, if none exists.
    */
  def indexOf(c: Char, from: Int): Int = theString.indexOf(c.toInt, from)

  /**
    * Finds first index where this <code>NEString</code> contains a given <code>Seq[Char]</code> as a slice.
    *
    * @param that the <code>Seq[Char]</code> defining the slice to look for
    * @return the first index at which the elements of this <code>NEString</code> starting at that index match the characters of
    *     <code>Seq</code> <code>that</code>, or <code>-1</code> of no such subsequence exists.
    */
  def indexOfSlice(that: Seq[Char]): Int = theString.indexOfSlice(that)

  /**
    * Finds first index after or at a start index where this <code>NEString</code> contains a given <code>Seq[Char]</code> as a slice.
    *
    * @param that the <code>Seq[Char]</code> defining the slice to look for
    * @param from the start index
    * @return the first index <code>&gt;=</code> <code>from</code> at which the characters of this <code>NEString</code> starting at that index match the characters of
    *     <code>Seq[Char]</code> <code>that</code>, or <code>-1</code> of no such subsequence exists.
    */
  def indexOfSlice(that: Seq[Char], from: Int): Int = theString.indexOfSlice(that, from)

  /**
    * Finds first index where this <code>NEString</code> contains a given <code>Vector</code> as a slice.
    *
    * @param that the <code>Vector</code> defining the slice to look for
    * @return the first index such that the characters of this <code>NEString</code> starting at this index match the characters of
    *     <code>Vector</code> <code>that</code>, or <code>-1</code> of no such subsequence exists.
    */
  def indexOfSlice(that: Vector[Char]): Int = theString.indexOfSlice(that)

  /**
    * Finds first index where this <code>NEString</code> contains a given <code>NEString</code> as a slice.
    *
    * @param that the <code>NEString</code> defining the slice to look for
    * @return the first index such that the characters of this <code>NEString</code> starting at this index match the characters of
    *     <code>NEString</code> <code>that</code>, or <code>-1</code> of no such subsequence exists.
    */
  def indexOfSlice(that: NEString): Int = theString.indexOfSlice(that.theString)

  /**
    * Finds first index after or at a start index where this <code>NEString</code> contains a given <code>Vector</code> as a slice.
    *
    * @param that the <code>Vector</code> defining the slice to look for
    * @param from the start index
    * @return the first index <code>&gt;=</code> <code>from</code> such that the characters of this <code>NEString</code> starting at this index match the characters of
    *     <code>Vector</code> <code>that</code>, or <code>-1</code> of no such subsequence exists.
    */
  def indexOfSlice(that: Vector[Char], from: Int): Int = theString.indexOfSlice(that, from)

  /**
    * Finds first index after or at a start index where this <code>NEString</code> contains a given <code>NEString</code> as a slice.
    *
    * @param that the <code>NEString</code> defining the slice to look for
    * @param from the start index
    * @return the first index <code>&gt;=</code> <code>from</code> such that the characters of this <code>NEString</code> starting at this index match the characters of
    *     <code>NEString</code> <code>that</code>, or <code>-1</code> of no such subsequence exists.
    */
  def indexOfSlice(that: NEString, from: Int): Int = theString.indexOfSlice(that.theString, from)

  /**
    * Finds index of the first character satisfying some predicate.
    *
    * @param p the predicate used to test characters.
    * @return the index of the first character of this <code>NEString</code> that satisfies the predicate <code>p</code>,
    *     or <code>-1</code>, if none exists.
    */
  def indexWhere(p: Char => Boolean): Int = theString.indexWhere(p)

  /**
    * Finds index of the first character satisfying some predicate after or at some start index.
    *
    * @param p the predicate used to test characters.
    * @param from the start index
    * @return the index <code>&gt;=</code> <code>from</code> of the first character of this <code>NEString</code> that satisfies the predicate <code>p</code>,
    *     or <code>-1</code>, if none exists.
    */
  def indexWhere(p: Char => Boolean, from: Int): Int = theString.indexWhere(p, from)

  /**
    * Produces the range of all indices of this <code>NEString</code>.
    *
    * @return a <code>Range</code> value from <code>0</code> to one less than the length of this <code>NEString</code>.
    */
  def indices: Range = theString.indices

  /**
    * Tests whether this <code>NEString</code> contains given index.
    *
    * @param idx the index to test
    * @return true if this <code>NEString</code> contains an character at position <code>idx</code>, <code>false</code> otherwise.
    */
  def isDefinedAt(idx: Int): Boolean = theString.isDefinedAt(idx)

  /**
    * Returns <code>false</code> to indicate this <code>NEString</code>, like all <code>NEString</code>s, is non-empty.
    *
    * @return false
    */
  def isEmpty: Boolean = false

  /**
    * Returns <code>true</code> to indicate this <code>NEString</code>, like all <code>NEString</code>s, can be traversed repeatedly.
    *
    * @return true
    */
  def isTraversableAgain: Boolean = true

  /**
    * Creates and returns a new iterator over all characters contained in this <code>NEString</code>.
    *
    * @return the new iterator
    */
  def iterator: Iterator[Char] = theString.iterator

  /**
    * Selects the last character of this <code>NEString</code>.
    *
    * @return the last character of this <code>NEString</code>.
    */
  def last: Char = theString.last

  /**
    * Finds the index of the last occurrence of some value in this <code>NEString</code>.
    *
    * @param c the character value to search for.
    * @return the index of the last character of this <code>NEString</code> that is equal (as determined by <code>==</code>) to <code>c</code>,
    *     or <code>-1</code>, if none exists.
    */
  def lastIndexOf(c: Char): Int = theString.lastIndexOf(c.toInt)

  /**
    * Finds the index of the last occurrence of some value in this <code>NEString</code> before or at a given <code>end</code> index.
    *
    * @param c the character value to search for.
    * @param end the end index.
    * @return the index <code>&gt;=</code> <code>end</code> of the last character of this <code>NEString</code> that is equal (as determined by <code>==</code>)
    *     to <code>elem</code>, or <code>-1</code>, if none exists.
    */
  def lastIndexOf(c: Char, end: Int): Int = theString.lastIndexOf(c.toInt, end)

  /**
    * Finds the last index where this <code>NEString</code> contains a given <code>Seq</code> as a slice.
    *
    * @param that the <code>Seq</code> defining the slice to look for
    * @return the last index at which the elements of this <code>NEString</code> starting at that index match the characters of
    *    <code>Seq</code> <code>that</code>, or <code>-1</code> of no such subsequence exists.
    */
  def lastIndexOfSlice(that: Seq[Char]): Int = theString.lastIndexOfSlice(that)

  /**
    * Finds the last index before or at a given end index where this <code>NEString</code> contains a given <code>Seq</code> as a slice.
    *
    * @param that the <code>Seq</code> defining the slice to look for
    * @param end the end index
    * @return the last index <code>&gt;=</code> <code>end</code> at which the elements of this <code>NEString</code> starting at that index match the characters of
    *    <code>Seq</code> <code>that</code>, or <code>-1</code> of no such subsequence exists.
    */
  def lastIndexOfSlice(that: Seq[Char], end: Int): Int = theString.lastIndexOfSlice(that, end)

  /**
    * Finds the last index where this <code>NEString</code> contains a given <code>Vector</code> as a slice.
    *
    * @param that the <code>Vector</code> defining the slice to look for
    * @return the last index at which the elements of this <code>NEString</code> starting at that index match the characters of
    *    <code>Vector</code> <code>that</code>, or <code>-1</code> of no such subsequence exists.
    */
  def lastIndexOfSlice(that: Vector[Char]): Int = theString.lastIndexOfSlice(that)

  /**
    * Finds the last index where this <code>NEString</code> contains a given <code>NEString</code> as a slice.
    *
    * @param that the <code>NEString</code> defining the slice to look for
    * @return the last index at which the elements of this <code>NEString</code> starting at that index match the characters of
    *    <code>NEString</code> <code>that</code>, or <code>-1</code> of no such subsequence exists.
    */
  def lastIndexOfSlice(that: NEString): Int = theString.lastIndexOfSlice(that.theString)

  /**
    * Finds the last index before or at a given end index where this <code>NEString</code> contains a given <code>Vector</code> as a slice.
    *
    * @param that the <code>Vector</code> defining the slice to look for
    * @param end the end index
    * @return the last index <code>&gt;=</code> <code>end</code> at which the elements of this <code>NEString</code> starting at that index match the characters of
    *    <code>Vector</code> <code>that</code>, or <code>-1</code> of no such subsequence exists.
    */
  def lastIndexOfSlice(that: Vector[Char], end: Int): Int = theString.lastIndexOfSlice(that, end)

  /**
    * Finds the last index before or at a given end index where this <code>NEString</code> contains a given <code>NEString</code> as a slice.
    *
    * @param that the <code>NEString</code> defining the slice to look for
    * @param end the end index
    * @return the last index <code>&gt;=</code> <code>end</code> at which the characters of this <code>NEString</code> starting at that index match the characters of
    *    <code>NEString</code> <code>that</code>, or <code>-1</code> of no such subsequence exists.
    */
  def lastIndexOfSlice(that: NEString, end: Int): Int = theString.lastIndexOfSlice(that.theString, end)

  /**
    * Finds index of last character satisfying some predicate.
    *
    * @param p the predicate used to test characters.
    * @return the index of the last character of this <code>NEString</code> that satisfies the predicate <code>p</code>, or <code>-1</code>, if none exists.
    */
  def lastIndexWhere(p: Char => Boolean): Int = theString.lastIndexWhere(p)

  /**
    * Finds index of last character satisfying some predicate before or at given end index.
    *
    * @param p the predicate used to test characters.
    * @param end the end index
    * @return the index <code>&gt;=</code> <code>end</code> of the last character of this <code>NEString</code> that satisfies the predicate <code>p</code>,
    *     or <code>-1</code>, if none exists.
    */
  def lastIndexWhere(p: Char => Boolean, end: Int): Int = theString.lastIndexWhere(p, end)

  /**
    * The length of this <code>NEString</code>.
    *
    * <p>
    * @note <code>length</code> and <code>size</code> yield the same result, which will be <code>&gt;</code>= 1.
    * </p>
    *
    * @return the number of characters in this <code>NEString</code>.
    */
  def length: Int = theString.length

  /**
    * Compares the length of this <code>NEString</code> to a test value.
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
  def lengthCompare(len: Int): Int = theString.lengthCompare(len)

  /**
    * Builds a new <code>NEString</code> by applying a function to all characters of this <code>NEString</code>.
    *
    * @tparam U the character type of the returned <code>NEString</code>.
    * @param f the function to apply to each character.
    * @return a new <code>NEString</code> resulting from applying the given function <code>f</code> to each character of this <code>NEString</code> and collecting the results.
    */
  def map[U](f: Char => U): NEString = {
    // NEString("test")
    new NEString(
      theString.map {
        c =>
          f(c).toString
      }.mkString
    )
  }

  /**
    * Finds the largest character.
    *
    * @return the largest element of this <code>NEString</code>.
    */
  def max(implicit cmp: Ordering[Char]): Char = theString.max(cmp)

  /**
    * Finds the largest result after applying the given function to every character.
    *
    * @return the largest result of applying the given function to every character of this <code>NEString</code>.
    */
  def maxBy[U](f: Char => U)(implicit cmp: Ordering[U]): Char = theString.maxBy(f)(cmp)

  /**
    * Finds the smallest character.
    *
    * @return the smallest character of this <code>NEString</code>.
    */
  def min(implicit cmp: Ordering[Char]): Char = theString.min(cmp)

  /**
    * Finds the smallest result after applying the given function to every character.
    *
    * @return the smallest result of applying the given function to every character of this <code>NEString</code>.
    */
  def minBy[U](f: Char => U)(implicit cmp: Ordering[U]): Char = theString.minBy(f)(cmp)

  /**
    * Displays all characters of this <code>NEString</code> in a string.
    *
    * @return a string representation of this <code>NEString</code>. In the resulting string, the result of invoking <code>toString</code> on all characters of this
    *     <code>NEString</code> follow each other without any separator string.
    */
  def mkString: String = theString.mkString

  /**
    * Displays all elements of this <code>NEString</code> in a string using a separator string.
    *
    * @param sep the separator string
    * @return a string representation of this <code>NEString</code>. In the resulting string, the result of invoking <code>toString</code> on all elements of this
    *     <code>NEString</code> are separated by the string <code>sep</code>.
    */
  def mkString(sep: String): String = theString.mkString(sep)

  /**
    * Displays all characters of this <code>NEString</code> in a string using start, end, and separator strings.
    *
    * @param start the starting string.
    * @param sep the separator string.
    * @param end the ending string.
    * @return a string representation of this <code>NEString</code>. The resulting string begins with the string <code>start</code> and ends with the string
    *     <code>end</code>. Inside, In the resulting string, the result of invoking <code>toString</code> on all characters of this <code>NEString</code> are
    *     separated by the string <code>sep</code>.
    */
  def mkString(start: String, sep: String, end: String): String = theString.mkString(start, sep, end)

  /**
    * Returns <code>true</code> to indicate this <code>NEString</code>, like all <code>NEString</code>s, is non-empty.
    *
    * @return true
    */
  def nonEmpty: Boolean = true

  /**
    * A copy of this <code>NEString</code> with an element value appended until a given target length is reached.
    *
    * @param len the target length
    * @param c the padding character
    * @return a new <code>NEString</code> consisting of all characters of this <code>NEString</code> followed by the minimal number of occurrences
    *     of <code>elem</code> so that the resulting <code>NEString</code> has a length of at least <code>len</code>.
    */
  def padTo(len: Int, c: Char): NEString =
    new NEString(theString.padTo(len, c))

  /**
    * Produces a new <code>NEString</code> where a slice of characters in this <code>NEString</code> is replaced by another <code>NEString</code>
    *
    * @param from the index of the first replaced character
    * @param that the <code>NEString</code> whose characters should replace a slice in this <code>NEString</code>
    * @param replaced the number of characters to drop in the original <code>NEString</code>
    */
  def patch(from: Int, that: NEString, replaced: Int): NEString =
    new NEString(theString.patch(from, that.theString, replaced))

  /**
    * Iterates over distinct permutations.
    *
    * <p>
    * Here's an example:
    * </p>
    *
    * <pre class="stHighlight">
    * NEString("abb").permutations.toList == list(NEString("abb"), NEString("bab"), NEString("bba"))
    * </pre>
    *
    * @return an iterator that traverses the distinct permutations of this <code>NEString</code>.
    */
  def permutations: Iterator[NEString] = {
    val it = theString.iterator.toSeq.permutations
    it map {
      list => new NEString(list.mkString)
    }
  }

  /**
    * Returns the length of the longest prefix whose characters all satisfy some predicate.
    *
    * @param p the predicate used to test characters.
    * @return the length of the longest prefix of this <code>NEString</code> such that every characters
    *     of the segment satisfies the predicate <code>p</code>.
    */
  def prefixLength(p: Char => Boolean): Int = theString.segmentLength(p, 0)

  /**
    * The result of multiplying all the characters of this <code>NEString</code>.
    *
    * <p>
    * This method can be invoked for any <code>NEString</code> for which an implicit <code>Numeric[T]</code> exists.
    * </p>
    *
    * @return the product of all elements
    */
  def product(implicit num: Numeric[Char]): Char = theString.product(num)

  /**
    * Reduces the elements of this <code>NEString</code> using the specified associative binary operator.
    *
    * <p>
    * The order in which operations are performed on characters is unspecified and may be nondeterministic.
    * </p>
    *
    * @param op a binary operator that must be associative.
    * @return the result of applying reduce operator <code>op</code> between all the characters of this <code>NEString</code>.
    */
  def reduce(op: (Char, Char) => Char): Char = theString.reduce(op)

  /**
    * Applies a binary operator to all characters of this <code>NEString</code>, going left to right.
    *
    * @param op the binary operator.
    * @return the result of inserting <code>op</code> between consecutive characters of this <code>NEString</code>, going left to right:
    *
    * <pre>
    * op(...op(op(x_1, x_2), x_3), ..., x_n)
    * </pre>
    *
    * <p>
    * where x<sub>1</sub>, ..., x<sub>n</sub> are the characters of this <code>NEString</code>.
    * </p>
    */
  def reduceLeft(op: (Char, Char) => Char): Char = theString.reduceLeft(op)

  /**
    * Applies a binary operator to all characters of this <code>NEString</code>, going left to right, returning the result in a <code>Some</code>.
    *
    * @param op the binary operator.
    * @return a <code>Some</code> containing the result of <code>reduceLeft(op)</code>
    * </p>
    */
  def reduceLeftOption(op: (Char, Char) => Char): Option[Char] = theString.reduceLeftOption(op)

  def reduceOption(op: (Char, Char) => Char): Option[Char] = theString.reduceOption(op)

  /**
    * Applies a binary operator to all characters of this <code>NEString</code>, going right to left.
    *
    * @param op the binary operator
    * @return the result of inserting <code>op</code> between consecutive characters of this <code>NEString</code>, going right to left:
    *
    * <pre>
    * op(x_1, op(x_2, ... op(x_{n-1}, x_n)...))
    * </pre>
    *
    * <p>
    * where x<sub>1</sub>, ..., x<sub>n</sub> are the elements of this <code>NEString</code>.
    * </p>
    */
  def reduceRight(op: (Char, Char) => Char): Char = theString.reduceRight(op)

  /**
    * Applies a binary operator to all elements of this <code>NEString</code>, going right to left, returning the result in a <code>Some</code>.
    *
    * @param op the binary operator
    * @return a <code>Some</code> containing the result of <code>reduceRight(op)</code>
    */
  def reduceRightOption(op: (Char, Char) => Char): Option[Char] = theString.reduceRightOption(op)

  /**
    * Returns new <code>NEString</code> with characters in reverse order.
    *
    * @return a new <code>NEString</code> with all characters of this <code>NEString</code> in reversed order.
    */
  def reverse: NEString =
    new NEString(theString.reverse)

  /**
    * An iterator yielding characters in reverse order.
    *
    * <p>
    * @note <code>NEString.reverseIterator</code> is the same as <code>NEString.reverse.iterator</code>, but might be more efficient.
    * </p>
    *
    * @return an iterator yielding the characters of this <code>NEString</code> in reversed order
    */
  def reverseIterator: Iterator[Char] = theString.reverseIterator

  /**
    * Builds a new <code>Iterable</code> by applying a function to all characters of this <code>NEString</code> and collecting the results in reverse order.
    *
    * <p>
    * @note <code>NEString.reverseMap(f)</code> is the same as <code>NEString.reverse.map(f)</code>, but might be more efficient.
    * </p>
    *
    * @tparam U the element type of the returned <code>Iterable</code>.
    * @param f the function to apply to each character.
    * @return a new <code>Iterable</code> resulting from applying the given function <code>f</code> to each character of this <code>NEString</code>
    *     and collecting the results in reverse order.
    */
  def reverseMap[U](f: Char => U): Iterable[U] = theString.reverseIterator.map(f).toVector

  /**
    * Checks if the given <code>Iterable</code> contains the same characters in the same order as this <code>NEString</code>.
    *
    * @param that the <code>Iterable</code> with which to compare
    * @return <code>true</code>, if both this <code>NEString</code> and the given <code>Iterable</code> contain the same characters
    *     in the same order, <code>false</code> otherwise.
    */
  def sameElements(that: Iterable[Char]): Boolean = theString.sameElements(that)

  /**
    * Checks if the given <code>Vector</code> contains the same characters in the same order as this <code>NEString</code>.
    *
    * @param that the <code>Vector</code> with which to compare
    * @return <code>true</code>, if both this and the given <code>Vector</code> contain the same characters
    *     in the same order, <code>false</code> otherwise.
    */
  def sameElements(that: Vector[Char]): Boolean = theString.sameElements(that)

  /**
    * Checks if the given <code>NEString</code> contains the same characters in the same order as this <code>NEString</code>.
    *
    * @param that the <code>NEString</code> with which to compare
    * @return <code>true</code>, if both this and the given <code>NEString</code> contain the same characters
    *     in the same order, <code>false</code> otherwise.
    */
  def sameElements(that: NEString): Boolean = theString.sameElements(that.theString)

  /**
    * Computes a prefix scan of the characters of this <code>NEString</code>.
    *
    * <p>
    * @note The neutral character z may be applied more than once.
    * </p>
    *
    * <p>
    * Here are some examples:
    * </p>
    *
    * <pre class="stHighlight">
    * NEString("123").scan(0)(_ + _) == NEString(0, 1, 3, 6)
    * NEString("123").scan("z")(_ + _.toString) == NEString("z", "z1", "z12", "z123")
    * </pre>
    *
    * @param z a neutral element for the scan operation; may be added to the result an arbitrary number of
    *     times, and must not change the result (<em>e.g.</em>, <code>Nil</code> for list concatenation,
    *     0 for addition, or 1 for multiplication.)
    * @param op a binary operator that must be associative
    * @return a new <code>NEString</code> containing the prefix scan of the elements in this <code>NEString</code>
    */
  def scan(z: Char)(op: (Char, Char) => Char): NEString = new NEString(theString.scan(z)(op).mkString)

  /**
    * Produces a <code>NEString</code> containing cumulative results of applying the operator going left to right.
    *
    * <p>
    * Here are some examples:
    * </p>
    *
    * <pre class="stHighlight">
    * NEString("123").scanLeft(0)(_ + _.toString.toInt) == Vector(0, 1, 3, 6)
    * NEString("123").scanLeft("z")(_ + _) == Vector("z", "z1", "z12", "z123")
    * </pre>
    *
    * @tparam B the result type of the binary operator and type of the resulting <code>NEString</code>
    * @param z the start value.
    * @param op the binary operator.
    * @return a new <code>NEString</code> containing the intermediate results of inserting <code>op</code> between consecutive characters of this <code>NEString</code>,
    *     going left to right, with the start value, <code>z</code>, on the left.
    */
  def scanLeft[B](z: B)(op: (B, Char) => B): Iterable[B] = theString.scanLeft(z)(op)

  /**
    * Produces a <code>NEString</code> containing cumulative results of applying the operator going right to left.
    *
    * <p>
    * Here are some examples:
    * </p>
    *
    * <pre class="stHighlight">
    * NEString("123").scanRight(0)(_.toString.toInt + _) == NEString(6, 5, 3, 0)
    * NEString("123").scanRight("z")(_ + _) == NEString("123z", "23z", "3z", "z")
    * </pre>
    *
    * @tparam B the result of the binary operator and type of the resulting <code>NEString</code>
    * @param z the start value
    * @param op the binary operator
    * @return a new <code>NEString</code> containing the intermediate results of inserting <code>op</code> between consecutive characters of this <code>NEString</code>,
    *     going right to left, with the start value, <code>z</code>, on the right.
    */
  def scanRight[B](z: B)(op: (Char, B) => B): Iterable[B] = theString.scanRight(z)(op)

  /**
    * Computes length of longest segment whose characters all satisfy some predicate.
    *
    * @param p the predicate used to test elements.
    * @param from the index where the search starts.
    * @return the length of the longest segment of this <code>NEString</code> starting from index <code>from</code> such that every character of the
    *     segment satisfies the predicate <code>p</code>.
    */
  def segmentLength(p: Char => Boolean, from: Int): Int = theString.segmentLength(p, from)

  /**
    * Groups characters in fixed size blocks by passing a &ldquo;sliding window&rdquo; over them (as opposed to partitioning them, as is done in grouped.)
    *
    * @param size the number of characters per group
    * @return an iterator producing <code>NEString</code>s of size <code>size</code>, except the last and the only element will be truncated
    *     if there are fewer characters than <code>size</code>.
    */
  def sliding(size: Int): Iterator[NEString] = theString.iterator.sliding(size).map(s => new NEString(s.mkString))

  /**
    * Groups characters in fixed size blocks by passing a &ldquo;sliding window&rdquo; over them (as opposed to partitioning them, as is done in grouped.),
    * moving the sliding window by a given <code>step</code> each time.
    *
    * @param size the number of characters per group
    * @param step the distance between the first characters of successive groups
    * @return an iterator producing <code>NEString</code>s of size <code>size</code>, except the last and the only character will be truncated
    *     if there are fewer characters than <code>size</code>.
    */
  def sliding(size: Int, step: Int): Iterator[NEString] = theString.iterator.sliding(size, step).map(s => new NEString(s.mkString))

  /**
    * The size of this <code>NEString</code>.
    *
    * <p>
    * @note <code>length</code> and <code>size</code> yield the same result, which will be <code>&gt;</code>= 1.
    * </p>
    *
    * @return the number of characters in this <code>NEString</code>.
    */
  def size: Int = theString.size

  /**
    * Sorts this <code>NEString</code> according to the <code>Ordering</code> of the result of applying the given function to every character.
    *
    * @tparam U the target type of the transformation <code>f</code>, and the type where the <code>Ordering</code> <code>ord</code> is defined.
    * @param f the transformation function mapping elements to some other domain <code>U</code>.
    * @param ord the ordering assumed on domain <code>U</code>.
    * @return a <code>NEString</code> consisting of the elements of this <code>NEString</code> sorted according to the <code>Ordering</code> where
    *    <code>x &lt; y if ord.lt(f(x), f(y))</code>.
    */
  def sortBy[U](f: Char => U)(implicit ord: Ordering[U]): NEString = new NEString(theString.iterator.toSeq.sortBy(f).mkString)

  /**
    * Sorts this <code>NEString</code> according to a comparison function.
    *
    * <p>
    * The sort is stable. That is, characters that are equal (as determined by <code>lt</code>) appear in the same order in the
    * sorted <code>NEString</code> as in the original.
    * </p>
    *
    * @param lt the comparison function that tests whether its first argument precedes its second argument in the desired ordering.
    * @return a <code>NEString</code> consisting of the elements of this <code>NEString</code> sorted according to the comparison function <code>lt</code>.
    */
  def sortWith(lt: (Char, Char) => Boolean): NEString = new NEString(theString.iterator.toSeq.sortWith(lt).mkString)

  /**
    * Sorts this <code>NEString</code> according to an <code>Ordering</code>.
    *
    * <p>
    * The sort is stable. That is, elements that are equal (as determined by <code>lt</code>) appear in the same order in the
    * sorted <code>NEString</code> as in the original.
    * </p>
    *
    * @param ord the <code>Ordering</code> to be used to compare elements.
    * @return a <code>NEString</code> consisting of the characters of this <code>NEString</code> sorted according to the ordering defined by <code>ord</code>.
    */
  def sorted(implicit ord: Ordering[Char]): NEString = new NEString(theString.iterator.toSeq.sorted(ord).mkString)

  /**
    * Indicates whether this <code>NEString</code> starts with the given <code>Seq</code>.
    *
    * @param that the <code>Seq</code> slice to look for in this <code>NEString</code>
    * @return <code>true</code> if this <code>NEString</code> has <code>that</code> as a prefix, <code>false</code> otherwise.
    */
  def startsWith(that: Seq[Char]): Boolean = wrapString(theString).startsWith(that)

  /**
    * Indicates whether this <code>NEString</code> starts with the given <code>Seq</code> at the given index.
    *
    * @param that the <code>Seq</code> slice to look for in this <code>NEString</code>
    * @param offset the index at which this <code>NEString</code> is searched.
    * @return <code>true</code> if this <code>NEString</code> has <code>that</code> as a slice at the index <code>offset</code>, <code>false</code> otherwise.
    */
  def startsWith(that: Seq[Char], offset: Int): Boolean = wrapString(theString).startsWith(that, offset)

  /**
    * Indicates whether this <code>NEString</code> starts with the given <code>Vector</code>.
    *
    * @param that the <code>Vector</code> to test
    * @return <code>true</code> if this collection has <code>that</code> as a prefix, <code>false</code> otherwise.
    */
  def startsWith(that: Vector[Char]): Boolean = wrapString(theString).startsWith(that)

  /**
    * Indicates whether this <code>NEString</code> starts with the given <code>NEString</code>.
    *
    * @param that the <code>NEString</code> to test
    * @return <code>true</code> if this collection has <code>that</code> as a prefix, <code>false</code> otherwise.
    */
  def startsWith(that: NEString): Boolean = theString.startsWith(that.theString)

  /**
    * Indicates whether this <code>NEString</code> starts with the given <code>Vector</code> at the given index.
    *
    * @param that the <code>Vector</code> slice to look for in this <code>NEString</code>
    * @param offset the index at which this <code>NEString</code> is searched.
    * @return <code>true</code> if this <code>NEString</code> has <code>that</code> as a slice at the index <code>offset</code>, <code>false</code> otherwise.
    */
  def startsWith(that: Vector[Char], offset: Int): Boolean = wrapString(theString).startsWith(that, offset)

  /**
    * Indicates whether this <code>NEString</code> starts with the given <code>NEString</code> at the given index.
    *
    * @param that the <code>NEString</code> slice to look for in this <code>NEString</code>
    * @param offset the index at which this <code>NEString</code> is searched.
    * @return <code>true</code> if this <code>NEString</code> has <code>that</code> as a slice at the index <code>offset</code>, <code>false</code> otherwise.
    */
  def startsWith(that: NEString, offset: Int): Boolean = theString.startsWith(that.theString, offset)

  /**
    * Returns <code>"NEString"</code>, the prefix of this object's <code>toString</code> representation.
    *
    * @return the string <code>"NEString"</code>
    */
  def stringPrefix: String = "NEString"

  /**
    * The result of summing all the characters of this <code>NEString</code>.
    *
    * <p>
    * This method can be invoked for any <code>NEString</code> for which an implicit <code>Numeric[Char]</code> exists.
    * </p>
    *
    * @return the sum of all elements
    */
  def sum(implicit num: Numeric[Long]): Long = theString.iterator.map(_.toLong).sum(num)

  /**
    * Converts this <code>NEString</code> into a collection of type <code>Col</code> by copying all elements.
    *
    * @tparam C1 the collection type to build.
    * @return a new collection containing all elements of this <code>NEString</code>.
    */
  def to[C1](factory: Factory[Char, C1]): C1 = factory.fromSpecific(iterator)

  /**
    * Converts this <code>NEString</code> to an array.
    *
    * @return an array containing all characters of this <code>NEString</code>. A <code>ClassTag</code> must be available for the element type of this <code>NEString</code>.
    */
  def toArray(implicit classTag: ClassTag[Char]): Array[Char] = theString.toArray

  /**
    * Converts this <code>NEString</code> to a <code>Vector</code>.
    *
    * @return a <code>Vector</code> containing all characters of this <code>NEString</code>.
    */
  def toVector: Vector[Char] = theString.toVector

  /**
    * Converts this <code>NEString</code> to a mutable buffer.
    *
    * @return a buffer containing all characters of this <code>NEString</code>.
    */
  def toBuffer: Buffer[Char] = theString.toBuffer

  /**
    * Converts this <code>NEString</code> to an immutable <code>IndexedSeq</code>.
    *
    * @return an immutable <code>IndexedSeq</code> containing all characters of this <code>NEString</code>.
    */
  def toIndexedSeq: collection.immutable.IndexedSeq[Char] = theString.toVector

  /**
    * Converts this <code>NEString</code> to an iterable collection.
    *
    * @return an <code>Iterable</code> containing all characters of this <code>NEString</code>.
    */
  def toIterable: Iterable[Char] = theString

  /**
    * Returns an <code>Iterator</code> over the elements in this <code>NEString</code>.
    *
    * @return an <code>Iterator</code> containing all characters of this <code>NEString</code>.
    */
  def toIterator: Iterator[Char] = theString.iterator

  /**
    * Converts this <code>NEString</code> to a map.
    *
    * <p>
    * This method is unavailable unless the elements are members of <code>Tuple2</code>, each <code>((K, V))</code> becoming a key-value pair
    * in the map. Duplicate keys will be overwritten by later keys.
    * </p>
    *
    * @return a map of type <code>immutable.Map[Int, Char]</code> containing all index/character pairs of type <code>(Int, Char)</code> of this <code>NEString</code>.
    */
  def toMap: Map[Int, Char] = Map.empty[Int, Char] ++ theString.zipWithIndex.map(e => e._2 -> e._1)

  /**
    * Converts this <code>NEString</code> to an immutable <code>IndexedSeq</code>.
    *
    * @return an immutable <code>IndexedSeq</code> containing all characters of this <code>NEString</code>.
    */
  def toSeq: collection.immutable.Seq[Char] = theString

  /**
    * Converts this <code>NEString</code> to a set.
    *
    * @return a set containing all characters of this <code>NEString</code>.
    */
  def toSet: Set[Char] = theString.toSet

  /**
    * Returns a string representation of this <code>NEString</code>.
    *
    * @return the string <code>"NEString"</code> followed by the result of invoking <code>toString</code> on
    *   this <code>NEString</code>'s elements, surrounded by parentheses.
    */
  override def toString: String = stringPrefix + "(" + theString + ")"

  /**
    * Produces a new <code>NEString</code> that contains all characters of this <code>NEString</code> and also all characters of a given <code>Vector</code>.
    *
    * <p>
    * <code>NEStringX</code> <code>union</code> <code>everyY</code> is equivalent to <code>NEStringX</code> <code>++</code> <code>everyY</code>.
    * </p>
    *
    * <p>
    * Another way to express this is that <code>NEStringX</code> <code>union</code> <code>everyY</code> computes the order-presevring multi-set union
    * of <code>NEStringX</code> and <code>everyY</code>. This <code>union</code> method is hence a counter-part of <code>diff</code> and <code>intersect</code> that
    * also work on multi-sets.
    * </p>
    *
    * @param that the <code>Vector</code> to add.
    * @return a new <code>NEString</code> that contains all characters of this <code>NEString</code> followed by all characters of <code>that</code> <code>Vector</code>.
    */
  def union(that: Vector[Char]): NEString = new NEString(theString + that.mkString)

  /**
    * Produces a new <code>NEString</code> that contains all characters of this <code>NEString</code> and also all characters of a given <code>NEString</code>.
    *
    * <p>
    * <code>NEStringX</code> <code>union</code> <code>NEStringY</code> is equivalent to <code>NEStringX</code> <code>++</code> <code>NEStringY</code>.
    * </p>
    *
    * <p>
    * Another way to express this is that <code>NEStringX</code> <code>union</code> <code>NEStringY</code> computes the order-presevring multi-set union
    * of <code>NEStringX</code> and <code>NEStringY</code>. This <code>union</code> method is hence a counter-part of <code>diff</code> and <code>intersect</code> that
    * also work on multi-sets.
    * </p>
    *
    * @param that the <code>NEString</code> to add.
    * @return a new <code>NEString</code> that contains all elements of this <code>NEString</code> followed by all characters of <code>that</code>.
    */
  def union(that: NEString): NEString = new NEString(theString + that.theString)

  /**
    * Produces a new <code>NEString</code> that contains all characters of this <code>NEString</code> and also all characters of a given <code>Seq</code>.
    *
    * <p>
    * <code>NEStringX</code> <code>union</code> <code>ys</code> is equivalent to <code>NEStringX</code> <code>++</code> <code>ys</code>.
    * </p>
    *
    * <p>
    * Another way to express this is that <code>NEStringX</code> <code>union</code> <code>ys</code> computes the order-presevring multi-set union
    * of <code>NEStringX</code> and <code>ys</code>. This <code>union</code> method is hence a counter-part of <code>diff</code> and <code>intersect</code> that
    * also work on multi-sets.
    * </p>
    *
    * @param that the <code>Seq</code> to add.
    * @return a new <code>NEString</code> that contains all elements of this <code>NEString</code> followed by all elements of <code>that</code> <code>Seq</code>.
    */
  def union(that: Seq[Char]): NEString = new NEString(theString + that.mkString)

  /**
    * Converts this <code>NEString</code> of pairs into two <code>NEString</code>s of the first and second half of each pair.
    *
    * @tparam L the type of the first half of the character pairs
    * @tparam R the type of the second half of the character pairs
    * @param asPair an implicit conversion that asserts that the character type of this <code>NEString</code> is a pair.
    * @return a pair of <code>NEString</code>s, containing the first and second half, respectively, of each character pair of this <code>NEString</code>.
    */
  def unzip[L, R](implicit asPair: Char => (L, R)): (Iterable[L], Iterable[R]) = {
    val unzipped = theString.unzip
    (unzipped._1, unzipped._2)
  }

  /**
    * Converts this <code>NEString</code> of triples into three <code>NEString</code>s of the first, second, and and third character of each triple.
    *
    * @tparam L the type of the first member of the character triples
    * @tparam R the type of the second member of the character triples
    * @tparam R the type of the third member of the character triples
    * @param asTriple an implicit conversion that character that the character type of this <code>NEString</code> is a triple.
    * @return a triple of <code>NEString</code>s, containing the first, second, and third member, respectively, of each character triple of this <code>NEString</code>.
    */
  def unzip3[L, M, R](implicit asTriple: Char => (L, M, R)): (Iterable[L], Iterable[M], Iterable[R]) = {
    val unzipped = theString.unzip3
    (unzipped._1, unzipped._2, unzipped._3)
  }

  /**
    * A copy of this <code>NEString</code> with one single replaced character.
    *
    * @param idx the position of the replacement
    * @param c the replacing character
    * @throws IndexOutOfBoundsException if the passed index is greater than or equal to the length of this <code>NEString</code>
    * @return a copy of this <code>NEString</code> with the character at position <code>idx</code> replaced by <code>c</code>.
    */
  def updated(idx: Int, c: Char): NEString =
    try new NEString(theString.updated(idx, c))
    catch { case _: UnsupportedOperationException => throw new IndexOutOfBoundsException(idx.toString) } // This is needed for 2.10 support. Can drop after.
  // Because 2.11 throws IndexOutOfBoundsException.

  /**
    * Returns a <code>NEString</code> formed from this <code>NEString</code> and an iterable collection by combining corresponding
    * elements in pairs. If one of the two collections is shorter than the other, placeholder elements will be used to extend the
    * shorter collection to the length of the longer.
    *
    * @tparam O the element type of the <code>other</code>
    * @param other the <code>Iterable</code> providing the second half of each result pair
    * @param thisElem the element to be used to fill up the result if this <code>NEString</code> is shorter than <code>that</code> <code>Iterable</code>.
    * @param otherElem the element to be used to fill up the result if <code>that</code> <code>Iterable</code> is shorter than this <code>NEString</code>.
    * @return a new <code>NEString</code> containing pairs consisting of corresponding characters of this <code>NEString</code> and <code>that</code>. The
    *     length of the returned collection is the maximum of the lengths of this <code>NEString</code> and <code>that</code>. If this <code>NEString</code>
    *     is shorter than <code>that</code>, <code>thisElem</code> values are used to pad the result. If <code>that</code> is shorter than this
    *     <code>NEString</code>, <code>thatElem</code> values are used to pad the result.
    */
  def zipAll[O](other: collection.Iterable[O], thisElem: Char, otherElem: O): Iterable[(Char, O)] =
    theString.zipAll(other, thisElem, otherElem)

  /**
    * Returns a <code>NEString</code> formed from this <code>NEString</code> and another <code>NEString</code> combining corresponding
    * elements in pairs. If one of the two <code></code> is shorter than the other, placeholder elements will be used to extend the
    * shorter collection to the length of the longer.
    *
    * @param other the <code>NEString</code> providing the second half of each result pair
    * @param thisElem the character to be used to fill up the result if this <code>NEString</code> is shorter than <code>that</code> <code>NEString</code>.
    * @param otherElem the character to be used to fill up the result if <code>that</code> <code>Iterable</code> is shorter than this <code>NEString</code>.
    * @return a new <code>NEString</code> containing pairs consisting of corresponding characters of this <code>NEString</code> and <code>that</code>. The
    *     length of the returned collection is the maximum of the lengths of this <code>NEString</code> and <code>that</code>. If this <code>NEString</code>
    *     is shorter than <code>that</code>, <code>thisElem</code> values are used to pad the result. If <code>that</code> is shorter than this
    *     <code>NEString</code>, <code>thatElem</code> values are used to pad the result.
    */
  def zipAll(other: NEString, thisElem: Char, otherElem: Char): Iterable[(Char, Char)] =
    theString.zipAll(other.theString, thisElem, otherElem)

  /**
    * Zips this <code>NEString</code>  with its indices.
    *
    * @return A new <code>NEString</code> containing pairs consisting of all elements of this <code>NEString</code> paired with their index. Indices start at 0.
    */
  def zipWithIndex: Iterable[(Char, Int)] = theString.zipWithIndex
}

/**
  * Companion object for class <code>NEString</code>.
  */
object NEString {
  /**
    * Constructs a new <code>NEString</code> given at least one character.
    *
    * @param firstChar the first character (with index 0) contained in this <code>NEString</code>
    * @param otherChars a varargs of zero or more other characters (with index 1, 2, 3, ...) contained in this <code>NEString</code>
    */
  @inline def apply(firstChar: Char, otherChars: Char*): NEString = new NEString(firstChar.toString + otherChars.mkString)

  /**
    * Variable argument extractor for <code>NEString</code>s.
    *
    * @param NEString: the <code>NEString</code> containing the elements to extract
    * @return an <code>Seq</code> containing this <code>NEString</code>s elements, wrapped in a <code>Some</code>
    */
  @inline def unapplySeq(NEString: NEString): Some[Seq[String]] = Some(Seq(NEString.theString))

  /**
    * Optionally construct a <code>NEString</code> containing the characters, if any, of a given <code>Seq</code>.
    *
    * @param seq the <code>Seq</code> of <code>Char</code> with which to construct a <code>NEString</code>
    * @return a <code>NEString</code> containing the elements of the given <code>Seq</code>, if non-empty, wrapped in
    *     a <code>Some</code>; else <code>None</code> if the <code>Seq</code> is empty
    */
  @inline def from[T](seq: Seq[Char]): Option[NEString] =
    seq.headOption match {
      case None => None
      case Some(_) => Some(new NEString(seq.mkString))
    }

  @inline def from[T](str: String): Option[NEString] =
    if (str.isEmpty) None else Some(new NEString(str))

  @inline def unsafeFrom(str: String): NEString = {
    require(str.nonEmpty)
    new NEString(str)
  }

  @inline implicit def asIterable(ne: NEString): IterableOnce[Char] = ne.toIterable
}
