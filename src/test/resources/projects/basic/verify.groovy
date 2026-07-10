// ── Test: basic ───────────────────────────────────────────────────────────────
// appName=Basic Test  integrations=database:main  serviceAreas=,  presentationTypes=rest
// Covers: db abbreviation, single service named "service", single presentation,
//         src skeleton, parent/ module structure, app/acceptance-tests deps

def _testName = new File(basedir, 'project').isDirectory() ? basedir.name : 'unknown'
def _projectSubdir = new File(basedir, 'project')
if (_projectSubdir.isDirectory()) basedir = _projectSubdir.listFiles().find { it.isDirectory() }
def _buildLog = new File(basedir, 'build.log')
def _props = new File(basedir.parentFile.parentFile, 'archetype.properties')
def _existing = _buildLog.exists() ? _buildLog.text : ''
def _propsText = _props.exists() ? _props.text.trim() + '\n' : ''
_buildLog.text = "=== IT: ${_testName} ===\n${_propsText}${'=' * 40}\n\n${_existing}"
def check = { String rel ->
    assert new File(basedir, rel).exists() : "Missing: $rel"
}

// Instruction 2: each module's artifactId is "<artifactId property>-<dir name>". The inter-module
// wiring assertions below are written in terms of bare module names, so text() strips the "<aid>-"
// prefix from <artifactId> tags; the prefix itself is verified explicitly via raw() (each module's
// own artifactId, the parent dependencyManagement, and the parent artifactId), and the generated
// project's real Maven build — run before this script — would fail on any inconsistent reference.
def aid = 'basic-test'
def appName = 'Basic Test'
def raw = { String rel ->
    new File(basedir, rel).text
}
def text = { String rel ->
    raw(rel).replace("<artifactId>${aid}-", '<artifactId>')
}

// ── 1. parent/ module exists; no root pom.xml ────────────────────────────────

check('parent/pom.xml')
assert !new File(basedir, 'pom.xml').exists() : "Root pom.xml must not exist; parent/pom.xml is the entry point"

// ── 2. All expected sibling modules exist ─────────────────────────────────────

def modules = [
    'acceptance-tests', 'app', 'common-domain', 'common-testing',
    'domain-service',
    'domain-db-main', 'domain-rest', 'integration-db-main',
    'presentation-rest', 'service'
]
modules.each { m -> check("${m}/pom.xml") }

// ── 3. "database" type is abbreviated to "db" ────────────────────────────────

assert !new File(basedir, 'domain-database-main').exists()    : "'database' must be abbreviated to 'db'"
assert !new File(basedir, 'integration-database-main').exists(): "'database' must be abbreviated to 'db'"

// ── 4. parent/pom.xml lists every module with "../" relative paths ───────────

def parentPom = raw('parent/pom.xml')
modules.each { m ->
    assert parentPom.contains("<module>../${m}</module>")             : "parent <modules> missing: ../$m"
    assert parentPom.contains("<artifactId>${aid}-${m}</artifactId>") : "parent <dependencyManagement> missing: $m"
}
assert parentPom.contains('<packaging>pom</packaging>')  : "parent must have pom packaging"
assert parentPom.contains('${project.version}')          : "dependencyManagement must use \${project.version}"
assert parentPom.contains('<groupId>com.example</groupId>')      : 'parent must have correct groupId'
assert parentPom.contains('<artifactId>basic-test-parent</artifactId>') : 'parent must have correct artifactId'
assert parentPom.contains('<version>1.0.0-SNAPSHOT</version>')   : 'parent must have correct version'
assert parentPom.contains("<name>${appName} Parent</name>")      : 'parent <name> must be "<appName> Parent"'
assert parentPom.contains("<description>${appName}'s parent POM.</description>") : "parent <description> must be \"<appName>'s parent POM.\""
assert parentPom.contains('<java.version>21</java.version>')     : 'parent must declare java.version=21'
assert parentPom.contains('<maven.compiler.release>${java.version}</maven.compiler.release>') \
    : 'parent must use maven.compiler.release'
assert parentPom.contains('<maven-compiler-plugin.version>3.13.0</maven-compiler-plugin.version>') : 'parent must pin maven-compiler-plugin version'
assert parentPom.contains('<maven-failsafe-plugin.version>3.5.5</maven-failsafe-plugin.version>')   : 'parent must pin maven-failsafe-plugin version'
assert parentPom.contains('<maven-jar-plugin.version>3.4.2</maven-jar-plugin.version>')             : 'parent must pin maven-jar-plugin version'
assert parentPom.contains('<maven-surefire-plugin.version>3.5.5</maven-surefire-plugin.version>')   : 'parent must pin maven-surefire-plugin version'
assert parentPom.contains('<artifactId>maven-compiler-plugin</artifactId>') : 'parent must declare maven-compiler-plugin in pluginManagement'
assert parentPom.contains('<artifactId>maven-failsafe-plugin</artifactId>') : 'parent must declare maven-failsafe-plugin in pluginManagement'
assert parentPom.contains('<artifactId>maven-jar-plugin</artifactId>')      : 'parent must declare maven-jar-plugin in pluginManagement'
assert parentPom.contains('<artifactId>maven-surefire-plugin</artifactId>') : 'parent must declare maven-surefire-plugin in pluginManagement'
assert parentPom.contains('<skipIfEmpty>true</skipIfEmpty>')                 : 'maven-jar-plugin must configure skipIfEmpty=true'

// third-party deps outside the Spring Boot BOM (datasource-proxy, Spring Boot Admin client) are
// each pinned via their own managed version property
assert parentPom.contains('<datasource-proxy-spring-boot-starter.version>') : 'parent must pin a datasource-proxy-spring-boot-starter.version property'
assert parentPom.contains('<spring-boot-admin-starter-client.version>')     : 'parent must pin a spring-boot-admin-starter-client.version property'
assert parentPom.contains('<artifactId>datasource-proxy-spring-boot-starter</artifactId>') : 'parent dependencyManagement must manage datasource-proxy-spring-boot-starter'
assert parentPom.contains('<artifactId>spring-boot-admin-starter-client</artifactId>')     : 'parent dependencyManagement must manage spring-boot-admin-starter-client'

// modernizer-maven-plugin: pinned, managed in pluginManagement, activated in build/plugins
assert parentPom.contains('<modernizer-maven-plugin.version>3.4.0</modernizer-maven-plugin.version>') \
    : 'parent must pin modernizer-maven-plugin version'
assert parentPom.count('<artifactId>modernizer-maven-plugin</artifactId>') == 2 \
    : 'modernizer-maven-plugin must appear twice: once in pluginManagement, once in active plugins'

def pmBlock = (parentPom =~ /(?s)<pluginManagement>.*<\/pluginManagement>/)[0]
assert pmBlock.contains('<groupId>org.gaul</groupId>')                       : 'modernizer must use groupId org.gaul'
assert pmBlock.contains('<artifactId>modernizer-maven-plugin</artifactId>') : 'modernizer must be declared in pluginManagement'
assert pmBlock.contains('<version>${modernizer-maven-plugin.version}</version>') : 'modernizer pluginManagement must reference the version property'
assert pmBlock.contains('<javaVersion>${java.version}</javaVersion>')        : 'modernizer must configure javaVersion'
assert pmBlock.contains('<goal>modernizer</goal>')                          : 'modernizer pluginManagement must bind the modernizer goal'

// versions-maven-plugin: pinned and managed in pluginManagement with the update ruleSet
assert parentPom.contains('<versions-maven-plugin.version>2.18.0</versions-maven-plugin.version>') \
    : 'parent must pin versions-maven-plugin version'
assert pmBlock.contains('<artifactId>versions-maven-plugin</artifactId>')         : 'versions-maven-plugin must be declared in pluginManagement'
assert pmBlock.contains('<version>${versions-maven-plugin.version}</version>')     : 'versions-maven-plugin pluginManagement must reference the version property'
assert pmBlock.contains('<artifactId>graphql-java-extended-scalars</artifactId>') : 'versions-maven-plugin must configure the graphql-java-extended-scalars rule'

// the active <plugins> section is what remains of <build> after removing <pluginManagement>
def buildBlock    = (parentPom =~ /(?s)<build>.*<\/build>/)[0]
def activePlugins = buildBlock.replaceAll(/(?s)<pluginManagement>.*<\/pluginManagement>/, '')
assert activePlugins.contains('<artifactId>modernizer-maven-plugin</artifactId>') \
    : 'modernizer-maven-plugin must be activated in the parent <build><plugins> section'

// ── 5. Modules are sorted alphabetically in <modules> ────────────────────────

def moduleOrder = modules.sort().collect { "<module>../${it}</module>" }
def moduleBlock = parentPom.replaceAll(/(?s).*<modules>(.*?)<\/modules>.*/, '$1')
def foundModules = moduleBlock.findAll(/<module>[^<]+<\/module>/)
assert foundModules == moduleOrder : "parent <modules> not sorted alphabetically"

def dmBlock = (parentPom =~ /(?s)<dependencyManagement>.*?<\/dependencyManagement>/)[0]
def dmIds = dmBlock.findAll(/<artifactId>[^<]+<\/artifactId>/).collect { it.replaceAll(/<\/?artifactId>/, '') }
def dmModuleIds = dmIds.findAll { it.startsWith("${aid}-") }.collect { it.substring(aid.length() + 1) }
assert dmModuleIds.sort() == modules.sort() : "parent <dependencyManagement> must manage exactly the sibling modules"
assert dmModuleIds.size() == modules.size() : "parent <dependencyManagement> wrong entry count: expected ${modules.size()}, got ${dmModuleIds.size()}"
// acceptance-tests must never appear as an actual <dependency> of any other module — only managed
modules.findAll { it != 'acceptance-tests' }.each { m ->
    assert !text("${m}/pom.xml").contains('<artifactId>acceptance-tests</artifactId>') : "${m} must not depend on acceptance-tests"
}

// ── 6. Child module <parent> references parent/pom.xml ───────────────────────

modules.each { m ->
    def pom = text("${m}/pom.xml")
    assert pom.contains('<relativePath>../parent/pom.xml</relativePath>') \
        : "${m}/pom.xml missing <relativePath>../parent/pom.xml</relativePath>"
}

modules.each { m ->
    assert raw("${m}/pom.xml").contains("<artifactId>${aid}-${m}</artifactId>") \
        : "${m}/pom.xml must declare its own artifactId as '${aid}-${m}'"
}

// ── 7. Source directory skeletons — package-info.java only under src/main/java ──
// Java rejects two package-info.java files compiled for the same package, so no module ever gets
// one under src/test/java (exhaustive check across every module, done here once for the archetype).

def p = 'com/example/basic'
def pkg = p.replace('/', '.')
[
    "common-domain/src/main/java/${p}/common/domain/package-info.java",
    "common-testing/src/main/java/${p}/common/testing/package-info.java",
    "domain-db-main/src/main/java/${p}/domain/db/main/package-info.java",
    "integration-db-main/src/main/java/${p}/integration/db/main/package-info.java",
    "service/src/main/java/${p}/service/package-info.java",
    "domain-rest/src/main/java/${p}/domain/rest/package-info.java",
    "presentation-rest/src/main/java/${p}/presentation/rest/package-info.java",
    "app/src/main/java/${p}/app/package-info.java",
    "acceptance-tests/src/main/java/${p}/at/package-info.java",
].each { check(it) }

assert text("common-domain/src/main/java/${p}/common/domain/package-info.java").contains("package ${pkg}.common.domain;") \
    : 'common-domain package-info.java has wrong package declaration'
assert text("service/src/main/java/${p}/service/package-info.java").contains("package ${pkg}.service;") \
    : 'service package-info.java has wrong package declaration'
assert text("acceptance-tests/src/main/java/${p}/at/package-info.java").contains("package ${pkg}.at;") \
    : 'acceptance-tests package-info.java has wrong package declaration'

// ── package-info.java Javadoc comments ───────────────────────────────────────

[
    "common-domain/src/main/java/${p}/common/domain/package-info.java",
    "common-testing/src/main/java/${p}/common/testing/package-info.java",
    "domain-db-main/src/main/java/${p}/domain/db/main/package-info.java",
    "integration-db-main/src/main/java/${p}/integration/db/main/package-info.java",
    "domain-rest/src/main/java/${p}/domain/rest/package-info.java",
    "presentation-rest/src/main/java/${p}/presentation/rest/package-info.java",
    "service/src/main/java/${p}/service/package-info.java",
    "app/src/main/java/${p}/app/package-info.java",
    "acceptance-tests/src/main/java/${p}/at/package-info.java",
].each { path ->
    def content = text(path)
    assert content.startsWith('/**') : "${path} must start with a Javadoc comment"
}

// no module has a src/test/java/**/package-info.java — exhaustive, every module in this project
modules.each { m ->
    def testJavaDir = new File(basedir, "${m}/src/test/java")
    if (testJavaDir.exists()) {
        testJavaDir.eachFileRecurse { f ->
            assert f.name != 'package-info.java' : "${m} must not have a src/test/java package-info.java: ${f}"
        }
    }
}

// ── 8. Module dependency wiring ───────────────────────────────────────────────

// common-testing depends on every domain module. No domain module depends back on
// common-testing (only service/service-{name} does), so this stays acyclic.
assert text('common-testing/pom.xml').contains('<artifactId>common-domain</artifactId>') \
    : 'common-testing must depend on common-domain'
assert text('common-testing/pom.xml').contains('<artifactId>domain-db-main</artifactId>') \
    : 'common-testing must depend on the integration domain module domain-db-main'
assert text('common-testing/pom.xml').contains('<artifactId>domain-service</artifactId>') \
    : 'common-testing must depend on domain-service'
assert text('common-testing/pom.xml').contains('<artifactId>domain-rest</artifactId>') \
    : 'common-testing must depend on domain-rest'
assert !text('domain-service/pom.xml').contains('<artifactId>common-testing</artifactId>') \
    : 'domain-service must not depend on common-testing (no domain module does)'

assert text('domain-db-main/pom.xml').contains('<artifactId>common-domain</artifactId>') \
    : 'domain-db-main missing common-domain dep'
assert text('domain-rest/pom.xml').contains('<artifactId>common-domain</artifactId>') \
    : 'domain-rest missing common-domain dep'
// domain-rest ("the service domain deps") depends on domain-service too
assert text('domain-rest/pom.xml').contains('<artifactId>domain-service</artifactId>') \
    : 'domain-rest missing domain-service dep (the service domain deps)'
assert text('service/pom.xml').contains('<artifactId>common-domain</artifactId>') \
    : 'service missing common-domain dep'

def intPom = text('integration-db-main/pom.xml')
assert intPom.contains('<artifactId>common-domain</artifactId>')  : 'integration-db-main missing common-domain'
assert intPom.contains('<artifactId>domain-db-main</artifactId>') : 'integration-db-main missing domain-db-main'
assert intPom.contains('<artifactId>datasource-proxy-spring-boot-starter</artifactId>') \
    : 'integration-db-main (a database integration) missing datasource-proxy dep'

def presPom = text('presentation-rest/pom.xml')
assert presPom.contains('<artifactId>common-domain</artifactId>') : 'presentation-rest missing common-domain'
assert presPom.contains('<artifactId>domain-rest</artifactId>')   : 'presentation-rest missing domain-rest'

// app → all non-test modules; NOT acceptance-tests or common-testing or itself
def appPom = text('app/pom.xml')
['common-domain', 'domain-db-main', 'domain-rest',
 'integration-db-main', 'presentation-rest', 'service'].each { dep ->
    assert appPom.contains("<artifactId>${dep}</artifactId>") : "app missing dep: $dep"
}
assert !appPom.contains('<artifactId>acceptance-tests</artifactId>') : 'app must not depend on acceptance-tests'
assert !appPom.contains('<artifactId>common-testing</artifactId>')   : 'app must not depend on common-testing'
assert appPom.count('<artifactId>app</artifactId>') == 1            : 'app must not depend on itself'

// acceptance-tests → app + common-domain + integration domain + presentation domain; + common-testing
// (TEST) and datasource-proxy (sql logging)
def atPom = text('acceptance-tests/pom.xml')
['app', 'common-domain', 'domain-db-main', 'domain-rest'].each { dep ->
    assert atPom.contains("<artifactId>${dep}</artifactId>") : "acceptance-tests missing dep: $dep"
}
assert !atPom.contains('<artifactId>service</artifactId>')             : 'acceptance-tests must not depend on service'
assert !atPom.contains('<artifactId>integration-db-main</artifactId>') : 'acceptance-tests must not depend on integration-db-main'
assert atPom.contains('<artifactId>common-testing</artifactId>')                       : 'acceptance-tests missing common-testing TEST dep'
assert atPom.contains('<artifactId>datasource-proxy-spring-boot-starter</artifactId>') : 'acceptance-tests missing datasource-proxy dep'
assert !atPom.contains('<artifactId>spring-graphql-test</artifactId>') : 'acceptance-tests must not add spring-graphql-test (no graphql presentation)'

// ── src directory scaffolding — all four dirs present in every module ─────────

modules.each { m ->
    ['src/main/java', 'src/main/resources', 'src/test/java', 'src/test/resources'].each { dir ->
        check("${m}/${dir}")
    }
}

// ── Failsafe plugin wiring ──────────────────────────────────────

assert text('acceptance-tests/pom.xml').contains('<artifactId>maven-failsafe-plugin</artifactId>') \
    : 'acceptance-tests must configure maven-failsafe-plugin'
modules.findAll { it.startsWith('integration-') }.each { m ->
    assert text("${m}/pom.xml").contains('<artifactId>maven-failsafe-plugin</artifactId>') \
        : "${m} must configure maven-failsafe-plugin"
}

// ── Version-update helper scripts in parent/ ──────────────────────────────────
// They live alongside parent/pom.xml, where the version properties are defined.
// The .sh exec bit is POSIX-only and cannot be set when generating on Windows,
// so it is intentionally not asserted here.

check('parent/mvn-update-properties-versions.sh')
check('parent/mvn-update-properties-versions.ps1')

def shScript = text('parent/mvn-update-properties-versions.sh')
assert shScript.startsWith('#!/bin/bash')                     : 'bash script must start with the bash shebang'
assert shScript.contains('mvn versions:update-properties')    : 'bash script must run mvn versions:update-properties'
assert !shScript.contains('\r')                               : 'bash script must use LF line endings (no CR)'

def psScript = text('parent/mvn-update-properties-versions.ps1')
assert psScript.contains('mvn versions:update-properties')      : 'PowerShell script must run mvn versions:update-properties'
assert psScript.contains('\r\n')                               : 'PowerShell script must use CRLF line endings'

// ── #8 service-area domain module + #4 Spring Boot wiring ────────────────────

check('domain-service/pom.xml')
assert text('domain-service/pom.xml').contains('<artifactId>common-domain</artifactId>') : 'domain-service missing common-domain dep'
assert text('domain-service/pom.xml').contains('<artifactId>spring-boot-starter-validation</artifactId>') : 'domain-service missing spring-boot-starter-validation dep'
assert text('service/pom.xml').contains('<artifactId>domain-service</artifactId>')       : 'service must depend on domain-service'
assert text('service/pom.xml').contains('<artifactId>common-testing</artifactId>')       : 'service missing common-testing TEST dep'
check("app/src/main/java/${p}/app/config/Application.java")
check("app/src/main/java/${p}/app/config/AppServletInitializer.java")
assert parentPom.contains('<artifactId>spring-boot-starter-parent</artifactId>') : 'parent must inherit spring-boot-starter-parent'
assert appPom.contains('<artifactId>spring-boot-starter</artifactId>')           : 'app must depend on the core spring-boot-starter'
assert appPom.contains('<artifactId>spring-boot-starter-webmvc</artifactId>')    : 'app must depend on spring-boot-starter-webmvc'
assert appPom.contains('<artifactId>spring-boot-starter-actuator</artifactId>')  : 'app must depend on spring-boot-starter-actuator'
assert appPom.contains('<artifactId>spring-boot-admin-starter-client</artifactId>') : 'app must depend on spring-boot-admin-starter-client'
assert appPom.contains('<artifactId>spring-boot-starter-data-jpa</artifactId>')     : 'app must depend on spring-boot-starter-data-jpa (database integration present)'

// #1: each presentation module depends on all service modules
assert text('presentation-rest/pom.xml').contains('<artifactId>service</artifactId>') : '#1: presentation-rest must depend on service'
// #2: the single default 'service' module depends on every integration module
assert text('service/pom.xml').contains('<artifactId>integration-db-main</artifactId>') : '#2: default service must depend on integration-db-main'

// ── acceptance-test infrastructure (REST only — no graphql presentation) ─────

def atConfigClass = 'BasicTestAcceptanceTestConfiguration'
check("acceptance-tests/src/test/java/${p}/at/config/${atConfigClass}.java")
def atConfig = text("acceptance-tests/src/test/java/${p}/at/config/${atConfigClass}.java")
assert atConfig.contains('@Configuration')                          : 'AT configuration class must be @Configuration'
assert atConfig.contains("Configuration class for ${appName} ATs.") : 'AT configuration class Javadoc must name the app'

check('acceptance-tests/src/test/resources/logback-spring.xml')
assert text('acceptance-tests/src/test/resources/logback-spring.xml')
    .contains('<include resource="org/springframework/boot/logging/logback/base.xml" />') \
    : 'logback-spring.xml must include the Boot base logback config'

check("acceptance-tests/src/test/java/${p}/at/AppRestAcceptanceTestBase.java")
def appRestAt = text("acceptance-tests/src/test/java/${p}/at/AppRestAcceptanceTestBase.java")
assert appRestAt.contains("Base test class for all ${appName} REST acceptance tests.") : 'AppRestAcceptanceTestBase Javadoc must name the app'
assert appRestAt.contains('extends RestAcceptanceTestBase')                            : 'AppRestAcceptanceTestBase must extend RestAcceptanceTestBase'
assert appRestAt.contains("@ContextConfiguration(classes = { ${atConfigClass}.class,")  : 'AppRestAcceptanceTestBase must @ContextConfiguration with the AT config class first'
assert appRestAt.contains('@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class, XADataSourceAutoConfiguration.class })') \
    : 'AppRestAcceptanceTestBase must exclude DataSourceAutoConfiguration/XADataSourceAutoConfiguration (database integration present)'
assert !new File(basedir, "acceptance-tests/src/test/java/${p}/at/AppGraphQlAcceptanceTestBase.java").exists() \
    : 'AppGraphQlAcceptanceTestBase must not be generated (no graphql presentation)'

check("common-testing/src/main/java/${p}/common/testing/RestAcceptanceTestBase.java")
assert text("common-testing/src/main/java/${p}/common/testing/RestAcceptanceTestBase.java")
    .contains('@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)') \
    : 'RestAcceptanceTestBase must be a random-port @SpringBootTest'
assert !new File(basedir, "common-testing/src/main/java/${p}/common/testing/GraphQlAcceptanceTestBase.java").exists() \
    : 'GraphQlAcceptanceTestBase must not be generated (no graphql presentation)'

// ── app: application.properties + per-environment profile files ─────────────

def appProps = text('app/src/main/resources/application.properties')
assert appProps.contains('spring.application.name=basictest') : 'spring.application.name must be appName with spaces removed and lowercased'
assert appProps.contains('decorator.datasource.enabled=true') : 'application.properties must configure SQL logging'
['ci', 'dev', 'local', 'prod', 'qa', 'uat'].each { profile ->
    def f = new File(basedir, "app/src/main/resources/application-${profile}.properties")
    assert f.exists()      : "Missing profile properties file: application-${profile}.properties"
    assert f.text.isEmpty(): "application-${profile}.properties must be empty"
}

_buildLog.append("\n=== PASSED ===\n")
true
