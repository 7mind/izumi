//package izumi.fundamentals.graphs.demo
//
//import izumi.fundamentals.graphs.GraphFixtures._
//import izumi.fundamentals.graphs.tools.MutationResolver
//import izumi.fundamentals.graphs.tools.MutationResolver.{Annotated, AxisPoint, SemiEdgeSeq}
//import izumi.fundamentals.graphs.ui.GVR
//import izumi.fundamentals.graphs.ui.LocationPolicy.Singleton
//
//
//object ConResVisualizer {
//
//  def main(args: Array[String]): Unit = {
//    show(mutators, Set(AxisPoint("test", "prod")), Some("dag-activation"))
//    show(mutators, Set.empty, Some("dag-noactivation"))
//    show(withLoop, Set(AxisPoint("test", "prod")), Some("dcg-activation"))
//    show(withLoop, Set.empty, Some("dcg-noactivation"))
//    show(complexMutators, Set.empty, Some("effmu"))
//  }
//
//
//  private def show(m: SemiEdgeSeq[Annotated[String], String, Int], act: Set[AxisPoint], name: Option[String]): Unit = {
//    val resolver = new MutationResolver.MutationResolverImpl[String, Int, Int]
//    val resolved = resolver.resolve(m, act)
//
//    resolved match {
//      case Left(value) =>
//        System.err.println(value)
//      case Right(value) =>
//        GVR.render(value, Singleton, () => System.exit(0), name)
//    }
//
//  }
//}
//
//
//
//
