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

import eu.unicredit.swagger.StringUtils._
import io.swagger.parser.SwaggerParser
import io.swagger.models._
import io.swagger.models.parameters._

import scala.collection.JavaConverters._
import scala.meta._

class DefaultClientGenerator extends ClientGenerator with SharedServerClientCode {

  def clientNameFromFileName(fn: String): String = objectNameFromFileName(fn, "Client")

  /** Errors when the given string does not start with capital letter */
  def lowerFirstLetter(pascal: String): String = pascal.toList match {
    case firstLetter :: rest if firstLetter.isUpper => (firstLetter.toLower :: rest).mkString
  }

  def generate(fileName: String, packageName: String): Iterable[SyntaxCode] = {
    val swagger = new SwaggerParser().read(fileName)

    val basePath = Option(swagger.getBasePath).getOrElse("/")

    val clientPackageName = packageName + ".client"

    val clientName = clientNameFromFileName(fileName)

    val clientConfigName = s"${clientName}Config"

    val clientConfigMemberName = lowerFirstLetter(clientConfigName)

    val completePaths = swagger.getPaths.keySet().asScala.toList

    def composeClient(p: String): List[Stat] = {
      val path = swagger.getPath(p)
      if (path == null) return List()

      val ops: List[(String, Operation)] =
        List(Option(path.getDelete) map ("delete" -> _),
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
        List(
          q"import ${getPackageName(packageName)}._",
          q"import ${getPackageName(packageName)}.json._",
          q"import play.api.libs.ws._",
          q"import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue",
          q"import play.api.libs.ws.JsonBodyReadables.readableAsJson",
          q"import play.api.libs.json._",
          q"import javax.inject._",
          q"import scala.concurrent.ExecutionContext"
        )

    val clientHttpMethods = completePaths.flatMap(composeClient)

    val clientParams = List(
      Term.Param(List(), q"WS", Some(t"StandaloneWSClient"), None),
      Term.Param(List(), Term.Name(clientConfigMemberName), Some(Type.Name(clientConfigName)), None)
    )

    val tree = List(
      q"""case class ${Type.Name(clientConfigName)}(host: String = "localhost", port: Int, ssl: Boolean = false)""",
      q"""class ${Type.Name(clientName)} @Inject() (..$clientParams)(implicit ec: ExecutionContext) {
            private val scheme: String = if (${Term.Name(clientConfigMemberName)}.ssl) "https" else "http"
            val baseUrl: String = s"$$scheme://$${petStoreClientConfig.host}:$${petStoreClientConfig.port}"

            private def renderUrlParams(pairs: (String, Option[Any])*) = {
              val parts = pairs.collect({
                 case (k, Some(v)) => k + "=" + v
              })
              if (parts.nonEmpty) parts.mkString("?", "&", "") else ""
            }

            private def renderHeaderParams(pairs: (String, Option[Any])*) = {
              pairs.collect({
                case (k, Some(v)) => k -> v.toString
              })
            }

            ..$clientHttpMethods
          }
       """)

    Seq(SyntaxCode(clientName + ".scala", getPackageName(clientPackageName), imports, tree))
  }

  def genClientMethod(methodName: String,
                      url: String,
                      opType: String,
                      params: List[Parameter],
                      respType: (String, Option[Type])): Stat = {
    val bodyParams = getBodyParams(params)

    if (bodyParams.size > 1) throw new Exception(s"Only one parameter in body is allowed in method $methodName")

    val bodyParamsToBody = getParamsToBody(params)

    val methodParams = getMethodParams(params)

    //probably to be fixed with a custom ordering
    val urlParams: List[Term] =
      params collect {
        case query: QueryParameter =>
          val name = Term.Name(query.getName)
          q"${Lit.String(query.getName)} -> ${if (query.getRequired) q"Some($name)" else name}"
      }

    val headerParams: List[Term] =
      params collect {
        case header: HeaderParameter =>
          val name = Term.Name(header.getName)
          q"${Lit.String(header.getName)} -> ${if (header.getRequired) q"Some($name)" else name}"
      }

    val baseUrl =
      if (urlParams.isEmpty)
        q"""s"$$baseUrl/""""
      else
        q"""s"$$baseUrl/$${renderUrlParams(..$urlParams)}""""

    val wsUrl =
      if (headerParams.isEmpty)
        q"WS.url($baseUrl)"
      else
        q"WS.url($baseUrl).addHttpHeaders(renderHeaderParams(..$headerParams): _*)"

    q"""def ${Term.Name(methodName)}(..${methodParams.values.toList ++ bodyParams}) = {
          $wsUrl.${Term.Name(opType)}(..${bodyParamsToBody.values.toList}).map { resp =>
            if ((resp.status >= 200) && (resp.status <= 299))
              ${respType._2.map(typ => q"Json.parse(resp.body).as[$typ]").getOrElse(q"Unit")}
            else
              throw new java.lang.RuntimeException(s"unexpected response status: $${resp.status} $${resp.body}")
          }
        }
     """
  }

  def getParamsToBody(params: List[Parameter]): Map[Term, Term] =
    params.collect {
      case body: BodyParameter =>
        val name = Term.Name(body.getName)
        name -> q"Json.toJson($name)"
    }.toMap

  def getBodyParams(params: List[Parameter]): List[Term.Param] =
    params.collect {
      case body: BodyParameter =>
        Term.Param(List(), Term.Name(body.getName), Some(noOptParamType(body)), None)
    }
}
