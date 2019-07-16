package com.github.pshirshov.izumi.distage.testkit.distagesuite.fixtures

import cats.effect.IO
import com.github.pshirshov.izumi.distage.monadic.modules.{CatsDIEffectModule, ZioDIEffectModule}
import com.github.pshirshov.izumi.distage.plugins.PluginDef
import com.github.pshirshov.izumi.distage.roles.model.IntegrationCheck
import com.github.pshirshov.izumi.distage.testkit.TODOMemoizeMe
import com.github.pshirshov.izumi.fundamentals.platform.integration.ResourceCheck
import distage.TagK

class MockPostgresCheck[F[_]]() extends IntegrationCheck {
  override def resourcesAvailable(): ResourceCheck = ResourceCheck.Success()
}

class MockPostgresDriver[F[_]](val check: MockPostgresCheck[F]) extends TODOMemoizeMe

class MockUserRepository[F[_]](val pg: MockPostgresDriver[F])

class MockRedis[F[_]]() extends TODOMemoizeMe

class MockCache[F[_]](val redis: MockRedis[F]) extends IntegrationCheck {
  override def resourcesAvailable(): ResourceCheck = ResourceCheck.Success()
}

class ApplePaymentProvider[F[_]]() extends IntegrationCheck {
  override def resourcesAvailable(): ResourceCheck = ResourceCheck.ResourceUnavailable("Test", None)
}


class MockCachedUserService[F[_]](val users: MockUserRepository[F], val cache: MockCache[F])

object MonadPlugin extends PluginDef
  with CatsDIEffectModule
  with ZioDIEffectModule

abstract class MockAppPlugin[F[_] : TagK]
  extends PluginDef {
  make[MockPostgresDriver[F]]
  make[MockUserRepository[F]]
  make[MockPostgresCheck[F]]
  make[MockRedis[F]]
  make[MockCache[F]]
  make[MockCachedUserService[F]]
  make[ApplePaymentProvider[F]]
}

object MockAppCatsIOPlugin extends MockAppPlugin[IO]
