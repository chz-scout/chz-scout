plugins {
    java
    jacoco
}

jacoco {
    toolVersion = "0.8.12"
}

// 커버리지 측정에서 제외할 패턴
val jacocoExcludes = listOf(
    "**/entity/**",
    "**/dto/**",
    "**/exception/**",
    "**/response/**",
    "**/config/**",
    "**/event/**",
    "**/infrastructure/**",
    "**/presentation/**",
    "**/*Application*"
)

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(jacocoExcludes)
            }
        })
    )
}

tasks.jacocoTestCoverageVerification {
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(jacocoExcludes)
            }
        })
    )

    violationRules {
        rule {
            limit {
                minimum = "0.60".toBigDecimal()
            }
        }
    }
}