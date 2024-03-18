package izumi.fundamentals.graphs.ui

import java.awt.BorderLayout
import java.awt.event.{ComponentEvent, ComponentListener, WindowAdapter, WindowEvent}
import java.awt.image.BufferedImage

import javax.swing.JFrame

class PngWindow(image: BufferedImage, onClose: () => Unit = () => (), title: Option[String] = None) extends JFrame {
  private val imageView: ImagePanel = new ImagePanel(image, title)

  locally {
    title.foreach(this.setTitle)
    this.getContentPane.add(imageView, BorderLayout.CENTER)
    // this.pack()
    this.setSize(image.getWidth, image.getHeight)
  }

  this.addWindowListener(new WindowAdapter() {
    override def windowClosing(e: WindowEvent): Unit = {
      onClose()
    }
  })

  this.addComponentListener(new ComponentListener {
    override def componentResized(e: ComponentEvent): Unit = {
      val W = image.getWidth
      val H = image.getHeight
      val b = e.getComponent.getBounds
      e.getComponent.setBounds(b.x, b.y, b.width, b.width * H / W)
    }
    override def componentMoved(e: ComponentEvent): Unit = ()
    override def componentShown(e: ComponentEvent): Unit = ()
    override def componentHidden(e: ComponentEvent): Unit = ()
  })

}
