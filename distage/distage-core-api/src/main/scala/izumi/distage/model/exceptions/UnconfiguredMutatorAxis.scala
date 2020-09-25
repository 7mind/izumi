package izumi.distage.model.exceptions

import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.platform.language.SourceFilePosition

case class UnconfiguredMutatorAxis(mutator: DIKey, pos: SourceFilePosition, unconfigured: Set[String])

class BadMutatorAxis(message: String, val problems: List[UnconfiguredMutatorAxis]) extends DIException(message)
