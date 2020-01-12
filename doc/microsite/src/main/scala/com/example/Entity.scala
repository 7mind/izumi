package com.example

import java.util.UUID

import io.circe.Encoder

final case class Entity(id: UUID)
object Entity {
  implicit val enc: Encoder.AsObject[Entity] = io.circe.derivation.deriveEncoder[Entity]
}
