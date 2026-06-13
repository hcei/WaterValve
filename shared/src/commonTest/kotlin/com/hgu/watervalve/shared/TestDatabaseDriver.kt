package com.hgu.watervalve.shared

import app.cash.sqldelight.db.SqlDriver

internal expect fun createTestDatabaseDriver(): SqlDriver
