/* NSC -- new Scala compiler
 * Copyright 2005-2009 LAMP/EPFL
 * @author  Martin Odersky
 */

// $Id$


package scala.tools.nsc.backend.icode

import scala.tools.nsc.ast._
import scala.tools.nsc.util.{Position,NoPosition}

/*
  A pattern match

  case THIS(clasz) =>
  case CONSTANT(const) =>
  case LOAD_ARRAY_ITEM(kind) =>
  case LOAD_LOCAL(local) =>
  case LOAD_FIELD(field, isStatic) =>
  case LOAD_MODULE(module) =>
  case STORE_ARRAY_ITEM(kind) =>
  case STORE_LOCAL(local) =>
  case STORE_FIELD(field, isStatic) =>
  case CALL_PRIMITIVE(primitive) =>
  case CALL_METHOD(method, style) =>
  case NEW(kind) =>
  case CREATE_ARRAY(elem, dims) =>
  case IS_INSTANCE(tpe) =>
  case CHECK_CAST(tpe) =>
  case SWITCH(tags, labels) =>
  case JUMP(whereto) =>
  case CJUMP(success, failure, cond, kind) =>
  case CZJUMP(success, failure, cond, kind) =>
  case RETURN(kind) =>
  case THROW() =>
  case DROP(kind) =>
  case DUP(kind) =>
  case MONITOR_ENTER() =>
  case MONITOR_EXIT() =>
  case SCOPE_ENTER(lv) =>
  case SCOPE_EXIT(lv) =>
  case LOAD_EXCEPTION() =>
*/


/**
 * The ICode intermediate representation. It is a stack-based
 * representation, very close to the JVM and .NET. It uses the
 * erased types of Scala and references Symbols to refer named entities
 * in the source files.
 */
trait Opcodes { self: ICodes =>
  import global.{Symbol, NoSymbol, Type, Name, Constant};

  /** This class represents an instruction of the intermediate code.
   *  Each case subclass will represent a specific operation.
   */
  abstract class Instruction {

    /** This abstract method returns the number of used elements on the stack */
    def consumed : Int = 0

    /** This abstract method returns the number of produced elements on the stack */
    def produced : Int = 0

    /** This instruction consumes these types from the top of the stack, the first
     *  element in the list is the deepest element on the stack.
     */
    def consumedTypes: List[TypeKind] = Nil

    /** This instruction produces these types on top of the stack. */
    def producedTypes: List[TypeKind] = Nil

    /** This method returns the difference of size of the stack when the instruction is used */
    def difference = produced-consumed

    /** The corresponding position in the source file */
    var pos: Position = NoPosition

    /** Used by dead code elimination. */
    var useful: Boolean = false

    def setPos(p: Position): this.type = {
      pos = p
      this
    }
  }

  object opcodes {

    /** Loads "this" on top of the stack.
     * Stack: ...
     *    ->: ...:ref
     */
    case class THIS(clasz: Symbol) extends Instruction {
      /** Returns a string representation of this constant */
      override def toString(): String = "THIS"

      override def consumed = 0
      override def produced = 1

      override def producedTypes = List(REFERENCE(clasz))
    }

    /** Loads a constant on the stack.
     * Stack: ...
     *    ->: ...:constant
     */
    case class CONSTANT(constant: Constant) extends Instruction {
      /** Returns a string representation of this constant */
      override def toString(): String = "CONSTANT ("+constant.toString()+")"

      override def consumed = 0
      override def produced = 1

      override def producedTypes = List(toTypeKind(constant.tpe))
    }

    /** Loads an element of an array. The array and the index should
     * be on top of the stack.
     * Stack: ...:array[a](Ref):index(Int)
     *    ->: ...:element(a)
     */
    case class LOAD_ARRAY_ITEM(kind: TypeKind) extends Instruction {
      /** Returns a string representation of this instruction */
      override def toString(): String = "LOAD_ARRAY_ITEM (" + kind + ")"

      override def consumed = 2
      override def produced = 1

      override def consumedTypes = List(ARRAY(kind), INT)
      override def producedTypes = List(kind)
    }

    /** Load a local variable on the stack. It can be a method argument.
     * Stack: ...
     *    ->: ...:value
     */
    case class LOAD_LOCAL(local: Local) extends Instruction {
      /** Returns a string representation of this instruction */
      override def toString(): String = "LOAD_LOCAL "+local.toString()  //+isArgument?" (argument)":"";

      override def consumed = 0
      override def produced = 1

      override def producedTypes = List(local.kind)
    }

    /** Load a field on the stack. The object to which it refers should be
     * on the stack.
     * Stack: ...:ref       (assuming isStatic = false)
     *    ->: ...:value
     */
    case class LOAD_FIELD(field: Symbol, isStatic: Boolean) extends Instruction {
      /** Returns a string representation of this instruction */
      override def toString(): String =
        "LOAD_FIELD " + (if (isStatic) field.fullNameString else field.toString());

      override def consumed = if (isStatic) 0 else 1
      override def produced = 1

      override def consumedTypes = if (isStatic) Nil else List(REFERENCE(field.owner));
      override def producedTypes = List(toTypeKind(field.tpe));
    }

    case class LOAD_MODULE(module: Symbol) extends Instruction {
      assert(module != NoSymbol,
             "Invalid module symbol");
      /** Returns a string representation of this instruction */
      override def toString(): String =
        "LOAD_MODULE " + module.toString()

      override def consumed = 0
      override def produced = 1

      override def producedTypes = List(REFERENCE(module))
    }

    /** Store a value into an array at a specified index.
     * Stack: ...:array[a](Ref):index(Int):value(a)
     *    ->: ...
     */
    case class STORE_ARRAY_ITEM(kind: TypeKind) extends Instruction {
      /** Returns a string representation of this instruction */
      override def toString(): String = "STORE_ARRAY_ITEM (" + kind + ")"

      override def consumed = 3
      override def produced = 0

      override def consumedTypes = List(ARRAY(kind), INT, kind)
    }

    /** Store a value into a local variable. It can be an argument.
     * Stack: ...:value
     *    ->: ...
     */
    case class STORE_LOCAL(local: Local) extends Instruction {
      /** Returns a string representation of this instruction */
      override def toString(): String = "STORE_LOCAL "+local.toString(); //+isArgument?" (argument)":"";

      override def consumed = 1
      override def produced = 0

      override def consumedTypes = List(local.kind)
    }

    /** Store a value into a field.
     * Stack: ...:ref:value   (assuming isStatic=false)
     *    ->: ...
     */
    case class STORE_FIELD(field: Symbol, isStatic: Boolean) extends Instruction {
      /** Returns a string representation of this instruction */
      override def toString(): String =
        "STORE_FIELD "+field.toString() + (if (isStatic) " (static)" else " (dynamic)");

      override def consumed = if(isStatic) 1 else 2;
      override def produced = 0;

      override def consumedTypes =
        if (isStatic)
          List(toTypeKind(field.tpe))
        else
          List(REFERENCE(field.owner), toTypeKind(field.tpe));
    }

    /** Store a value into the 'this' pointer.
       * Stack: ...:ref
       *    ->: ...
       */
    case class STORE_THIS(kind: TypeKind) extends Instruction {
      override def toString() = "STORE_THIS(" + kind + ")"
      override def consumed = 1
      override def produced = 0
      override def consumedTypes = List(kind)
    }

    /** Call a primitive function.
     * Stack: ...:arg1:arg2:...:argn
     *    ->: ...:result
     */
    case class CALL_PRIMITIVE(primitive: Primitive) extends Instruction {
      /** Returns a string representation of this instruction */
      override def toString(): String ="CALL_PRIMITIVE "+primitive.toString()

      override def consumed = primitive match {
        case Negation(_)       => 1
        case Test(_,_, true)   => 1
        case Test(_,_, false)  => 2
        case Comparison(_,_)   => 2
        case Arithmetic(NOT,_) => 1
        case Arithmetic(_,_)   => 2
        case Logical(_,_)      => 2
        case Shift(_,_)        => 2
        case Conversion(_,_)   => 1
        case ArrayLength(_)    => 1
        case StringConcat(_)   => 2
        case StartConcat       => 0
        case EndConcat         => 1
      }
      override def produced = 1

      override def consumedTypes = primitive match {
        case Negation(kind)        => List(kind)
        case Test(_, kind, true)   => List(kind)
        case Test(_, kind, false)  => List(kind, kind)
        case Comparison(_, kind)   => List(kind, kind)
        case Arithmetic(NOT, kind) => List(kind)
        case Arithmetic(_, kind)   => List(kind, kind)
        case Logical(_, kind)      => List(kind, kind)
        case Shift(_, kind)        => List(kind, INT)
        case Conversion(from, _)   => List(from)
        case ArrayLength(kind)     => List(ARRAY(kind))
        case StringConcat(kind)    => List(ConcatClass, kind)
        case StartConcat           => Nil
        case EndConcat             => List(ConcatClass)
      }

      override def producedTypes = primitive match {
        case Negation(kind)      => List(kind)
        case Test(_, _, true)    => List(BOOL)
        case Test(_, _, false)   => List(BOOL)
        case Comparison(_, _)    => List(INT)
        case Arithmetic(_, kind) => List(kind)
        case Logical(_, kind)    => List(kind)
        case Shift(_, kind)      => List(kind)
        case Conversion(_, to)   => List(to)
        case ArrayLength(_)      => List(INT)
        case StringConcat(_)     => List(ConcatClass)
        case StartConcat         => List(ConcatClass)
        case EndConcat           => List(REFERENCE(global.definitions.StringClass))
      }
   }

    /** This class represents a CALL_METHOD instruction
     * STYLE: dynamic / static(StaticInstance)
     * Stack: ...:ref:arg1:arg2:...:argn
     *    ->: ...:result
     *
     * STYLE: static(StaticClass)
     * Stack: ...:arg1:arg2:...:argn
     *    ->: ...:result
     *
     */
    case class CALL_METHOD(method: Symbol, style: InvokeStyle) extends Instruction {
      /** Returns a string representation of this instruction */
      override def toString(): String =
        "CALL_METHOD " + hostClass.fullNameString + method.fullNameString +" ("+style.toString()+")";

      var hostClass: Symbol = method.owner;
      def setHostClass(cls: Symbol): this.type = { hostClass = cls; this }

      override def consumed = {
        var result = method.tpe.paramTypes.length;
        result = result + (style match {
          case Dynamic | InvokeDynamic => 1
          case Static(true) => 1
          case Static(false) => 0
          case SuperCall(_) => 1
        });

        result;
      }

      override def consumedTypes = {
        val args = method.tpe.paramTypes map toTypeKind
        style match {
          case Dynamic | Static(true) => AnyRefReference :: args
          case _ => args
        }
      }

      override def produced =
        if(toTypeKind(method.tpe.resultType) == UNIT)
          0
        else if(method.isConstructor)
          0
        else 1

      /** object idenity is equality for CALL_METHODs. Needed for
       *  being able to store such instructions into maps, when more
       *  than one CALL_METHOD to the same method might exist.
       */
      override def equals(other: Any) = other match {
        case o: AnyRef => this eq o
        case _ => false
      }
    }

    case class BOX(boxType: TypeKind) extends Instruction {
      override def toString(): String = "BOX " + boxType
      override def consumed = 1
      override def consumedTypes = boxType :: Nil
      override def produced = 1
    }

    case class UNBOX(boxType: TypeKind) extends Instruction {
      override def toString(): String = "UNBOX " + boxType
      override def consumed = 1
      override def consumedTypes = AnyRefReference :: Nil
      override def produced = 1
    }

    /** Create a new instance of a class through the specified constructor
     * Stack: ...:arg1:arg2:...:argn
     *    ->: ...:ref
     */
    case class NEW(kind: REFERENCE) extends Instruction {
      /** Returns a string representation of this instruction */
      override def toString(): String = "NEW "+ kind;

      override def consumed = 0;
      override def produced = 1;

      /** The corresponding constructor call. */
      var init: CALL_METHOD = _
    }


    /** This class represents a CREATE_ARRAY instruction
     * Stack: ...:size_1:size_2:..:size_n
     *    ->: ...:arrayref
     */
    case class CREATE_ARRAY(elem: TypeKind, dims: Int) extends Instruction {
      /** Returns a string representation of this instruction */
      override def toString(): String ="CREATE_ARRAY "+elem.toString() + " x " + dims;

      override def consumed = dims;
      override def consumedTypes = List.make(dims, INT)
      override def produced = 1;
    }

    /** This class represents a IS_INSTANCE instruction
     * Stack: ...:ref
     *    ->: ...:result(boolean)
     */
    case class IS_INSTANCE(typ: TypeKind) extends Instruction {
      /** Returns a string representation of this instruction */
      override def toString(): String ="IS_INSTANCE "+typ.toString()

      override def consumed = 1
      override def consumedTypes = AnyRefReference :: Nil
      override def produced = 1
    }

    /** This class represents a CHECK_CAST instruction
     * Stack: ...:ref(oldtype)
     *    ->: ...:ref(typ <=: oldtype)
     */
    case class CHECK_CAST(typ: TypeKind) extends Instruction {
      /** Returns a string representation of this instruction */
      override def toString(): String ="CHECK_CAST "+typ.toString()

      override def consumed = 1
      override def produced = 1
      override val consumedTypes = List(AnyRefReference)
      override def producedTypes = List(typ)
    }

    /** This class represents a SWITCH instruction
     * Stack: ...:index(int)
     *    ->: ...:
     *
     * The tags array contains one entry per label, each entry consisting of
     * an array of ints, any of which will trigger the jump to the corresponding label.
     * labels should contain an extra label, which is the 'default' jump.
     */
    case class SWITCH(tags: List[List[Int]], labels: List[BasicBlock]) extends Instruction {
      /** Returns a string representation of this instruction */
      override def toString(): String ="SWITCH ..."

      override def consumed = 1
      override def produced = 0
    }

    /** This class represents a JUMP instruction
     * Stack: ...
     *    ->: ...
     */
    case class JUMP(whereto: BasicBlock) extends Instruction {
      /** Returns a string representation of this instruction */
      override def toString(): String ="JUMP "+whereto.label

      override def consumed = 0
      override def produced = 0
    }

    /** This class represents a CJUMP instruction
     * It compares the two values on the stack with the 'cond' test operator
     * Stack: ...:value1:value2
     *    ->: ...
     */
    case class CJUMP(successBlock: BasicBlock,
		     failureBlock: BasicBlock,
		     cond: TestOp,
                     kind: TypeKind) extends Instruction
    {

      /** Returns a string representation of this instruction */
      override def toString(): String = (
        "CJUMP (" + kind + ")" +
        cond.toString()+" ? "+successBlock.label+" : "+failureBlock.label
      );

      override def consumed = 2
      override def produced = 0
    }

    /** This class represents a CZJUMP instruction
     * It compares the one value on the stack and zero with the 'cond' test operator
     * Stack: ...:value:
     *    ->: ...
     */
    case class CZJUMP(successBlock: BasicBlock,
                      failureBlock: BasicBlock,
                      cond: TestOp,
                      kind: TypeKind) extends Instruction {
      /** Returns a string representation of this instruction */
      override def toString(): String = (
        "CZJUMP (" + kind + ")" +
        cond.toString()+" ? "+successBlock.label+" : "+failureBlock.label
      );

      override def consumed = 1
      override def produced = 0
    }


    /** This class represents a RETURN instruction
     * Stack: ...
     *    ->: ...
     */
    case class RETURN(kind: TypeKind) extends Instruction {
      /** Returns a string representation of this instruction */
      override def toString(): String ="RETURN (" + kind + ")"

      override def consumed = if (kind == UNIT) 0 else 1
      override def produced = 0
    }

    /** This class represents a THROW instruction
     * Stack: ...:Throwable(Ref)
     *    ->: ...:
     */
    case class THROW() extends Instruction {
      /** Returns a string representation of this instruction */
      override def toString(): String ="THROW"

      override def consumed = 1
      override def produced = 0
    }

    /** This class represents a DROP instruction
     * Stack: ...:something
     *    ->: ...
     */
    case class DROP (typ: TypeKind) extends Instruction {
      /** Returns a string representation of this instruction */
      override def toString(): String ="DROP "+typ.toString()

      override def consumed = 1
      override def produced = 0
    }

    /** This class represents a DUP instruction
     * Stack: ...:something
     *    ->: ...:something:something
     */
    case class DUP (typ: TypeKind) extends Instruction {
      /** Returns a string representation of this instruction */
      override def toString(): String ="DUP"

      override def consumed = 1
      override def produced = 2
    }

    /** This class represents a MONITOR_ENTER instruction
     * Stack: ...:object(ref)
     *    ->: ...:
     */
    case class MONITOR_ENTER() extends Instruction {

      /** Returns a string representation of this instruction */
      override def toString(): String ="MONITOR_ENTER"

      override def consumed = 1
      override def produced = 0
    }

    /** This class represents a MONITOR_EXIT instruction
     * Stack: ...:object(ref)
     *    ->: ...:
     */
    case class MONITOR_EXIT() extends Instruction {

      /** Returns a string representation of this instruction */
      override def toString(): String ="MONITOR_EXIT";

      override def consumed = 1;
      override def produced = 0;
    }

    /** A local variable becomes visible at this point in code.
     *  Used only for generating precise local variable tables as
     *  debugging information.
     */
    case class SCOPE_ENTER(lv: Local) extends Instruction {
      override def toString(): String = "SCOPE_ENTER " + lv
      override def consumed = 0
      override def produced = 0
    }

    /** A local variable leaves its scope at this point in code.
     *  Used only for generating precise local variable tables as
     *  debugging information.
     */
    case class SCOPE_EXIT(lv: Local) extends Instruction {
      override def toString(): String = "SCOPE_EXIT " + lv
      override def consumed = 0
      override def produced = 0
    }

    /** Fake instruction. It designates the VM who pushes an exception
     *  on top of the /empty/ stack at the beginning of each exception handler.
     *  Note: Unlike other instructions, it consumes all elements on the stack!
     *        then pushes one exception instance.
     */
    case class LOAD_EXCEPTION() extends Instruction {
      override def toString(): String = "LOAD_EXCEPTION"
      override def consumed = error("LOAD_EXCEPTION does clean the whole stack, no idea how many things it consumes!")
      override def produced = 1
      override def producedTypes = AnyRefReference :: Nil
    }

    /** This class represents a method invocation style. */
    sealed abstract class InvokeStyle {

      /** Is this a dynamic method call? */
      def isDynamic: Boolean = this match {
        case Dynamic =>  true
        case _       => false
      }

      /** Is this a static method call? */
      def isStatic: Boolean = this match {
        case Static(_) => true
        case _ =>  false
      }

      def isSuper: Boolean = this match {
        case SuperCall(_) => true
        case _ => false
      }

      /** Is this an instance method call? */
      def hasInstance: Boolean = this match {
        case Dynamic => true
        case Static(onInstance) => onInstance
        case SuperCall(_) => true
        case _ => false
      }

      /** Returns a string representation of this style. */
      override def toString(): String = this match {
        case Dynamic =>  "dynamic"
        case InvokeDynamic => "invoke-dynamic"
        case Static(false) => "static-class"
        case Static(true) =>  "static-instance"
        case SuperCall(mix) => "super(" + mix + ")"
      }
    }

    /** Virtual calls */
    case object Dynamic extends InvokeStyle

    /** InvokeDynamic a la JSR 292 (experimental). */
    case object InvokeDynamic extends InvokeStyle

    /**
     * Special invoke. Static(true) is used for calls to private
     * members.
     */
    case class Static(onInstance: Boolean) extends InvokeStyle

    /** Call through super[mix]. */
    case class SuperCall(mix: Name) extends InvokeStyle

  }
}
