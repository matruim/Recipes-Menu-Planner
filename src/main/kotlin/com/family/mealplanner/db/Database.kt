package com.family.mealplanner.db

import com.family.mealplanner.config.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import javax.sql.DataSource

object DatabaseFactory {

    /** Opens the pool, brings the schema up to date, then hands Exposed the source. */
    fun connect(config: DatabaseConfig): Database {
        val dataSource = hikari(config)
        migrate(dataSource)
        return Database.connect(dataSource)
    }

    private fun hikari(config: DatabaseConfig): HikariDataSource {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.url
            username = config.user
            password = config.password
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = config.maxPoolSize
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
            validate()
        }
        return HikariDataSource(hikariConfig)
    }

    private fun migrate(dataSource: DataSource) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }
}

/**
 * Exposed's JDBC API is blocking, so every query is pushed off the event loop.
 */
suspend fun <T> dbQuery(block: JdbcTransaction.() -> T): T =
    withContext(Dispatchers.IO) { transaction { block() } }
