package izumi.fundamentals.platform.resources

final case class IzArtifactMaterializer(get: IzArtifact) extends AnyVal

object IzArtifactMaterializer {
  @inline def currentArtifact(implicit ev: IzArtifactMaterializer): IzArtifact = ev.get

  // FIXME: Scala 3 implement IzArtifactMaterializer ???
  implicit def materialize: IzArtifactMaterializer = IzArtifactMaterializer(IzArtifact.undefined)
}
