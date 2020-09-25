package izumi.distage.effect

import izumi.distage.modules.support.{CatsIOSupportModule, IdentitySupportModule, MonixBIOSupportModule, ZIOSupportModule}

package object modules {
  @deprecated("Redundant inclusion. Now included by default when using distage-framework/testkit or any of Injector.produceF/planF methods", "0.11")
  type IdentityDIEffectModule = IdentitySupportModule
  @deprecated("Redundant inclusion. Now included by default when using distage-framework/testkit or any of Injector.produceF/planF methods", "0.11")
  lazy val IdentityDIEffectModule: IdentitySupportModule.type = IdentitySupportModule

  @deprecated("Redundant inclusion. Now included by default when using distage-framework/testkit or any of Injector.produceF/planF methods", "0.11")
  type CatsDIEffectModule = CatsIOSupportModule
  @deprecated("Redundant inclusion. Now included by default when using distage-framework/testkit or any of Injector.produceF/planF methods", "0.11")
  lazy val CatsDIEffectModule: CatsIOSupportModule.type = CatsIOSupportModule

  @deprecated("Redundant inclusion. Now included by default when using distage-framework/testkit or any of Injector.produceF/planF methods", "0.11")
  type MonixDIEffectModule = MonixBIOSupportModule
  @deprecated("Redundant inclusion. Now included by default when using distage-framework/testkit or any of Injector.produceF/planF methods", "0.11")
  lazy val MonixDIEffectModule: MonixBIOSupportModule.type = MonixBIOSupportModule

  @deprecated("Redundant inclusion. Now included by default when using distage-framework/testkit or any of Injector.produceF/planF methods", "0.11")
  type ZIODIEffectModule = ZIOSupportModule
  @deprecated("Redundant inclusion. Now included by default when using distage-framework/testkit or any of Injector.produceF/planF methods", "0.11")
  lazy val ZIODIEffectModule: ZIOSupportModule.type = ZIOSupportModule
}
