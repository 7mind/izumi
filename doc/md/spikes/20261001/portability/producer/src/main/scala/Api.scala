package portability

import scala.quoted.*

final case class Payload(value: Int)
object Api {
  def ordinary(value: Payload): Int = value.value
  inline def twice(value: Int): Int = value + value
  inline def checked(inline value: Boolean): Unit = ${ checkedImpl('value) }
  private def checkedImpl(value: Expr[Boolean])(using Quotes): Expr[Unit] = '{ assert($value) }
}
