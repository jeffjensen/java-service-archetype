// ── Test: basic ───────────────────────────────────────────────────────────────
// integrations=database:main  serviceAreas=,  presentationTypes=rest
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
assert dmModuleIds == modules.sort()        : "parent <dependencyManagement> not sorted alphabetically"
assert dmModuleIds.size() == modules.size() : "parent <dependencyManagement> wrong entry count: expected ${modules.size()}, got ${dmModuleIds.size()}"

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

// ── 7. Source directory skeletons ────────────────────────────────────────────

def p = 'com/example/basic'
def pkg = p.replace('/', '.')
[
    "common-domain/src/main/java/${p}/common/domain/package-info.java",
    "common-domain/src/test/java/${p}/common/domain/package-info.java",
    "common-testing/src/main/java/${p}/common/testing/package-info.java",
    "domain-db-main/src/main/java/${p}/domain/db/main/package-info.java",
    "domain-db-main/src/test/java/${p}/domain/db/main/package-info.java",
    "integration-db-main/src/main/java/${p}/integration/db/main/package-info.java",
    "integration-db-main/src/test/java/${p}/integration/db/main/package-info.java",
    "service/src/main/java/${p}/service/package-info.java",
    "service/src/test/java/${p}/service/package-info.java",
    "domain-rest/src/main/java/${p}/domain/rest/package-info.java",
    "domain-rest/src/test/java/${p}/domain/rest/package-info.java",
    "presentation-rest/src/main/java/${p}/presentation/rest/package-info.java",
    "presentation-rest/src/test/java/${p}/presentation/rest/package-info.java",
    "app/src/main/java/${p}/app/package-info.java",
    "app/src/test/java/${p}/app/package-info.java",
    "acceptance-tests/src/test/java/${p}/at/package-info.java",
].each { check(it) }

assert text("common-domain/src/main/java/${p}/common/domain/package-info.java").contains("package ${pkg}.common.domain;") \
    : 'common-domain package-info.java has wrong package declaration'
assert text("service/src/main/java/${p}/service/package-info.java").contains("package ${pkg}.service;") \
    : 'service package-info.java has wrong package declaration'
assert text("acceptance-tests/src/test/java/${p}/at/package-info.java").contains("package ${pkg}.at;") \
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
    "acceptance-tests/src/test/java/${p}/at/package-info.java",
].each { path ->
    def content = text(path)
    assert content.startsWith('/**') : "${path} must start with a Javadoc comment"
}

// common-testing and acceptance-tests now carry package-info in both source roots
assert new File(basedir, "common-testing/src/test/java/${p}/common/testing/package-info.java").exists() \
    : 'common-testing must have test Java package-info.java'
assert new File(basedir, "acceptance-tests/src/main/java/${p}/at/package-info.java").exists() \
    : 'acceptance-tests must have main Java package-info.java'

// ── 8. Module dependency wiring ───────────────────────────────────────────────

assert text('common-testing/pom.xml').contains('<artifactId>common-domain</artifactId>') \
    : 'common-testing must depend on common-domain'

assert text('domain-db-main/pom.xml').contains('<artifactId>common-domain</artifactId>') \
    : 'domain-db-main missing common-domain dep'
assert text('domain-rest/pom.xml').contains('<artifactId>common-domain</artifactId>') \
    : 'domain-rest missing common-domain dep'
assert text('service/pom.xml').contains('<artifactId>common-domain</artifactId>') \
    : 'service missing common-domain dep'

def intPom = text('integration-db-main/pom.xml')
assert intPom.contains('<artifactId>common-domain</artifactId>')  : 'integration-db-main missing common-domain'
assert intPom.contains('<artifactId>domain-db-main</artifactId>') : 'integration-db-main missing domain-db-main'

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

// acceptance-tests → app + common-domain + integration domain + presentation domain
def atPom = text('acceptance-tests/pom.xml')
['app', 'common-domain', 'domain-db-main', 'domain-rest'].each { dep ->
    assert atPom.contains("<artifactId>${dep}</artifactId>") : "acceptance-tests missing dep: $dep"
}
assert !atPom.contains('<artifactId>service</artifactId>')             : 'acceptance-tests must not depend on service'
assert !atPom.contains('<artifactId>integration-db-main</artifactId>') : 'acceptance-tests must not depend on integration-db-main'

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
assert text('service/pom.xml').contains('<artifactId>domain-service</artifactId>')       : 'service must depend on domain-service'
check("app/src/main/java/${p}/app/config/Application.java")
check("app/src/main/java/${p}/app/config/AppServletInitializer.java")
assert parentPom.contains('<artifactId>spring-boot-starter-parent</artifactId>') : 'parent must inherit spring-boot-starter-parent'
assert appPom.contains('<artifactId>spring-boot-starter-web</artifactId>')     : 'app must depend on spring-boot-starter-web'

_buildLog.append("\n=== PASSED ===\n")
true
