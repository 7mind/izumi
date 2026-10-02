package izumi.fundamentals.assertions

object AssertionInlineFixture {
  def left: Int = 1
  def right: Int = 2
  inline def condition: Boolean = left > right
}
