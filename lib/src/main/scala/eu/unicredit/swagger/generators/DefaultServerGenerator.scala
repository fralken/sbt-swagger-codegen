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

import io.swagger.models.Path
import io.swagger.parser.SwaggerParser

import scala.collection.JavaConverters._
import scala.meta._

class DefaultServerGenerator extends ServerGenerator with SharedServerClientCode {

  def controllerNameFromFileName(fn: String): String =
    objectNameFromFileName(fn, "Controller")

  def routerNameFromFileName(fn: String): String =
    objectNameFromFileName(fn, "Router")

  def serviceNameFromFileName(fn: String): String =
    objectNameFromFileName(fn, "Service")

  def fileNameWithoutPath(fn: String): String = new File(fn).getName

  def generateControllerImports(packageName: String, codeProvidedPackage: String, serviceName: String): List[Import] = {
    List(
      q"import ${getPackageTerm(packageName)}.json._",
      q"import play.api.mvc.Results._",
      q"import play.api.mvc._",
      q"import play.api.libs.json._",
      q"import javax.inject._",
      q"import ..${getImporters((codeProvidedPackage, serviceName))}"
    )
  }

  def generateRouterImports(packageName: String): List[Import] = {
    List(
      q"import play.api.mvc._",
      q"import play.api.routing.Router.Routes",
      q"import play.api.routing.SimpleRouter",
      q"import play.api.routing.sird._",
      q"import javax.inject._"
    )
  }

  def generateRouter(fileName: String, destPackage: String): Iterable[SyntaxCode] = {
    val swagger = new SwaggerParser().read(fileName)

    val controllerName = controllerNameFromFileName(fileName)

    val routerName = routerNameFromFileName(fileName)

    val basePath = Option(swagger.getBasePath).getOrElse("/")

    def composeRoutes(pathEntry: (String, Path)): List[Case] = {
      def sanitizeParamName(name: String) = name.replaceAll("-", "_")

      def castParam(param: Term.Param) = {
        def getInnerType(tpe: Type ): Type = {
          tpe match {
            case Type.Apply(name, args) if List("Option", "List").contains(name.syntax) => getInnerType(args.head)
            case _ => tpe
          }
        }

        val patVar = Pat.Var(Term.Name(sanitizeParamName(param.name.value)))
        param.decltpe match {
          case None => patVar
          case Some(tpe) =>
            getInnerType(tpe).syntax match {
              case "Int" => p"int($patVar)"
              case "Long" => p"long($patVar)"
              case "Float" => p"float($patVar)"
              case "Double" => p"double($patVar)"
              case "Bool" => p"bool($patVar)"
              case _ => patVar
            }
        }
      }

      def castedPathParams(pat: Pat.Interpolate, params: List[Term.Param]): Pat.Interpolate = {
        val castedParams = pat.args.map {
          case arg @ Pat.Var(term) =>
            val maybeParam = params.find { param => param.name.value == term.value }
            maybeParam.map(castParam).getOrElse(arg)
          case arg => arg
        }
        pat.copy(args = castedParams)
      }

      val (p, path) = pathEntry

      for {
        (verb, op) <- getOperations(path)
      } yield {

        try if (!op.getProduces.asScala.forall(_ == "application/json"))
          println("WARNING - only 'application/json' is supported")
        catch {
          case _: Throwable =>
        }

        val methodName =
          if (op.getOperationId != null) op.getOperationId
          else throw new Exception("Please provide an operationId in: " + p)

        val parameters = op.getParameters.asScala.toList

        val methodParams = parametersToPathParams(parameters) ++ parametersToQueryParams(parameters)

        val paramNames = getParamsNames(methodParams).map { t => t.copy(sanitizeParamName(t.value))}

        val queryParams = for {
             queryParam <- parametersToQueryParams(parameters)
             paramType <- queryParam.decltpe
          } yield {
            val prefix =
              if (isSeq(getTypeInOption(paramType))) { "q_*" }
              else if (isOption(paramType)) { "q_?" }
              else { "q" }

            Pat.Interpolate(Term.Name(prefix), List(Lit.String(s"${queryParam.name.value}="), Lit.String("")), List(castParam(queryParam)))
          }

        val pathParams = castedPathParams(patternInterpolateUrl("p", basePath + p), parametersToPathParams(parameters))

        val params = if (queryParams.isEmpty) {
          pathParams
        } else if (queryParams.tail.isEmpty) {
          p"$pathParams ? ${queryParams.head}"
        } else {
          p"$pathParams ? ${queryParams.head} & (..${queryParams.tail})"
        }

        p"case ${Term.Name(verb)}($params) => controller.${Term.Name(methodName)}(..$paramNames)"
      }
    }

    Option(swagger.getPaths) match {
      case Some(paths) =>
        val imports = generateRouterImports(destPackage)

        val tree = List(
          q"""class ${Type.Name(routerName)} @Inject()(controller: ${Type.Name(controllerName)}) extends SimpleRouter {
                override def routes: Routes = {
                  ..case ${paths.asScala.toList.flatMap(composeRoutes)}
                }
              }
           """
        )

        Seq(SyntaxCode(routerName + ".scala", getPackageTerm(destPackage), imports, tree))
      case None => Iterable.empty
    }
  }

  def generateController(fileName: String, destPackage: String, codeProvidedPackage: String): Iterable[SyntaxCode] = {
    val swagger = new SwaggerParser().read(fileName)

    val controllerName = controllerNameFromFileName(fileName)

    val serviceName = serviceNameFromFileName(fileName)

    def composeController(pathEntry: (String, Path)): List[Stat] = {
      val (p, path) = pathEntry

      for {
        (verb, op) <- getOperations(path)
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

        val params = op.getParameters.asScala.toList

        val bodyParams = parametersToBodyParams(params)
        if (bodyParams.size > 1)
          throw new Exception(s"Only one parameter in body is allowed in method $methodName")
        if ((verb == "PUT" || verb == "POST") && bodyParams.size < 1)
          throw new Exception(s"One parameter in body is required for $verb in method $methodName")

        val headerParams = parametersToHeaderParams(params)

        val methodParams = parametersToPathParams(params) ++ parametersToQueryParams(params)

        generateControllerMethod(methodName, methodParams, headerParams, bodyParams, okRespType)
      }
    }

    Option(swagger.getPaths) match {
      case Some(paths) =>
        val imports = generateControllerImports(destPackage, codeProvidedPackage, serviceName)

        val tree = List(generateControllerClass(Type.Name(controllerName), Type.Name(serviceName), paths.asScala.toList.flatMap(composeController)))

        Seq(SyntaxCode(controllerName + ".scala", getPackageTerm(destPackage), imports, tree))
      case None => Iterable.empty
    }
  }

  def generateControllerClass(controllerType: Type.Name, serviceType: Type.Name, methods: List[Stat]): Stat = {
    q"""class $controllerType @Inject() (${Term.Param(List(), q"service", Some(serviceType), None)}, cc: ControllerComponents)
            extends AbstractController(cc) {
          ..$methods
        }
     """
  }

  def generateControllerMethod(methodName: String, methodParams: List[Term.Param], headerParams: List[Term.Param], bodyParams: List[Term.Param], resType: (Term, Option[Type])): Stat = {
    val paramNames = getParamsNames(headerParams ++ methodParams ++ bodyParams)

    val methodTerm = Term.Name(methodName)
    val methodLiteral = Lit.String(methodName)

    val answer =
      resType._2.map { typ =>
        q"${resType._1}(Json.toJson[$typ](service.$methodTerm(..$paramNames)))"
      }.getOrElse (
        q"service.$methodTerm(..$paramNames); ${resType._1}"
      )

    q"""def $methodTerm(..$methodParams) = {
          Action(request =>
            try {
              ..${headerParams.map(generateStatementFromHeaderParameter)}
              ..${bodyParams.map(generateStatementFromBodyParameter)}
              $answer
            } catch {
              case err: Throwable => BadRequest(service.onError($methodLiteral, err))
            }
          )
        }
     """
  }

  def generateStatementFromBodyParameter(param: Term.Param): Stat = {
    q"val ${Pat.Var(Term.Name(param.name.value))} = Json.fromJson[${param.decltpe.get}](request.body.asJson.get).get"
  }

  def generateStatementFromHeaderParameter(param: Term.Param): Stat = {
    val termName = Term.Name(param.name.value)
    val litName = Lit.String(param.name.value)
    if (isOption(param.decltpe.get))
      q"val ${Pat.Var(termName)} = request.headers.get($litName)"
    else
      q"val ${Pat.Var(termName)} = request.headers.get($litName).get"
  }

  override def generate(fileName: String, destPackage: String, codeProvidedPackage: String): Iterable[SyntaxCode] = {
      generateController(fileName, destPackage, codeProvidedPackage) ++ generateRouter(fileName, destPackage)
  }
}

class DefaultAsyncServerGenerator extends DefaultServerGenerator {

  override def generateControllerImports(packageName: String, codeProvidedPackage: String, serviceName: String): List[Import] =
    super.generateControllerImports(packageName, codeProvidedPackage, serviceName) :+
      q"import scala.concurrent.ExecutionContext"

  override def generateControllerMethod(methodName: String, methodParams: List[Term.Param], headerParams: List[Term.Param], bodyParams: List[Term.Param], resType: (Term, Option[Type])): Stat = {
    val paramNames = getParamsNames(headerParams ++ methodParams ++ bodyParams)

    val methodTerm = Term.Name(methodName)
    val methodLiteral = Lit.String(methodName)

    q"""def $methodTerm(..$methodParams) = {
          Action.async(request =>
            {
              ..${headerParams.map(generateStatementFromHeaderParameter)}
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
