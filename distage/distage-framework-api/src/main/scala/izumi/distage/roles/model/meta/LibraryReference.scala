package izumi.distage.roles.model.meta

import izumi.fundamentals.platform.resources.IzArtifact

final case class LibraryReference(libraryName: String, artifact: Option[IzArtifact])
