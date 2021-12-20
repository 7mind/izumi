package izumi.distage.injector

import izumi.distage.X

object WithFilter extends App {

  for {
    (Some(a: String), b, c) <- X[(Any, Int, Int)]()
  } yield ()

}
