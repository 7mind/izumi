package izumi.distage.effect.modules

import distage.ModuleDef
import izumi.distage.model.effect.DIEffectRunner
import monix.bio

object MonixDIEffectModule extends MonixDIEffectModule

trait MonixDIEffectModule extends ModuleDef {
  addImplicit[DIEffectRunner[bio.Task]]
}
