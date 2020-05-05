package izumi.fundamentals.graphs.tools.random

import java.util.concurrent.atomic.AtomicInteger

trait Generator[N] {
  def make(): N
}

object Generator {

  implicit object IntGenerator extends Generator[Int] {
    private val last = new AtomicInteger(0)

    override def make(): Int = {
      last.getAndIncrement()
    }
  }

}
