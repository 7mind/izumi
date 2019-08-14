package com.github.pshirshov.izumi.idealingua.il

import com.github.pshirshov.izumi.functional.Renderable

package object renderer {
  implicit class StaticRenderer[T](value: T) {
    def render()(implicit ev: Renderable[T]): String = ev.render(value)
  }

}
