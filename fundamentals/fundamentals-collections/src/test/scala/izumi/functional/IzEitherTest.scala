package izumi.functional

import izumi.fundamentals.collections.nonempty.{NonEmptyList, NonEmptySet}
import org.scalatest.wordspec.AnyWordSpec
import IzEither.*

import scala.annotation.nowarn
import scala.collection.mutable

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

    val nel0 = List(Right(1), Right(2), Left(NonEmptyList("error")))

    implicit def factoryNel[A]: Factory[A, NonEmptyList[A]] = new Factory[A, NonEmptyList[A]] {
      override def fromSpecific(it: IterableOnce[A]): NonEmptyList[A] = NonEmptyList.unsafeFrom(it.iterator.toList)

      override def newBuilder: mutable.Builder[A, NonEmptyList[A]] = {
        new mutable.Builder[A, NonEmptyList[A]] {

          val sub = implicitly[Factory[A, List[A]]].newBuilder
          override def clear(): Unit = sub.clear()

          override def result(): NonEmptyList[A] = {
            NonEmptyList.unsafeFrom(sub.result())
          }

          override def addOne(elem: A): this.type = {
            sub.addOne(elem)
            this
          }
        }
      }
    }

    implicit def factoryNes[A]: Factory[A, NonEmptySet[A]] = new Factory[A, NonEmptySet[A]] {
      override def fromSpecific(it: IterableOnce[A]): NonEmptySet[A] = NonEmptySet.unsafeFrom(it.iterator.toSet)

      override def newBuilder: mutable.Builder[A, NonEmptySet[A]] = {
        new mutable.Builder[A, NonEmptySet[A]] {

          val sub = implicitly[Factory[A, Set[A]]].newBuilder

          override def clear(): Unit = sub.clear()

          override def result(): NonEmptySet[A] = {
            NonEmptySet.unsafeFrom(sub.result())
          }

          override def addOne(elem: A): this.type = {
            sub.addOne(elem)
            this
          }
        }
      }
    }

    implicitly[Factory[String, NonEmptyList[String]]]
    assert(nel0.biAggregate == Left(NonEmptyList("error")))

    val nes0 = List(Right(()), Left(NonEmptySet("error")))
    assert(nes0.biAggregate == Left(NonEmptySet("error")))

    assert(nes0.biMapAggregateVoid {
      case Left(value) => Left(value + "error1")
      case Right(value) => Right(value)
    } == Left(NonEmptySet("error", "error1")))

    val nel1 = List(Right(List(1)), Left(NonEmptyList("error")))
    assert(nel1.biFlatAggregate == Left(NonEmptyList("error")))

    assert(nel1.biMapAggregate {
      case Left(value) => Left(Set(value ++ NonEmptyList("a")))
      case Right(value) => Right(Set(value ++ List(1)))
    } == Left(Set(NonEmptyList("error", "a"))))

    val nel2 = List(Right(1), Left(NonEmptyList("error")))
    assert(nel2.biFlatMapAggregate(_.map(i => List(i))) == Left(NonEmptyList("error")))
    assert(nel2.biFlatMapAggregateTo(_.map(i => List(i)))(Set) == Left(NonEmptyList("error")))

  }
}
