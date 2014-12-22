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

import scala.collection.mutable.{ListBuffer, HashMap}

import language.dynamics
import language.higherKinds
import language.experimental.macros


package internal {

  trait Json_2 {
    implicit def jsonExtractorMacro[T <: Product]: Extractor[T, Json] =
      macro internal.JsonMacros.jsonExtractorMacro[T]

    implicit def jsonSerializerMacro[T <: Product](implicit ast: JsonAst): Serializer[T, Json] =
      macro internal.JsonMacros.jsonSerializerMacro[T]
  }

  trait Json_1 extends Json_2 {
    implicit def dynamicWorkaround(j: Json) = new DynamicWorkaround(j)
  }

  class DynamicWorkaround(json: Json) {
    def self: Json = json.selectDynamic("self")
  }

  trait JsonDataCompanion[+Type <: JsonDataType[Type, AstType],
      AstType <: JsonAst] extends DataCompanion[Type, AstType] {

    /** Formats the JSON object for multi-line readability. */
    private[json] def doFormat(json: Any, ln: Int, ast: AstType, pad: String = " ",
        brk: String = "\n"): String = {
      val indent = pad*ln
      json match {
        case j =>
          if(ast.isString(j)) {
            "\""+ast.getString(j).replaceAll("\\\\", "\\\\\\\\").replaceAll("\r",
                "\\\\r").replaceAll("\n", "\\\\n").replaceAll("\"", "\\\\\"")+"\""
          } else if(ast.isBoolean(j)) {
            if(ast.getBoolean(j)) "true" else "false"
          } else if(ast.isNumber(j)) {
            val n = ast.getDouble(j)
            if(n == n.floor) n.toInt.toString else n.toString
          } else if(ast.isArray(j)) {
            val arr = ast.getArray(j)
            if(arr.isEmpty) "[]" else List("[", arr map { v =>
              s"${indent}${pad}${doFormat(v, ln + 1, ast, pad, brk)}"
            } mkString s",${brk}", s"${indent}]") mkString brk
          } else if(ast.isObject(j)) {
            val keys = ast.getKeys(j)
            if(keys.isEmpty) "{}" else List("{", keys map { k =>
              val inner = ast.dereferenceObject(j, k)
              s"""${indent}${pad}"${k}":${pad}${doFormat(inner, ln + 1, ast, pad, brk)}"""
            } mkString s",${brk}", s"${indent}}") mkString brk
          } else if(ast.isNull(j)) "null"
          else if(j == DataCompanion.Empty) "empty"
          else "undefined"
      }
    }
  }


  object JsonDataType extends internal.Extractors with internal.Serializers

  trait JsonDataType[+T <: JsonDataType[T, AstType], AstType <: JsonAst]
      extends DataType[T, AstType]
}

object JsonBuffer extends internal.JsonDataCompanion[JsonBuffer, JsonBufferAst] {
  
  def construct(any: MutableCell, path: Vector[Either[Int, String]])(implicit ast:
      JsonBufferAst): JsonBuffer = new JsonBuffer(any, path)
}

/** Companion object to the `Json` type, providing factory and extractor methods, and a JSON
  * pretty printer. */
object Json extends internal.JsonDataCompanion[Json, JsonAst] with internal.Json_1 {
  
  def construct(any: MutableCell, path: Vector[Either[Int, String]])(implicit ast:
      JsonAst): Json = new Json(any, path)

  def extractor[T](implicit ext: Extractor[T, Json]) = ext
  def serializer[T](implicit ser: Serializer[T, Json]) = ser

  implicit def jsonCastExtractor[T: internal.JsonCastExtractor](implicit ast: JsonAst):
      Extractor[T, internal.JsonDataType[_, _ <: JsonAst]] =
    new Extractor[T, internal.JsonDataType[_, _ <: JsonAst]] {
      def construct(value: internal.JsonDataType[_, _ <: JsonAst], fail: Exception, ast2: DataAst): T =
        ast2 match {
          case ast2: JsonAst =>
            val norm = value.$normalize
            try {
              if(ast == ast2) norm.asInstanceOf[T]
              else internal.JsonDataType.jsonSerializer.serialize(Json.construct(MutableCell(norm),
                  Vector())(ast2)).asInstanceOf[T]
            } catch { case e: ClassCastException =>
              throw TypeMismatchException(ast.getType(norm),
                  implicitly[internal.JsonCastExtractor[T]].dataType, Vector())
            }
          case _ => ???
        }
    }

}

/** Represents some parsed JSON. */
class Json(val $root: MutableCell, val $path: Vector[Either[Int, String]] = Vector())(implicit
    val $ast: JsonAst) extends internal.JsonDataType[Json, JsonAst] with DynamicData[Json, JsonAst] {
  
  def $wrap(any: Any, path: Vector[Either[Int, String]]): Json =
    new Json(MutableCell(any), path)
  
  def $deref(path: Vector[Either[Int, String]]): Json = new Json($root, path)

  def $extract(sp: Vector[Either[Int, String]]): Json =
    if(sp.isEmpty) this else sp match {
      case Left(i) +: tail => apply(i).$extract(tail)
      case Right(e) +: tail => selectDynamic(e).$extract(tail)
    }
  
  override def toString =
    try Json.format(this)(formatters.compact()($ast)) catch {
      case e: Exception => "undefined"
    }
}

class JsonBuffer(val $root: MutableCell, val $path: Vector[Either[Int, String]] = Vector())
    (implicit val $ast: JsonBufferAst) extends
    internal.JsonDataType[JsonBuffer, JsonBufferAst] with
    MutableDataType[JsonBuffer, JsonBufferAst] with DynamicData[JsonBuffer, JsonBufferAst] {

  def $wrap(any: Any, path: Vector[Either[Int, String]]): JsonBuffer =
    new JsonBuffer(MutableCell(any), path)
  
  def $deref(path: Vector[Either[Int, String]]): JsonBuffer = new JsonBuffer($root, path)
  
  def $extract(sp: Vector[Either[Int, String]]): JsonBuffer =
    if(sp.isEmpty) this else sp match {
      case Left(i) +: tail => apply(i).$extract(tail)
      case Right(e) +: tail => selectDynamic(e).$extract(tail)
    }
  
  override def toString =
    try JsonBuffer.format(this)(formatters.compact()($ast)) catch {
      case e: Exception => "undefined"
    }
}
