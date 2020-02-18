package izumi.distage.config.extractor

import izumi.distage.model.definition.BootstrapModuleDef
import izumi.distage.model.planning.PlanningHook

class ConfigPathExtractorModule extends BootstrapModuleDef {
  many[PlanningHook]
    .add[ConfigPathExtractor]
}
