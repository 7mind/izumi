package izumi.idealingua.runtime.rpc.http4s.fixtures

import izumi.functional.bio.BIO
import izumi.functional.bio.BIO._
import izumi.idealingua.runtime.rpc._
import izumi.idealingua.runtime.rpc.http4s.{IRTBadCredentialsException, IRTNoCredentialsException}
import org.http4s.{BasicCredentials, Status}

final class DummyAuthorizingDispatcher[R[+ _, + _] : BIO, Ctx](proxied: IRTWrappedService[R, Ctx]) extends IRTWrappedService[R, Ctx] {
  override def serviceId: IRTServiceId = proxied.serviceId

  override def allMethods: Map[IRTMethodId, IRTMethodWrapper[R, Ctx]] = proxied.allMethods.mapValues {
    method =>
      new IRTMethodWrapper[R, Ctx] {
        val R: BIO[R] = implicitly

        override val signature: IRTMethodSignature = method.signature
        override val marshaller: IRTCirceMarshaller = method.marshaller

        override def invoke(ctx: Ctx, input: signature.Input): R.Just[signature.Output] = {
          ctx match {
            case DummyRequestContext(_, Some(BasicCredentials(user, pass))) =>
              if (user == "user" && pass == "pass") {
                method.invoke(ctx, input.asInstanceOf[method.signature.Input]).map(_.asInstanceOf[signature.Output])
              } else {
                R.terminate(IRTBadCredentialsException(Status.Unauthorized))
              }

            case _ =>
              R.terminate(IRTNoCredentialsException(Status.Forbidden))
          }
        }
      }
  }.toMap
}
