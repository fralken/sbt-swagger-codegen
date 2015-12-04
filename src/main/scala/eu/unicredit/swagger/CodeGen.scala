/* Copyright 2015 UniCredit S.p.A.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package eu.unicredit.swagger

import treehugger.forest._
import definitions._
import treehuggerDSL._

import io.swagger.parser.SwaggerParser
import io.swagger.models.properties._
import io.swagger.models._
import io.swagger.models.parameters._
import scala.collection.JavaConversions._

import java.io.File.separator
import java.io.File.separatorChar

object CodeGen extends SwaggerToTree with StringUtils {

  def generatePlayServerRoutes(fileName: String, packageName: String): Seq[String] = {
    val swagger = new SwaggerParser().read(fileName)

    val basePath = Option(swagger.getBasePath).getOrElse("/")

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

    completePaths.flatMap(composePlayRoutes)
  }

  def generatePlayServerStub(fileName: String, packageName: String, codeProvidedPackage: String, async: Boolean): (String, String) = {
    val swagger = new SwaggerParser().read(fileName)

    val controllerPackageName =
      packageName + ".controller"

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

      for {
        op <- ops
      } yield {

        val methodName =
          op.getOperationId

        val methodCall =
          genControllerMethod(methodName, op.getParameters, async)

        methodCall
      }
    }

    val imports: Seq[Tree] =
      Seq(
        IMPORT(packageName, "_"),
        IMPORT(packageName + ".json", "_"),
        IMPORT("play.api.mvc.Results", "_"),
        IMPORT("play.api.mvc", "_"),
        IMPORT("play.api.libs.json", "_"),
        IMPORT(codeProvidedPackage, controllerName + "Impl")) ++
        {
          if (!async) Nil
          else Seq(IMPORT("play.api.libs.concurrent.Execution.Implicits", "_"))
        }

    val tree =
      BLOCK {
        imports :+
          (OBJECTDEF(controllerName) withParents (controllerName + "Impl") := BLOCK(
            completePaths.map(composePlayController).flatten))
      } inPackage controllerPackageName

    controllerName -> treeToString(tree)
  }

  def generatePlayClientStub(fileName: String, packageName: String): (String, String) = {
    val swagger = new SwaggerParser().read(fileName)

    val basePath = Option(swagger.getBasePath).getOrElse("/")

    val clientPackageName =
      packageName + ".client"

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

      for {
        op <- ops
      } yield {
        val (httpVerb, swaggerOp) = op
        val resps = swaggerOp.getResponses
        if (resps == null || resps.keySet().isEmpty) {
          throw new RuntimeException(s"""Can not generate Play client code for endpoint '$httpVerb $p' because no responses were provided in the swagger spec. Write at least one response for that path to fix this error. See the swagger spec: https://github.com/swagger-api/swagger-spec/blob/master/versions/2.0.md#pathsObject""")
        }
        val fallbackResp = {
          val key = resps.keySet.min
          key -> resps(resps.keySet().toSeq.min)
        }

        val okResp =
          resps.find(x => x._1 == "200") getOrElse (
            resps.find(x => x._1 == "default") getOrElse
            fallbackResp)

        val retSchema = okResp._2.getSchema

        //work only like this at the moment
        try {
          retSchema.setRequired(true)
        } catch {
          case ex: NullPointerException =>
            throw new Exception("Only valid schema are supported in default/200 answer in: " + p)
        }

        val respType = propType(retSchema, true)

        val methodName =
          if (op._2.getOperationId != null) op._2.getOperationId
          else throw new Exception("Please provide an operationId in: " + p)

        val opType =
          op._1.toLowerCase

        val url =
          doUrl(basePath, p, op._2.getParameters.toList)

        val methodCall =
          genClientMethod(methodName, url, opType, op._2.getParameters, respType)

        methodCall
      }
    }

    val imports: Seq[Tree] =
      Seq(
        IMPORT(packageName, "_"),
        IMPORT(packageName + ".json", "_"),
        IMPORT("play.api.libs.ws", "_"),
        IMPORT("play.api.libs.json", "_"),
        IMPORT("play.api.Play", "current"),
        IMPORT("play.api.libs.concurrent.Execution.Implicits", "_"))

    val tree =
      BLOCK {
        imports :+
          (CLASSDEF(clientName) withParams PARAM("baseUrl", StringClass) := BLOCK(
            completePaths.map(composePlayClient).flatten))
      } inPackage clientPackageName

    clientName -> treeToString(tree)
  }

}

trait SwaggerToTree extends SwaggerConversion {
  self: StringUtils =>

  def doUrl(basePath: String, path: String, parameters: List[Parameter]) = {

    cleanUrl(
      cleanDuplicateSlash(
        basePath + sanitizePath(path, ':')))
  }

  def objectNameFromFileName(fn: String, obj: String) = {
    val sep = if (separatorChar == 92.toChar) "\\\\" else separator
    fn.split(sep).toList.last.replace(".yaml", "").replace(".json", "").capitalize + obj
  }

  def controllerNameFromFileName(fn: String) = objectNameFromFileName(fn, "Controller")

  def clientNameFromFileName(fn: String) = objectNameFromFileName(fn, "Client")

  def paramsToURL(params: Seq[Parameter]): String = {
    params.filter {
      case path: PathParameter => true
      case query: QueryParameter => false
      case body: BodyParameter => false
      case _ => println("unmanaged parameter please contact the developer to implement it XD"); false
    }.map(":" + _.getName).mkString("/", "/", "")
  }

  def genClientMethod(methodName: String, url: String, opType: String, params: Seq[Parameter], respType: Type): Tree = {
    val bodyParams = getPlainParamsFromBody(params)

    val fullBodyParams = getParamsToBody(params)

    val methodParams = getMethodParamas(params)

    //probably to be fixed with a custom ordering
    val urlParams =
      params.foldLeft("")((old, np) =>
        np match {
          case path: PathParameter => old
          case query: QueryParameter =>
            old +
              (if (old.contains("?")) "&"
              else "?") + query.getName + "=$" + query.getName
          case _ => old
        })

    val tree: Tree =
      DEFINFER(methodName) withParams (methodParams.values ++ bodyParams.values) := BLOCK(
        REF("WS") DOT "url" APPLY
          INTERP("s", LIT(cleanDuplicateSlash("$baseUrl/" + cleanPathParams(url) + urlParams))) DOT opType APPLY fullBodyParams.values DOT "map" APPLY (
            LAMBDA(PARAM("resp")) ==>
            REF("Json") DOT "parse" APPLY (REF("resp") DOT "body") DOT "as" APPLYTYPE respType))

    tree
  }

  def genControllerMethod(methodName: String, params: Seq[Parameter], async: Boolean): Tree = {
    val bodyParams = getParamsFromBody(params)

    val methodParams = getMethodParamas(params)

    val ACTION =
      if (!async) REF("Action")
      else REF("Action.async")

    val ANSWER =
      if (!async)
        REF("Ok") APPLY (
          REF("Json") DOT "toJson" APPLY (
            REF(methodName + "Impl") APPLY (methodParams ++ bodyParams).map(x => REF(x._1))))
      else
        REF(methodName + "Impl") APPLY (methodParams ++ bodyParams).map(x => REF(x._1)) DOT "map" APPLY (
          LAMBDA(PARAM("answer")) ==> REF("Ok") APPLY (REF("Json") DOT "toJson" APPLY REF("answer")))

    val ERROR =
      if (!async)
        REF("BadRequest") APPLY (REF("onError") APPLY (LIT(methodName), REF("err")))
      else
        REF("onError") APPLY (LIT(methodName), REF("err")) DOT "map" APPLY (
          LAMBDA(PARAM("errAnswer")) ==> REF("BadRequest") APPLY REF("errAnswer"))

    val BODY_WITH_EXCEPTION_HANDLE =
      if (!async)
        TRY {
          BLOCK {
            (bodyParams.values: Seq[Tree]) :+
              ANSWER
          }
        } CATCH (
          CASE(REF("err") withType RootClass.newClass("Throwable")) ==> BLOCK {
            REF("err") DOT "printStackTrace"
            ERROR
          }) ENDTRY
      else
        BLOCK {
          (bodyParams.values: Seq[Tree]) :+
            ANSWER
        } DOT "recoverWith" APPLY BLOCK(CASE(REF("err") withType RootClass.newClass("Throwable")) ==> BLOCK {
          REF("err") DOT "printStackTrace"
          ERROR
        })

    val tree: Tree =
      DEFINFER(methodName) withParams methodParams.values := BLOCK {
        ACTION APPLY {
          LAMBDA(PARAM("request")) ==>
            BODY_WITH_EXCEPTION_HANDLE
        }
      }

    tree
  }

  def getParamsFromBody(params: Seq[Parameter]): Map[String, ValDef] = {
    params.filter {
      case path: PathParameter => false
      case query: QueryParameter => false
      case body: BodyParameter => true
      case _ => println("unmanaged parameter please contact the developer to implement it XD"); false
    }.flatMap {
      case bp: BodyParameter =>
        //for sure it is not enough ...
        val paramType = bp.getSchema.getReference

        val tree: ValDef = VAL(bp.getName) :=
          REF("Json") DOT "fromJson" APPLYTYPE bp.getSchema.getReference APPLY (
            REF("getJsonBody") APPLY REF("request")) DOT "get"

        Some(bp.getName -> tree)
      case _ =>
        None
    }.toMap
  }

  def getParamsToBody(params: Seq[Parameter]): Map[String, Tree] = {
    params.filter {
      case path: PathParameter => false
      case query: QueryParameter => false
      case body: BodyParameter => true
      case _ => println("unmanaged parameter please contact the developer to implement it XD"); false
    }.flatMap {
      case bp: BodyParameter =>
        //for sure it is not enough ...
        val paramType = bp.getSchema.getReference

        val tree = REF("Json") DOT "toJson" APPLY REF(bp.getName)

        Some(bp.getName -> tree)
      case _ =>
        None
    }.toMap
  }

  def getPlainParamsFromBody(params: Seq[Parameter]): Map[String, ValDef] = {
    params.filter {
      case path: PathParameter => false
      case query: QueryParameter => false
      case body: BodyParameter => true
      case _ => println("unmanaged parameter please contact the developer to implement it XD"); false
    }.flatMap {
      case bp: BodyParameter =>
        //for sure it is not enough ...
        val paramType = bp.getSchema.getReference

        val tree: ValDef = PARAM(bp.getName, RootClass.newClass(paramType))

        Some(bp.getName -> tree)
      case _ =>
        None
    }.toMap
  }

  def getMethodParamas(params: Seq[Parameter]): Map[String, ValDef] = {
    params.filter {
      case path: PathParameter => true
      case query: QueryParameter => true
      case body: BodyParameter => false
      case _ => println("unmanaged parameter please contact the developer to implement it XD"); false
    }.sortWith((p1, p2) => //the order musty be verified...
      p1 match {
        case _: PathParameter =>
          p2 match {
            case _: PathParameter => true
            case _: QueryParameter => true
            case _ => true
          }
        case _: QueryParameter =>
          p2 match {
            case _: PathParameter => false
            case _: QueryParameter => true
            case _ => true
          }
        case _ => true
      }).map(p => {
      (p.getName,
        (p match {
          case pp: PathParameter =>
            PARAM(pp.getName, paramType(pp))
          case qp: QueryParameter =>
            if (qp.getDefaultValue == null)
              PARAM(qp.getName, paramType(qp))
            else
              PARAM(qp.getName, paramType(qp))
        }): ValDef)
    }).toMap
  }

  def genMethodCall(className: String, methodName: String, params: Seq[Parameter]): String = {
    val p = getMethodParamas(params).map { case (n, v) => s"$n: ${treeToString(v.tpt)}" }
    // since it is a route definition, this is not Scala code, so we generate it manually
    s"$className.$methodName" + p.mkString("(", ", ", ")")
  }

  val yodaDateTimeClass = RootClass.newClass("DateTime")

}
