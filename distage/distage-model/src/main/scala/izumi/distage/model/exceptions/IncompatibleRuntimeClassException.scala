package izumi.distage.model.exceptions

import izumi.distage.model.reflection.universe.RuntimeDIUniverse

class IncompatibleRuntimeClassException(
  val expected: RuntimeDIUniverse.DIKey,
  val got: Class[_],
  val clue: String,
) extends DIException(
    s"Instance of type `$got` supposed to be assigned to incompatible key $expected. Context: $clue",
    null
  )
