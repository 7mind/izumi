package com.github.pshirshov.izumi.distage.testkit

import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference

import com.github.pshirshov.izumi.distage.model.definition.BindingTag
import com.github.pshirshov.izumi.distage.model.references.IdentifiedRef
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.distage.testkit.TestkitMemoizationTest.Ctx

object TestkitMemoizationTest {

  case class Ctx(
                  testService1: TestService1,
                  initCounter: InitCounter
                )

}


class TestkitMemoizationTest extends DistagePluginSpec {
  override protected def disabledTags: BindingTag.Expressions.Expr = BindingTag.Expressions.False

  val r = new AtomicReference[WeakReference[Ctx]](null)

  "testkit" must {
    "memoize values" in di {
      ctx: Ctx =>
        assert(r.get().get() == ctx)
        assert(ctx.initCounter.startedRoleComponents.size == 3)
        assert(instanceStore.memoizedInstances.size() == 7)
        assert(instanceStore.memoizedInstances.values().contains(ctx))
        assert(ctx.initCounter.closedCloseables.isEmpty)
    }

    "retrieve memoized values" in di {
      ctx: Ctx =>
        assert(r.get().get() == ctx)
        assert(ctx.initCounter.startedRoleComponents.size == 3)
        assert(instanceStore.memoizedInstances.size() == 7)
        assert(instanceStore.memoizedInstances.values().contains(ctx))
        assert(ctx.initCounter.closedCloseables.isEmpty)
    }
  }

  val instanceStore = new DirtyGlobalTestResourceStoreImpl

  override protected val resourceCollection: DistageResourceCollection = new MemoizingDistageResourceCollection(instanceStore) {
    override def memoize(ref: IdentifiedRef): Boolean = {
      if (ref.key == DIKey.get[Ctx]) {
        r.set(new WeakReference(ref.value.asInstanceOf[Ctx]))
        true
      } else if (ref.key == DIKey.get[TestResource1] ||
        ref.key == DIKey.get[TestResource2] ||
        ref.key == DIKey.get[TestService1] ||
        ref.key == DIKey.get[TestComponent1] ||
        ref.key == DIKey.get[TestComponent2] ||
        ref.key == DIKey.get[TestComponent3]
      ) {
        true
      } else {
        false
      }
    }
  }
}

class TestkitMemoizationOrderTest extends DistagePluginSpec {

  "testkit" must {
    "progression test: can't start memoized and non-memoized components with interdependencies in correct order" in intercept[AssertionError] {
      di {
        _: Ctx => ()
      }
    }
  }

  val instanceStore = new DirtyGlobalTestResourceStoreImpl

  override protected val resourceCollection: DistageResourceCollection = new MemoizingDistageResourceCollection(instanceStore) {
    override def memoize(ref: IdentifiedRef): Boolean = {
      ref.key == DIKey.get[TestComponent1] ||
        ref.key == DIKey.get[TestComponent3]
    }
  }
}
