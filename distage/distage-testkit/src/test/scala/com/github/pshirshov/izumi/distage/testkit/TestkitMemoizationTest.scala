package com.github.pshirshov.izumi.distage.testkit

import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference

import com.github.pshirshov.izumi.distage.model.references.IdentifiedRef
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.fundamentals.tags.TagExpr


class TestkitMemoizationTest extends DistagePluginSpec {
  override protected def disabledTags: TagExpr.Strings.Expr = TagExpr.Strings.False

  val r = new AtomicReference[WeakReference[TestService1]](null)

  "testkit" must {
    "memoize values" in di {
      service: TestService1 =>
        assert(r.get().get() == service)
        assert(MemoizingDistageResourceCollection.memoizedInstances.size() == 1)
        assert(MemoizingDistageResourceCollection.memoizedInstances.values().contains(service))
    }

    "retrieve memoized values" in di {
      service: TestService1 =>
        assert(r.get().get() == service)
        assert(MemoizingDistageResourceCollection.memoizedInstances.size() == 1)
        assert(MemoizingDistageResourceCollection.memoizedInstances.values().contains(service))
    }

  }

  override protected val resourceCollection: DistageResourceCollection = new MemoizingDistageResourceCollection {
    override def memoize(ref: IdentifiedRef): Boolean = {
      if (ref.key == DIKey.get[TestService1]) {
        r.set(new WeakReference(ref.value.asInstanceOf[TestService1]))
        true
      } else {
        false
      }
    }
  }
}
