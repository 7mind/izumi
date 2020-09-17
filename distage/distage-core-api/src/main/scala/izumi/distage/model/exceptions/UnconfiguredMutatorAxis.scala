package izumi.distage.model.exceptions

import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.platform.language.SourceFilePosition

class UnconfiguredMutatorAxis(message: String, val mutator: DIKey, val pos: SourceFilePosition) extends DIException(message)
