package izumi.idealingua.il.loader


import izumi.idealingua.model.loader.UnresolvedDomains


class ModelLoaderImpl(
                        enumerator: FilesystemEnumerator,
                        parser: ModelParser,
                        modelExt:String,
                        domainExt: String,
                        overlayExt: String,
                      ) extends ModelLoader {
  def load(): UnresolvedDomains = {
    val files = enumerator.enumerate()
    val domainFiles = files.filter(_._1.name.endsWith(domainExt))
    val modelFiles = files.filter(_._1.name.endsWith(modelExt))
    val modelOverlayFiles = files.filter(_._1.name.endsWith(overlayExt)).map {
      case (k, v) =>
        k.rename(n => n.replace(overlayExt, "")).move(p => ModelLoader.overlayVirtualDir +: p) -> v
    }

    val domains = parser.parseDomains(domainFiles)
    val models = parser.parseModels(modelFiles)
    val overlays = parser.parseModels(modelOverlayFiles)

    UnresolvedDomains(domains, models, overlays)
  }
}

