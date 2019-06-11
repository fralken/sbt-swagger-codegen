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

import eu.unicredit.swagger.SwaggerConversion
import io.swagger.parser.SwaggerParser
import scala.meta._

import scala.collection.JavaConverters._

class DefaultJsonGenerator extends JsonGenerator with SwaggerConversion {

  def generateImports(): List[Import] = {
    List(q"import play.api.libs.json._")
  }

  def generateStatements(fileName: String): List[Stat] = {
    val swagger = new SwaggerParser().read(fileName)

    val models = swagger.getDefinitions.asScala

    models.flatMap { case (name, model) =>
      val properties = getProperties(model)
      val typeName = Type.Name(if (properties.isEmpty) s"$name.type" else name)

      val readsName = Pat.Var(Term.Name(s"${name}Reads"))
      val readsParams = properties.map { case (pname, prop) =>
          val mtd = Term.Name(if (prop.getRequired) "as" else "asOpt")
          q"""(json \ ${Lit.String(pname)}).$mtd[${noOptPropType(prop)}]"""
        }.toList

      val readsStat =
        q"""implicit lazy val $readsName: Reads[$typeName] = Reads[$typeName] {
              json => JsSuccess(${Term.Name(name)}(..$readsParams))
            }
         """

      val writesName = Pat.Var(Term.Name(s"${name}Writes"))
      val writeParams = properties.map { case (pname, _) =>
          q"""${Lit.String(pname)} -> Json.toJson(o.${Term.Name(pname)})"""
        }.toList

      val writesStat =
        q"""implicit lazy val $writesName: Writes[$typeName] = Writes[$typeName] {
              o => JsObject(Seq(..$writeParams).filter(_._2 != JsNull))
            }
         """

      List(readsStat, writesStat)
    }.toList
  }

  def generate(fileName: String, destPackage: String): Iterable[SyntaxCode] = {
    val imports = generateImports()
    val pkgObj = q"package object json { ..${generateStatements(fileName)} }"
    Seq(SyntaxCode("json", getPackageName(destPackage), imports, List(pkgObj)))
  }
}
