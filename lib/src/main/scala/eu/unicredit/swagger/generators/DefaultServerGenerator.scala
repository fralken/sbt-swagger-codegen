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

import java.io.File

import eu.unicredit.swagger.StringUtils._
import io.swagger.parser.SwaggerParser
import io.swagger.models._
import io.swagger.models.parameters._

import scala.collection.JavaConverters._
import scala.meta._

class DefaultServerGenerator extends ServerGenerator with SharedServerClientCode {

  def controllerNameFromFileName(fn: String) =
    objectNameFromFileName(fn, "Controller")

  def serviceNameFromFileName(fn: String) =
    objectNameFromFileName(fn, "Service")

  def fileNameWithoutPath(fn: String) = new File(fn).getName

  override def generateRoutes(fileName: String, packageName: String): Option[String] = {
    val swagger = new SwaggerParser().read(fileName)

    val basePath = Option(swagger.getBasePath).getOrElse("/")

    val completePaths =
      swagger.getPaths.asScala.keySet.toSeq

    def composeRoutes(p: String): Seq[(String, String, String)] = {
      val path = swagger.getPath(p)
      if (path == null) return Seq()

      val ops: Map[String, Operation] =
        Seq(Option(path.getDelete) map ("DELETE" -> _),
            Option(path.getGet) map ("GET" -> _),
            Option(path.getPost) map ("POST" -> _),
            Option(path.getPut) map ("PUT" -> _)).flatten.toMap

      val controllerName =
        packageName + ".controller" + "." + controllerNameFromFileName(fileName)

      (for {
        (verb, op) <- ops
      } yield {

        val url = doUrl(basePath, p)

        val methodName =
          if (op.getOperationId != null) op.getOperationId
          else throw new Exception("Please provide an operationId in: " + p)

        def genMethodCall(className: String, methodName: String, params: Seq[Parameter]): String = {
          // since it is a route definition, this is not Scala code, so we generate it manually
          s"$className.$methodName${getMethodParams(params).map(_._2.syntax).mkString("(", ", ", ")")}"
        }

        val methodCall = genMethodCall(controllerName, methodName, op.getParameters.asScala)

        (verb, url, methodCall)
      }).toSeq
    }

    val routeParts = completePaths.flatMap(composeRoutes)
    val maxUrlLength = routeParts.map(_._2.length).max
    val routes = routeParts.map {
      case (verb, url, methodCall) =>
        s"${verb.padTo(8, ' ')} ${url.padTo(maxUrlLength, ' ')}    $methodCall"
    }

    if (routes.nonEmpty) Some(routes.mkString(s"\n# Spec: ${fileNameWithoutPath(fileName)}\n\n", "\n", ""))
    else None
  }

  def generateImports(packageName: String, codeProvidedPackage: String, serviceName: String): List[Import] = {
    List(
      q"import ${getPackageName(packageName)}._",
      q"import ${getPackageName(packageName)}.json._",
      q"import play.api.mvc.Results._",
      q"import play.api.mvc._",
      q"import play.api.libs.json._",
      q"import javax.inject._",
      q"import ..${getImporter(codeProvidedPackage, serviceName)}"
    )
  }

  def generate(fileName: String, packageName: String, codeProvidedPackage: String): Iterable[SyntaxCode] = {
    val swagger = new SwaggerParser().read(fileName)

    val controllerPackageName = packageName + ".controller"

    val controllerName = controllerNameFromFileName(fileName)

    val serviceName = serviceNameFromFileName(fileName)

    val completePaths = swagger.getPaths.asScala.keySet.toList

    def composeController(p: String): List[Stat] = {
      val path = swagger.getPath(p)
      if (path == null) return List()

      val ops: List[Operation] =
        List(Option(path.getDelete), Option(path.getGet), Option(path.getPost), Option(path.getPut)).flatten

      for {
        op <- ops
      } yield {

        try if (!op.getProduces.asScala.forall(_ == "application/json"))
          println("WARNING - only 'application/json' is supported")
        catch {
          case _: Throwable =>
        }

        val methodName =
          if (op.getOperationId != null) op.getOperationId
          else throw new Exception("Please provide an operationId in: " + p)

        val okRespType: (String, Option[Type]) =
          getOkRespType(op) getOrElse {
            throw new Exception(s"cannot determine Ok result type for $methodName")
          }

        genControllerMethod(methodName, op.getParameters.asScala, okRespType)
      }
    }

    val imports = generateImports(packageName, codeProvidedPackage, serviceName)

    val tree = List(
      q"""class ${Type.Name(controllerName)} @Inject() (${Term.Param(List(), q"service", Some(Type.Name(serviceName)), None)}) {
            ..${completePaths.flatMap(composeController)}
          }
       """)

    Seq(SyntaxCode(controllerName + ".scala", getPackageName(controllerPackageName), imports, tree))
  }

  def genControllerMethod(methodName: String, params: Seq[Parameter], resType: (String, Option[Type])): Stat = {
    val bodyParams = getParamsFromBody(params)

    if (bodyParams.size > 1) throw new Exception(s"Only one parameter in body is allowed in method $methodName")

    val methodParams = getMethodParams(params)

    val answer =
      resType._2.map { typ =>
        q"${Term.Name(resType._1)}(Json.toJson[$typ](service.${Term.Name(methodName)}(..${(methodParams ++ bodyParams).keys.toList})))"
      }.getOrElse (
        q"""
            service.${Term.Name(methodName)}(..${(methodParams ++ bodyParams).keys.toList})
            ${Term.Name(resType._1)}
         """
      )

    val tree: Stat =
      q"""
          def ${Term.Name(methodName)}(..${methodParams.values.toList}) = {
            Action(request =>
              try {
                ..${bodyParams.values.toList}
                $answer
              } catch {
                case err: Throwable => BadRequest(service.onError(${Lit.String(methodName)}, err))
              }
            )}
       """

    tree
  }

  def getParamsFromBody(params: Seq[Parameter]): Map[Term, Stat] =
    params
      .filter {
        case _: BodyParameter => true
        case _ => false
      }
      .flatMap {
        case body: BodyParameter =>
          val name = Term.Name(body.getName)
          val tree: Stat =
            q"val ${Pat.Var(name)} = Json.fromJson[${noOptParamType(body)}](request.body.asJson.get).get"
          Some(name -> tree)
        case _ =>
          None
      }
      .toMap
}

class DefaultAsyncServerGenerator extends DefaultServerGenerator {

  override def generateImports(packageName: String, codeProvidedPackage: String, serviceName: String): List[Import] =
    super.generateImports(packageName, codeProvidedPackage, serviceName) :+
      q"import play.api.libs.concurrent.Execution.Implicits._"

  override def genControllerMethod(methodName: String, params: Seq[Parameter], resType: (String, Option[Type])): Stat = {
    val bodyParams = getParamsFromBody(params)

    if (bodyParams.size > 1) throw new Exception(s"Only one parameter in body is allowed in method $methodName")

    val methodParams = getMethodParams(params)

    val tree: Stat =
      q"""
          def ${Term.Name(methodName)}(..${methodParams.values.toList}) = {
            Action.async(request =>
              {
                ..${bodyParams.values.toList}
                service.${Term.Name(methodName)}(..${(methodParams ++ bodyParams).keys.toList}).map(answer => ${resType._2.map { typ => q"${Term.Name(resType._1)}(Json.toJson[$typ](answer))"}.getOrElse(Term.Name(resType._1))})
              }.recoverWith {
                case err: Throwable => service.onError(${Lit.String(methodName)}, err).map(errAnswer => BadRequest(errAnswer))
              }
            )
          }
       """

    tree
  }
}
