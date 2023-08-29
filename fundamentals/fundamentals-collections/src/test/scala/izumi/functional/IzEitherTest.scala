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

  "IzEither.biFlatAggregate is callable with typealiases" in {
    def test: Either[List[BuilderFail], Unit] = {
      val ret = listTList.biFlatAggregate
      x(ret)
    }

    assert(test.isRight)
  }

  "IzEither should support NonEmptyCollections" in {

    val nel0 = List(Right(1), Right(2), Left(NEList("error")))

    implicitly[Factory[String, NEList[String]]]
    assert(nel0.biAggregate == Left(NEList("error")))

    val nes0 = List(Right(()), Left(NESet("error")))
    assert(nes0.biAggregate == Left(NESet("error")))

    assert(nes0.biMapAggregateVoid {
      case Left(value) => Left(value + "error1")
      case Right(value) => Right(value)
    } == Left(NESet("error", "error1")))

    val nel1 = List(Right(List(1)), Left(NEList("error")))
    assert(nel1.biFlatAggregate == Left(NEList("error")))

    assert(nel1.biMapAggregate {
      case Left(value) => Left(Set(value ++ NEList("a")))
      case Right(value) => Right(Set(value ++ List(1)))
    } == Left(Set(NEList("error", "a"))))

    val nel2 = List(Right(1), Left(NEList("error")))
    assert(nel2.biFlatMapAggregate(_.map(i => List(i))) == Left(NEList("error")))
    assert(nel2.biFlatMapAggregateTo(_.map(i => List(i)))(Set) == Left(NEList("error")))

  }
}
