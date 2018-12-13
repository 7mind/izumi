package com.github.pshirshov.izumi.idealingua.il.loader


import com.github.pshirshov.izumi.idealingua.model.loader.UnresolvedDomains


class ModelLoaderImpl(
                        enumerator: FilesystemEnumerator,
                        parser: ModelParser,
                        modelExt:String,
                        domainExt: String,
                      ) extends ModelLoader {
  def load(): UnresolvedDomains = {
    val files = enumerator.enumerate()
    val domainFiles = files.filter(_._1.name.endsWith(domainExt))
    val modelFiles = files.filter(_._1.name.endsWith(modelExt))
    val domains = parser.parseDomains(domainFiles)
    val models = parser.parseModels(modelFiles)
    UnresolvedDomains(domains, models)
  }
}






