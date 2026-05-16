package com.github.pavelkuliaka.console

import java.util.Scanner

val scanner = Scanner(System.`in`)
fun readLine(): String? = try { scanner.nextLine() } catch (_: NoSuchElementException) { null }
fun clear() = print("\u001B[2J\u001B[H")
