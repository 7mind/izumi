package izumi.logstage.api.rendering

import izumi.fundamentals.platform.language.Quirks.Discarder

import scala.collection.mutable.{ArrayBuffer, Stack}

private[logstage] object LogstageTokenRendering {
  def translate[T, A](stack: Stack[T], close: T)(
    open: PartialFunction[T, Boolean],
    value: PartialFunction[T, A],
    wrap: A => T,
    renderMap: ArrayBuffer[A] => A,
    renderList: ArrayBuffer[A] => A,
    empty: A,
    multiple: List[A] => A,
  ): A = {
    val boundaries = Stack[T]()
    while (stack.nonEmpty) {
      val token = stack.pop()
      if (open.isDefinedAt(token)) {
        val elements = ArrayBuffer[A]()
        while (boundaries.head != close) {
          val element = boundaries.pop()
          if (value.isDefinedAt(element)) {
            elements += value(element)
          } else {
            throw new RuntimeException(s"Unexpected token: $element; stack=$stack, bstack=$boundaries")
          }
        }
        boundaries.pop().discard()
        boundaries.push(wrap(if (open(token)) renderMap(elements) else renderList(elements)))
      } else {
        boundaries.push(token)
      }
    }
    boundaries.collect(value).toList match {
      case one :: Nil =>
        one
      case Nil =>
        empty
      case many =>
        multiple(many)
    }
  }
}
