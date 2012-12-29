package io.viper.common

import io.viper.core.server.router.{JsonResponse, Utf8Response}
import org.json.{JSONArray, JSONObject}

object Response {
  def apply(str: String) = new Utf8Response(str)
  def apply(json: JSONObject) = new JsonResponse(json)
  def apply(json: JSONArray) = new JsonResponse(json)
}
