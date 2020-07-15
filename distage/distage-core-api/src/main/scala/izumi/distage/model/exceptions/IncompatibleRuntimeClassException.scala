package izumi.distage.model.exceptions

import izumi.distage.model.reflection.DIKey

class IncompatibleRuntimeClassException(
  val expected: DIKey,
  val got: Class[_],
  val clue: String,
) extends DIException(
    s"Instance of type `$got` supposed to be assigned to incompatible key $expected. Context: $clue"
  )
