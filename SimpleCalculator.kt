import java.util.Scanner

fun add(a: Double, b: Double) = a + b
fun subtract(a: Double, b: Double) = a - b
fun multiply(a: Double, b: Double) = a * b
fun divide(a: Double, b: Double): Double {
    if (b == 0.0) {
        println("Cannot divide by zero.")
        return Double.NaN
    }
    return a / b
}

fun main() {
    val scanner = Scanner(System.`in`)

    print("Enter first number: ")
    val num1 = scanner.nextDouble()

    print("Enter second number: ")
    val num2 = scanner.nextDouble()

    print("Enter operator (+, -, *, /): ")
    val op = scanner.next()[0]

    val result = when (op) {
        '+' -> add(num1, num2)
        '-' -> subtract(num1, num2)
        '*' -> multiply(num1, num2)
        '/' -> divide(num1, num2)
        else -> {
            println("Invalid operator.")
            Double.NaN
        }
    }

    if (!result.isNaN()) {
        println("Result: $result")
    }
}