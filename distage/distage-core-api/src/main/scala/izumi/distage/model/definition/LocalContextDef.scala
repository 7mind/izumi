package izumi.distage.model.definition

import izumi.distage.model.providers.Functoid

/**
  * Can be obtained through [[ModuleBase.running]] implicit extension
  */
case class LocalContextDef[R](module: ModuleBase, function: Functoid[R])
