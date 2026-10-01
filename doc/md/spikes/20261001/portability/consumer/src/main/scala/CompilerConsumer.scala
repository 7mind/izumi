import portability.{Api, Payload}

object CompilerConsumer {
  def main(args: Array[String]): Unit = {
    assert(Api.ordinary(Payload(21)) == 21)
    assert(Api.twice(21) == 42)
    Api.checked(true)
    println("ORDINARY_INLINE_AND_MACRO_CONSUMER_PASS")
  }
}
