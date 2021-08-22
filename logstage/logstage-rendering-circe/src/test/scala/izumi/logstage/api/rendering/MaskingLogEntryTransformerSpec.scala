package izumi.logstage.api.rendering

import io.circe.Encoder
import izumi.logstage.api.IzLogger
import izumi.logstage.sink.ConsoleSink
import logstage.circe.{LogstageCirceCodec, LogstageCirceJsonCodec, LogstageCirceRenderingPolicy}
import org.scalatest.wordspec.AnyWordSpec
import io.circe.derivation.*
import izumi.logstage.api.rendering.MaskingLogEntryTransformerSpec.CardInfo

object MaskingLogEntryTransformerSpec {
  case class CardInfo(number: String, expMonth: Int, expYear: Int)
  object CardInfo {
    implicit val encoder: Encoder[CardInfo] = deriveEncoder[CardInfo]
    implicit val logstageCodec: LogstageCodec[CardInfo] = LogstageCirceCodec.derived[CardInfo]
  }
}

class MaskingLogEntryTransformerSpec extends AnyWordSpec {
  "MaskingLogEntryTransformer" should {
    "mask log entries with console sink" in {

      val logEntryTransformer = new MaskingLogEntryTransformer(Map("receiverCard" -> cardNumberMasking, "sender_card" -> cardNumberMasking))
      val renderingPolicy = new TransformerChainRenderingPolicy(
        List(logEntryTransformer),
        RenderingPolicy.simplePolicy(),
      )
      val logger = IzLogger(sink = ConsoleSink(renderingPolicy))
      val card = "4242424242424242"
      logger("sender_card" -> card).info(s"Transfer ${card -> "receiverCard"}")
    }
  }

  "mask log entries with json sink" in {
    val logEntryTransformer = new MaskingLogEntryTransformer(Map("receiverCard" -> cardNumberMasking, "sender_card" -> cardNumberMasking))
    val renderingPolicy = new TransformerChainRenderingPolicy(
      List(logEntryTransformer),
      new LogstageCirceRenderingPolicy,
    )
    val logger = IzLogger(sink = ConsoleSink(renderingPolicy))
    val card = "4242424242424242"
    logger("sender_card" -> card).info(s"Transfer ${card -> "receiverCard"}")
  }

  "mask json rendered log entries" in {
    val logEntryTransformer = new MaskingLogEntryTransformer(Map("sender_card" -> cardInfoMasking, "card" -> cardInfoMasking))
    val renderingPolicy = new TransformerChainRenderingPolicy(
      List(logEntryTransformer),
      new LogstageCirceRenderingPolicy,
    )

    val logger = IzLogger(sink = ConsoleSink(renderingPolicy))
    val card = CardInfo("4242424242424242", 10, 22)

    logger("sender_card" -> card).info(s"Transfer $card")
  }

  private def cardInfoMasking =
    MaskingLogEntryTransformer.logMasking[CardInfo]((card, _) => card.copy(number = maskCardNumber(card.number)))

  private def cardNumberMasking =
    MaskingLogEntryTransformer.logMasking[String]((number, _) => maskCardNumber(number))

  private def maskCardNumber(number: String): String = {
    if (number.length <= 6) {
      number
    } else {
      val maskLength = (number.length - 10).max(0)
      val numberFromEnd = maskLength + 6
      number.substring(0, 6) + ("*" * maskLength) + number.substring(numberFromEnd)
    }
  }
}
