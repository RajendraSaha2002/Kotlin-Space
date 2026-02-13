import java.util.Scanner

data class Student(
    val id: Int,
    var name: String,
    var age: Int,
    var grade: String
)

class StudentManager {
    private val students = mutableListOf<Student>()

    // Manual initial data
    init {
        students.add(Student(1, "Asha", 19, "A"))
        students.add(Student(2, "Rahul", 20, "B"))
        students.add(Student(3, "Neha", 18, "A+"))
    }

    fun addStudent(student: Student) {
        if (students.any { it.id == student.id }) {
            println("Student with ID ${student.id} already exists.")
            return
        }
        students.add(student)
        println("Student added successfully.")
    }

    fun updateStudent(id: Int, name: String, age: Int, grade: String) {
        val student = students.find { it.id == id }
        if (student != null) {
            student.name = name
            student.age = age
            student.grade = grade
            println("Student updated successfully.")
        } else {
            println("Student not found.")
        }
    }

    fun deleteStudent(id: Int) {
        val removed = students.removeIf { it.id == id }
        if (removed) {
            println("Student deleted successfully.")
        } else {
            println("Student not found.")
        }
    }

    fun searchStudent(id: Int) {
        val student = students.find { it.id == id }
        if (student != null) {
            println("Found: $student")
        } else {
            println("Student not found.")
        }
    }

    fun listStudents() {
        if (students.isEmpty()) {
            println("No students available.")
            return
        }
        println("All Students:")
        students.forEach { println(it) }
    }
}

fun main() {
    val manager = StudentManager()
    val scanner = Scanner(System.`in`)

    while (true) {
        println("\n=== Student Management System ===")
        println("1. List Students")
        println("2. Add Student")
        println("3. Update Student")
        println("4. Delete Student")
        println("5. Search Student")
        println("6. Exit")
        print("Enter choice: ")

        when (scanner.nextInt()) {
            1 -> manager.listStudents()
            2 -> {
                print("Enter ID: ")
                val id = scanner.nextInt()
                print("Enter Name: ")
                val name = readLine()?.trim().orEmpty()
                print("Enter Age: ")
                val age = scanner.nextInt()
                print("Enter Grade: ")
                val grade = readLine()?.trim().orEmpty()
                manager.addStudent(Student(id, name, age, grade))
            }
            3 -> {
                print("Enter ID to update: ")
                val id = scanner.nextInt()
                print("Enter New Name: ")
                val name = readLine()?.trim().orEmpty()
                print("Enter New Age: ")
                val age = scanner.nextInt()
                print("Enter New Grade: ")
                val grade = readLine()?.trim().orEmpty()
                manager.updateStudent(id, name, age, grade)
            }
            4 -> {
                print("Enter ID to delete: ")
                val id = scanner.nextInt()
                manager.deleteStudent(id)
            }
            5 -> {
                print("Enter ID to search: ")
                val id = scanner.nextInt()
                manager.searchStudent(id)
            }
            6 -> {
                println("Exiting... Goodbye!")
                break
            }
            else -> println("Invalid choice.")
        }
    }
}