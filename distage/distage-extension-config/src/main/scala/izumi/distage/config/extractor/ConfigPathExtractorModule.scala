package izumi.distage.config.extractor

import distage.BootstrapModuleDef
import izumi.distage.model.planning.PlanningHook

class ConfigPathExtractorModule extends BootstrapModuleDef {
  many[PlanningHook]
    .add[ConfigPathExtractor]
}
