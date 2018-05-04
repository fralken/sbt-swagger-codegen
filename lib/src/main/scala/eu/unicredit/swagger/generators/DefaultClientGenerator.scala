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
import eu.unicredit.swagger.StringUtils._

import io.swagger.parser.SwaggerParser
import io.swagger.models._
import io.swagger.models.parameters._
import scala.collection.JavaConverters._

class DefaultClientGenerator extends ClientGenerator with SharedServerClientCode {

  def clientNameFromFileName(fn: String): String = objectNameFromFileName(fn, "Client")

  /** Errors when the given string does not start with capital letter */
  def lowerFirstLetter(pascal: String): String = pascal.toList match {
    case firstLetter :: rest if firstLetter.isUpper => (firstLetter.toLower :: rest).mkString
  }

  def generate(fileName: String, packageName: String): Iterable[SyntaxString] = {
    val swagger = new SwaggerParser().read(fileName)

    val basePath = Option(swagger.getBasePath).getOrElse("/")

    val clientPackageName =
      packageName + ".client"

    val clientName =
      clientNameFromFileName(fileName)

    val clientConfigName =
      s"${clientName}Config"

    val clientConfigMemberName =
      lowerFirstLetter(clientConfigName)

    val completePaths =
      swagger.getPaths.keySet().asScala.toList

    def composeClient(p: String): Seq[Tree] = {
      val path = swagger.getPath(p)
      if (path == null) return Seq()

      val ops: Seq[(String, Operation)] =
        Seq(Option(path.getDelete) map ("delete" -> _),
          Option(path.getGet) map ("get" -> _),
          Option(path.getPost) map ("post" -> _),
          Option(path.getPut) map ("put" -> _)).flatten

      for {
        (verb, op) <- ops
      } yield {
        val methodName =
          if (op.getOperationId != null) op.getOperationId
          else throw new Exception("Please provide an operationId in: " + p)

        val okRespType: (String, Option[Type]) =
          getOkRespType(op) getOrElse {
            throw new Exception(s"cannot determine Ok result type for $methodName")
          }

        val url = doUrl(basePath, p)

        genClientMethod(methodName, url, verb, op.getParameters.asScala.toList, okRespType)
      }
    }

    val imports =
      BLOCK {
        Seq(
          IMPORT(packageName, "_"),
          IMPORT(packageName + ".json", "_"),
          IMPORT("play.api.libs.ws", "_"),
          IMPORT("play.api.libs.ws.JsonBodyWritables", "_"),
          IMPORT("play.api.libs.json", "_"),
          IMPORT("javax.inject", "_"),
          IMPORT("scala.concurrent.ExecutionContext")
        )
      } inPackage clientPackageName

    def RENDER_SCHEME_MEMBER: Tree =
      VAL("scheme", StringClass).withFlags(Flags.PRIVATE) :=
        IF(REF(clientConfigMemberName) DOT "ssl") THEN LIT("https") ELSE LIT("http")

    def RENDER_BASE_URL_MEMBER: Tree =
      VAL("baseUrl", StringClass) := INTERP(
        StringContext_s,
        REF("scheme"),
        LIT("://"),
        REF(clientConfigMemberName) DOT "host",
        LIT(":"),
        REF(clientConfigMemberName) DOT "port"
      )

    val RENDER_URL_PARAMS: Tree =
      DEFINFER("_render_url_params") withFlags Flags.PRIVATE withParams PARAM(
        "pairs",
        TYPE_*(TYPE_TUPLE(StringClass, OptionClass TYPE_OF AnyClass))) := BLOCK(
        Seq(
          VAL("parts") := (
            REF("pairs")
              DOT "collect" APPLY BLOCK(
              CASE(TUPLE(ID("k"), REF("Some") UNAPPLY ID("v"))) ==> (REF("k") INFIX ("+", LIT("=")) INFIX ("+", REF(
                "v"))))
            ),
          IF(REF("parts") DOT "nonEmpty")
            THEN (
            REF("parts") DOT "mkString" APPLY (LIT("?"), LIT("&"), LIT(""))
            )
            ELSE LIT("")
        ))

    val clientConfigTree = CASECLASSDEF(clientConfigName)
      .withParams(
        PARAM("host", StringClass) := LIT("localhost"),
        PARAM("port", IntClass),
        PARAM("ssl", BooleanClass) := FALSE
      )
      .tree

    val clientMembers = List(RENDER_SCHEME_MEMBER, RENDER_BASE_URL_MEMBER)
    val clientHttpMethods = completePaths.flatMap(composeClient)

    val tree = CLASSDEF(s"$clientName @Inject() (WS: StandaloneWSClient, $clientConfigMemberName: $clientConfigName)")
      .withParams(PARAM("ec", "ExecutionContext") withFlags Flags.IMPLICIT) := BLOCK {
      clientMembers ::: clientHttpMethods
    }

    Seq(SyntaxString(clientName + ".scala", treeToString(imports), treeToString(clientConfigTree, "", tree)))
  }

  def genClientMethod(methodName: String,
                      url: String,
                      opType: String,
                      params: Seq[Parameter],
                      respType: (String, Option[Type])): Tree = {
    val bodyParams = getBodyParams(params)

    if (bodyParams.size > 1) throw new Exception(s"Only one parameter in body is allowed in method $methodName")

    val bodyParamsToBody = getParamsToBody(params)

    val methodParams = getMethodParams(params)

    //probably to be fixed with a custom ordering
    val urlParams: Seq[Tree] =
      params collect {
        case query: QueryParameter =>
          val name = query.getName
          LIT(name) INFIX ("->",
            if (query.getRequired) REF("Some") APPLY REF(name)
            else REF(name))
      }

    val RuntimeExceptionClass =
      definitions.getClass("java.lang.RuntimeException")

    val headerParams: Seq[Tree] =
      params collect {
        case param: HeaderParameter =>
          val name = if (param.getName.contains("-")) s"`${param.getName}`" else param.getName
          LIT(param.getName) INFIX ("->",
            if (param.getRequired)  REF(name)
            else REF(name))
      }

    val baseUrl =
      INTERP("s", LIT(cleanDuplicateSlash("$baseUrl/" + cleanPathParams(url))))
    val baseUrlWithParams =
      if (urlParams.isEmpty)
        baseUrl
      else
        baseUrl INFIX ("+", THIS DOT "_render_url_params" APPLY (urlParams: _*))

    val wsUrl = REF("WS") DOT "url" APPLY baseUrlWithParams
    val wsUrlWithHeaders =
      if (headerParams.isEmpty)
        wsUrl
      else {
        wsUrl DOT "addHttpHeaders" APPLY (headerParams: _*)
      }
    val tree: Tree =
      DEFINFER(methodName) withParams (methodParams.values ++ bodyParams) := BLOCK(
        wsUrlWithHeaders DOT opType APPLY bodyParamsToBody.values DOT "map" APPLY (
          LAMBDA(PARAM("resp")) ==> BLOCK {
            Seq(
              IF(
                INFIX_CHAIN(
                  "&&",
                  PAREN(REF("resp") DOT "status" INFIX (">=", LIT(200))),
                  PAREN(REF("resp") DOT "status" INFIX ("<=", LIT(299)))
                )
              ).THEN(
                respType._2.map { typ =>
                {
                  REF("Json") DOT "parse" APPLY (REF("resp") DOT "body") DOT
                    "as" APPLYTYPE typ
                }
                }.getOrElse(REF("Unit"))
              )
                .ELSE(
                  THROW(
                    RuntimeExceptionClass,
                    INTERP(
                      StringContext_s,
                      LIT("unexpected response status: "),
                      REF("resp") DOT "status",
                      LIT(" "),
                      REF("resp") DOT "body"
                    )
                  )
                )
            )
          }
          ))

    tree
  }

  def getParamsToBody(params: Seq[Parameter]): Map[String, Tree] =
    params.collect {
      case bp: BodyParameter =>
        val tree = REF("Json") DOT "toJson" APPLY REF(bp.getName)
        bp.getName -> tree
    }.toMap

  def getBodyParams(params: Seq[Parameter]): Seq[ValDef] =
    params.collect {
      case bp: BodyParameter =>
        val tree: ValDef = PARAM(bp.getName, noOptParamType(bp))
        tree
    }
}
