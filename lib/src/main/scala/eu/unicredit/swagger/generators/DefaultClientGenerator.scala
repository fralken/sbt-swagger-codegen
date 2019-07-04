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

import io.swagger.parser.SwaggerParser
import io.swagger.models.Path
import io.swagger.models.parameters._

import scala.collection.JavaConverters._
import scala.meta._

class DefaultClientGenerator extends ClientGenerator with SharedServerClientCode {

  def clientNameFromFileName(fn: String): String = objectNameFromFileName(fn, "Client")

  def generateImports(packageName: String): List[Import] = {
    List(
      q"import ${getPackageTerm(packageName)}.json._",
      q"import play.api.libs.ws._",
      q"import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue",
      q"import play.api.libs.ws.JsonBodyReadables.readableAsJson",
      q"import play.api.libs.json._",
      q"import javax.inject._",
      q"import scala.concurrent.ExecutionContext"
    )
  }

  def generate(fileName: String, destPackage: String): Iterable[SyntaxCode] = {
    val swagger = new SwaggerParser().read(fileName)

    val basePath = Option(swagger.getBasePath).getOrElse("/")

    val clientName = clientNameFromFileName(fileName)

    val clientConfigType = Type.Name(s"${clientName}Config")

    def composeClient(pathEntry: (String, Path)): List[Stat] = {
      val (p, path) = pathEntry
      for {
        (verb, op) <- getOperations(path)
      } yield {
        val methodName =
          if (op.getOperationId != null) op.getOperationId
          else throw new Exception("Please provide an operationId in: " + p)

        val reesponseType: (Term, Option[Type]) =
          getResponseResultsAndTypes(op) getOrElse {
            throw new Exception(s"cannot determine Ok result type for $methodName")
          }

        genClientMethod(methodName, basePath + p, verb, op.getParameters.asScala.toList, reesponseType)
      }
    }

    Option(swagger.getPaths) match {
      case Some(paths) =>
        val clientHttpMethods = paths.asScala.toList.flatMap(composeClient)

        val tree = List(
          q"""case class $clientConfigType(host: String = "localhost", port: Int, ssl: Boolean = false)""",
          q"""class ${Type.Name(clientName)} @Inject() (ws: StandaloneWSClient, clientConfig: $clientConfigType)(implicit ec: ExecutionContext) {
                private val scheme: String = if (clientConfig.ssl) "https" else "http"
                val baseUrl: String = s"$$scheme://$${clientConfig.host}:$${clientConfig.port}"

                private def renderUrlParams(pairs: (String, Option[Any])*) = {
                  val parts = pairs.collect({
                    case (k, Some(l: Iterable[_])) => l.map(v => k + "=" + v).mkString("&")
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

        Seq(SyntaxCode(clientName + ".scala", getPackageTerm(destPackage), generateImports(destPackage), tree))
      case None => Iterable.empty
    }
  }

  def genClientMethod(methodName: String,
                      url: String,
                      verb: String,
                      params: List[Parameter],
                      responseType: (Term, Option[Type])): Stat = {
    def generateStatementFromParam(param: Term.Param): Term = {
      val name = Term.Name(param.name.value)
      q"${Lit.String(param.name.value)} -> ${param.decltpe.map(t => if (!isOption(t)) q"Some($name)" else name).getOrElse(name)}"
    }

    val bodyParams = parametersToBodyParams(params)
    if (bodyParams.size > 1)
      throw new Exception(s"Only one parameter in body is allowed in method $methodName")
    if ((verb == "PUT" || verb == "POST") && bodyParams.size < 1)
      throw new Exception(s"One parameter in body is required for $verb in method $methodName")

    val headerParams = parametersToHeaderParams(params)
    val queryParams = parametersToQueryParams(params)
    val methodParams = headerParams ++ parametersToPathParams(params) ++ queryParams

    val queryTerms = queryParams.map(generateStatementFromParam)
    val headerTerms = headerParams.map(generateStatementFromParam)

    val baseUrl =
      if (queryTerms.isEmpty)
        q"${termInterpolateUrl("s", s"{baseUrl}$url")}"
      else
        q"${termInterpolateUrl("s", s"{baseUrl}$url")} + renderUrlParams(..$queryTerms)"

    val wsUrl =
      if (headerTerms.isEmpty)
        q"ws.url($baseUrl)"
      else
        q"ws.url($baseUrl).addHttpHeaders(renderHeaderParams(..$headerTerms): _*)"

    q"""def ${Term.Name(methodName)}(..${methodParams ++ bodyParams}) = {
          $wsUrl.${Term.Name(verb.toLowerCase)}(..${getParamsNames(bodyParams).map(name => q"Json.toJson($name)")}).map { resp =>
            if ((resp.status >= 200) && (resp.status <= 299))
              ${responseType._2.map(typ => q"Json.parse(resp.body).as[$typ]").getOrElse(q"Unit")}
            else
              throw new java.lang.RuntimeException(s"unexpected response status: $${resp.status} $${resp.body}")
          }
        }
     """
  }
}
