package izumi.fundamentals.platform.resources

final case class IzArtifactMaterializer(get: IzArtifact) extends AnyVal

object IzArtifactMaterializer {
  @inline def currentArtifact(implicit ev: IzArtifactMaterializer): IzArtifact = ev.get

  implicit def materialize: IzArtifactMaterializer = IzArtifactMaterializer(IzArtifact.undefined)
}
