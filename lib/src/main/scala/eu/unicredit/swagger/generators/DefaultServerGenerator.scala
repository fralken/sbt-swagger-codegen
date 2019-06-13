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

import eu.unicredit.swagger.UrlGenerator._
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
      if (path == null) return Seq.empty

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

        val url = generateUrl(basePath, p)

        val methodName =
          if (op.getOperationId != null) op.getOperationId
          else throw new Exception("Please provide an operationId in: " + p)

        def genMethodCall(className: String, methodName: String, params: List[Parameter]): String = {
          // since it is a route definition, this is not Scala code, so we generate it manually
          s"$className.$methodName${parametersToMethodParams(params).map(_.syntax).mkString("(", ", ", ")")}"
        }

        val methodCall = genMethodCall(controllerName, methodName, op.getParameters.asScala.toList)

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
      q"import ${getPackageTerm(packageName)}._",
      q"import ${getPackageTerm(packageName)}.json._",
      q"import play.api.mvc.Results._",
      q"import play.api.mvc._",
      q"import play.api.libs.json._",
      q"import javax.inject._",
      q"import ..${getImporters((codeProvidedPackage, serviceName))}"
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

        val okRespType: (Term, Option[Type]) =
          getResponseResultsAndTypes(op) getOrElse {
            throw new Exception(s"cannot determine Ok result type for $methodName")
          }

        generateControllerMethod(methodName, op.getParameters.asScala.toList, okRespType)
      }
    }

    val imports = generateImports(packageName, codeProvidedPackage, serviceName)

    val tree = List(generateControllerClass(Type.Name(controllerName), Type.Name(serviceName), completePaths.flatMap(composeController)))

    Seq(SyntaxCode(controllerName + ".scala", getPackageTerm(controllerPackageName), imports, tree))
  }

  def generateControllerClass(controllerType: Type.Name, serviceType: Type.Name, methods: List[Stat]): Stat = {
    q"""class $controllerType @Inject() (${Term.Param(List(), q"service", Some(serviceType), None)}, cc: ControllerComponents)
            extends AbstractController(cc) {
            ..$methods
          }
     """
  }

  def generateControllerMethod(methodName: String, params: List[Parameter], resType: (Term, Option[Type])): Stat = {
    val bodyParams = parametersToBodyParams(params)
    if (bodyParams.size > 1) throw new Exception(s"Only one parameter in body is allowed in method $methodName")

    val methodParams = parametersToMethodParams(params)

    val paramNames = getParamsNames(methodParams ++ bodyParams)

    val methodTerm = Term.Name(methodName)
    val methodLiteral = Lit.String(methodName)

    val answer =
      resType._2.map { typ =>
        q"${resType._1}(Json.toJson[$typ](service.$methodTerm(..$paramNames)))"
      }.getOrElse (
        q"""
            service.$methodTerm(..$paramNames)
            ${resType._1}
         """
      )

    q"""
        def ${Term.Name(methodName)}(..$methodParams) = {
          Action(request =>
            try {
              ..${bodyParams.map(generateStatementFromBodyParameter)}
              $answer
            } catch {
              case err: Throwable => BadRequest(service.onError($methodLiteral, err))
            }
          )}
     """
  }

  def generateStatementFromBodyParameter(param: Term.Param): Stat = {
    q"val ${Pat.Var(Term.Name(param.name.value))} = Json.fromJson[${param.decltpe.get}](request.body.asJson.get).get"
  }
}

class DefaultAsyncServerGenerator extends DefaultServerGenerator {

  override def generateImports(packageName: String, codeProvidedPackage: String, serviceName: String): List[Import] =
    super.generateImports(packageName, codeProvidedPackage, serviceName) :+
      q"import scala.concurrent.ExecutionContext"

  override def generateControllerMethod(methodName: String, params: List[Parameter], resType: (Term, Option[Type])): Stat = {
    val bodyParams = parametersToBodyParams(params)
    if (bodyParams.size > 1) throw new Exception(s"Only one parameter in body is allowed in method $methodName")

    val methodParams = parametersToMethodParams(params)

    val paramNames = getParamsNames(methodParams ++ bodyParams)

    val methodTerm = Term.Name(methodName)
    val methodLiteral = Lit.String(methodName)

    q"""
        def $methodTerm(..$methodParams) = {
          Action.async(request =>
            {
              ..${bodyParams.map(generateStatementFromBodyParameter)}
              service.$methodTerm(..$paramNames).map(answer => ${resType._2.map { typ => q"${resType._1}(Json.toJson[$typ](answer))"}.getOrElse(resType._1)})
            }.recoverWith {
              case err: Throwable => service.onError($methodLiteral, err).map(errAnswer => BadRequest(errAnswer))
            }
          )
        }
     """
  }

  override def generateControllerClass(controllerType: Type.Name, serviceType: Type.Name, methods: List[Stat]): Stat = {
    q"""class $controllerType @Inject() (${Term.Param(List(), q"service", Some(serviceType), None)}, cc: ControllerComponents)
          (implicit ec: ExecutionContext) extends AbstractController(cc) {
            ..$methods
          }
     """
  }
}
