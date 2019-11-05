annotation class Macro(val template: String, val replacements: String)

private const val template = ".*?(fun .*?\\(.*?\\) )\\{(.*?)\\}"

private const val replacement = """$1{ println("Before"); val m = System.currentTimeMillis(); $2; println(System.currentTimeMillis() - m);}"""

@Macro(template, replacement)
fun test() { println("function body") }

fun main() {
    test()
}
