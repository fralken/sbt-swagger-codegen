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
package eu.unicredit.swagger.generators

import treehugger.forest._
import definitions._
import treehuggerDSL._

import io.swagger.parser.SwaggerParser
import io.swagger.models._
import io.swagger.models.parameters._
import scala.collection.JavaConversions._

class DefaultServerGenerator extends ServerGenerator with SharedServerClientCode {

  def controllerNameFromFileName(fn: String) = objectNameFromFileName(fn, "Controller")

  override def generateRoutes(fileName: String, packageName: String): Option[String] = {
    val swagger = new SwaggerParser().read(fileName)

    val basePath = Option(swagger.getBasePath).getOrElse("/")

    val completePaths =
      swagger.getPaths.keySet().toSeq

    def composeRoutes(p: String): Seq[String] = {
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
          doUrl(basePath, p)

        val methodName =
          op._2.getOperationId

        def genMethodCall(className: String, methodName: String, params: Seq[Parameter]): String = {
          val p = getMethodParamas(params).map { case (n, v) => s"$n: ${treeToString(v.tpt)}" }
          // since it is a route definition, this is not Scala code, so we generate it manually
          s"$className.$methodName" + p.mkString("(", ", ", ")")
        }

        val methodCall =
          genMethodCall(controllerName, methodName, op._2.getParameters)

        s"${trimTo(8, op._1)}            ${trimTo(50, url)}          ${trimTo(20, methodCall)}"
      }).toSeq
    }

    val routes =
      completePaths.flatMap(composeRoutes)

    if (routes.nonEmpty) Some(routes.mkString("\n\n", "\n\n", "\n"))
    else None
  }

  def generateImports(packageName: String, codeProvidedPackage: String, controllerName: String): Seq[Tree] =
      Seq(
        IMPORT(packageName, "_"),
        IMPORT(packageName + ".json", "_"),
        IMPORT("play.api.mvc.Results", "_"),
        IMPORT("play.api.mvc", "_"),
        IMPORT("play.api.libs.json", "_"),
        IMPORT(codeProvidedPackage, controllerName + "Impl")
      )

  def generate(fileName: String, packageName: String, codeProvidedPackage: String): Iterable[SyntaxString] = {
    val swagger = new SwaggerParser().read(fileName)

    val controllerPackageName =
      packageName + ".controller"

    val controllerName =
      controllerNameFromFileName(fileName)

    val completePaths =
      swagger.getPaths.keySet().toSeq

    def composeController(p: String): Seq[Tree] = {
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

        try
        if (!op.
            getProduces.
            forall(_ == "application/json"))
          println("WARNING - only 'application/json' is supported")
        catch {
          case _ : Throwable =>
        }

        val methodName =
          op.getOperationId

        val okRespType =
          respType(op.getResponses.get).
          find(x => x._2 ne null).
            map(x => x._1 ->
              Option(x._2.getSchema).map(y => noOptPropType(y)))

        val methodCall =
          genControllerMethod(methodName, op.getParameters, okRespType.get)

        if (okRespType.isEmpty)
          throw new Exception(s"cannot determine Ok result type for $methodName")

        methodCall
      }
    }

    val imports =
      BLOCK {
        generateImports(packageName, codeProvidedPackage, controllerName)
      } inPackage controllerPackageName

    val tree =
        OBJECTDEF(controllerName) withParents (controllerName + "Impl") := BLOCK(
          completePaths.map(composeController).flatten)

    Seq(SyntaxString(controllerName, treeToString(imports), treeToString(tree)))
  }

  def genControllerMethod(methodName: String, params: Seq[Parameter], resType: (String, Option[Type])): Tree = {
    val bodyParams = getParamsFromBody(params)

    val methodParams = getMethodParamas(params)

    val ACTION =
      REF("Action")

    val ANSWER =
        resType._2.map{ typ =>
          REF(resType._1) APPLY (
            REF("Json") DOT "toJson" APPLYTYPE typ APPLY (
              REF(methodName + "Impl") APPLY (methodParams ++ bodyParams).map(x => REF(x._1)))
          )
        }.getOrElse(
          BLOCK {Seq(
            REF(methodName + "Impl") APPLY (methodParams ++ bodyParams).map(x => REF(x._1)),
            REF(resType._1)
          )}
        )

    val ERROR =
        REF("BadRequest") APPLY (REF("onError") APPLY (LIT(methodName), REF("err")))

    val BODY_WITH_EXCEPTION_HANDLE =
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
      case body: BodyParameter => true
      case _ => false
    }.flatMap {
      case bp: BodyParameter =>

        val tree: ValDef = VAL(bp.getName) :=
          REF("Json") DOT "fromJson" APPLYTYPE noOptParamType(bp) APPLY (
            REF("request") DOT "body" DOT "asJson" DOT "get") DOT "get"

        Some(bp.getName -> tree)
      case _ =>
        None
    }.toMap
  }
}

class DefaultAsyncServerGenerator extends DefaultServerGenerator {

  override def generateImports(packageName: String, codeProvidedPackage: String, controllerName: String): Seq[Tree] =
    super.generateImports(packageName, codeProvidedPackage, controllerName) :+
      IMPORT("play.api.libs.concurrent.Execution.Implicits", "_")

  override def genControllerMethod(methodName: String, params: Seq[Parameter], resType: (String, Option[Type])): Tree = {
    val bodyParams = getParamsFromBody(params)

    val methodParams = getMethodParamas(params)

    val ACTION =
      REF("Action.async")

    val ANSWER =
        REF(methodName + "Impl") APPLY (methodParams ++ bodyParams).map(x => REF(x._1)) DOT "map" APPLY (
          LAMBDA(PARAM("answer")) ==> resType._2.map{ typ =>
            REF(resType._1) APPLY (
              REF("Json") DOT "toJson" APPLYTYPE typ APPLY REF("answer")
            )}.getOrElse(
              REF(resType._1)
            )
        )

    val ERROR =
        REF("onError") APPLY (LIT(methodName), REF("err")) DOT "map" APPLY (
          LAMBDA(PARAM("errAnswer")) ==> REF("BadRequest") APPLY REF("errAnswer"))

    val BODY_WITH_EXCEPTION_HANDLE =
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
}
