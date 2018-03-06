package com.github.pshirshov.izumi.functional

package object result  {
  object Safe extends SafeBase[Problematic] {
    override protected def pclass: Class[Problematic] = classOf[Problematic]
  }

  type Rs[R] = result.Result[R, Problematic]
  type Sc[R] = result.Result.Success[R, Problematic]
  type Fl[R] = result.Result.Failure[R, Problematic]
  type Pr[R] = result.Result.Problem[R, Problematic]
}
