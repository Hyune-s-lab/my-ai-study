plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.spring") version "2.3.0"
    id("org.springframework.boot") version "4.0.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "dev.hyune"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
}

dependencies {
    // Spring AI 2.0.0-M1
    implementation(platform("org.springframework.ai:spring-ai-bom:2.0.0-M1"))
    
    // Spring AI MCP Server (SSE/WebFlux 기반)
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webflux")
    
    // Markdown 파싱 (Commonmark)
    implementation("org.commonmark:commonmark:0.24.0")
    
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    
    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.3")
    
    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.mockk:mockk:1.13.16")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// JAR 패키징 설정 (Claude Desktop 연동용)
tasks.bootJar {
    archiveFileName.set("opengateway-mcp.jar")
}
