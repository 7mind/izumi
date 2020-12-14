package izumi.distage.effect

import izumi.distage.modules.support.{CatsIOSupportModule, IdentitySupportModule, MonixBIOSupportModule, ZIOSupportModule}

package object modules {
  @deprecated("Redundant inclusion. Now included by `distage.DefaultModule[F]` when using distage-framework/testkit or any of Injector.produce*/plan methods", "1.0")
  type IdentityDIEffectModule = IdentitySupportModule
  @deprecated("Redundant inclusion. Now included by `distage.DefaultModule[F]` when using distage-framework/testkit or any of Injector.produce*/plan methods", "1.0")
  lazy val IdentityDIEffectModule: IdentitySupportModule.type = IdentitySupportModule

  @deprecated("Redundant inclusion. Now included by `distage.DefaultModule[F]` when using distage-framework/testkit or any of Injector.produce*/plan methods", "1.0")
  type CatsDIEffectModule = CatsIOSupportModule
  @deprecated("Redundant inclusion. Now included by `distage.DefaultModule[F]` when using distage-framework/testkit or any of Injector.produce*/plan methods", "1.0")
  lazy val CatsDIEffectModule: CatsIOSupportModule.type = CatsIOSupportModule

  @deprecated("Redundant inclusion. Now included by `distage.DefaultModule[F]` when using distage-framework/testkit or any of Injector.produce*/plan methods", "1.0")
  type MonixDIEffectModule = MonixBIOSupportModule
  @deprecated("Redundant inclusion. Now included by `distage.DefaultModule[F]` when using distage-framework/testkit or any of Injector.produce*/plan methods", "1.0")
  lazy val MonixDIEffectModule: MonixBIOSupportModule.type = MonixBIOSupportModule

  @deprecated("Redundant inclusion. Now included by `distage.DefaultModule[F]` when using distage-framework/testkit or any of Injector.produce*/plan methods", "1.0")
  type ZIODIEffectModule = ZIOSupportModule
  @deprecated("Redundant inclusion. Now included by `distage.DefaultModule[F]` when using distage-framework/testkit or any of Injector.produce*/plan methods", "1.0")
  lazy val ZIODIEffectModule: ZIOSupportModule.type = ZIOSupportModule
}
