package org.example.stock

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class StockExampleApplication

fun main(args: Array<String>) {
    runApplication<StockExampleApplication>(*args)
}
