package izumi.fundamentals.graphs.ui

trait LocationPolicy {
  def nextLocation(sz: (Int, Int)): (Int, Int)
}

object LocationPolicy {
  object Singleton extends LocationPolicy {
    private val xMargin = 10
    private val yShift = 40

    private var nextX = xMargin
    private var nextY = xMargin

    import java.awt.{Dimension, Toolkit}

    def reset(): Unit = {
      nextX = xMargin
      nextY = xMargin
    }

    override def nextLocation(sz: (Int, Int)): (Int, Int) = {
      val (w, _) = sz
      val ret = (nextX, nextY)
      val screenSize: Dimension = Toolkit.getDefaultToolkit.getScreenSize
      if (nextX + w <= screenSize.width) {
        nextX += w + xMargin
      } else {
        nextX = 0
        val deltaY = nextY + yShift
        if (deltaY < screenSize.height) {
          nextY = deltaY
        } else {
          nextY = 0
        }
      }

      ret
    }
  }
}
