package com.github.pshirshov.izumi.functional.result

protected[result] sealed trait Result[+R, +P] {
  def flatten[R1, P1 >: P](implicit ev: R <:< Result[R1, P1]): Result[R1, P1]

  def flatMap[R1, P1 >: P](f: R => Result[R1, P1]): Result[R1, P1]

  def map[R1](f: R => R1): Result[R1, P]
}

protected[result]object Result {

  protected[result] abstract case class Success[+R, +P](value: R) extends Result[R, P] {
    protected val context: SafeBase[P]


    override def flatten[R1, P1 >: P](implicit ev: <:<[R, Result[R1, P1]]): Result[R1, P1] = ev.apply(value)

    override def flatMap[R1, P1 >: P](f: R => Result[R1, P1]): Result[R1, P1] = context.fapply(value, f)

    override def map[R1](f: R => R1): Result[R1, P] = context.apply(f(value))
  }

  protected[result] abstract case class Problem[+R, +P](problem: P) extends Result[R, P] {
    protected val context: SafeBase[P]

    override def flatten[R1, P1 >: P](implicit ev: <:<[R, Result[R1, P1]]): Result[R1, P1] = this.asInstanceOf[Problem[R1, P1]]

    override def flatMap[R1, P1 >: P](f: R => Result[R1, P1]): Result[R1, P1] = this.asInstanceOf[Problem[R1, P1]]

    override def map[R1](f: R => R1): Result[R1, P] = this.asInstanceOf[Problem[R1, P]]
  }

  protected[result] abstract case class Failure[+R, +P](value: Throwable) extends Result[R, P] {
    protected val context: SafeBase[P]

    override def flatten[R1, P1 >: P](implicit ev: <:<[R, Result[R1, P1]]): Result[R1, P1] = this.asInstanceOf[Problem[R1, P1]]

    override def flatMap[R1, P1 >: P](f: R => Result[R1, P1]): Result[R1, P1] = this.asInstanceOf[Problem[R1, P1]]

    override def map[R1](f: R => R1): Result[R1, P] = this.asInstanceOf[Failure[R1, P]]
  }

}
