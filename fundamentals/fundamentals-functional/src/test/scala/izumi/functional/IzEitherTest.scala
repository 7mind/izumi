package izumi.functional

import org.scalatest.wordspec.AnyWordSpec

class IzEitherTest extends AnyWordSpec {
  type BuilderFail
  type IzType
  type Result[+T] = Either[List[BuilderFail], T]
  type TList = Result[List[IzType]]

  def listTList: List[TList] = Nil
  def x(t: TList): Result[Unit] = Right { val _ = t }

  "IzEither.biFlatAggregate is callable with typealiases" in {
    assertCompiles(
      """
        import IzEither._
        def test: Either[List[BuilderFail], Unit] = {
          val ret = listTList.biFlatAggregate
          x(ret)
        }
      """
    )
  }
}
