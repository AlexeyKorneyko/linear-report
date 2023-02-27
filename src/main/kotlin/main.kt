import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.prompt
import com.github.ajalt.clikt.parameters.types.int
import org.http4k.client.OkHttp
import org.http4k.client.asGraphQLHandler
import org.http4k.core.Filter
import org.http4k.core.then
import org.http4k.filter.ClientFilters
import org.http4k.filter.GzipCompressionMode
import org.http4k.format.Jackson.asA
import org.http4k.format.Jackson.asJsonObject
import org.http4k.graphql.GraphQLRequest
import java.lang.Exception
import java.time.LocalDate
import java.time.YearMonth

fun main(args: Array<String>) = CLITool().main(args)

class CLITool: CliktCommand() {
    private val year: Int by option(help="Changelog year").int().default(LocalDate.now().year)
    private val month: Int by option(help="Changelog month").int().default(LocalDate.now().monthValue)
    private val apiKey: String by option(help = "Linear API Key.")
        .prompt(hideInput = true, requireConfirmation = false)

    override fun run() {
        val yearMonth = YearMonth.of(year, month)
        println("# Changelog draft ${yearMonth.month.name} ${yearMonth.year}")
        println(process(requestReport(apiKey, yearMonth)))
    }
}

private fun requestReport(apiKey: String, yearMonth: YearMonth): LinearReport {
    val handler =
        ClientFilters.AcceptGZip(GzipCompressionMode.Streaming).then(
            Filter { next ->
                {
                    next(it.header("Authorization", apiKey))
                }
            }
        )
            .then { OkHttp()(it) }
            .asGraphQLHandler("https://api.linear.app/graphql")

    val date = LocalDate.from(yearMonth.atDay(1))
    return handler(
        // language=graphql
        GraphQLRequest(
            query = """
                query {
                  issues(filter: {
                    state: {
                      name: {
                        eq: "Done"
                      }
                    }
                    completedAt: {
                      gte: "$date",
                      lt: "${date.plusMonths(1)}"
                    }
                	attachments: {
                		sourceType: {
                			eq: "github"
                		}
                	}
                  } ) {
                    nodes {
                      assignee {
                        name
                        email
                      }
                      url
                      title
                      team {
                        name
                      }
                      project {
                        name
                        lead {
                          email
                        }
                      }
                    }
                  }
                }
            """.trimIndent()
        )
    ).let {
        it.takeIf { it.errors.isNullOrEmpty() }?.let {
          it.data!!.asJsonObject().asA(LinearReport::class)
        } ?: throw Exception(it.errors.toString())
    }
}

fun process(report: LinearReport): String {
    return report.issues.nodes.groupBy {
        it.team
    }.mapValues { it.value.groupBy { it.project } }
        .map {
            it.key.toMd() + it.value.map {
                it.key.toMd() + it.value.joinToString("", transform = Issue::toMd)
            }.joinToString("")
        }.joinToString("  \n")
}

private fun Team?.toMd() =
    "## ${this?.name ?: "No team"}\n"

private fun Project?.toMd() =
    "### ${this?.name ?: "No project"}\n"

private fun Issue.toMd() =
    "* " + (url?.let { "[$title]($url)" } ?: title) + (assignee?.let { " @${it.name}" } ?: "") + "\n"

data class Assignee(
    val name: String,
    val email: String,
)

data class Team(
    val name: String
)

data class Project(
    val name: String,
    val lead: Any?
)

data class Issue(
    val assignee: Assignee?,
    val url: String?,
    val title: String,
    val team: Team?,
    val project: Project?
)

data class Issues(
    val nodes: List<Issue>
)

data class LinearReport(
    val issues: Issues
)
