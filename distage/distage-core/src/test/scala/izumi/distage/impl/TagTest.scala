package izumi.distage.impl

import distage._
import izumi.distage.fixtures.HigherKindCases.HigherKindsCase1.{OptionT, id}
import izumi.distage.model.definition.With
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.Quirks._
import izumi.fundamentals.reflection.Tags.HKTag
import izumi.fundamentals.reflection.macrortti._
import org.scalatest.WordSpec
import org.scalatest.exceptions.TestFailedException

import scala.reflect.ClassTag
import scala.reflect.runtime.{universe => ru}

trait XY[Y] {
  type Z = id[Y]

  implicit def tagZ: Tag[Z]
}

trait ZY {
  type T
  val x: String = "5"

  object y
}

// https://github.com/scala/bug/issues/11139
final case class testTag[T: Tag]() {
  type X[A] = Either[Int, A]
  type Y = T
  val res = Tag[X[Y {}]]
}
final case class testTag2[T: Tag]() {
  type X = List[T]
  val res = Tag[X]
}
final case class testTag3[F[_]: TagK]() {
  type X = OptionT[F, Int]
  val res = SafeType.get[X]
}

class TagTest extends WordSpec with XY[String] {

  def safe[T: ru.TypeTag](implicit maybeClassTag: ClassTag[T] = null): SafeType = {
    SafeType(
      tag = LightTypeTagImpl.makeLightTypeTag(ru)(ru.typeOf[T]),
      cls = Option(maybeClassTag).fold[Class[_]](classOf[Any])(_.runtimeClass),
    )
  }
  def safeTypeLTag[T: LTag](implicit maybeClassTag: ClassTag[T] = null): SafeType = {
    SafeType(LTag[T].tag, Option(maybeClassTag).fold[Class[_]](classOf[Any])(_.runtimeClass))
  }

  implicit class TagSafeType(tag: Tag[_]) {
    def toSafe: SafeType = SafeType(tag.tag, tag.closestClass)
  }

  implicit class TagKSafeType(tagK: HKTag[_]) {
    def toSafe: LightTypeTag = tagK.tag
  }

  override final val tagZ = Tag[String]
  final val str = "str"

  trait H1
  trait T1[A, B, C, D, E, F[_]]
  trait T2[A, B, C[_[_], _], D[_], E]
  trait Test[A, dafg, adfg, LS, L[_], SD, GG[A] <: L[A], ZZZ[_, _], S, SDD, TG]
  trait YX[V] extends XY[V]
  case class ZOBA[A, B, C](value: Either[B, C])
  trait BIOService[F[_, _]]
  type Swap[A, B] = Either[B, A]
  type SwapF2[F[_, _], A, B] = F[B, A]
  type Id[A] = A
  type Id1[F[_], A] = F[A]
  type Const[A, B] = A
  trait ZIO[-R, +E, +A]
  type IO[+E, +A] = ZIO[Any, E, A]
  type EitherR[-_, +L, +R] = Either[L, R]
  type EitherRSwap[-_, +L, +R] = Either[R, L]

  type F2To3[F[_, _], R, E, A] = F[E, A]

  trait BlockingIO3[F[_, _, _]]
  type BlockingIO[F[_, _]] = BlockingIO3[Lambda[(R, E, A) => F[E, A]]]

  trait BlockingIO3T[F[_, _[_], _]]
  type BlockingIOT[F[_[_], _]] = BlockingIO3T[Lambda[(R, `E[_]`, A) => F[E, A]]]

  type BIOServiceL[F[+_, +_], E, A] = BIOService[Lambda[(X, Y) => F[A, E]]]

  class ApplePaymentProvider[F0[_]] extends H1

  "Tag" should {

    "Work for any concrete type" in {
      assert(Tag[Int].toSafe == safe[Int])
      assert(Tag[Set[String]].toSafe == safe[Set[String]])
      assert(Tag[Map[Boolean, Double]].toSafe == safe[Map[Boolean, Double]])
      assert(Tag[_ => Unit].toSafe == safe[_ => Unit])
      assert(Tag[Unit => _].toSafe == safe[Unit => _])
      assert(Tag[_ => _].toSafe == safe[_ => _])

      assert(Tag[Any].toSafe == safe[Any])
      assert(Tag[Nothing].toSafe == safe[Nothing])
      assert(Tag[Any => Nothing].toSafe == safe[Any => Nothing])
      assert(Tag[Nothing => Any].toSafe == safe[Nothing => Any])
      assert(TagK[Identity].toSafe == TagK[Lambda[A => A]].toSafe)

      assert(Tag[With[Any]].toSafe == safe[With[Any]])
      assert(Tag[With[Nothing]].toSafe == safe[With[Nothing]])
      assert(Tag[With[_]].toSafe == safe[With[_]])

      assert(Tag[ {def a: Int; def g: Boolean}].toSafe == safe[ {def a: Int; def g: Boolean}])
      assert(Tag[Int with String].toSafe == safe[Int with String])
      assert(Tag[Int {def a: Int}].toSafe == safe[Int {def a: Int}])

      assert(Tag[str.type].toSafe == safe[str.type])
      assert(Tag[this.Z].toSafe == safe[this.Z])
      assert(Tag[TagTest#Z].toSafe == safe[TagTest#Z])
    }

    "Work for structural concrete types" in {
      assert(Tag[With[str.type] with ({type T = str.type with Int})].toSafe == safe[With[str.type] with ({type T = str.type with Int})])
      assert(Tag[With[str.type] with ({type T = str.type with Int})].toSafe != safe[With[str.type] with ({type T = str.type with Long})])
    }

    "Work with term type prefixes" in {
      val zy = new ZY {}
      val zx = new ZY {}

      assert(Tag[zy.T].toSafe == safeTypeLTag[zy.T])
      assert(Tag[zy.T].toSafe <:< safeTypeLTag[zy.T])
      assert(Tag[zy.T].toSafe != safeTypeLTag[zx.T])
      assert(Tag[zy.x.type].toSafe == safeTypeLTag[zy.x.type])
      assert(Tag[zy.x.type].toSafe <:< safeTypeLTag[zy.x.type])
      assert(Tag[zy.x.type].toSafe <:< safeTypeLTag[String])
      assert(Tag[zy.x.type].toSafe <:< safeTypeLTag[java.io.Serializable])
      assert(Tag[zy.x.type].toSafe != safeTypeLTag[zx.x.type])
      assert(Tag[zy.y.type].toSafe == safeTypeLTag[zy.y.type])
      assert(Tag[zy.y.type].toSafe <:< safeTypeLTag[zy.y.type])
      assert(Tag[zy.y.type].toSafe <:< safeTypeLTag[java.lang.Object])
      assert(Tag[zy.y.type].toSafe != safeTypeLTag[zx.y.type])
      assert(Tag[zy.y.type].toSafe != safeTypeLTag[zx.x.type])
    }

    "Work for any abstract type with available Tag when obscured by empty refinement" in {
      def testTag[T: Tag] = Tag[T {}]

      assert(testTag[String].toSafe == safe[String])
    }

    "Work for any abstract type with available Tag while preserving additional refinement" in {
      def testTag[T: Tag] = Tag[T {def x: Int}]

      assert(testTag[String].toSafe == safe[String {def x: Int}])
    }

    "handle function local type aliases" in {
      def testTag[T: Tag] = {
        type X[A] = Either[Int, A]

        Tag[X[T {}]]
      }

      assert(testTag[String].toSafe == safe[Either[Int, String]])

      def testTag2[T: Tag] = {
        type X = List[T]

        Tag[X]
      }

      assert(testTag2[String].toSafe == safe[List[String]])

      def testTag3[F[_]: TagK] = {
        type X = OptionT[F, Int]

        Tag[X]
      }

      assert(testTag3[List].toSafe == safe[OptionT[List, Int]])
    }

    "Can dealias transparent type members with class type parameters inside them when a tag is summoned _inside_ the class, because LightTypeTags are not affected by https://github.com/scala/bug/issues/11139" in {
      assert(testTag[String]().res.toSafe == safe[Either[Int, String]])
      assert(testTag2[String]().res.toSafe == safe[List[String]])
      assert(testTag3[List]().res == safe[OptionT[List, Int]])
    }

    "Work for an abstract type with available TagK when obscured by empty refinement" in {
      def testTagK[F[_]: TagK, T: Tag] = Tag[F[T {}] {}]

      assert(testTagK[Set, Int].toSafe == safe[Set[Int]])
    }

    "Work for an abstract type with available TagK when TagK is requested through an explicit implicit" in {
      def testTagK[F[_], T: Tag](implicit ev: HKTag[ {type Arg[C] = F[C]}]) = {
        ev.discard()
        Tag[F[T {}] {}]
      }

      assert(testTagK[Set, Int].toSafe == safe[Set[Int]])
    }

    "Tag.auto.T kind inference macro works for known cases" in {
      def x[T[_] : Tag.auto.T]: TagK[T] = implicitly[Tag.auto.T[T]]

      def x2[T[_, _] : Tag.auto.T]: TagKK[T] = implicitly[Tag.auto.T[T]]

      def x3[T[_, _, _[_[_], _], _[_], _]](implicit x: Tag.auto.T[T]): Tag.auto.T[T] = x

      val b1 = x[Option].tag =:= TagK[Option].tag
      val b2 = x2[Either].tag =:= TagKK[Either].tag
      val b3 = implicitly[Tag.auto.T[OptionT]].tag =:= TagTK[OptionT].tag
      val b4 = x3[T2].tag.withoutArgs =:= LTag[T2[Nothing, Nothing, Nothing, Nothing, Nothing]].tag.withoutArgs

      assert(b1)
      assert(b2)
      assert(b3)
      assert(b4)
    }

    "Work for an abstract type with available TagKK" in {
      def t1[F[_, _] : TagKK, T: Tag, G: Tag] = Tag[F[T, G]]

      assert(t1[ZOBA[Int, ?, ?], Int, String].toSafe == safe[ZOBA[Int, Int, String]])
    }

    "Handle Tags outside of a predefined set" in {
      type TagX[T[_, _, _[_[_], _], _[_], _]] = HKTag[ {type Arg[A, B, C[_[_], _], D[_], E] = T[A, B, C, D, E]}]

      def testTagX[F[_, _, _[_[_], _], _[_], _] : TagX, A: Tag, B: Tag, C[_[_], _] : TagTK, D[_] : TagK, E: Tag] = Tag[F[A, B, C, D, E]]

      val value = testTagX[T2, Int, String, OptionT, List, Boolean]
      assert(value.toSafe == safe[T2[Int, String, OptionT, List, Boolean]])
    }

    "Shouldn't work for any abstract type without available TypeTag or Tag or TagK" in {
      assertTypeError(
        """
      def testTag[T] = Tag[T]
      def testTagK[F[_], T] = Tag[F[T]]
         """)
    }

    "Work for any configuration of parameters" in {

      def t1[A: Tag, B: Tag, C: Tag, D: Tag, E: Tag, F[_] : TagK]: Tag[T1[A, B, C, D, E, F]] = Tag[T1[A, B, C, D, E, F]]

      type ZOB[A, B, C] = Either[B, C]

      assert(t1[Int, Boolean, ZOB[Unit, Int, Int], TagK[Option], Nothing, ZOB[Unit, Int, ?]].toSafe
        == safe[T1[Int, Boolean, Either[Int, Int], TagK[Option], Nothing, Either[Int, ?]]])

      def t2[A: Tag, dafg: Tag, adfg: Tag, LS: Tag, L[_] : TagK, SD: Tag, GG[A] <: L[A] : TagK, ZZZ[_, _] : TagKK, S: Tag, SDD: Tag, TG: Tag]: Tag[Test[A, dafg, adfg, LS, L, SD, GG, ZZZ, S, SDD, TG]] =
        Tag[Test[A, dafg, adfg, LS, L, SD, GG, ZZZ, S, SDD, TG]]

      assert(t2[TagTest.this.Z, TagTest.this.Z, T1[ZOB[String, Int, Byte], String, String, String, String, List], TagTest.this.Z, XY, TagTest.this.Z, YX, Either, TagTest.this.Z, TagTest.this.Z, TagTest.this.Z].toSafe
        == safe[Test[String, String, T1[Either[Int, Byte], String, String, String, String, List], String, XY, String, YX, Either, String, String, String]])
    }

    "handle Swap type lambda" in {
      def t1[F[_, _] : TagKK, A: Tag, B: Tag] = Tag[F[A, B]]

      assert(t1[Swap, Int, String].toSafe == safe[Either[String, Int]])
    }

    "handle Id type lambda" in {
      assert(TagK[Id].tag == TagK[Id].tag)
      assert(TagK[Id].tag != TagTK[Id1].tag)
    }

    "handle Id1 type lambda" in {
      assert(TagTK[Id1].tag == TagTK[Id1].tag)
      assert(TagTK[Id1].tag != TagK[Id].tag)
    }

    "Assemble from higher than TagKK tags" in {
      def tag[T[_[_], _] : TagTK, F[_] : TagK, A: Tag] = Tag[T[F, A]]

      assert(tag[OptionT, Option, Int].toSafe == safe[OptionT[Option, Int]])
    }

    "Handle abstract types instead of parameters" in {
      trait T1 {
        type F[F0[_], A0] = OptionT[F0, A0]
        type C[_, _]
        type G[_]
        type A
        type B

        def x: Tag[F[G, Either[A, B]]]
      }

      val t1: T1 {
        type G[T] = List[T]
        type C[A0, B0] = Either[A0, B0]
        type A = Int
        type B = Byte
      } = new T1 {
        type G[T] = List[T]
        type C[A0, B0] = Either[A0, B0]
        type A = Int
        type B = Byte

        // Inconsistent handling of type aliases by scalac...
        // No TagK for G, but if G is inside an object or enclosing class
        // then there is a TagK
        val g: TagK[G] = TagK[List]

        final val x: Tag[F[G, Either[A, B]]] = {
          implicit val g0: TagK[G] = g
          g0.discard()
          Tag[F[G, C[A, B]]]
        }
      }

      assert(t1.x.toSafe == safe[OptionT[List, Either[Int, Byte]]])
    }

    "Can create custom type tags to support bounded generics, e.g. <: Dep in TagK" in {
      import izumi.distage.fixtures.TypesCases.TypesCase3._

      type `TagK<:Dep`[K[_ <: Dep]] = HKTag[ {type Arg[A <: Dep] = K[A]}]

      implicitly[`TagK<:Dep`[Trait3]].tag.withoutArgs =:= LTag[Trait3[Nothing]].tag.withoutArgs
    }

    "can find HKTag when obscured by type lambda" in {
      assertCompiles("HKTag.hktagFromTagMacro[{ type Arg[C] = Option[C] }]")
      assertCompiles("HKTag.hktagFromTagMacro[({ type l[F[_]] = { type Arg[C] = F[C] } })#l[Option]]")
    }

    "return expected class tag" in {
      assert(Tag[List[_] with Set[_]].closestClass eq classOf[scala.collection.immutable.Iterable[_]])
      assert(!Tag[List[_] with Set[_]].hasPreciseClass)

      assert(Tag[AnyVal].closestClass eq classOf[AnyVal])
      assert(!Tag[AnyVal].hasPreciseClass)

      assert(Tag[String with Int].closestClass eq classOf[AnyVal])
      assert(!Tag[String with Int].hasPreciseClass)

      assert(Tag[List[Int]].closestClass eq classOf[List[_]])
      assert(Tag[List[Int]].hasPreciseClass)
      assert(Tag[H1].hasPreciseClass)

      assert(Tag[ZY#T].closestClass eq classOf[Any])
      assert(!Tag[ZY#T].hasPreciseClass)
    }

    "simple combined Tag" in {
      def get[F[_]: TagK] = Tag[ApplePaymentProvider[F]]
      val tag = get[Identity]

      val left = tag.tag
      val right = Tag[H1].tag

      assert(left <:< right)
    }

    "resolve TagK from TagKK" in {
      def getTag[F[+_, +_]: TagKK] = TagK[F[Throwable, ?]]
      val tagEitherThrowable = getTag[Either].tag
      val tag = TagK[Either[Throwable, ?]].tag

      assert(tagEitherThrowable =:= tag)
      assert(tagEitherThrowable <:< tag)
      assert(tagEitherThrowable <:< TagK[Either[Any, ?]].tag)
      assert(TagK[Either[Nothing, ?]].tag <:< tagEitherThrowable)
    }

    "can materialize TagK for type lambdas that close on a generic parameter with available Tag" in {
      def partialEitherTagK[A: Tag] = TagK[Either[A, ?]]

      val tag = partialEitherTagK[Int].tag
      val expectedTag = TagK[Either[Int, ?]].tag

      assert(tag =:= expectedTag)
    }

    "can materialize TagK for type lambdas that close on a generic parameter with available Tag when the constructor is a type parameter" in {
      def partialFTagK[F[_, _]: TagKK, A: Tag] = TagK[F[A, ?]]

      val tag = partialFTagK[Either, Int].tag
      val expectedTag = TagK[Either[Int, ?]].tag

      assert(tag =:= expectedTag)
    }

    "type parameter covariance works after combine" in {
      def getTag[F[+_, +_]: TagKK] = TagK[F[Throwable, ?]]
      val tagEitherThrowable = getTag[Either].tag
      val tagEitherSerializable = TagK[Either[java.io.Serializable, ?]]
      assert(tagEitherThrowable <:< tagEitherSerializable.tag)
    }

    "combine Const Lambda to TagK" in {
      def get[F[_, _]: TagKK] = TagK[F[Int, ?]]
      val tag = get[Const]

      assert(tag.tag =:= TagK[Const[Int, ?]].tag)
      assert(tag.tag <:< TagK[Const[AnyVal, ?]].tag)
      assert(tag.tag.hashCode() == TagK[Const[Int, ?]].tag.hashCode())
    }

    "combined TagK 3 & 2 parameter coherence" in {
      def get[F[+_, +_]: TagKK] = TagK[F[Throwable, ?]]
      val tag = get[IO]

      assert(tag.tag =:= TagK[IO[Throwable, ?]].tag)
      assert(tag.tag <:< TagK[IO[Throwable, ?]].tag)
      assert(tag.tag <:< TagK[IO[Any, ?]].tag)
    }

    "resolve TagKK from an odd higher-kinded Tag with swapped & ignored parameters (low-level)" in {
      type Lt[F[_, _, _], _1, _2, _3] = F[_2, _3, _1]

      val ctorTag: LightTypeTag = implicitly[Tag.auto.T[Lt]].tag
      val eitherRSwapTag = LTagK3[EitherRSwap].tag
      val throwableTag = LTag[Throwable].tag

      val combinedTag = HKTag.appliedTagNonPosAux(classOf[Any],
        ctor = ctorTag,
        args = List(
          Some(eitherRSwapTag),
          Some(throwableTag),
          None,
          None,
        )).tag
      val expectedTag = TagKK[Lt[EitherRSwap, Throwable, ?, ?]].tag
      assert(combinedTag =:= expectedTag)
    }

    "resolve TagKK from an odd higher-kinded Tag with swapped & ignored parameters" in {
      def getTag[F[-_, +_, +_]: TagK3] = TagKK[F[?, ?, Throwable]]
      val tagEitherSwap = getTag[EitherRSwap].tag
      val tagEitherThrowable = getTag[EitherR].tag

      val expectedTagSwap = TagKK[EitherRSwap[?, ?, Throwable]].tag
      val expectedTagEitherThrowable = TagKK[EitherR[?, ?, Throwable]].tag

      assert(!(tagEitherSwap =:= expectedTagEitherThrowable))
      assert(tagEitherSwap =:= expectedTagSwap)
      assert(tagEitherThrowable =:= expectedTagEitherThrowable)
      assert(tagEitherSwap <:< expectedTagSwap)
      assert(tagEitherSwap <:< TagKK[EitherRSwap[?, ?, Any]].tag)
      assert(TagKK[EitherRSwap[?, ?, Nothing]].tag <:< tagEitherSwap)
    }

    "combine higher-kinded types without losing ignored type arguments" in {
      def mk[F[+_, +_]: TagKK] = Tag[BlockingIO[F]]
      val tag = mk[IO]

      assert(tag.tag == Tag[BlockingIO[IO]].tag)
    }

    "resolve a higher-kinded type inside a named type lambda with ignored type arguments" in {
      def mk[F[+_, +_]: TagKK] = Tag[BlockingIO3[F2To3[F, ?, ?, ?]]]
      val tag = mk[IO]

      assert(tag.tag == Tag[BlockingIO[IO]].tag)
    }

    "resolve a higher-kinded type inside an anonymous type lambda with ignored & higher-kinded type arguments" in {
      def mk[F[_[_], _]: TagTK] = Tag[BlockingIO3T[Lambda[(`-R`, `E[_]`, `A`) => F[E, A]]]]
      val tag = mk[OptionT]

      assert(tag.tag == Tag[BlockingIOT[OptionT]].tag)
    }

    "correctly resolve a higher-kinded nested type inside a named swap type lambda" in {
      def mk[F[+_, +_]: TagKK] = Tag[BIOService[SwapF2[F, ?, ?]]]
      val tag = mk[Either]

      assert(tag.tag == Tag[BIOService[SwapF2[Either, ?, ?]]].tag)
      assert(tag.tag == Tag[BIOService[Swap]].tag)
      assert(tag.tag == Tag[BIOService[Lambda[(E, A) => Either[A, E]]]].tag)
    }

    "correctly resolve a higher-kinded nested type inside an anonymous swap type lambda" in {
      def mk[F[+_, +_]: TagKK] = Tag[BIOService[Lambda[(E, A) => F[A, E]]]]
      val tag = mk[Either]

      assert(tag.tag == Tag[BIOService[SwapF2[Either, ?, ?]]].tag)
      assert(tag.tag == Tag[BIOService[Swap]].tag)
      assert(tag.tag == Tag[BIOService[Lambda[(E, A) => Either[A, E]]]].tag)
    }

    "progression test: cannot resolve a higher-kinded type in a higher-kinded tag in a named deeply-nested type lambda" in {
      val t = intercept[TestFailedException] {
        assertCompiles(
      """
      def mk[F[+_, +_]: TagKK] = TagKK[({ type l[A, B] = BIOServiceL[F, A, B] })#l]
      val tag = mk[Either]

      assert(tag.tag == LTagKK[Lambda[(E, A) => BIOService[Lambda[(X, Y) => Either[A, E]]]]].tag)
      """
        )
      }
      assert(t.message.get contains "could not find implicit value")
    }

    "progression test: cannot resolve a higher-kinded type in a higher-kinded tag in an anonymous deeply-nested type lambda" in {
      val t = intercept[TestFailedException] {
        assertCompiles(
      """
      def mk[F[+_, +_]: TagKK] = TagKK[ ({ type l[E, A] = BIOService[ ({ type l[X, Y] = F[A, E] })#l ] })#l ]
      val tag = mk[Either]

      assert(tag.tag == LTagKK[Lambda[(E, A) => BIOService[Lambda[(X, Y) => Either[A, E]]]]].tag)
      """
        )
      }
      assert(t.message.get contains "could not find implicit value")
    }

    "progression test: cannot resolve type prefix or a type projection (this case is no longer possible in dotty at all. not worth it to support?)" in {
      val res = intercept[IllegalArgumentException] {
        class Path {
          type Child
        }
        val path = new Path

        def getTag[A <: Path] = Tag[A#Child]

        assert(getTag[path.type].tag =:= Tag[path.type].tag)
        assert(getTag[path.type].tag <:< Tag[Path#Child].tag)
        assert(!(Tag[Path#Child].tag <:< getTag[path.type].tag))
      }
      assert(res.getMessage.contains("is not a type lambda, it cannot be parameterized"))
    }

    "progression test: type tags with bounds are not currently requested by the macro" in {
      val t = intercept[TestFailedException] {
        assertCompiles(
          //      identity(
          """
      import izumi.distage.fixtures.TypesCases.TypesCase3._

      type `TagK<:Dep`[K[_ <: Dep]] = HKTag[ { type Arg[A <: Dep] = K[A] } ]

      def t[T[_ <: Dep]: `TagK<:Dep`, A: Tag] = Tag[T[A]]

      assert(t[Trait3, Dep].tag == safe[Trait3[Dep]].tag)
      """)
      }
      assert(t.message.get contains "could not find implicit value")
    }

    "progression test: can't handle parameters in structural types yet" in {
      val t = intercept[TestFailedException] {
        assertCompiles(
          """
      def t[T: Tag]: Tag[{ type X = T }] = Tag[{ type X = T }]

      assert(t[Int].tpe == safe[{ type X = Int }])
      """)
      }
      assert(t.message.get contains "could not find implicit value")
    }

  }

}
