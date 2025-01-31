package com.malinskiy.marathon.gradle

import groovy.lang.Closure
import java.time.Instant
import java.time.temporal.ChronoUnit

class SortingStrategyConfiguration {
    var executionTime: ExecutionTimeSortingStrategyConfiguration? = null
    var successRate: SuccessRateSortingStrategyConfiguration? = null

    fun executionTime(block: ExecutionTimeSortingStrategyConfiguration.() -> Unit) {
        executionTime = ExecutionTimeSortingStrategyConfiguration().also(block)
    }

    fun executionTime(closure: Closure<*>) {
        executionTime = ExecutionTimeSortingStrategyConfiguration()
        closure.delegate = executionTime
        closure.call()
    }

    fun successRate(block: SuccessRateSortingStrategyConfiguration.() -> Unit) {
        successRate = SuccessRateSortingStrategyConfiguration().also(block)
    }

    fun successRate(closure: Closure<*>) {
        successRate = SuccessRateSortingStrategyConfiguration()
        closure.delegate = successRate
        closure.call()
    }
}

private const val DEFAULT_PERCENTILE = 90.0
const val DEFAULT_DAYS_COUNT = 30L

class ExecutionTimeSortingStrategyConfiguration {
    var percentile: Double = DEFAULT_PERCENTILE
    var timeLimit: Instant = Instant.now().minus(DEFAULT_DAYS_COUNT, ChronoUnit.DAYS)
}

class SuccessRateSortingStrategyConfiguration {
    var limit: Instant = Instant.now().minus(DEFAULT_DAYS_COUNT, ChronoUnit.DAYS)
    var ascending: Boolean = false
}

fun SortingStrategyConfiguration.toStrategy(): com.malinskiy.marathon.config.strategy.SortingStrategyConfiguration = executionTime?.let {
    com.malinskiy.marathon.config.strategy.SortingStrategyConfiguration.ExecutionTimeSortingStrategyConfiguration(
        it.percentile,
        it.timeLimit
    )
} ?: successRate?.let {
    com.malinskiy.marathon.config.strategy.SortingStrategyConfiguration.SuccessRateSortingStrategyConfiguration(it.limit, it.ascending)
} ?: com.malinskiy.marathon.config.strategy.SortingStrategyConfiguration.NoSortingStrategyConfiguration
