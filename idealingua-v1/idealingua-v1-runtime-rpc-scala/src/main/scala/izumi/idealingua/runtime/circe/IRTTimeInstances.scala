package izumi.idealingua.runtime.circe

import java.time.format.{DateTimeFormatter, DateTimeParseException}
import java.time._

import izumi.fundamentals.platform.time.IzTime
import io.circe.{Decoder, DecodingFailure, Encoder, Json}

object IRTTimeInstances extends IRTTimeInstances

trait IRTTimeInstances {
  import izumi.fundamentals.platform.time.IzTime._
  implicit final val decodeInstant: Decoder[Instant] =
    Decoder.instance { c =>
      c.as[String] match {
        case Right(s) => try Right(Instant.parse(s)) catch {
          case _: DateTimeParseException => Left(DecodingFailure("Instant", c.history))
        }
        case l @ Left(_) => l.asInstanceOf[Decoder.Result[Instant]]
      }
    }

  implicit final val encodeInstant: Encoder[Instant] = Encoder.instance(time => Json.fromString(time.toString))

  implicit final val decodeZoneId: Decoder[ZoneId] =
    Decoder.instance{ c =>
      c.as[String] match {
        case Right(s) =>
          try Right(ZoneId.of(s)) catch {
            case _: DateTimeException => Left(DecodingFailure("ZoneId", c.history))
          }
        case l @ Left(_) => l.asInstanceOf[Decoder.Result[ZoneId]]
      }
    }

  implicit final val encodeZoneId: Encoder[ZoneId] =
    Encoder[String].contramap(_.getId)

  final def decodeLocalDateTime(formatter: DateTimeFormatter): Decoder[LocalDateTime] =
    Decoder.instance { c =>
      c.as[String] match {
        case Right(s) => try Right(LocalDateTime.parse(s, formatter)) catch {
          case _: DateTimeParseException => Left(DecodingFailure("LocalDateTime", c.history))
        }
        case l @ Left(_) => l.asInstanceOf[Decoder.Result[LocalDateTime]]
      }
    }

  final def encodeLocalDateTime(formatter: DateTimeFormatter): Encoder[LocalDateTime] =
    Encoder.instance(time => Json.fromString(time.format(formatter)))

  implicit final val decodeLocalDateTimeDefault: Decoder[LocalDateTime] = decodeLocalDateTime(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
  implicit final val encodeLocalDateTimeDefault: Encoder[LocalDateTime] = encodeLocalDateTime(ISO_LOCAL_DATE_TIME_3NANO)

  final def decodeZonedDateTime(formatter: DateTimeFormatter): Decoder[ZonedDateTime] =
    Decoder.instance { c =>
      c.as[String] match {
        case Right(s) => try {
          val parsed = ZonedDateTime.parse(s, formatter)
          if (parsed.getZone.getId == "Z") {
            Right(parsed.withZoneSameLocal(IzTime.TZ_UTC))
          } else {
            Right(parsed)
          }
        } catch {
          case _: DateTimeParseException => Left(DecodingFailure("ZonedDateTime", c.history))
        }
        case l @ Left(_) => l.asInstanceOf[Decoder.Result[ZonedDateTime]]
      }
    }

  // TODO: this is a temporary solution!
  final def encodeZonedDateTime(formatter: DateTimeFormatter): Encoder[ZonedDateTime] =
    Encoder.instance(time => Json.fromString(time.withZoneSameInstant(IzTime.TZ_UTC).toOffsetDateTime.format(formatter)))

  implicit final val decodeZonedDateTimeDefault: Decoder[ZonedDateTime] = decodeZonedDateTime(DateTimeFormatter.ISO_ZONED_DATE_TIME)
  implicit final val encodeZonedDateTimeDefault: Encoder[ZonedDateTime] = encodeZonedDateTime(ISO_ZONED_DATE_TIME_3NANO)

  final def decodeOffsetDateTime(formatter: DateTimeFormatter): Decoder[OffsetDateTime] =
    Decoder.instance { c =>
      c.as[String] match {
        case Right(s) => try Right(OffsetDateTime.parse(s, formatter)) catch {
          case _: DateTimeParseException => Left(DecodingFailure("OffsetDateTime", c.history))
        }
        case l @ Left(_) => l.asInstanceOf[Decoder.Result[OffsetDateTime]]
      }
    }

  final def encodeOffsetDateTime(formatter: DateTimeFormatter): Encoder[OffsetDateTime] =
    Encoder.instance(time => Json.fromString(time.format(formatter)))

  implicit final val decodeOffsetDateTimeDefault: Decoder[OffsetDateTime] = decodeOffsetDateTime(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
  implicit final val encodeOffsetDateTimeDefault: Encoder[OffsetDateTime] = encodeOffsetDateTime(ISO_OFFSET_DATE_TIME_3NANO)

  final def decodeLocalDate(formatter: DateTimeFormatter): Decoder[LocalDate] =
    Decoder.instance { c =>
      c.as[String] match {
        case Right(s) => try Right(LocalDate.parse(s, formatter)) catch {
          case _: DateTimeParseException => Left(DecodingFailure("LocalDate", c.history))
        }
        case l @ Left(_) => l.asInstanceOf[Decoder.Result[LocalDate]]
      }
    }

  final def encodeLocalDate(formatter: DateTimeFormatter): Encoder[LocalDate] =
    Encoder.instance(time => Json.fromString(time.format(formatter)))

  implicit final val decodeLocalDateDefault: Decoder[LocalDate] = decodeLocalDate(DateTimeFormatter.ISO_LOCAL_DATE)
  implicit final val encodeLocalDateDefault: Encoder[LocalDate] = encodeLocalDate(ISO_LOCAL_DATE)

  final def decodeLocalTime(formatter: DateTimeFormatter): Decoder[LocalTime] =
    Decoder.instance { c =>
      c.as[String] match {
        case Right(s) => try Right(LocalTime.parse(s, formatter)) catch {
          case _: DateTimeParseException => Left(DecodingFailure("LocalTime", c.history))
        }
        case l @ Left(_) => l.asInstanceOf[Decoder.Result[LocalTime]]
      }
    }

  final def encodeLocalTime(formatter: DateTimeFormatter): Encoder[LocalTime] =
    Encoder.instance(time => Json.fromString(time.format(formatter)))

  implicit final val decodeLocalTimeDefault: Decoder[LocalTime] = decodeLocalTime(DateTimeFormatter.ISO_LOCAL_TIME)
  implicit final val encodeLocalTimeDefault: Encoder[LocalTime] = encodeLocalTime(ISO_LOCAL_TIME_3NANO)

  final def decodeOffsetTime(formatter: DateTimeFormatter): Decoder[OffsetTime] =
    Decoder.instance { c =>
      c.as[String] match {
        case Right(s) => try Right(OffsetTime.parse(s, formatter)) catch {
          case _: DateTimeParseException => Left(DecodingFailure("OffsetTime", c.history))
        }
        case l @ Left(_) => l.asInstanceOf[Decoder.Result[OffsetTime]]
      }
    }

  final def encodeOffsetTime(formatter: DateTimeFormatter): Encoder[OffsetTime] =
    Encoder.instance(time => Json.fromString(time.format(formatter)))

  implicit final val decodeOffsetTimeDefault: Decoder[OffsetTime] = decodeOffsetTime(DateTimeFormatter.ISO_OFFSET_TIME)
  implicit final val encodeOffsetTimeDefault: Encoder[OffsetTime] = encodeOffsetTime(ISO_OFFSET_TIME_3NANO)

  implicit final val decodePeriod: Decoder[Period] = Decoder.instance { c =>
    c.as[String] match {
      case Right(s) => try Right(Period.parse(s)) catch {
        case _: DateTimeParseException => Left(DecodingFailure("Period", c.history))
      }
      case l@Left(_) => l.asInstanceOf[Decoder.Result[Period]]
    }
  }

  implicit final val encodePeriod: Encoder[Period] = Encoder.instance { period =>
    Json.fromString(period.toString)
  }

  final def decodeYearMonth(formatter: DateTimeFormatter): Decoder[YearMonth] =
    Decoder.instance { c =>
      c.as[String] match {
        case Right(s) =>
          try Right(YearMonth.parse(s, formatter))
          catch {
            case _: DateTimeParseException => Left(DecodingFailure("YearMonth", c.history))
          }
        case l @ Left(_) => l.asInstanceOf[Decoder.Result[YearMonth]]
      }
    }

  final def encodeYearMonth(formatter: DateTimeFormatter): Encoder[YearMonth] =
    Encoder.instance(time => Json.fromString(time.format(formatter)))

  private final val yearMonthFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

  implicit final val decodeYearMonthDefault: Decoder[YearMonth] = decodeYearMonth(yearMonthFormatter)
  implicit final val encodeYearMonthDefault: Encoder[YearMonth] = encodeYearMonth(yearMonthFormatter)

  implicit final val decodeDuration: Decoder[Duration] =
    Decoder.instance { c =>
      c.as[String] match {
        case Right(s) => try Right(Duration.parse(s)) catch {
          case _: DateTimeParseException => Left(DecodingFailure("Duration", c.history))
        }
        case l @ Left(_) => l.asInstanceOf[Decoder.Result[Duration]]
      }
    }

  implicit final val encodeDuration: Encoder[Duration] =
    Encoder.instance(duration => Json.fromString(duration.toString))
}
