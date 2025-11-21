package izumi.fundamentals.platform.time

import izumi.fundamentals.platform.IzPlatformSyntax

import java.time.*
import java.util.Date

trait IzTimeOrderingSafe extends IzPlatformSyntax {
  implicit val offsetDateTimeOrdering: Ordering[OffsetDateTime] = Ordering.fromLessThan(_ `isBefore` _)

  implicit val localDateTimeOrdering: Ordering[LocalDateTime] = Ordering.fromLessThan(_ `isBefore` _)

  implicit val instantDateTimeOrdering: Ordering[Instant] = Ordering.fromLessThan(_ `isBefore` _)

  implicit val dateOrdering: Ordering[Date] = Ordering.fromLessThan(_ `before` _)

  implicit val localDateOrdering: Ordering[LocalDate] = Ordering.fromLessThan(_ `isBefore` _)

  implicit val localTimeOrdering: Ordering[LocalTime] = Ordering.fromLessThan(_ `isBefore` _)

  implicit val offsetTimeOrdering: Ordering[OffsetTime] = Ordering.fromLessThan(_ `isBefore` _)
}

trait IzTimeOrdering extends IzTimeOrderingSafe {
  implicit val zonedDateTimeOrdering: Ordering[ZonedDateTime] = Ordering.fromLessThan(_ `isBefore` _)
}
