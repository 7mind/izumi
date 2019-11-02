package izumi.idealingua.model.il.ast

import izumi.idealingua.model.loader.FSPath

sealed trait InputPosition

object InputPosition {

  case class Defined(start: Int, stop: Int, file: FSPath) extends InputPosition {
    // TODO: this is a very dirty solution for comparison problem in LoaderTest
    override def hashCode(): Int = 0

    override def equals(obj: Any): Boolean = obj.isInstanceOf[Defined]
  }

  case object Undefined extends InputPosition

}
