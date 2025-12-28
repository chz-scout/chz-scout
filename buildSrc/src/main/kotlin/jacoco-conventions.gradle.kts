plugins {
    java
    jacoco
}

jacoco {
    toolVersion = "0.8.12"
}

// 커버리지 측정에서 제외할 패턴
val jacocoExcludes = listOf(
    "**/domain/**",
    "**/exception/**",
    "**/response/**",
    "**/config/**",
    "**/example/**",
    "**/usecase/**",
    "**/event/**",
    "**/ai/infrastructure/**",
    "**/prompt/**",
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
    dependsOn(tasks.test, tasks.classes)

    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(jacocoExcludes)
            }
        })
    )

    violationRules {
        // 전체 커버리지 70% 이상
        rule {
            limit {
                minimum = "0.70".toBigDecimal()
            }
        }
        // 개별 클래스 커버리지 60% 이상
        rule {
            element = "CLASS"
            limit {
                minimum = "0.60".toBigDecimal()
            }
        }
    }
}