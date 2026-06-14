package com.hgu.watervalve.shared.domain.model

import kotlinx.serialization.Serializable

/**
 * 用户信息
 */
@Serializable
data class UserInfo(
    val userId: String,
    val nickname: String
)
