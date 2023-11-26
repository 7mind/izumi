package izumi.functional

import izumi.functional.IzEither.*
import izumi.fundamentals.collections.nonempty.{NEList, NESet}
import org.scalatest.wordspec.AnyWordSpec

import scala.annotation.nowarn

@nowarn("msg=Unused import")
class IzEitherTest extends AnyWordSpec {
  import scala.collection.compat.*

  type BuilderFail
  type IzType
  type Result[+T] = Either[List[BuilderFail], T]
  type TList = Result[List[IzType]]

  def listTList: List[TList] = Nil
  def x(t: TList): Result[Unit] = Right { val _ = t }

  "IzEither" should {
    "have biFlatAggregate callable with typealiases" in {

      def test: Either[List[BuilderFail], Unit] = {
        val ret = listTList.biFlatten
        x(ret)
      }

      assert(test.isRight)
    }

    "support NonEmptyCollections" in {
      val nel0 = List(Right(1), Right(2), Left(NEList("error")))

      assert(implicitly[Factory[String, NEList[String]]] ne null)
      assert(nel0.biSequence == Left(NEList("error")))

      val nes0 = List(Right(()), Left(NESet("error")))
      assert(nes0.biSequence == Left(NESet("error")))

      assert(nes0.biTraverse_ {
        case Left(value) => Left(value + "error1")
        case Right(value) => Right(value)
      } == Left(NESet("error", "error1")))

      val nel1 = List(Right(List(1)), Left(NEList("error")))
      assert(nel1.biFlatten == Left(NEList("error")))

      assert(nel1.biTraverse {
        case Left(value) => Left(Set(value ++ NEList("a")))
        case Right(value) => Right(Set(value ++ List(1)))
      } == Left(Set(NEList("error", "a"))))

      val nel2 = List(Right(1), Left(NEList("error")))
      assert(nel2.biFlatTraverse(_.map(i => List(i))) == Left(NEList("error")))
      assert(nel2.biFlatTraverse(_.map(i => List(i))).to(Set) == Left(NEList("error")))

    }

    "support the happy path" in {
      val l0: Seq[Either[List[String], Int]] = List(Right(1), Right(2), Right(3))
      assert((l0.biSequence: Either[List[String], Seq[Int]]) == Right(Seq(1, 2, 3)))
      assert(l0.biSequence_ == Right(()))
      assert(l0.biTraverse(identity) == Right(Seq(1, 2, 3)))
      assert(l0.biTraverse(identity).to(List) == Right(List(1, 2, 3)))
      assert(l0.biTraverse_(_.map(_ => ())) == Right(()))

      val l1: Seq[Either[List[String], Seq[Int]]] = List(Right(Seq(1)), Right(Seq(2)), Right(Seq(3)))
      assert(l1.biFlatTraverse(identity) == Right(Seq(1, 2, 3)))
      assert(l1.biFlatTraverse(identity).to(List) == Right(List(1, 2, 3)))

      assert(l1.biFlatten == Right(List(1, 2, 3)))

      val l2: Seq[Either[String, Int]] = List(Right(1), Right(2), Right(3), Left("error"))
      assert(l2.biSequenceScalar == Left(NEList("error")))

      val l3: Seq[Either[List[String], Int]] = List(Right(1), Right(2), Right(3), Left(List("error")))
      assert(l3.biSequence == Left(List("error")))

      assert(Right(Seq(1, 2, 3)).to(List) == Right(List(1, 2, 3)))
    }

    "support search" in {
      assert(List(1, 2, 3).biFind(i => Right(i == 2)) == Right(Some(2)))
      assert(List(1, 2, 3).biFind(i => Right(i == 4)) == Right(None))
      assert(List(1, 2, 3).biFind(_ => Left("error")) == Left("error"))
    }

    "support fold" in {
      assert(List(1, 2, 3).biFoldLeft("") { case (acc, v) => Right(s"$acc.$v") } == Right(".1.2.3"))
      assert(List(1, 2, 3).biFoldLeft("") { case (_, _) => Left("error") } == Left("error"))
    }

    "support partitioning" in {
      val lst = List(Right(1), Right(2), Left(3))
      val (l, r) = lst.biPartition
      assert(l == List(3))
      assert(r == List(1, 2))
    }

    "support conditionals" in {
      assert(Either.ifThenElse(cond = true)(1)("error") == Right(1))
      assert(Either.ifThenElse(cond = false)(1)("error") == Left("error"))

      assert(Either.ifThenFail(cond = true)("error") == Left("error"))
      assert(Either.ifThenFail(cond = false)("error") == Right(()))
    }
  }

}
