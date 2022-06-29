package com.admin3.byeruli.data

import kotlinx.serialization.Serializable

@Serializable
data class DelResp(
  val success: Boolean,
  val message: String = "",
)
