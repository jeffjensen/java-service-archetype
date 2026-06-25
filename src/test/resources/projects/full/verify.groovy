// ── Test: full ────────────────────────────────────────────────────────────────
// integrations=database:users,graphql:catalog
// serviceAreas=orders,notifications  presentationTypes=rest,graphql
// Covers: all features together; graphql as both integration type (graphql:catalog)
//         and presentation type (graphql) — two distinct modules, no name collision

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

def text = { String rel ->
    new File(basedir, rel).text
}

// ── 1. parent/ module exists; no root pom.xml ────────────────────────────────

check('parent/pom.xml')
assert !new File(basedir, 'pom.xml').exists() : "Root pom.xml must not exist"

// ── 2. All expected sibling modules exist ─────────────────────────────────────

def modules = [
    'acceptance-tests', 'app', 'common-domain', 'common-testing',
    'domain-service-notifications',
    'domain-service-orders',
    'domain-db-users',
    'domain-graphql',
    'domain-graphql-catalog',
    'domain-rest',
    'integration-db-users',
    'integration-graphql-catalog',
    'presentation-graphql',
    'presentation-rest',
    'service-notifications',
    'service-orders',
]
modules.each { m -> check("${m}/pom.xml") }

// ── 3. Name collision check: domain-graphql ≠ domain-graphql-catalog ─────────

assert text('domain-graphql/pom.xml').contains('<artifactId>domain-graphql</artifactId>')               : 'domain-graphql artifactId wrong'
assert text('domain-graphql-catalog/pom.xml').contains('<artifactId>domain-graphql-catalog</artifactId>'): 'domain-graphql-catalog artifactId wrong'

// ── 4. No unexpected modules ─────────────────────────────────────────────────

assert !new File(basedir, 'service').exists()           : "plain 'service' must not exist"
assert !new File(basedir, 'service-inventory').exists() : "undeclared 'service-inventory' must not exist"

// ── 5. parent/pom.xml completeness and sort order ────────────────────────────

def parentPom = text('parent/pom.xml')
modules.each { m ->
    assert parentPom.contains("<module>../${m}</module>")      : "parent <modules> missing: ../$m"
    assert parentPom.contains("<artifactId>${m}</artifactId>") : "parent <dependencyManagement> missing: $m"
}
assert parentPom.contains('<packaging>pom</packaging>') : "parent must have pom packaging"

def moduleOrder = modules.sort().collect { "<module>../${it}</module>" }
def moduleBlock = parentPom.replaceAll(/(?s).*<modules>(.*?)<\/modules>.*/, '$1')
assert moduleBlock.findAll(/<module>[^<]+<\/module>/) == moduleOrder : "parent <modules> not sorted"

def dmBlock = (parentPom =~ /(?s)<dependencyManagement>.*?<\/dependencyManagement>/)[0]
def dmIds = dmBlock.findAll(/<artifactId>[^<]+<\/artifactId>/).collect { it.replaceAll(/<\/?artifactId>/, '') }
assert dmIds == modules.sort()        : "parent <dependencyManagement> not sorted alphabetically"
assert dmIds.size() == modules.size() : "parent <dependencyManagement> wrong entry count: expected ${modules.size()}, got ${dmIds.size()}"

// ── 6. Child modules reference parent/pom.xml ────────────────────────────────

modules.each { m ->
    assert text("${m}/pom.xml").contains('<relativePath>../parent/pom.xml</relativePath>') \
        : "${m}/pom.xml missing relativePath"
}

modules.each { m ->
    assert text("${m}/pom.xml").contains("<artifactId>${m}</artifactId>") \
        : "${m}/pom.xml must declare its own artifactId as '${m}'"
}

// ── 7. Source skeletons ───────────────────────────────────────────────────────

def p = 'com/example/full'
def pkg = p.replace('/', '.')
[
    "common-domain/src/main/java/${p}/common/domain/package-info.java",
    "common-domain/src/test/java/${p}/common/domain/package-info.java",
    "common-testing/src/main/java/${p}/common/testing/package-info.java",
    "domain-db-users/src/main/java/${p}/domain/db/users/package-info.java",
    "domain-db-users/src/test/java/${p}/domain/db/users/package-info.java",
    "integration-db-users/src/main/java/${p}/integration/db/users/package-info.java",
    "integration-db-users/src/test/java/${p}/integration/db/users/package-info.java",
    "domain-graphql-catalog/src/main/java/${p}/domain/graphql/catalog/package-info.java",
    "domain-graphql-catalog/src/test/java/${p}/domain/graphql/catalog/package-info.java",
    "integration-graphql-catalog/src/main/java/${p}/integration/graphql/catalog/package-info.java",
    "integration-graphql-catalog/src/test/java/${p}/integration/graphql/catalog/package-info.java",
    "domain-graphql/src/main/java/${p}/domain/graphql/package-info.java",
    "domain-graphql/src/test/java/${p}/domain/graphql/package-info.java",
    "presentation-graphql/src/main/java/${p}/presentation/graphql/package-info.java",
    "presentation-graphql/src/test/java/${p}/presentation/graphql/package-info.java",
    "domain-rest/src/main/java/${p}/domain/rest/package-info.java",
    "domain-rest/src/test/java/${p}/domain/rest/package-info.java",
    "presentation-rest/src/main/java/${p}/presentation/rest/package-info.java",
    "presentation-rest/src/test/java/${p}/presentation/rest/package-info.java",
    "service-orders/src/main/java/${p}/service/orders/package-info.java",
    "service-orders/src/test/java/${p}/service/orders/package-info.java",
    "service-notifications/src/main/java/${p}/service/notifications/package-info.java",
    "service-notifications/src/test/java/${p}/service/notifications/package-info.java",
    "app/src/main/java/${p}/app/package-info.java",
    "app/src/test/java/${p}/app/package-info.java",
    "acceptance-tests/src/test/java/${p}/at/package-info.java",
].each { check(it) }

assert text("common-domain/src/main/java/${p}/common/domain/package-info.java").contains("package ${pkg}.common.domain;")       : 'common-domain package-info.java has wrong package declaration'
assert text("common-testing/src/main/java/${p}/common/testing/package-info.java").contains("package ${pkg}.common.testing;")    : 'common-testing package-info.java has wrong package declaration'
assert text("service-orders/src/main/java/${p}/service/orders/package-info.java").contains("package ${pkg}.service.orders;")    : 'service-orders package-info.java has wrong package declaration'
assert text("service-notifications/src/main/java/${p}/service/notifications/package-info.java").contains("package ${pkg}.service.notifications;") : 'service-notifications package-info.java has wrong package declaration'
assert text("app/src/main/java/${p}/app/package-info.java").contains("package ${pkg}.app;")                                     : 'app package-info.java has wrong package declaration'
assert text("acceptance-tests/src/test/java/${p}/at/package-info.java").contains("package ${pkg}.at;")          : 'acceptance-tests package-info.java has wrong package declaration'
assert new File(basedir, "common-testing/src/test/java/${p}/common/testing/package-info.java").exists()                        : 'common-testing must have test Java package-info.java'
assert new File(basedir, "acceptance-tests/src/main/java/${p}/at/package-info.java").exists()                          : 'acceptance-tests must have main Java package-info.java'

// ── 8. common-domain wiring for domain/service modules ───────────────────────

assert text('domain-db-users/pom.xml').contains('<artifactId>common-domain</artifactId>')        : 'domain-db-users missing common-domain dep'
assert text('domain-graphql-catalog/pom.xml').contains('<artifactId>common-domain</artifactId>') : 'domain-graphql-catalog missing common-domain dep'
assert text('domain-rest/pom.xml').contains('<artifactId>common-domain</artifactId>')            : 'domain-rest missing common-domain dep'
assert text('domain-graphql/pom.xml').contains('<artifactId>common-domain</artifactId>')         : 'domain-graphql missing common-domain dep'
assert text('service-orders/pom.xml').contains('<artifactId>common-domain</artifactId>')         : 'service-orders missing common-domain dep'
assert text('service-notifications/pom.xml').contains('<artifactId>common-domain</artifactId>')  : 'service-notifications missing common-domain dep'

// ── 9. Integration module → domain module dependencies ───────────────────────

assert text('integration-db-users/pom.xml').contains('<artifactId>domain-db-users</artifactId>')               : 'integration-db-users missing domain dep'
assert text('integration-graphql-catalog/pom.xml').contains('<artifactId>domain-graphql-catalog</artifactId>') : 'integration-graphql-catalog missing domain dep'

// presentation-graphql → domain-graphql (presentation), NOT domain-graphql-catalog (integration)
def presGraphqlPom = text('presentation-graphql/pom.xml')
assert presGraphqlPom.contains('<artifactId>domain-graphql</artifactId>')           : 'presentation-graphql missing domain-graphql dep'
assert !presGraphqlPom.contains('<artifactId>domain-graphql-catalog</artifactId>') : 'presentation-graphql must not depend on integration domain'

// ── 10. app deps ──────────────────────────────────────────────────────────────

def appPom = text('app/pom.xml')
['common-domain', 'domain-db-users', 'domain-graphql', 'domain-graphql-catalog',
 'domain-rest', 'integration-db-users', 'integration-graphql-catalog',
 'presentation-graphql', 'presentation-rest',
 'service-notifications', 'service-orders'].each { dep ->
    assert appPom.contains("<artifactId>${dep}</artifactId>") : "app missing dep: $dep"
}
assert !appPom.contains('<artifactId>acceptance-tests</artifactId>') : 'app must not depend on acceptance-tests'
assert !appPom.contains('<artifactId>common-testing</artifactId>')   : 'app must not depend on common-testing'
assert appPom.count('<artifactId>app</artifactId>') == 1            : 'app must not depend on itself'

// ── 11. acceptance-tests deps ─────────────────────────────────────────────────

def atPom = text('acceptance-tests/pom.xml')
['app', 'common-domain', 'domain-db-users', 'domain-graphql',
 'domain-graphql-catalog', 'domain-rest'].each { dep ->
    assert atPom.contains("<artifactId>${dep}</artifactId>") : "acceptance-tests missing dep: $dep"
}
assert !atPom.contains('<artifactId>service-orders</artifactId>')              : 'acceptance-tests must not depend on service-orders'
assert !atPom.contains('<artifactId>integration-graphql-catalog</artifactId>') : 'acceptance-tests must not depend on integration impl'

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

// ── Service-area domain modules (domain-service-*) and wiring (#8) ───────────

['domain-service-orders', 'domain-service-notifications'].each { m ->
    def area = m.replaceFirst('domain-service-', '')
    check("${m}/pom.xml")
    assert text("${m}/pom.xml").contains('<artifactId>common-domain</artifactId>') : "${m} missing common-domain dep"
    check("${m}/src/main/java/${p}/domain/service/${area}/package-info.java")
    check("${m}/src/test/java/${p}/domain/service/${area}/package-info.java")
    assert atPom.contains("<artifactId>${m}</artifactId>") : "acceptance-tests must depend on ${m}"
}
assert text('service-orders/pom.xml').contains('<artifactId>domain-service-orders</artifactId>')               : 'service-orders must depend on domain-service-orders'
assert text('service-notifications/pom.xml').contains('<artifactId>domain-service-notifications</artifactId>') : 'service-notifications must depend on domain-service-notifications'

// ── Spring Boot wiring (#4) ──────────────────────────────────────────────────

def appConfig = "app/src/main/java/${p}/app/config"
check("${appConfig}/Application.java")
check("${appConfig}/AppServletInitializer.java")
def appMain = text("${appConfig}/Application.java")
assert appMain.contains('@SpringBootApplication')       : 'Application must be @SpringBootApplication'
assert appMain.contains('@EnableTransactionManagement') : 'Application must enable transaction management'
assert appMain.contains('@Import({')                    : 'Application must @Import module configs'
assert appMain.contains('com.example.full.domain.service.orders.config.DomainServiceOrdersConfiguration.class') \
    : 'Application must @Import each module config class'
assert text("${appConfig}/AppServletInitializer.java").contains('extends SpringBootServletInitializer') \
    : 'AppServletInitializer must extend SpringBootServletInitializer'

// every app-composed module carries a @Configuration class in its .config package
check("common-domain/src/main/java/${p}/common/domain/config/CommonDomainConfiguration.java")
check("presentation-rest/src/main/java/${p}/presentation/rest/config/PresentationRestConfiguration.java")
assert text("common-domain/src/main/java/${p}/common/domain/config/CommonDomainConfiguration.java").contains('@Configuration') \
    : 'config class must be annotated @Configuration'

// every app-composed module also carries a {Module}ComponentScan marker interface in its root
// package, with the @Configuration anchored to it via @ComponentScan(basePackageClasses=…)
check("common-domain/src/main/java/${p}/common/domain/CommonDomainComponentScan.java")
check("presentation-rest/src/main/java/${p}/presentation/rest/PresentationRestComponentScan.java")
def scanIface = text("common-domain/src/main/java/${p}/common/domain/CommonDomainComponentScan.java")
assert scanIface.contains('public interface CommonDomainComponentScan')     : 'marker must be a public interface'
assert scanIface.contains('Anchor class for based package class scanning.') : 'marker must carry the anchor Javadoc'
def cdConfig = text("common-domain/src/main/java/${p}/common/domain/config/CommonDomainConfiguration.java")
assert cdConfig.contains('@ComponentScan(basePackageClasses = CommonDomainComponentScan.class)') \
    : 'config class must @ComponentScan its module marker interface'
assert cdConfig.contains("import ${p.replace('/', '.')}.common.domain.CommonDomainComponentScan;") \
    : 'config class must import its module marker interface'

// parent imports the Spring Boot BOM; app pulls the starters
assert parentPom.contains('<artifactId>spring-boot-starter-parent</artifactId>') : 'parent must inherit spring-boot-starter-parent'
assert parentPom.contains('<version>4.1.0</version>')                            : 'parent must pin Spring Boot 4.1.0'
assert appPom.contains('<artifactId>spring-boot-starter-web</artifactId>')    : 'app must depend on spring-boot-starter-web'
assert appPom.contains('<artifactId>spring-boot-starter-data-jpa</artifactId>')   : 'app must depend on spring-boot-starter-data-jpa'

// ── per-module Spring dependencies (#3): the blanket spring-context is gone; common-domain
//    carries the core starter (so "plain" modules inherit @Configuration support transitively)
//    and presentation/integration modules each declare a role-specific starter.
modules.each { m ->
    assert !text("${m}/pom.xml").contains('spring-context') : "${m} must not declare spring-context"
}
assert text('common-domain/pom.xml').contains('<artifactId>spring-boot-starter</artifactId>') \
    : 'common-domain must carry the core spring-boot-starter (provides the @Configuration API transitively)'
assert text('presentation-rest/pom.xml').contains('<artifactId>spring-boot-starter-web</artifactId>') \
    : 'presentation-rest must declare spring-boot-starter-web'
assert text('presentation-graphql/pom.xml').contains('<artifactId>spring-boot-starter-graphql</artifactId>') \
    : 'presentation-graphql must declare spring-boot-starter-graphql (#2)'
assert text('integration-db-users/pom.xml').contains('<artifactId>spring-boot-starter-data-jpa</artifactId>') \
    : 'database integration must declare spring-boot-starter-data-jpa'
assert text('integration-graphql-catalog/pom.xml').contains('<artifactId>spring-boot-starter-graphql</artifactId>') \
    : 'graphql integration must declare spring-boot-starter-graphql'
// plain domain/service modules declare no Spring dependency directly (inherited via common-domain)
['domain-rest', 'domain-service-orders', 'service-orders'].each { m ->
    assert !text("${m}/pom.xml").contains('org.springframework') : "${m} must not declare a Spring dependency directly"
}

// ── package-info Javadoc conventions (#2 test wording, #6 infrastructure, #7 GraphQL) ──

assert text("common-domain/src/test/java/${p}/common/domain/package-info.java").contains('Tests for') \
    : 'test package-info Javadoc must begin "Tests for ..."'
assert text("common-testing/src/main/java/${p}/common/testing/package-info.java").contains('infrastructure') \
    : 'common-testing package-info must say infrastructure, not utilities'
assert !text('common-testing/pom.xml').contains('utilities') : 'common-testing pom must not say utilities'
assert text("domain-graphql/src/main/java/${p}/domain/graphql/package-info.java").contains('GraphQL presentation tier') \
    : 'graphql must be spelled GraphQL in Javadoc prose'

// ── pom descriptions are complete sentences ending with a period (#5) ────────

assert text('common-domain/pom.xml') =~ /<description>[^<]*\.<\/description>/ \
    : 'pom descriptions must be sentences ending with a period'

// ── dependency lists are split into TEST and PROD sections (TEST first) ──────

def depPom = text('domain-db-users/pom.xml')
assert depPom.contains('<!-- TEST -->') : 'dependency list must carry a TEST section header'
assert depPom.contains('<!-- PROD -->') : 'dependency list must carry a PROD section header'
assert depPom.indexOf('<!-- TEST -->') < depPom.indexOf('<!-- PROD -->')                          : 'TEST section must come before PROD'
assert depPom.indexOf('<!-- PROD -->') < depPom.indexOf('<artifactId>common-domain</artifactId>') : 'compile-scope deps must sit under PROD'

_buildLog.append("\n=== PASSED ===\n")
true
