package com.hgu.watervalve.di

import javax.inject.Qualifier

/** 学校 UWC API 客户端 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UwcClient

/** 设备同步云服务客户端 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SyncClient

/** GitHub API 客户端（更新检查） */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GitHubClient
