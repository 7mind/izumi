package izumi.distage.model.definition.errors

import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.platform.language.SourceFilePosition

final case class UnconfiguredMutatorAxis(mutator: DIKey, pos: SourceFilePosition, unconfigured: Set[String])


