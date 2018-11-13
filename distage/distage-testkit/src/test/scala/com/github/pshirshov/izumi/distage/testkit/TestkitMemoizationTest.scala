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
        assert(MemoizingResourceCollection.memoizedInstances.size() == 7)
        assert(MemoizingResourceCollection.memoizedInstances.values().contains(ctx))
        assert(ctx.initCounter.closedCloseables.isEmpty)
    }

    "retrieve memoized values" in di {
      ctx: Ctx =>
        assert(r.get().get() == ctx)
        assert(ctx.initCounter.startedRoleComponents.size == 3)
        assert(MemoizingResourceCollection.memoizedInstances.size() == 7)
        assert(MemoizingResourceCollection.memoizedInstances.values().contains(ctx))
        assert(ctx.initCounter.closedCloseables.isEmpty)
    }
  }

  override protected val resourceCollection: ResourceCollection = new MemoizingResourceCollection {
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
