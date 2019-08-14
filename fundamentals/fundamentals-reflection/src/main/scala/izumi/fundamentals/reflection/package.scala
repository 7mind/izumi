package izumi.fundamentals

import scala.reflect.api.Universe

package object reflection {
  type SingletonUniverse = Universe with Singleton
}
