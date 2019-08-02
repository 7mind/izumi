package com.github.pshirshov.izumi.fundamentals.platform.time

import java.time._
import java.util.Date

trait IzTimeOrderingSafe {
  implicit def offsetDateTimeOrdering: Ordering[OffsetDateTime] = Ordering.fromLessThan(_ isBefore _)

  implicit def localDateTimeOrdering: Ordering[LocalDateTime] = Ordering.fromLessThan(_ isBefore _)

  implicit def instantDateTimeOrdering: Ordering[Instant] = Ordering.fromLessThan(_ isBefore _)

  implicit def dateOrdering: Ordering[Date] = Ordering.fromLessThan(_ before _)

  implicit def localDateOrdering: Ordering[LocalDate] = Ordering.fromLessThan(_ isBefore _)

  implicit def localTimeOrdering: Ordering[LocalTime] = Ordering.fromLessThan(_ isBefore _)

  implicit def offsetTimeOrdering: Ordering[OffsetTime] = Ordering.fromLessThan(_ isBefore _)

}

trait IzTimeOrdering extends IzTimeOrderingSafe {
  implicit def zonedDateTimeOrdering: Ordering[ZonedDateTime] = Ordering.fromLessThan(_ isBefore _)

}
