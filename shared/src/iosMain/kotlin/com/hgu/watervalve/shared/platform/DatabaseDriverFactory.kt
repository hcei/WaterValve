package com.hgu.watervalve.shared.platform

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.hgu.watervalve.shared.data.local.WaterValveDb
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSUserDomainMask

/**
 * iOS SQLDelight 数据库驱动工厂
 *
 * 数据库文件路径：Documents/WaterValve.db
 * 使用 NativeSqliteDriver（基于系统 SQLite）
 */
class DatabaseDriverFactory {

    fun createDriver(): SqlDriver {
        val documentsPath = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true
        ).first() as String

        val dbPath = "$documentsPath/WaterValve.db"

        return NativeSqliteDriver(
            schema = WaterValveDb.Schema,
            name = dbPath
        )
    }
}
