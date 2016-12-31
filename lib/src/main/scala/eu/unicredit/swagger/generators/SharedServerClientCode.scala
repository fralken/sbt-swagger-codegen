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
import eu.unicredit.swagger.StringUtils._

import treehugger.forest._
import treehuggerDSL._

import io.swagger.models.parameters._

trait SharedServerClientCode extends SwaggerConversion {
  import java.io.File.separator
  import java.io.File.separatorChar

  def objectNameFromFileName(fn: String, obj: String) = {
    val sep = if (separatorChar == 92.toChar) "\\\\" else separator
    fn.split(sep)
      .toList
      .last
      .replace(".yaml", "")
      .replace(".json", "")
      .capitalize + obj
  }

  def genMethodCall(className: String, methodName: String, params: Seq[Parameter]): String = {
    val p = getMethodParamas(params).map {
      case (n, v) => s"$n: ${treeToString(v.tpt)}"
    }
    // since it is a route definition, this is not Scala code, so we generate it manually
    s"$className.$methodName" + p.mkString("(", ", ", ")")
  }

  def getMethodParamas(params: Seq[Parameter]): Map[String, ValDef] = {
    params
      .filter {
        case path: PathParameter => true
        case query: QueryParameter => true
        case header: HeaderParameter => true
        case body: BodyParameter => false
        case _ =>
          println(
            s"unmanaged parameter type for parameter ${x.getName}, please contact the developer to implement it XD");
          false
      }
      .sortWith((p1, p2) => //the order must be verified...
        p1 match {
          case _: PathParameter =>
            p2 match {
              case _: PathParameter => true
              case _: QueryParameter => true
              case _ => true
            }
          case _: QueryParameter =>
            p2 match {
              case _: PathParameter => false
              case _: QueryParameter => true
              case _ => true
            }
          case _ => true
      })
      .map(p => {
        (p.getName, PARAM(p.getName, paramType(p)): ValDef)
      })
      .toMap
  }

  def respType[T](f: String => T): Seq[(String, T)] =
    Seq(
      "Ok" -> f("200"),
      "Created" -> f("201"),
      "Accepted" -> f("202"),
      "NonAuthoritativeInformation" -> f("203"),
      "NoContent" -> f("204"),
      "ResetContent" -> f("205"),
      "PartialContent" -> f("206"),
      "MultiStatus" -> f("207"),
      "Ok" -> f("default")
    )

}
