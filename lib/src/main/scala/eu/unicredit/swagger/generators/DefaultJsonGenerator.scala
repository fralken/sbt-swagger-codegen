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

import eu.unicredit.swagger.SwaggerConverters
import io.swagger.models.Swagger
import io.swagger.parser.SwaggerParser

import scala.meta._
import scala.collection.JavaConverters._

class DefaultJsonGenerator extends JsonGenerator with SwaggerConverters {

  def generateImports(): List[Import] = {
    List(q"import play.api.libs.json._")
  }

  def generateStatements(swagger: Swagger): List[Stat] = {
    val models = swagger.getDefinitions.asScala

    models.flatMap { case (originalName, model) =>
      propertiesToParams(model.getProperties) match {
        case None => List.empty
        case Some(params) =>
          val name = originalName.capitalize
          val typeName = Type.Name(if (params.isEmpty) s"$name.type" else name)

          val readsName = Pat.Var(Term.Name(s"${name}Reads"))
          val readsParams = params.map { param =>
            val mtd = Term.Name(if (isOption(param.decltpe.get)) "validateOpt" else "validate")
            enumerator"""${Pat.Var(Term.Name(param.name.value))} <- (json \ ${Lit.String(param.name.value)}).$mtd[${getTypeInOption(param.decltpe.get)}]"""
          }

          val readsStat =
            q"""implicit lazy val $readsName: Reads[$typeName] = Reads[$typeName] {
                  json => for {
                     ..$readsParams
                  } yield ${Term.Name(name)}(..${getParamsNames(params)})
                }
             """

          val writesName = Pat.Var(Term.Name(s"${name}Writes"))
          val writeParams = params.map { param =>
            q"""${Lit.String(param.name.value)} -> Json.toJson(o.${Term.Name(param.name.value)})"""
          }

          val writesStat =
            q"""implicit lazy val $writesName: Writes[$typeName] = Writes[$typeName] {
                  o => JsObject(Seq(..$writeParams).filter(_._2 != JsNull))
                }
             """

          List(readsStat, writesStat)
      }
    }.toList
  }

  def generate(fileName: String, destPackage: String): Iterable[SyntaxCode] = {
    val swagger = new SwaggerParser().read(fileName)
    Option(swagger.getPaths) match {
      case Some(_) =>
        val imports = generateImports()
        val pkgObj = q"package object json { ..${generateStatements(swagger)} }"
        Seq(SyntaxCode("json", getPackageTerm(destPackage), imports, List(pkgObj)))
      case None => Iterable.empty
    }
  }
}
