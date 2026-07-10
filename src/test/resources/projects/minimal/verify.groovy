// ── Test: minimal ─────────────────────────────────────────────────────────────
// appName=Minimal Test  integrations=  serviceAreas=,  presentationTypes= (integrations and presentation empty)
// Covers: absolute minimal project — no integration modules, no presentation
//         modules; atDeps collapses to [app, common-domain]; appDeps to
//         [common-domain, service]

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

// Instruction 2: each module's artifactId is "<artifactId property>-<dir name>". The wiring
// assertions below use bare module names, so text() strips the "<aid>-" prefix from <artifactId>
// tags; the prefix is verified explicitly via raw() (module's own artifactId + parent DM), and the
// generated project's real Maven build — run before this script — fails on any inconsistent reference.
def aid = 'minimal-test'
def appName = 'Minimal Test'
def raw = { String rel ->
    new File(basedir, rel).text
}
def text = { String rel ->
    raw(rel).replace("<artifactId>${aid}-", '<artifactId>')
}

// ── 1. parent/ module exists; no root pom.xml ────────────────────────────────

check('parent/pom.xml')
assert !new File(basedir, 'pom.xml').exists() : "Root pom.xml must not exist"

// ── 2. Exactly the fixed modules plus service — no integration/presentation ──

def modules = [
    'acceptance-tests', 'app', 'common-domain', 'common-testing',
    'domain-service', 'service',
]
modules.each { m -> check("${m}/pom.xml") }

assert !new File(basedir, 'domain-rest').exists()        : "No presentation domain module must be created"
assert !new File(basedir, 'presentation-rest').exists()  : "No presentation module must be created"
assert !new File(basedir, 'domain-db-main').exists()     : "No integration domain module must be created"
assert !new File(basedir, 'integration-db-main').exists(): "No integration module must be created"

// ── 3. parent/pom.xml completeness, packaging, sort order ────────────────────

def parentPom = raw('parent/pom.xml')
modules.each { m ->
    assert parentPom.contains("<module>../${m}</module>")             : "parent <modules> missing: ../$m"
    assert parentPom.contains("<artifactId>${aid}-${m}</artifactId>") : "parent <dependencyManagement> missing: $m"
}
assert parentPom.contains('<packaging>pom</packaging>') : "parent must have pom packaging"
assert parentPom.contains("<name>${appName} Parent</name>")                     : 'parent <name> must be "<appName> Parent"'
assert parentPom.contains("<description>${appName}'s parent POM.</description>") : "parent <description> must be \"<appName>'s parent POM.\""

def moduleOrder = modules.sort().collect { "<module>../${it}</module>" }
def moduleBlock = parentPom.replaceAll(/(?s).*<modules>(.*?)<\/modules>.*/, '$1')
assert moduleBlock.findAll(/<module>[^<]+<\/module>/) == moduleOrder : "parent <modules> not sorted"

def dmBlock = (parentPom =~ /(?s)<dependencyManagement>.*?<\/dependencyManagement>/)[0]
def dmIds = dmBlock.findAll(/<artifactId>[^<]+<\/artifactId>/).collect { it.replaceAll(/<\/?artifactId>/, '') }
def dmModuleIds = dmIds.findAll { it.startsWith("${aid}-") }.collect { it.substring(aid.length() + 1) }
assert dmModuleIds.sort() == modules.sort() : "parent <dependencyManagement> must manage exactly the sibling modules"
assert dmModuleIds.size() == modules.size() : "parent <dependencyManagement> wrong entry count: expected ${modules.size()}, got ${dmModuleIds.size()}"

// ── 4. Child modules reference parent/pom.xml ────────────────────────────────

modules.each { m ->
    assert text("${m}/pom.xml").contains('<relativePath>../parent/pom.xml</relativePath>') \
        : "${m}/pom.xml missing relativePath"
}

modules.each { m ->
    assert raw("${m}/pom.xml").contains("<artifactId>${aid}-${m}</artifactId>") \
        : "${m}/pom.xml must declare its own artifactId as '${aid}-${m}'"
}

// ── 5. Source skeletons — package-info.java only under src/main/java ────────

def p = 'com/example/minimal'
def pkg = p.replace('/', '.')
[
    "common-domain/src/main/java/${p}/common/domain/package-info.java",
    "common-testing/src/main/java/${p}/common/testing/package-info.java",
    "service/src/main/java/${p}/service/package-info.java",
    "app/src/main/java/${p}/app/package-info.java",
    "acceptance-tests/src/main/java/${p}/at/package-info.java",
].each { check(it) }

assert text("common-domain/src/main/java/${p}/common/domain/package-info.java").contains("package ${pkg}.common.domain;")    : 'common-domain package-info.java has wrong package declaration'
assert text("common-testing/src/main/java/${p}/common/testing/package-info.java").contains("package ${pkg}.common.testing;") : 'common-testing package-info.java has wrong package declaration'
assert text("service/src/main/java/${p}/service/package-info.java").contains("package ${pkg}.service;")                      : 'service package-info.java has wrong package declaration'
assert text("app/src/main/java/${p}/app/package-info.java").contains("package ${pkg}.app;")                                  : 'app package-info.java has wrong package declaration'
assert text("acceptance-tests/src/main/java/${p}/at/package-info.java").contains("package ${pkg}.at;")                       : 'acceptance-tests package-info.java has wrong package declaration'
assert !new File(basedir, "acceptance-tests/src/test/java/${p}/at/package-info.java").exists() : 'acceptance-tests must not have a test-side package-info.java'
assert !new File(basedir, "common-domain/src/test/java/${p}/common/domain/package-info.java").exists() : 'common-domain must not have a test-side package-info.java'

// ── 6. Dependency wiring ─────────────────────────────────────────────────────

assert text('service/pom.xml').contains('<artifactId>common-domain</artifactId>') : 'service missing common-domain dep'
assert text('service/pom.xml').contains('<artifactId>common-testing</artifactId>') : 'service missing common-testing TEST dep'
assert text('common-testing/pom.xml').contains('<artifactId>common-domain</artifactId>') : 'common-testing missing common-domain dep'
assert text('common-testing/pom.xml').contains('<artifactId>domain-service</artifactId>') : 'common-testing missing domain-service dep'

// app depends only on common-domain and service
def appPom = text('app/pom.xml')
['common-domain', 'service'].each { dep ->
    assert appPom.contains("<artifactId>${dep}</artifactId>") : "app missing dep: $dep"
}
assert !appPom.contains('<artifactId>domain-rest</artifactId>')       : 'app must not reference non-existent presentation module'
assert !appPom.contains('<artifactId>presentation-rest</artifactId>') : 'app must not reference non-existent presentation module'
assert !appPom.contains('<artifactId>domain-db-main</artifactId>')    : 'app must not reference non-existent integration module'
assert !appPom.contains('<artifactId>acceptance-tests</artifactId>')  : 'app must not depend on acceptance-tests'
assert !appPom.contains('<artifactId>common-testing</artifactId>')    : 'app must not depend on common-testing'
assert appPom.count('<artifactId>app</artifactId>') == 1             : 'app must not depend on itself'
// no database integration: no spring-boot-starter-data-jpa; the web/actuator/admin stack is
// unconditional regardless of presentationTypes/integrations
assert !appPom.contains('<artifactId>spring-boot-starter-data-jpa</artifactId>') : 'no database integration: app must not declare spring-boot-starter-data-jpa'
assert appPom.contains('<artifactId>spring-boot-starter</artifactId>')             : 'app must depend on the core spring-boot-starter'
assert appPom.contains('<artifactId>spring-boot-starter-webmvc</artifactId>')      : 'app must depend on spring-boot-starter-webmvc regardless of presentationTypes'
assert appPom.contains('<artifactId>spring-boot-starter-actuator</artifactId>')    : 'app must depend on spring-boot-starter-actuator'
assert appPom.contains('<artifactId>spring-boot-admin-starter-client</artifactId>'): 'app must depend on spring-boot-admin-starter-client'

// acceptance-tests depends only on app + common-domain (no domain modules exist), + common-testing
def atPom = text('acceptance-tests/pom.xml')
['app', 'common-domain'].each { dep ->
    assert atPom.contains("<artifactId>${dep}</artifactId>") : "acceptance-tests missing dep: $dep"
}
assert !atPom.contains('<artifactId>service</artifactId>')     : 'acceptance-tests must not depend on service'
assert !atPom.contains('<artifactId>domain-rest</artifactId>') : 'acceptance-tests must not reference non-existent domain module'
assert atPom.contains('<artifactId>common-testing</artifactId>') : 'acceptance-tests missing common-testing TEST dep'
// no database integration: datasource-proxy (sql logging) must not be added
assert !atPom.contains('<artifactId>datasource-proxy-spring-boot-starter</artifactId>') : 'acceptance-tests must not get the datasource-proxy dep'

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

// ── #8 service-area domain module + #4 Spring Boot wiring ────────────────────

check('domain-service/pom.xml')
assert text('domain-service/pom.xml').contains('<artifactId>common-domain</artifactId>') : 'domain-service missing common-domain dep'
assert !text('domain-service/pom.xml').contains('<artifactId>common-testing</artifactId>') : 'domain-service must not depend on common-testing (no domain module does)'
assert text('domain-service/pom.xml').contains('<artifactId>spring-boot-starter-validation</artifactId>') : 'domain-service missing spring-boot-starter-validation dep'
assert text('service/pom.xml').contains('<artifactId>domain-service</artifactId>')       : 'service must depend on domain-service'
check("domain-service/src/main/java/${p}/domain/service/package-info.java")
assert !new File(basedir, "domain-service/src/test/java/${p}/domain/service/package-info.java").exists() \
    : 'domain-service must not have a test-side package-info.java'
check("app/src/main/java/${p}/app/config/Application.java")
def appMain = text("app/src/main/java/${p}/app/config/Application.java")
assert appMain.contains('@SpringBootApplication')                                : 'Application must be @SpringBootApplication'
assert appMain.contains('import com.example.minimal.domain.service.config.DomainServiceConfiguration;') \
    : 'Application must import DomainServiceConfiguration'
assert appMain.contains('    DomainServiceConfiguration.class,') : 'Application must @Import DomainServiceConfiguration by simple name'
assert parentPom.contains('<artifactId>spring-boot-starter-parent</artifactId>')   : 'parent must inherit spring-boot-starter-parent'
// minimal has neither a 'rest' presentation nor a 'database' integration, so Application omits
// @EnableTransactionManagement, but AppServletInitializer is still generated (app is always a
// servlet web app now, regardless of presentationTypes)
check("app/src/main/java/${p}/app/config/AppServletInitializer.java")
assert !appMain.contains('@EnableTransactionManagement') : 'no database integration: Application must not @EnableTransactionManagement'

// ── acceptance-test infrastructure exists (includeSpring=true) even with no presentation types ──

def atConfigClass = 'MinimalTestAcceptanceTestConfiguration'
check("acceptance-tests/src/test/java/${p}/at/config/${atConfigClass}.java")
assert text("acceptance-tests/src/test/java/${p}/at/config/${atConfigClass}.java").contains("Configuration class for ${appName} ATs.") \
    : 'AT configuration class Javadoc must name the app'
check('acceptance-tests/src/test/resources/logback-spring.xml')
assert !new File(basedir, "acceptance-tests/src/test/java/${p}/at/AppRestAcceptanceTestBase.java").exists() \
    : 'AppRestAcceptanceTestBase must not be generated (no rest presentation)'
assert !new File(basedir, "acceptance-tests/src/test/java/${p}/at/AppGraphQlAcceptanceTestBase.java").exists() \
    : 'AppGraphQlAcceptanceTestBase must not be generated (no graphql presentation)'
assert !new File(basedir, "common-testing/src/main/java/${p}/common/testing/RestAcceptanceTestBase.java").exists() \
    : 'RestAcceptanceTestBase must not be generated (no rest presentation)'
assert !new File(basedir, "common-testing/src/main/java/${p}/common/testing/GraphQlAcceptanceTestBase.java").exists() \
    : 'GraphQlAcceptanceTestBase must not be generated (no graphql presentation)'

assert text('app/src/main/resources/application.properties').contains('spring.application.name=minimaltest') \
    : 'spring.application.name must be appName with spaces removed and lowercased'

_buildLog.append("\n=== PASSED ===\n")
true
