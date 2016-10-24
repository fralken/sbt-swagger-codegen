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

class DefaultClientGenerator extends ClientGenerator with SharedServerClientCode {

  def clientNameFromFileName(fn: String) = objectNameFromFileName(fn, "Client")

  def generate(fileName: String, packageName: String): Iterable[SyntaxString] = {
    val swagger = new SwaggerParser().read(fileName)

    val basePath = Option(swagger.getBasePath).getOrElse("/")

    val clientPackageName =
      packageName + ".client"

    val clientName =
      clientNameFromFileName(fileName)

    val completePaths =
      swagger.getPaths.keySet().toSeq

    def composeClient(p: String): Seq[Tree] = {
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

        val okRespType =
          respType(swaggerOp.getResponses.get).
          find(x => x._2 ne null).
            map(x => x._1 ->
              Option(x._2.getSchema).map(y => noOptPropType(y)))

        val methodName =
          if (op._2.getOperationId != null) op._2.getOperationId
          else throw new Exception("Please provide an operationId in: " + p)

        if (okRespType.isEmpty)
          throw new Exception(s"cannot determine Ok result type for $methodName")

        val opType =
          op._1.toLowerCase

        val url =
          doUrl(basePath, p)

        val methodCall =
          genClientMethod(methodName, url, opType, op._2.getParameters, okRespType.get)

        methodCall
      }
    }

    val imports =
      BLOCK {
        Seq(
          IMPORT(packageName, "_"),
          IMPORT(packageName + ".json", "_"),
          IMPORT("play.api.libs.ws", "_"),
          IMPORT("play.api.libs.json", "_"),
          IMPORT("javax.inject", "_"),
          IMPORT("play.api.libs.concurrent.Execution.Implicits", "_")
        )
      } inPackage clientPackageName

    val classDef = CLASSDEF(clientName).empty
    val params1 = "@Inject() (WS: WSClient)"
    val params2 = (CLASSDEF("") withParams PARAM("baseUrl", StringClass)).empty

    val UNFOLD: Tree =
      DEFINFER("unfold") withParams (PARAM("x", OptionClass TYPE_OF RootClass.newClass("Any"))) := BLOCK {
        REF("x") DOT "map" APPLY {
          LAMBDA(PARAM("y")) ==> BLOCK {
            INTERP("s", LIT("$x=$y"))
          }
        } DOT "getOrElse" APPLY NEW("String")
      }

    val body = BLOCK {
      UNFOLD +:
      completePaths.map(composeClient).flatten
    }

    Seq(SyntaxString(clientName, treeToString(imports), treeToString(classDef) + " " + params1 + treeToString(params2).replace("class", "") + " " + treeToString(body)))
  }

  def genClientMethod(methodName: String, url: String, opType: String, params: Seq[Parameter], respType: (String, Option[Type])): Tree = {
    val bodyParams = getPlainParamsFromBody(params)

    val fullBodyParams = getParamsToBody(params)

    val methodParams = getMethodParamas(params)

    //probably to be fixed with a custom ordering
    val urlParams =
      params.foldLeft("")((old, np) =>
        np match {
          case path: PathParameter => old
          case query: QueryParameter =>
            val pre =
              (if (old.contains("?")) "&" else "?")

            val queryValue =
              if (query.getRequired) query.getName + "=$" + query.getName
              else
                "${unfold(" + query.getName + ")}"

            old + pre + queryValue
          case _ => old
        })

    val tree: Tree =
      DEFINFER(methodName) withParams (methodParams.values ++ bodyParams.values) := BLOCK(
        REF("WS") DOT "url" APPLY
          INTERP("s", LIT(cleanDuplicateSlash("$baseUrl/" + cleanPathParams(url) + urlParams))) DOT opType APPLY fullBodyParams.values DOT "map" APPLY (
            LAMBDA(PARAM("resp")) ==> BLOCK {
              Seq(
                REF("assert") APPLY INFIX_CHAIN("&&",
                  PAREN(REF("resp") DOT "status" INFIX(">", LIT(199))),
                  REF("resp") DOT "status" INFIX("<", LIT(300))
                ),
                respType._2.map{ typ => {
                  REF("Json") DOT "parse" APPLY (REF("resp") DOT "body") DOT
                    "as" APPLYTYPE typ
                  }}.getOrElse(REF("Unit"))
              )
            }
          ))

    tree
  }

  //the next two methods have to be refactored
  def getParamsToBody(params: Seq[Parameter]): Map[String, Tree] = {
    params.filter {
      case body: BodyParameter => true
      case _ => false
    }.flatMap {
      case bp: BodyParameter =>

        val tree = REF("Json") DOT "toJson" APPLY REF(bp.getName)

        Some(bp.getName -> tree)
      case _ =>
        None
    }.toMap
  }

  def getPlainParamsFromBody(params: Seq[Parameter]): Map[String, ValDef] = {
    params.filter {
      case body: BodyParameter => true
      case _ => false
    }.flatMap {
      case bp: BodyParameter =>
        val tree: ValDef = PARAM(bp.getName, noOptParamType(bp))

        Some(bp.getName -> tree)
      case _ =>
        None
    }.toMap
  }
}
