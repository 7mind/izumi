package izumi.distage.model.exceptions.interpretation

import izumi.distage.model.exceptions.DIException
import izumi.distage.model.reflection.DIKey

@deprecated("Needs to be removed", "20/10/2021")
class IncompatibleRuntimeClassException(
  val expected: DIKey,
  val got: Class[?],
  val clue: String,
) extends DIException(
    s"Instance of type `$got` supposed to be assigned to incompatible key $expected. Context: $clue"
  )
