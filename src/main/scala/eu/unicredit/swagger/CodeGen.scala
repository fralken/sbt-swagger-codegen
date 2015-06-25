package eu.unicredit.swagger

import treehugger.forest._, definitions._, treehuggerDSL._
import treehugger.forest._
import definitions._
import treehuggerDSL._

import io.swagger.parser.SwaggerParser
import io.swagger.models.properties._
import io.swagger.models._
import io.swagger.models.parameters._
import scala.collection.JavaConversions._

object CodeGen extends SwaggerToTree with StringUtils {

  def generateClass(name: String, props: Map[String, Property], comments: Option[String]): String = {
    val GenClass = RootClass.newClass(name)

    val params: Iterable[ValDef] =
      props.map {
        case (name, prop) => PARAM(name, propType(prop)): ValDef
      }

    val tree: Tree = CLASSDEF(GenClass).withFlags(Flags.CASE).withParams(params)

    val resTree =
      comments.map(tree withComment _).getOrElse(tree)

    treeToString(resTree)
  }

  def generateModelInit(packageName: String): String = {
    val initTree =
      IMPORT("org.joda.time", "DateTime") inPackage (packageName)

    treeToString(initTree) + "\n"

    //treeToString(IMPORT("play.api.libs.json","JsValue")) + "\n"
  }

  def generateModels(fileName: String): Map[String, String] = {
    val swagger = new SwaggerParser().read(fileName)
    val models = swagger.getDefinitions

    val modelTrees =
      for {
        (name, model) <- models
        description = model.getDescription
        properties = model.getProperties
      } yield {

        val comments =
          if (description eq null) None
          else Some(description)

        name -> generateClass(name, properties.toMap, comments)
      }

    modelTrees.toMap
  }

  def generateJsonInit(packageName: String): String = {
    //let see what happens...
    val initTree =
      BLOCK {
        Seq(
          IMPORT("play.api.libs.json", "_"),
          IMPORT(packageName, "_"))
      } inPackage (packageName)

    treeToString(initTree) /*+ "\n" +
    treeToString(IMPORT(packageName, "_"))*/
  }

  def generateJsonImplicits(vds: List[ValDef]): String = {
    val tree =
      PACKAGEOBJECTDEF("json") := BLOCK(vds)

    treeToString(tree)
  }

  def generateJsonRW(fileNames: List[String]): Map[String, ValDef] = {
    val fmts =
      (for {
        file <- fileNames
      } yield {
        val swagger = new SwaggerParser().read(file)
        val models = swagger.getDefinitions

        val formats =
          for {
            (name, model) <- models
          } yield {
            (name, VAL(name + "Fmt").withFlags(Flags.IMPLICIT) := REF("Json") DOT ("format") APPLYTYPE name)
          }

        formats
      }).flatten.toMap

    //val tree = 
    //  BLOCK(fmts)
    /*OBJECTDEF("Implicits") := BLOCK(fmts)*/

    //treeToString(tree)
    fmts
  }

  def generatePlayServerRoutes(fileName: String, packageName: String): Seq[String] = {
    val swagger = new SwaggerParser().read(fileName)

    val basePath = swagger.getBasePath

    val completePaths =
      swagger.getPaths.keySet().toSeq

    def composePlayRoutes(p: String): Seq[String] = {
      val path = swagger.getPath(p)
      if (path == null) return Seq()

      val ops: Map[String, Operation] =
        Seq(
          Option(path.getDelete) map ("DELETE" -> _),
          Option(path.getGet) map ("GET" -> _),
          Option(path.getPost) map ("POST" -> _),
          Option(path.getPut) map ("PUT" -> _)).flatten.toMap

      val controllerName =
        packageName + ".controller" + "." + controllerNameFromFileName(fileName)
      
      (for {
        op <- ops
      } yield {

        val url =
          doUrl(basePath, p, op._2.getParameters.toList)

        val methodName =
          op._2.getOperationId

        val methodCall =
          genMethodCall(controllerName, methodName, op._2.getParameters)

        s"${trimTo(8, op._1)}            ${trimTo(50, url)}          ${trimTo(20, methodCall)}"
      }).toSeq
    }

    completePaths.map(composePlayRoutes).flatten
  }

  def generatePlayServerStub(fileName: String, packageName: String, codeProvidedPackage: String, async: Boolean): (String, String) = {
    val swagger = new SwaggerParser().read(fileName)

    val basePath = swagger.getBasePath

    val controllerPackageName =
      (packageName + ".controller")

    val controllerName =
      controllerNameFromFileName(fileName)

    val completePaths =
      swagger.getPaths.keySet().toSeq

    def composePlayController(p: String): Seq[Tree] = {
      val path = swagger.getPath(p)
      if (path == null) return Seq()

      val ops: Seq[Operation] =
        Seq(
          Option(path.getDelete),
          Option(path.getGet),
          Option(path.getPost),
          Option(path.getPut)).flatten

      (for {
        op <- ops
      } yield {

        val resps = op.getResponses

        //val okResp = resps.find(x => x._1 == "200")

        //val respType = okResp.propType(okResp.get._2.getSchema)

        val methodName =
          op.getOperationId

        val methodCall =
          genControllerMethod(methodName, op.getParameters, async /*, respType*/ )

        //val controllerFullName = 
        //  controllerPackageName + controllerName

        methodCall
      }).toSeq
    }

    val imports: Seq[Tree] =
      Seq(
        IMPORT(packageName, "_"),
        IMPORT(packageName + ".json", "_"),
        IMPORT("play.api.mvc.Results", "_"),
        IMPORT("play.api.mvc", "_"),
        IMPORT("play.api.libs.json", "_"),
        IMPORT(codeProvidedPackage, controllerName + "Impl")) ++
        {if (!async) Nil
        else Seq(IMPORT("play.api.libs.concurrent.Execution.Implicits","_"))}

    val tree =
      BLOCK {
        imports :+
          (OBJECTDEF(controllerName) withParents (controllerName + "Impl") := BLOCK(
            completePaths.map(composePlayController).flatten))
      } inPackage (controllerPackageName)

    controllerName -> treeToString(tree)
  }

  def generatePlayClientStub(fileName: String, packageName: String): (String, String) = {
    val swagger = new SwaggerParser().read(fileName)

    val basePath = swagger.getBasePath

    val clientPackageName =
      (packageName + ".client")

    val clientName =
      clientNameFromFileName(fileName)

    val completePaths =
      swagger.getPaths.keySet().toSeq

    def composePlayClient(p: String): Seq[Tree] = {
      val path = swagger.getPath(p)
      if (path == null) return Seq()

      val ops: Seq[(String, Operation)] =
        Seq(
          Option(path.getDelete) map ("DELETE" -> _),
          Option(path.getGet) map ("GET" -> _),
          Option(path.getPost) map ("POST" -> _),
          Option(path.getPut) map ("PUT" -> _)).flatten

      (for {
        op <- ops
      } yield {

        val resps = op._2.getResponses
        
        val fallbackResp = {
          val key = resps.keySet().toSeq.min
          key -> resps(resps.keySet().toSeq.min)
        }
        
        val okResp =
          resps.find(x => x._1 == "200") getOrElse(
            resps.find(x => x._1 == "default") getOrElse
              fallbackResp)

        val retSchema = okResp._2.getSchema
        
        //work only like this at the moment
        try {
          retSchema.setRequired(true)
        } catch {
          case ex: NullPointerException =>
            throw new Exception("Only valid schema are supported in default/200 answare in: "+p)
        }
            
        val respType = propType(retSchema)

        val methodName =
          if (op._2.getOperationId != null) op._2.getOperationId
          else throw new Exception("Please provide an operationId in: "+p)

        val opType =
          op._1.toLowerCase()
          
        val url =
          doUrl(basePath, p, op._2.getParameters.toList)

        val methodCall =
          genClientMethod(methodName, url, opType, op._2.getParameters, respType)

        methodCall
      }).toSeq
    }

    val imports: Seq[Tree] =
      Seq(
        IMPORT(packageName, "_"),
        IMPORT(packageName + ".json", "_"),
        IMPORT("play.api.libs.ws", "_"),
        IMPORT("play.api.libs.json", "_"),
        IMPORT("play.api.Play", "current"),
        IMPORT("play.api.libs.concurrent.Execution.Implicits","_"))

    val tree =
      BLOCK {
        imports :+
          (CLASSDEF(clientName) withParams(PARAM("baseUrl", StringClass)) := BLOCK(
            completePaths.map(composePlayClient).flatten))
      } inPackage (clientPackageName)

    //dirty trick to get the string interpolator working
    val str = treeToString(tree).replace("(s(","((s")
/*    var afterS = false        
    
    implicit val customPrinter: Option[(treehugger.forest.TreePrinter) => PartialFunction[Tree, Unit]] =
      Some(
        (tp) => {
          case tree @ Ident(name) if (name.name == "s") =>
          afterS = true
          tree match {
            case BackQuotedIdent(name) =>
              tp.print("`", tp.symName(tree, name), "`")
            case _ =>
              tp.print(tp.symName(tree, name))
          }
         case tree @ Apply(fun, vargs) =>
          if (!isTupleTree(tree)) tp.print(fun)
          if (vargs.size == 1
            && vargs.head.symbol == definitions.PartiallyAppliedParam) tp.print(" _")
          else {
            if (afterS) {
              afterS = false
              tp.printRow(vargs, "", ", ", "")
            } else {
              tp.printRow(vargs, "(", ", ", ")")
            }
          }
  
        }
      )
    
    val str = treeToString(tree)(customPrinter)
*/      
    clientName -> str
  }

}

trait SwaggerToTree {
  self: StringUtils =>

  def doUrl(basePath: String, path: String, parameters: List[Parameter]) = {
    
    cleanUrl(
      cleanDuplicateSlash(
        basePath + sanitizePath(path, ':') /*+ paramsToURL(parameters)*/)
    )
  }
    
  def controllerNameFromFileName(fn: String) =
    capitalize(fn.split(java.io.File.separator).toList.last.replace(".yaml", "").replace(".json", "")) + "Controller"

  def clientNameFromFileName(fn: String) =
    capitalize(fn.split(java.io.File.separator).toList.last.replace(".yaml", "").replace(".json", "")) + "Client"

  def paramsToURL(params: Seq[Parameter]): String = {
    params.filter(p =>
      p match {
        case path: PathParameter   => true
        case query: QueryParameter => false
        case body: BodyParameter   => false
        case _                     => println("unmanaged parameter please contact the developer to implement it XD"); false
      }).map(":" + _.getName).mkString("/", "/", "")
  }

  def genClientMethod(methodName: String, url: String, opType: String, params: Seq[Parameter], respType: Type): Tree = {
    val bodyParams = getPlainParamsFromBody(params)
    
    val fullBodyParams = getParamsToBody(params)

    val methodParams = getMethodParamas(params)
    
    //probably to be fixed with a custom ordering
    val urlParams =
      params.foldLeft("")((old, np) =>
        np match {
          case path: PathParameter => old// + "/$"+path.getName
          case query: QueryParameter => 
            old +
            (if (old.contains("?")) "&" 
            else "?") + query.getName + "=$" + query.getName 
          case _ => old
        }
      )

    val tree: Tree =
      DEFINFER(methodName) withParams (methodParams.map(_._2) ++ bodyParams.map(_._2)) := BLOCK {
        REF("WS") DOT("url") APPLY(
            
          
          REF("s") APPLY( LIT(cleanDuplicateSlash("$baseUrl/"+cleanPathParams(url)+urlParams)) )
          
        ) DOT(opType) APPLY(fullBodyParams.map(_._2)) DOT("map") APPLY(
            LAMBDA(PARAM("resp")) ==> 
              REF("Json") DOT("parse") APPLY(REF("resp") DOT("body")) DOT("as") APPLYTYPE(respType)
        )
      }
      
    tree
  }

  def genControllerMethod(methodName: String, params: Seq[Parameter], async: Boolean /*, respType: Type*/ ): Tree = {
    val bodyParams = getParamsFromBody(params)

    val methodParams = getMethodParamas(params)
    
    val ACTION = 
      if (!async) REF("Action")
      else REF("Action.async")
      
    val ANSWARE = 
      if (!async)
        (REF("Ok") APPLY (
          REF("Json") DOT ("toJson") APPLY (
            REF(methodName + "Impl") APPLY ((methodParams ++ bodyParams).map(x => REF(x._1))))))
      else
        REF(methodName + "Impl") APPLY ((methodParams ++ bodyParams).map(x => REF(x._1))) DOT("map") APPLY(
            LAMBDA(PARAM("answare")) ==> REF("Ok") APPLY (REF("Json") DOT ("toJson") APPLY (REF("answare"))))

    val ERROR = 
        if (!async)
          REF("BadRequest") APPLY (REF("onError") APPLY (LIT(methodName), REF("err")))
        else
          REF("onError") APPLY (LIT(methodName), REF("err")) DOT("map") APPLY(
              LAMBDA(PARAM("errAnsware")) ==> REF("BadRequest") APPLY (REF("errAnsware")))
              
    val BODY_WITH_EXCEPTION_HANDLE =
      if (!async)
        (TRY {
        BLOCK {
          (bodyParams.map(_._2): Seq[Tree]) :+
          ANSWARE
        }
        } CATCH (
          CASE(REF("err") withType (RootClass.newClass("Throwable"))) ==> BLOCK {
            REF("err") DOT ("printStackTrace")
            ERROR
        }) ENDTRY)
      else
         BLOCK {
          (bodyParams.map(_._2): Seq[Tree]) :+
          ANSWARE 
         } DOT ("recoverWith") APPLY BLOCK (CASE (REF("err") withType (RootClass.newClass("Throwable"))) ==> BLOCK {
           REF("err") DOT ("printStackTrace")
           ERROR
         })
              
    val tree: Tree =
      DEFINFER(methodName) withParams (methodParams.map(_._2)) := BLOCK {
        ACTION APPLY {
          LAMBDA(PARAM("request")) ==>
            BODY_WITH_EXCEPTION_HANDLE
        }
      }

    //val treeTrait = 
    //   PROC(methodName+"Impl") withParams ((methodParams ++ bodyParams).map(_._2))

    tree
  }

  def getParamsFromBody(params: Seq[Parameter]): Map[String, ValDef] = {
    params.filter(p =>
      p match {
        case path: PathParameter   => false
        case query: QueryParameter => false
        case body: BodyParameter   => true
        case _                     => println("unmanaged parameter please contact the developer to implement it XD"); false
      }).map(p =>
      p match {
        case bp: BodyParameter =>
          //for sure it is not enough ...
          val paramType = bp.getSchema.getReference

          val tree: ValDef = VAL(bp.getName) :=
            REF("Json") DOT ("fromJson") APPLYTYPE (bp.getSchema.getReference) APPLY (
              REF("getJsonBody") APPLY (REF("request"))) DOT ("get")

          Some(bp.getName -> tree)
        case _ =>
          None
      }).flatten.toMap
  }
  
  def getParamsToBody(params: Seq[Parameter]): Map[String, Tree] = {
    params.filter(p =>
      p match {
        case path: PathParameter   => false
        case query: QueryParameter => false
        case body: BodyParameter   => true
        case _                     => println("unmanaged parameter please contact the developer to implement it XD"); false
      }).map(p =>
      p match {
        case bp: BodyParameter =>
          //for sure it is not enough ...
          val paramType = bp.getSchema.getReference

          val tree =  REF("Json") DOT ("toJson") APPLY (REF(bp.getName))

          Some(bp.getName -> tree)
        case _ =>
          None
      }).flatten.toMap
  }
  
  def getPlainParamsFromBody(params: Seq[Parameter]): Map[String, ValDef] = {
    params.filter(p =>
      p match {
        case path: PathParameter   => false
        case query: QueryParameter => false
        case body: BodyParameter   => true
        case _                     => println("unmanaged parameter please contact the developer to implement it XD"); false
      }).map(p =>
      p match {
        case bp: BodyParameter =>
          //for sure it is not enough ...
          val paramType = bp.getSchema.getReference

          val tree: ValDef = PARAM(bp.getName, RootClass.newClass(paramType))

          Some(bp.getName -> tree)
        case _ =>
          None
      }).flatten.toMap
  }

  def getMethodParamas(params: Seq[Parameter]): Map[String, ValDef] = {
    params.filter(p =>
      p match {
        case path: PathParameter   => true
        case query: QueryParameter => true
        case body: BodyParameter   => false
        case _                     => println("unmanaged parameter please contact the developer to implement it XD"); false
      }).sortWith((p1, p2) => //the order musty be verified...
      p1 match {
        case _: PathParameter =>
          p2 match {
            case _: PathParameter  => true
            case _: QueryParameter => true
            case _                 => true
          }
        case _: QueryParameter =>
          p2 match {
            case _: PathParameter  => false
            case _: QueryParameter => true
            case _                 => true
          }
        case _ => true
      }).map(p => {
      (p.getName,
        (p match {
          case pp: PathParameter =>
            PARAM(pp.getName, paramType(pp))
          //REF(pp.getName) withType(paramType(pp))
          case qp: QueryParameter =>
            if (qp.getDefaultValue == null)
              PARAM(qp.getName, paramType(qp))
            //REF(qp.getName) withType(paramType(qp))
            else
              PARAM(qp.getName, paramType(qp))
          //cannot manage properly default params 'till now...
          //:= LIT(qp.getDefaultValue)
          //REF(qp.getName) withType(paramType(qp)) := LIT(qp.getDefaultValue)
        }): ValDef)
    }).toMap
  }

  def genMethodCall(className: String, methodName: String, params: Seq[Parameter]): String = {
    val tree: Tree = REF(className) DOT (methodName) APPLY (getMethodParamas(params).map(_._2))

    //Sorry this is a dirty work around
    /*
    treeToString(tree).toCharArray().foldLeft("")((old, nc) => {
      if ((nc == '(' && old.endsWith("(")) ||
          (nc == ')' && old.endsWith(")"))) old
      else old + nc
    })
    */
    //please refactor treehugger to get custom pretty printers
    //original
    treeToString(tree).replace("val ", "")
    /*
    implicit val customPrinter: Option[(treehugger.forest.TreePrinter) => PartialFunction[Tree, Unit]] =
      Some(
        (tp) => {
         case tree @ ValDef(mods, lhs, rhs) =>
          tp.printAnnotations(tree)
          tp.printModifiers(tree, mods)
          
          //in this case I will avoid the print of val
          //print(if (mods.isMutable) "var " else "val ")
          
          // , symName(tree, name)
          lhs match {
            case Typed(expr, tpt) => tp.print(expr, ": ", tpt)
            case _ => tp.print(lhs)
          }
          if (!mods.isDeferred && !rhs.isEmpty)
            tp.print(" = ", rhs)

        }
      )
     
    treeToString(tree)(customPrinter)
    */
  }

  val yodaDateTimeClass = RootClass.newClass("DateTime")

  def basicTypes: PartialFunction[Property, Type] = {
    case s: StringProperty =>
      StringClass
    case b: BooleanProperty =>
      BooleanClass
    case d: DoubleProperty =>
      DoubleClass
    case f: FloatProperty =>
      FloatClass
    case i: IntegerProperty =>
      IntClass
    case l: LongProperty =>
      LongClass
  }

  def paramType(p: AbstractSerializableParameter[_]): Type = {

    def complexTypes: PartialFunction[Property, Type] = {
      case a: ArrayProperty =>
        ListClass TYPE_OF baseType(p.getItems)
      case d: DecimalProperty =>
        BigDecimalClass
      case r: RefProperty =>
        RootClass.newClass(r.getSimpleRef)
      case any =>
        any match {
          case ar: AnyRef =>
            if (ar eq null)
              throw new Exception("Trying to resolve null class " + any + " for property "+any.getName)
            else
              AnyClass
          case a =>
            throw new Exception("Unmanaged primitive type " + a + " for property "+any.getName)
        }
    }

    def baseType(_p: Property): Type =
      basicTypes.orElse(complexTypes)(_p)

    val prop =
      PropertyBuilder.build(p.getType, p.getFormat, null)
      
    if (p.getRequired) baseType(prop)
    else OptionClass TYPE_OF baseType(prop)
  }

  def propType(p: Property): Type = {

    def complexTypes: PartialFunction[Property, Type] = {
      case m: MapProperty =>
        RootClass.newClass("Map") TYPE_OF (StringClass, baseType(m.getAdditionalProperties))
      case a: ArrayProperty =>
        ListClass TYPE_OF baseType(a.getItems)
      case d: DecimalProperty =>
        BigDecimalClass
      case r: RefProperty =>
        RootClass.newClass(r.getSimpleRef)
      case any =>
        any match {
          case ar: AnyRef =>
            if (ar eq null)
              throw new Exception("Trying to resolve null class " + any + " for property "+any.getName)
            else {
              AnyClass
            }
          case a =>
            throw new Exception("Unmanaged primitive type " + a + " for property "+any.getName)
        }
    }

    def baseType(_p: Property): Type =
      basicTypes.orElse(complexTypes)(_p)

    if (p.getRequired) baseType(p)
    else OptionClass TYPE_OF baseType(p)
  }

}

trait StringUtils {

  def sanitizePath(s: String, replaceChar: Char) =
    s.toCharArray().foldLeft(("", false))((old, nc) => {
      if (old._2 && nc != '}') (old._1 + nc, old._2)
      else if (old._2 && nc == '}') (old._1, false)
      else if (!old._2 && nc == '{') (old._1 + replaceChar, true)
      else (old._1 + nc, false)
    })._1.trim()

  def cleanDuplicateSlash(s: String) =
    s.toCharArray().foldLeft("")((old, nc) => {
      if (nc == '/' && old.endsWith("/")) old
      else old + nc
    })
    
  def cleanUrl(s: String) = {
    val str = 
      s.replace("/?","?")
      
    if (str.endsWith("/"))
      str.substring(0, str.length()-1)
    else
      str
  }
    
  def cleanPathParams(s: String) =
    s.toCharArray().foldLeft("")((old, nc) => {
      if (nc == ':') old + '$'
      else old + nc
    }).trim()

  def capitalize(s: String) = {
    val ca = s.toCharArray()
    val first = ca(0).toString.toUpperCase.getBytes()(0).toChar
    val rest = ca.toList.drop(1)
    new String((first +: rest).toArray[Char])
  }

  def empty(n: Int): String =
    new String((for (i <- 1 to n) yield ' ').toArray)

  def trimTo(n: Int, s: String): String =
    new String((empty(n).zipAll(s, ' ', ' ').map(_._2)).toArray)

}
