package izumi.distage.effect

import izumi.distage.modules.support.{CatsIOSupportModule, IdentitySupportModule, MonixBIOSupportModule, ZIOSupportModule}

package object modules {
  @deprecated("Redundant inclusion. Now included by default when using distage-framework/testkit or any of Injector.produce*/plan methods", "0.11")
  type IdentityQuasiEffectModule = IdentitySupportModule
  @deprecated("Redundant inclusion. Now included by default when using distage-framework/testkit or any of Injector.produce*/plan methods", "0.11")
  lazy val IdentityQuasiEffectModule: IdentitySupportModule.type = IdentitySupportModule

  @deprecated("Redundant inclusion. Now included by default when using distage-framework/testkit or any of Injector.produce*/plan methods", "0.11")
  type CatsQuasiEffectModule = CatsIOSupportModule
  @deprecated("Redundant inclusion. Now included by default when using distage-framework/testkit or any of Injector.produce*/plan methods", "0.11")
  lazy val CatsQuasiEffectModule: CatsIOSupportModule.type = CatsIOSupportModule

  @deprecated("Redundant inclusion. Now included by default when using distage-framework/testkit or any of Injector.produce*/plan methods", "0.11")
  type MonixQuasiEffectModule = MonixBIOSupportModule
  @deprecated("Redundant inclusion. Now included by default when using distage-framework/testkit or any of Injector.produce*/plan methods", "0.11")
  lazy val MonixQuasiEffectModule: MonixBIOSupportModule.type = MonixBIOSupportModule

  @deprecated("Redundant inclusion. Now included by default when using distage-framework/testkit or any of Injector.produce*/plan methods", "0.11")
  type ZIOQuasiEffectModule = ZIOSupportModule
  @deprecated("Redundant inclusion. Now included by default when using distage-framework/testkit or any of Injector.produce*/plan methods", "0.11")
  lazy val ZIOQuasiEffectModule: ZIOSupportModule.type = ZIOSupportModule
}
