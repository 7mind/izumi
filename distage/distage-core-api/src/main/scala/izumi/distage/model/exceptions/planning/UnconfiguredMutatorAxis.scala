package izumi.distage.model.exceptions.planning

import izumi.distage.model.exceptions.DIException
import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.platform.language.SourceFilePosition

@deprecated("Needs to be removed", "20/10/2021")
case class UnconfiguredMutatorAxis(mutator: DIKey, pos: SourceFilePosition, unconfigured: Set[String])

@deprecated("Needs to be removed", "20/10/2021")
class BadMutatorAxis(message: String, val problems: List[UnconfiguredMutatorAxis]) extends DIException(message)
