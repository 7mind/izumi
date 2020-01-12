package izumi.distage.injector

import izumi.distage.fixtures.InnerClassCases.InnerClassUnstablePathsCase
import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.exceptions.ProvisioningException
import izumi.distage.provisioning.strategies.cglib.exceptions.CgLibInstantiationOpException
import net.sf.cglib.core.CodeGenerationException
import org.scalatest.WordSpec

class CglibProxiesTest extends WordSpec with MkInjector {

  "progression test: cglib proxies can't resolve circular path-dependent dependencies (we don't take prefix type into account when calling constructor for generated lambdas and end up choosing the wrong constructor...)" in {
    // the value prefix probably has to be stored inside the Provider to fix this
    val exc = intercept[ProvisioningException] {
      import InnerClassUnstablePathsCase._
      val testProviderModule = new TestModule

      val definition = PlannerInput.noGc(new ModuleDef {
//        make[testProviderModule.type].from[testProviderModule.type](testProviderModule: testProviderModule.type)
        make[testProviderModule.Circular1]
        make[testProviderModule.Circular2]
      })

      val context = mkInjector().produceUnsafe(definition)

      assert(context.get[testProviderModule.TestFactory].mk(testProviderModule.TestDependency()) == testProviderModule.TestClass(testProviderModule.TestDependency()))
    }
    assert(exc.getSuppressed.head.isInstanceOf[CgLibInstantiationOpException])
    assert(exc.getSuppressed.head.getCause.isInstanceOf[CodeGenerationException])
    assert(exc.getSuppressed.head.getCause.getCause.isInstanceOf[NoSuchMethodException])
  }

}
