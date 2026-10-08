package izumi.distage.plugins

import distage.{Injector, ModuleDef, Roots}
import izumi.distage.plugins.load.PluginLoaderDefaultImpl.RuntimePluginScanningNotSupportedOnScalaNative
import izumi.distage.plugins.load.{PluginLoader, PluginLoaderDefaultImpl, PluginPackageCache}
import izumi.distage.testkit.runner.spec.AnyWordSpec

final class NativeExplicitPlugin extends PluginDef {
  make[String].fromValue("plugin")
}

final class NativePluginLoaderTest extends AnyWordSpec {
  "Native plugin loading" should {
    "accept empty and disabled-only package configurations" in {
      val loader = PluginLoader()
      assert(loader.load(PluginConfig.empty).result.isEmpty)
      assert(loader.load(PluginConfig.empty.disablePackage("unused.plugins")).result.isEmpty)
    }

    "provision explicit plugins and merges with override precedence" in {
      val extra = new ModuleDef {
        make[Int].fromValue(42)
      }
      val replacement = new ModuleDef {
        make[String].fromValue("override")
      }
      val config = (PluginConfig.const(new NativeExplicitPlugin) ++ extra).overriddenBy(replacement)
      val module = PluginLoader().load(config).result.merge

      Injector().produce(module, Roots.Everything).use {
        objects =>
          assert(objects.get[String] == "override")
          assert(objects.get[Int] == 42)
      }
    }

    "allow a supplied loader to provide explicit plugins" in {
      val loader = PluginLoader.const(new NativeExplicitPlugin)
      val module = loader.load(PluginConfig.packages("unused.plugins")).result.merge

      Injector().produce(module, Roots.Everything).use {
        objects => assert(objects.get[String] == "plugin")
      }
    }

    "reject enabled runtime packages through default and cache-aware factories" in {
      val packages = Seq("example.plugins", "other.plugins")
      val config = PluginConfig.packages(packages).disablePackage("example.plugins")
      val loaders = Seq(PluginLoader(), PluginLoaderDefaultImpl.withPackageCache(new PluginPackageCache.Impl))

      loaders.foreach {
        loader =>
          val error = intercept[RuntimePluginScanningNotSupportedOnScalaNative](loader.load(config))
          assert(error.packagesEnabled == packages)
          assert(error.getMessage.contains("Scala Native"))
          assert(packages.forall(error.getMessage.contains))
      }
    }
  }
}
