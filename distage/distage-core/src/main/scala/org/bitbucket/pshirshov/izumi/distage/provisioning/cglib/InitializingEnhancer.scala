package org.bitbucket.pshirshov.izumi.distage.provisioning.cglib

import java.lang.reflect.InvocationTargetException

import net.sf.cglib.asm.{$ClassVisitor, $Opcodes, $Type}
import net.sf.cglib.core.TypeUtils
import net.sf.cglib.proxy.Enhancer
import org.bitbucket.pshirshov.izumi.distage.TypeFull
import org.bitbucket.pshirshov.izumi.distage.commons.Value
import org.bitbucket.pshirshov.izumi.distage.model.exceptions.DIException

import scala.reflect.runtime.universe._
import scala.reflect.runtime.currentMirror

class InitializingEnhancer(fullType: TypeFull, runtimeClass: Class[_]) extends Enhancer {
  override def generateClass(v: $ClassVisitor): Unit = {
    super.generateClass(v)
//    injectTraitSupport(v)
  }


  private def injectTraitSupport(v: $ClassVisitor): Unit = {
    val terms = fullType.tpe.decls.collect { case m: TermSymbol => m }
    terms.foreach {
      case t if t.isVal =>

        val runtimeClass = currentMirror.runtimeClass(t.typeSignature.resultType)
        generateField(t.name.toString, runtimeClass, v)

      case t if t.isVar =>
        throw new DIException(s"I don't like your `var` in $runtimeClass", null)
        
      case _ =>
    }


    addInitializer(v)
  }


  private def addInitializer(v: $ClassVisitor): Unit = {
    val internalName = TypeUtils.getType(runtimeClass.getTypeName).getInternalName

    val `call_$init$` = false

    val mv = v.visitMethod($Opcodes.ACC_PUBLIC, InitializingEnhancer.IZ_INIT_METHOD_NAME, "()Z", null, null)
    mv.visitCode()
    mv.visitVarInsn($Opcodes.ALOAD, 0)
    if (`call_$init$`) {
      mv.visitVarInsn($Opcodes.ALOAD, 0)
      mv.visitMethodInsn($Opcodes.INVOKESTATIC
        , internalName
        , "$init$"
        , s"(L$internalName;)V"
        , false
      )
    }
    mv.visitInsn($Opcodes.ICONST_1)
    mv.visitInsn($Opcodes.IRETURN)
    mv.visitMaxs(2, 1)
    mv.visitEnd()
  }

  private def generateField(name: String, tpe: Class[_], v: $ClassVisitor): Unit = {
    val descriptor = $Type.getDescriptor(tpe)

    val (load, ret) = ($Opcodes.ILOAD, $Opcodes.IRETURN)

    val fieldName = s"${$Type.getInternalName(runtimeClass)}.$name"

    Value(v.visitField($Opcodes.ACC_PUBLIC, name, descriptor, null, null))
      .eff(_.visitEnd())

    Value(v.visitMethod($Opcodes.ACC_PUBLIC, name, s"()$descriptor", null, null))
      .eff(_.visitCode())
      .eff(_.visitVarInsn($Opcodes.ALOAD, 0))
      .eff(_.visitFieldInsn($Opcodes.GETFIELD, fieldName, name, descriptor))
      .eff(_.visitInsn(ret))
      .eff(_.visitMaxs(1, 1))
      .eff(_.visitEnd())

    val setterBase = runtimeClass.getCanonicalName.replace('.', '$')
    val setterName = s"$setterBase$$_setter_$$${name}_$$eq"
    
    Value(v.visitMethod($Opcodes.ACC_PUBLIC, setterName, s"($descriptor)V", null, null))
      .eff(_.visitCode())
      .eff(_.visitVarInsn($Opcodes.ALOAD, 0))
      .eff(_.visitVarInsn(load, 1))
      .eff(_.visitFieldInsn($Opcodes.PUTFIELD, fieldName, name, descriptor))
      .eff(_.visitInsn($Opcodes.RETURN))
      .eff(_.visitMaxs(2, 2))
      .eff(_.visitEnd())
    ()
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
