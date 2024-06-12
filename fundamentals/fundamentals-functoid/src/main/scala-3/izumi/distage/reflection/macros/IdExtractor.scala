package izumi.distage.reflection.macros

import scala.quoted.Quotes

trait IdExtractor[Q <: Quotes] {
  val qctx: Q

  import qctx.reflect.*

  def extractId(name: String, annotSym: Option[Symbol], annotTpe: Either[TypeTree, TypeRepr]): Option[String]
}
