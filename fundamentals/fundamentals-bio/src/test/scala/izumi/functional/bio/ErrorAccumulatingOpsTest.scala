package izumi.functional.bio

import izumi.fundamentals.collections.nonempty.{NEList, NESet}
import org.scalatest.wordspec.AnyWordSpec

import scala.annotation.{nowarn, unused}

final class ErrorAccumulatingOpsTestEither extends ErrorAccumulatingOpsTest[Either] {
  override implicit def F: Error2[Either] = Root.BIOEither
  override def unsafeRun[E, A](f: Either[E, A]): Either[E, A] = f
}

final class ErrorAccumulatingOpsTestZIO extends ErrorAccumulatingOpsTest[zio.IO] {
  private val runner: UnsafeRun2[zio.IO] = UnsafeRun2.createZIO()

  override implicit def F: Error2[zio.IO] = Root.Convert3To2(Root.BIOZIO)
  override def unsafeRun[E, A](f: zio.IO[E, A]): Either[E, A] = runner.unsafeRun(f.attempt)
}

@nowarn("msg=Unused import")
abstract class ErrorAccumulatingOpsTest[F[+_, +_]] extends AnyWordSpec {
  import scala.collection.compat.*

  type BuilderFail
  type IzType
  type Result[+T] = F[List[BuilderFail], T]
  type TList = Result[List[IzType]]

  def listTList: List[TList] = Nil
  def x(@unused t: TList): Result[Unit] = F.unit

  implicit def F: Error2[F]
  def unsafeRun[E, A](f: F[E, A]): Either[E, A]

  implicit final class Run[+E, +A](f: F[E, A]) {
    def run(): Either[E, A] = unsafeRun(f)
  }

  "ErrorAccumulatingOps" should {

    "have biFlatAggregate callable with typealiases" in {

      def test: F[List[BuilderFail], Unit] = {
        val ret = F.flatSequenceAccumErrors(listTList)
        x(ret)
      }

      assert(test.run().isRight)
    }

    "support NonEmptyCollections" in {
      val nel0 = List(F.pure(1), F.pure(2), F.fail(NEList("error")))

      assert(implicitly[Factory[String, NEList[String]]] ne null)
      assert(F.sequenceAccumErrors(nel0).run() == Left(NEList("error")))

      val nes0 = List(F.pure(()), F.fail(NESet("error")))
      assert(F.sequenceAccumErrors(nes0).run() == Left(NESet("error")))

      assert(
        F.traverseAccumErrors_(nes0)(_.attempt.flatMap {
          case Left(value) => F.fail(value + "error1")
          case Right(value) => F.pure(value)
        }).run() == Left(NESet("error", "error1"))
      )

      val nel1 = List(F.pure(List(1)), F.fail(NEList("error")))
      assert(F.flatSequenceAccumErrors(nel1).run() == Left(NEList("error")))

      assert(
        F.traverseAccumErrors(nel1)(_.attempt.flatMap {
          case Left(value) => F.fail(Set(value ++ NEList("a")))
          case Right(value) => F.pure(Set(value ++ List(1)))
        }).run() == Left(Set(NEList("error", "a")))
      )

      val nel2 = List(F.pure(1), F.fail(NEList("error")))
      assert(F.flatTraverseAccumErrors(nel2)(_.map(i => List(i))).run() == Left(NEList("error")))
      assert(F.flatTraverseAccumErrors(nel2)(_.map(i => List(i))).map(_.to(Set)).run() == Left(NEList("error")))
    }

    "support the happy path" in {
      val l0: Seq[F[List[String], Int]] = List(F.pure(1), F.pure(2), F.pure(3))
      assert(F.sequenceAccumErrors(l0).run() == Right(Seq(1, 2, 3)))
      assert(F.sequenceAccumErrors_(l0).run() == Right(()))
      assert(F.traverseAccumErrors(l0)(identity).run() == Right(Seq(1, 2, 3)))
      assert(F.traverseAccumErrors(l0)(identity).map(_.to(List)).run() == Right(List(1, 2, 3)))
      assert(F.traverseAccumErrors_(l0)(_.map(_ => ())).run() == Right(()))

      val l1: Seq[F[List[String], Seq[Int]]] = List(F.pure(Seq(1)), F.pure(Seq(2)), F.pure(Seq(3)))
      assert(F.flatTraverseAccumErrors(l1)(identity).run() == Right(Seq(1, 2, 3)))
      assert(F.flatTraverseAccumErrors(l1)(identity).map(_.to(List)).run() == Right(List(1, 2, 3)))

      assert(F.flatSequenceAccumErrors(l1).run() == Right(List(1, 2, 3)))

      val l2: Seq[F[String, Int]] = List(F.pure(1), F.pure(2), F.pure(3), F.fail("error"))
      assert(F.sequenceAccumErrorsNEList(l2).run() == Left(NEList("error")))

      val l3: Seq[F[List[String], Int]] = List(F.pure(1), F.pure(2), F.pure(3), F.fail(List("error")))
      assert(F.sequenceAccumErrors(l3).run() == Left(List("error")))

      assert(Right(Seq(1, 2, 3)).map(_.to(List)) == Right(List(1, 2, 3)))
    }

    "support search" in {
      assert(F.find(List(1, 2, 3))(i => F.pure(i == 2)).run() == Right(Some(2)))
      assert(F.find(List(1, 2, 3))(i => F.pure(i == 4)).run() == Right(None))
      assert(F.find(List(1, 2, 3))(_ => F.fail("error")).run() == Left("error"))
    }

    "support fold" in {
      assert(F.foldLeft(List(1, 2, 3))("") { case (acc, v) => F.pure(s"$acc.$v") }.run() == Right(".1.2.3"))
      assert(F.foldLeft(List(1, 2, 3))("") { case (_, _) => F.fail("error") }.run() == Left("error"))
    }

    "support partitioning" in {
      val lst = List(F.pure(1), F.pure(2), F.fail(3))
      val Right((l, r)) = F.partition(lst).run(): @unchecked
      assert(l == List(3))
      assert(r == List(1, 2))
    }

  }

}
