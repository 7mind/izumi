//package izumi.fundamentals.graphs.ui
//
//import java.util
//
//import guru.nidi.graphviz.attribute.Rank.RankDir
//import guru.nidi.graphviz.attribute.{Color, Rank}
//import guru.nidi.graphviz.engine.{Format, Graphviz}
//import guru.nidi.graphviz.model.Factory._
//import guru.nidi.graphviz.model.LinkSource
//import izumi.fundamentals.graphs.GraphImpl.DirectedGraphSucc
//import izumi.fundamentals.graphs.GraphImpl.DirectedGraphSucc
//import izumi.fundamentals.graphs.NodeShow
//
//
//object GVR {
//
//  def render[N: NodeShow, M: NodeShow](
//                                        input: DirectedGraphSucc[N, M],
//                                        locationPolicy: LocationPolicy,
//                                        onClose: () => Unit,
//                                        name: Option[String] = None
//                                      ): Unit = {
//    val NS = implicitly[NodeShow[N]]
//
//    import scala.jdk.CollectionConverters._
//
//    val nodes: util.List[LinkSource] = input.successors.links.map {
//      case (p, s) =>
//        node(NS.show(p)).link(s.map(NS.show).toSeq: _*).asInstanceOf[LinkSource]
//    }.toList.asJava
//
//    val fg = Color.rgb("dddddd")
//    val ln = Color.rgb("00aa00")
//    val bg = Color.rgb("222222")
//
//    val ga = graph("dump")
//      .directed()
//      .linkAttr()
//      .`with`(
//        ln,
//        ln.fill(),
//        fg.font(),
//        fg.labelFont(),
//      )
//      .nodeAttr()
//      .`with`(
//        ln,
//        fg.font(),
//        fg.labelFont(),
//      )
//      .graphAttr()
//      .`with`(
//        Rank.dir(RankDir.TOP_TO_BOTTOM),
//
//        bg.background(),
//        fg.font(),
//        fg.labelFont(),
//        fg.fill(),
//        fg.radial(),
//      )
//      .`with`(nodes)
//
//    val image = Graphviz.fromGraph(ga)
//      .height(800)
//      .render(Format.PNG)
//      .toImage
//
//    SwingTheme.setDark()
//
//    val win = new PngWindow(image, onClose, name)
//    val pos = locationPolicy.nextLocation((image.getWidth, image.getHeight))
//    win.setLocation(pos._1, pos._2)
//    win.setVisible(true)
//
//  }
//}
//
//
