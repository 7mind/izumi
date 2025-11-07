package izumi.functional.quasi

import izumi.fundamentals.platform.functional.Identity

private[izumi] object __QuasiAsyncPlatformSpecific {

  private[izumi] def quasiAsyncIdentity: QuasiAsync[Identity] = {
    new QuasiAsync[Identity] {
      override def async[A](effect: (Either[Throwable, A] => Unit) => Unit): Identity[A] = {
        var res: Either[Throwable, A] = null
        effect(res = _)
        res.fold(throw _, identity)
      }

      override def parTraverse_[A](l: IterableOnce[A])(f: A => Unit): Unit = {
        l.iterator.foreach(f)
      }

      override def parTraverse[A, B](l: IterableOnce[A])(f: A => Identity[B]): Identity[List[B]] = {
        l.iterator.map(f).toList
      }

      override def parTraverseN[A, B](n: Int)(l: IterableOnce[A])(f: A => Identity[B]): Identity[List[B]] = {
        parTraverse(l)(f)
      }

      override def parTraverseN_[A](n: Int)(l: IterableOnce[A])(f: A => Identity[Unit]): Identity[Unit] = {
        parTraverse_(l)(f)
      }
    }
  }

}
