/**********************************************************************************************\
* Rapture JSON Library                                                                         *
* Version 1.1.0                                                                                *
*                                                                                              *
* The primary distribution site is                                                             *
*                                                                                              *
*   http://rapture.io/                                                                         *
*                                                                                              *
* Copyright 2010-2014 Jon Pretty, Propensive Ltd.                                              *
*                                                                                              *
* Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file    *
* except in compliance with the License. You may obtain a copy of the License at               *
*                                                                                              *
*   http://www.apache.org/licenses/LICENSE-2.0                                                 *
*                                                                                              *
* Unless required by applicable law or agreed to in writing, software distributed under the    *
* License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,    *
* either express or implied. See the License for the specific language governing permissions   *
* and limitations under the License.                                                           *
\**********************************************************************************************/
package rapture.json

import rapture.core._
import rapture.data._

import language.higherKinds
import language.experimental.macros

object `package` extends internal.package_1 {

  val patternMatching = rapture.data.patternMatching

  implicit def jsonCastExtractor[T: JsonCastExtractor](implicit ast: JsonAst):
      Extractor[T, JsonDataType[_, _ <: JsonAst]] =
    new Extractor[T, JsonDataType[_, _ <: JsonAst]] {
      def construct(value: JsonDataType[_, _ <: JsonAst], fail: Exception, ast2: DataAst): T =
        ast2 match {
          case ast2: JsonAst =>
            val norm = value.$normalize
            try {
              if(ast == ast2) norm.asInstanceOf[T]
              else JsonDataType.jsonSerializer.serialize(Json.construct(MutableCell(norm),
                  Vector())(ast2)).asInstanceOf[T]
            } catch { case e: ClassCastException =>
              throw TypeMismatchException(ast.getType(norm),
                  implicitly[JsonCastExtractor[T]].dataType, Vector())
            }
          case _ => ???
        }
    }

  implicit class DynamicWorkaround(json: Json) {
    def self: Json = json.selectDynamic("json")
  }

  implicit def jsonStrings(sc: StringContext)(implicit parser: Parser[String, JsonAst]) =
    new JsonStrings(sc)
  
  implicit def jsonBufferStrings(sc: StringContext)(implicit parser: Parser[String,
      JsonBufferAst]) = new JsonBufferStrings(sc)
}

package internal {
  trait package_2 {
    implicit def jsonExtractorMacro[T <: Product]: Extractor[T, Json] =
      macro internal.JsonMacros.jsonExtractorMacro[T]
    
    implicit def jsonSerializerMacro[T <: Product](implicit ast: JsonAst): Serializer[T, Json] =
      macro internal.JsonMacros.jsonSerializerMacro[T]
  }

  trait package_1 extends package_2
}
