package logstage

import izumi.logstage.api.Log.LogArg
import izumi.logstage.api.TestSink
import org.scalatest.wordspec.AnyWordSpec
import zio.*
import logstage.LogZIO.log

class LogZIOSpec extends AnyWordSpec {
  private val runtime = Runtime.default

  "LogZIO.withCustomContext" should {
    "provide context" in {
      val testSink = withTestSink {
        for {
          _ <- log.info("Hello")
          _ <- LogZIO.withCustomContext("kek" -> "cheburek") {
            ZIO.effect(1 + 1) <* log.info("It's me")
          }
        } yield ()
      }
      val messages = testSink.fetch()
      assert(messages.size == 2)
      val Seq(first, second) = messages
      assert(first.context.customContext.values.isEmpty)
      assert(
        second.context.customContext.values == List(
          LogArg(
            value = Seq("kek"),
            t = "cheburek",
            hiddenName = false,
            codec = Some(LogstageCodec.LogstageCodecString),
          )
        )
      )
    }

    "clean context on scope exit" in {
      val testSink = withTestSink {
        for {
          _ <- LogZIO.withCustomContext("kek" -> "cheburek") {
            ZIO.effect(1 + 1) <* log.info("Hello")
          }
          _ <- log.info("Bye")
        } yield ()
      }

      val messages = testSink.fetch()
      assert(messages.size == 2)
      val Seq(first, second) = messages
      assert(
        first.context.customContext.values == List(
          LogArg(
            value = Seq("kek"),
            t = "cheburek",
            hiddenName = false,
            codec = Some(LogstageCodec.LogstageCodecString),
          )
        )
      )
      assert(second.context.customContext.values.isEmpty)
    }

    "nest context in multiple scopes" in {
      val testSink = withTestSink {
        LogZIO.withCustomContext("correlation_id" -> "kek") {
          log.info("service layer") *>
          ZIO.effect(1 + 2).flatMap {
            entityId =>
              LogZIO
                .withCustomContext("entity_id" -> entityId) {
                  log.info("DAO layer")
                }.as(entityId) <* log.info("service layer 2")
          }
        }
      }

      val messages = testSink.fetch()
      assert(messages.size == 3)
      val Seq(serviceLater, daoLayer, serviceLayer2) = messages
      assert(
        serviceLater.context.customContext.values == List(
          LogArg(
            value = Seq("correlation_id"),
            t = "kek",
            hiddenName = false,
            codec = Some(LogstageCodec.LogstageCodecString),
          )
        )
      )
      assert(
        daoLayer.context.customContext.values == List(
          LogArg(
            value = Seq("correlation_id"),
            t = "kek",
            hiddenName = false,
            codec = Some(LogstageCodec.LogstageCodecString),
          ),
          LogArg(
            value = Seq("entity_id"),
            t = 3,
            hiddenName = false,
            codec = Some(LogstageCodec.LogstageCodecInt),
          ),
        )
      )
      assert(
        serviceLayer2.context.customContext.values == List(
          LogArg(
            value = Seq("correlation_id"),
            t = "kek",
            hiddenName = false,
            codec = Some(LogstageCodec.LogstageCodecString),
          )
        )
      )
    }
  }

  private def withTestSink[U](thunk: RIO[LogZIO, U]): TestSink = {
    val sink = new TestSink
    val logger = LogIO.fromLogger[UIO](
      IzLogger(Log.Level.Info, List(sink))
    )
    runtime.unsafeRun {
      thunk.provide(Has(logger))
    }
    sink
  }
}
