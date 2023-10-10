package izumi.functional.bio

import izumi.fundamentals.collections.nonempty.{NEList, NESet}
import org.scalatest.wordspec.AnyWordSpec

import scala.annotation.nowarn

@nowarn("msg=Unused import")
class ErrorAccumulatingOpsTest extends AnyWordSpec {
  import scala.collection.compat.*

  type BuilderFail
  type IzType
  type Result[+T] = Either[List[BuilderFail], T]
  type TList = Result[List[IzType]]

  def listTList: List[TList] = Nil
  def x(t: TList): Result[Unit] = Right { val _ = t }

  def F: Error2[Either] = Root.BIOEither

  /** `flatSequence` with error accumulation */
  def flatSequenceAccumErrors[ColR[x] <: IterableOnce[x], ColL[_], E, A](
    col: ColR[Either[ColL[E], IterableOnce[A]]]
  )(implicit
    buildR: Factory[A, ColR[A]],
    buildL: Factory[E, ColL[E]],
    iterL: ColL[E] => IterableOnce[E],
  ): Either[ColL[E], ColR[A]] = {
    F.flatSequenceAccumErrors(col)
  }

  "ErrorAccumulatingOps" should {

    "have biFlatAggregate callable with typealiases" in {

      def test: Either[List[BuilderFail], Unit] = {
        val ret = F.flatSequenceAccumErrors /*[List, List, Any, BuilderFail, IzType]*/ (listTList: List[Either[List[BuilderFail], List[IzType]]])
        x(ret)
      }

      assert(test.isRight)
    }

    "support NonEmptyCollections" in {
      val nel0 = List(Right(1), Right(2), Left(NEList("error")))

      assert(implicitly[Factory[String, NEList[String]]] ne null)
      assert(F.sequenceAccumErrors(nel0) == Left(NEList("error")))

      val nes0 = List(Right(()), Left(NESet("error")))
      assert(F.sequenceAccumErrors(nes0) == Left(NESet("error")))

      assert(F.traverseAccumErrors_(nes0) {
        case Left(value) => Left(value + "error1")
        case Right(value) => Right(value)
      } == Left(NESet("error", "error1")))

      val nel1 = List(Right(List(1)), Left(NEList("error")))
      assert(F.flatSequenceAccumErrors(nel1) == Left(NEList("error")))

      assert(F.traverseAccumErrors(nel1) {
        case Left(value) => Left(Set(value ++ NEList("a")))
        case Right(value) => Right(Set(value ++ List(1)))
      } == Left(Set(NEList("error", "a"))))

      val nel2 = List(Right(1), Left(NEList("error")))
      assert(F.flatTraverseAccumErrors(nel2)(_.map(i => List(i))) == Left(NEList("error")))
      assert(F.flatTraverseAccumErrors(nel2)(_.map(i => List(i))).map(_.to(Set)) == Left(NEList("error")))
    }

    "support the happy path" in {
      val l0: Seq[Either[List[String], Int]] = List(Right(1), Right(2), Right(3))
      assert((F.sequenceAccumErrors(l0): Either[List[String], Seq[Int]]) == Right(Seq(1, 2, 3)))
      assert(F.sequenceAccumErrors_(l0) == Right(()))
      assert(F.traverseAccumErrors(l0)(identity) == Right(Seq(1, 2, 3)))
      assert(F.traverseAccumErrors(l0)(identity).map(_.to(List)) == Right(List(1, 2, 3)))
      assert(F.traverseAccumErrors_(l0)(_.map(_ => ())) == Right(()))

      val l1: Seq[Either[List[String], Seq[Int]]] = List(Right(Seq(1)), Right(Seq(2)), Right(Seq(3)))
      assert(F.flatTraverseAccumErrors(l1)(identity) == Right(Seq(1, 2, 3)))
      assert(F.flatTraverseAccumErrors(l1)(identity).map(_.to(List)) == Right(List(1, 2, 3)))

      assert(F.flatSequenceAccumErrors(l1) == Right(List(1, 2, 3)))

      val l2: Seq[Either[String, Int]] = List(Right(1), Right(2), Right(3), Left("error"))
      assert(F.sequenceAccumErrorsNEList(l2) == Left(NEList("error")))

      val l3: Seq[Either[List[String], Int]] = List(Right(1), Right(2), Right(3), Left(List("error")))
      assert(F.sequenceAccumErrors(l3) == Left(List("error")))

      assert(Right(Seq(1, 2, 3)).map(_.to(List)) == Right(List(1, 2, 3)))
    }

    "support search" in {
      assert(F.find(List(1, 2, 3))(i => Right(i == 2)) == Right(Some(2)))
      assert(F.find(List(1, 2, 3))(i => Right(i == 4)) == Right(None))
      assert(F.find(List(1, 2, 3))(_ => Left("error")) == Left("error"))
    }

    "support fold" in {
      assert(F.foldLeft(List(1, 2, 3))("") { case (acc, v) => Right(s"$acc.$v") } == Right(".1.2.3"))
      assert(F.foldLeft(List(1, 2, 3))("") { case (_, _) => Left("error") } == Left("error"))
    }

    "support partitioning" in {
      val lst = List(Right(1), Right(2), Left(3))
      val Right((l, r)) = F.partition(lst)
      assert(l == List(3))
      assert(r == List(1, 2))
    }

  }

}
