package org.bitbucket.pshirshov.izumi.distage.provisioning.cglib

import java.lang.reflect.InvocationTargetException

import net.sf.cglib.asm.{$ClassVisitor, $Opcodes}
import net.sf.cglib.core.TypeUtils
import net.sf.cglib.proxy.Enhancer
import org.bitbucket.pshirshov.izumi.distage.TypeFull
import org.bitbucket.pshirshov.izumi.distage.model.exceptions.DIException

class InitializingEnhancer(fullType: TypeFull, runtimeClass: Class[_]) extends Enhancer {
  override def generateClass(v: $ClassVisitor): Unit = {
    super.generateClass(v)
    //injectTraitSupport(v)
  }

  private def injectTraitSupport(v: $ClassVisitor): Unit = {
    import InitializingEnhancer._
    //    val methods = fullType.tpe.decls.collect {
    //      case m: MethodSymbol => m
    //    }
    //
    //    System.err.println(methods)

    // TODO: in order to support fields we need to generate setter/getter pair
    /*
    *   // access flags 0x1
  public a()I
   L0
    LINENUMBER 111 L0
    ALOAD 0
    GETFIELD org/bitbucket/pshirshov/izumi/distage/Case3$CircularX.a : I
    IRETURN
   L1
    LOCALVARIABLE this Lorg/bitbucket/pshirshov/izumi/distage/Case3$CircularX; L0 L1 0
    MAXSTACK = 1
    MAXLOCALS = 1

  // access flags 0x1
  public org$bitbucket$pshirshov$izumi$distage$Case3$Circular1$_setter_$a_$eq(I)V
    // parameter final  x$1
   L0
    LINENUMBER 111 L0
    ALOAD 0
    ILOAD 1
    PUTFIELD org/bitbucket/pshirshov/izumi/distage/Case3$CircularX.a : I
    RETURN
   L1
    LOCALVARIABLE this Lorg/bitbucket/pshirshov/izumi/distage/Case3$CircularX; L0 L1 0
    LOCALVARIABLE x$1 I L0 L1 1
    MAXSTACK = 2
    MAXLOCALS = 2
    *
    *
    * */


    val internalName = TypeUtils.getType(runtimeClass.getTypeName).getInternalName

    val `call_$init$` = false

    val mv = v.visitMethod($Opcodes.ACC_PUBLIC, IZ_INIT_METHOD_NAME, "()Z", null, null)
    mv.visitCode()
    mv.visitVarInsn($Opcodes.ALOAD, 0) // ALOAD
    if (`call_$init$`) {
      mv.visitVarInsn($Opcodes.ALOAD, 0)
      mv.visitMethodInsn($Opcodes.INVOKESTATIC // invoke static
        , internalName
        , "$init$"
        , s"(L$internalName;)V"
        , false
      )
    }
    mv.visitInsn($Opcodes.ICONST_1) // ICONST_1
    mv.visitInsn($Opcodes.IRETURN)
    mv.visitMaxs(2, 1)
    mv.visitEnd()
  }
}

object InitializingEnhancer {
  private final val IZ_INIT_METHOD_NAME = "__IZUMI__INIT__"

  def initTrait(instance: AnyRef): Unit = {

    try {
      val ret = instance
        .getClass
        .getMethod(IZ_INIT_METHOD_NAME)
        .invoke(instance)
      if (!ret.asInstanceOf[Boolean]) {
        throw new DIException("Self-check failed, trait initializer didn't return `true`", null)
      }
    } catch {
      case e: InvocationTargetException =>
        throw e.getCause
    }


  }
}
