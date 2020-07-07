package izumi.fundamentals.graphs.ui

import java.awt.event.{ActionEvent, ActionListener, MouseAdapter, MouseEvent}
import java.awt.image.BufferedImage
import java.awt.{Graphics, RenderingHints}
import java.io.File

import javax.swing.JPanel

class ImagePanel(image: BufferedImage, name: Option[String]) extends JPanel {

  import javax.swing.{JMenuItem, JPopupMenu}

  class PopupMenu() extends JPopupMenu {
    val anItem = new JMenuItem("Save image")
    add(anItem)
    anItem.addActionListener(new ActionListener {
      override def actionPerformed(e: ActionEvent): Unit = {
        import javax.imageio.ImageIO
        try {
          import javax.swing.JFileChooser
          val fc = new JFileChooser
          fc.setSelectedFile(new File(s"${name.getOrElse("graph")}.png"))
          val returnVal = fc.showSaveDialog(PopupMenu.this)

          if (returnVal == JFileChooser.APPROVE_OPTION) {
            val file = fc.getSelectedFile
            if (!ImageIO.write(image, "png", file)) {
              throw new RuntimeException(s"Cannot write file: $file")
            }
          }
        } catch {
          case t: Throwable =>
            t.printStackTrace()
        }

      }
    })
  }

  private val menu = new PopupMenu()

  class PopClickListener extends MouseAdapter {
    override def mousePressed(e: MouseEvent): Unit = mouseReleased(e)

    override def mouseReleased(e: MouseEvent): Unit = {
      if (e.isPopupTrigger) {
        menu.show(e.getComponent, e.getX, e.getY)
      }
    }
  }

  this.addMouseListener(new PopClickListener)

  override protected def paintComponent(g: Graphics): Unit = {
    super.paintComponent(g)

    g.drawImage(createScaledImage(this.image), 0, 0, null)
    ()
  }

  private def createScaledImage(bufferedImage: BufferedImage): BufferedImage = {
    val image = new BufferedImage(getWidth, getHeight, BufferedImage.TYPE_INT_RGB)
    val g2d = image.createGraphics
    g2d.addRenderingHints(new RenderingHints(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY))
    g2d.drawImage(bufferedImage, 0, 0, getWidth, getHeight, null)
    image
  }

}
