package izumi.fixtures.assertions

import izumi.fundamentals.assertions.{Assert, AssertionFailure, Assertions, Evaluation}

object PublishedAssertionConsumer {
  def main(args: Array[String]): Unit = {
    var checks = 0
    def value: Int = { checks += 1; 1 }
    Assert.assert(value == 1)
    val caught = try { Assert.assert(value > 2 && value == 1); None } catch { case failure: AssertionFailure => Some(failure) }
    if (checks != 2 || caught.isEmpty) throw new IllegalStateException("Published assertion changed evaluation semantics")
    val failure = caught.get
    if (!failure.diagnostic.observations.exists(_.evaluation == Evaluation.NotEvaluated)) throw new IllegalStateException("Published assertion omitted the skipped branch")
    if (!failure.getMessage.contains("Assertion failed")) throw new IllegalStateException("Published assertion has no usable message")
    var receiverEvaluations = 0
    lazy val receiver: Assertions = { receiverEvaluations += 1; new Assertions {} }
    receiver.assert {
      if (receiverEvaluations != 1) throw new IllegalStateException("Published assertion omitted receiver initialization")
      true
    }
    if (receiverEvaluations != 1) throw new IllegalStateException("Published assertion evaluated its receiver more than once")
    println("PUBLISHED_ASSERTION_CONSUMER_OK")
  }
}
