package distage

import com.github.pshirshov.izumi.distage.DIStageInstances
import com.github.pshirshov.izumi.distage.model.plan.OrderedPlan
import _root_.cats.effect.Sync

import scala.language.higherKinds

package object cats extends DIStageInstances {

  implicit final class ProducerIOExts(private val producer: Producer) extends AnyVal {
    def produceIO[F[_]: Sync](plan: OrderedPlan): F[Locator] =
      Sync[F].delay(producer.produce(plan))
  }

  implicit final class InjectorIOExts(private val injector: Injector) extends AnyVal {
    def runIO[F[_]: Sync](definition: ModuleBase): F[Locator] =
      Sync[F].delay(injector.run(definition))
  }

}
