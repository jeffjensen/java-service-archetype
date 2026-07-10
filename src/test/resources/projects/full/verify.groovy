// ── Test: full ────────────────────────────────────────────────────────────────
// appName=Full Test  integrations=database:users,graphql:catalog
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

// Instruction 2: each module's artifactId is "<artifactId property>-<dir name>". The wiring
// assertions below use bare module names, so text() strips the "<aid>-" prefix from <artifactId>
// tags; the prefix is verified explicitly via raw() (module's own artifactId + parent DM), and the
// generated project's real Maven build — run before this script — fails on any inconsistent reference.
def aid = 'full-test'
def appName = 'Full Test'
def raw = { String rel ->
    new File(basedir, rel).text
}
def text = { String rel ->
    raw(rel).replace("<artifactId>${aid}-", '<artifactId>')
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

def parentPom = raw('parent/pom.xml')
modules.each { m ->
    assert parentPom.contains("<module>../${m}</module>")             : "parent <modules> missing: ../$m"
    assert parentPom.contains("<artifactId>${aid}-${m}</artifactId>") : "parent <dependencyManagement> missing: $m"
}
assert parentPom.contains('<packaging>pom</packaging>') : "parent must have pom packaging"

def moduleOrder = modules.sort().collect { "<module>../${it}</module>" }
def moduleBlock = parentPom.replaceAll(/(?s).*<modules>(.*?)<\/modules>.*/, '$1')
assert moduleBlock.findAll(/<module>[^<]+<\/module>/) == moduleOrder : "parent <modules> not sorted"

def dmBlock = (parentPom =~ /(?s)<dependencyManagement>.*?<\/dependencyManagement>/)[0]
def dmIds = dmBlock.findAll(/<artifactId>[^<]+<\/artifactId>/).collect { it.replaceAll(/<\/?artifactId>/, '') }
def dmModuleIds = dmIds.findAll { it.startsWith("${aid}-") }.collect { it.substring(aid.length() + 1) }
assert dmModuleIds.sort() == modules.sort() : "parent <dependencyManagement> must manage exactly the sibling modules"
assert dmModuleIds.size() == modules.size() : "parent <dependencyManagement> wrong entry count: expected ${modules.size()}, got ${dmModuleIds.size()}"

// ── 6. Child modules reference parent/pom.xml ────────────────────────────────

modules.each { m ->
    assert text("${m}/pom.xml").contains('<relativePath>../parent/pom.xml</relativePath>') \
        : "${m}/pom.xml missing relativePath"
}

modules.each { m ->
    assert raw("${m}/pom.xml").contains("<artifactId>${aid}-${m}</artifactId>") \
        : "${m}/pom.xml must declare its own artifactId as '${aid}-${m}'"
}

// ── 7. Source skeletons — package-info.java only under src/main/java ─────────
// Java rejects two package-info.java files for the same package, so src/test/java never gets one
// (see basic/verify.groovy for the exhaustive main-only/no-test-side assertions).

def p = 'com/example/full'
def pkg = p.replace('/', '.')
[
    "common-domain/src/main/java/${p}/common/domain/package-info.java",
    "common-testing/src/main/java/${p}/common/testing/package-info.java",
    "domain-db-users/src/main/java/${p}/domain/db/users/package-info.java",
    "integration-db-users/src/main/java/${p}/integration/db/users/package-info.java",
    "domain-graphql-catalog/src/main/java/${p}/domain/graphql/catalog/package-info.java",
    "integration-graphql-catalog/src/main/java/${p}/integration/graphql/catalog/package-info.java",
    "domain-graphql/src/main/java/${p}/domain/graphql/package-info.java",
    "presentation-graphql/src/main/java/${p}/presentation/graphql/package-info.java",
    "domain-rest/src/main/java/${p}/domain/rest/package-info.java",
    "presentation-rest/src/main/java/${p}/presentation/rest/package-info.java",
    "service-orders/src/main/java/${p}/service/orders/package-info.java",
    "service-notifications/src/main/java/${p}/service/notifications/package-info.java",
    "app/src/main/java/${p}/app/package-info.java",
    "acceptance-tests/src/main/java/${p}/at/package-info.java",
].each { check(it) }

assert text("common-domain/src/main/java/${p}/common/domain/package-info.java").contains("package ${pkg}.common.domain;")       : 'common-domain package-info.java has wrong package declaration'
assert text("common-testing/src/main/java/${p}/common/testing/package-info.java").contains("package ${pkg}.common.testing;")    : 'common-testing package-info.java has wrong package declaration'
assert text("service-orders/src/main/java/${p}/service/orders/package-info.java").contains("package ${pkg}.service.orders;")    : 'service-orders package-info.java has wrong package declaration'
assert text("service-notifications/src/main/java/${p}/service/notifications/package-info.java").contains("package ${pkg}.service.notifications;") : 'service-notifications package-info.java has wrong package declaration'
assert text("app/src/main/java/${p}/app/package-info.java").contains("package ${pkg}.app;")                                     : 'app package-info.java has wrong package declaration'
assert text("acceptance-tests/src/main/java/${p}/at/package-info.java").contains("package ${pkg}.at;")                          : 'acceptance-tests package-info.java has wrong package declaration'

// No module gets a src/test/java/**/package-info.java — only src/main/java does (spot-checked
// exhaustively across every module in basic/verify.groovy; here just the two AT-relevant dirs).
assert !new File(basedir, "acceptance-tests/src/test/java/${p}/at/package-info.java").exists() : 'acceptance-tests must not have a test-side package-info.java'
assert !new File(basedir, "common-domain/src/test/java/${p}/common/domain/package-info.java").exists() : 'common-domain must not have a test-side package-info.java'

// ── #1 acceptance-tests package-info wording ─────────────────────────────────
// Only src/main/java carries a package-info.java for acceptance-tests (its test package holds the
// *AT classes themselves); that lone package-info describes the functional tests, not "supporting
// infrastructure for" them.
def atMainPkgInfo = text("acceptance-tests/src/main/java/${p}/at/package-info.java")
assert atMainPkgInfo.contains('Functional acceptance tests for the application.') : '#1: acceptance-tests package-info must describe the functional tests'

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

// presentation-tier domain modules ("the service domain deps") depend on every domain-service* module
['domain-graphql', 'domain-rest'].each { m ->
    ['domain-service-orders', 'domain-service-notifications'].each { svcDom ->
        assert text("${m}/pom.xml").contains("<artifactId>${svcDom}</artifactId>") : "${m} must depend on ${svcDom} (the service domain deps)"
    }
}

// common-testing depends on every domain module. No domain module depends back on
// common-testing (only service/service-{name} does), so this stays acyclic.
def commonTestingPom = text('common-testing/pom.xml')
['domain-db-users', 'domain-graphql-catalog', 'domain-service-orders', 'domain-service-notifications',
 'domain-graphql', 'domain-rest'].each { m ->
    assert commonTestingPom.contains("<artifactId>${m}</artifactId>") : "common-testing missing domain dep: ${m}"
}
['domain-service-orders', 'domain-service-notifications', 'domain-graphql', 'domain-rest',
 'domain-db-users', 'domain-graphql-catalog'].each { m ->
    assert !text("${m}/pom.xml").contains('<artifactId>common-testing</artifactId>') : "${m} must not depend on common-testing (no domain module does)"
}

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
assert atPom.contains('<artifactId>common-testing</artifactId>')              : 'acceptance-tests missing common-testing dep'
assert atPom.contains('<artifactId>spring-graphql-test</artifactId>')         : 'acceptance-tests missing spring-graphql-test dep (graphql presentation present)'
assert atPom.contains('<artifactId>datasource-proxy-spring-boot-starter</artifactId>') : 'acceptance-tests missing datasource-proxy dep'

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
    assert !text("${m}/pom.xml").contains('<artifactId>common-testing</artifactId>') : "${m} must not depend on common-testing (no domain module does)"
    assert text("${m}/pom.xml").contains('<artifactId>spring-boot-starter-validation</artifactId>') : "${m} missing spring-boot-starter-validation dep"
    check("${m}/src/main/java/${p}/domain/service/${area}/package-info.java")
    assert !new File(basedir, "${m}/src/test/java/${p}/domain/service/${area}/package-info.java").exists() : "${m} must not have a test-side package-info.java"
    assert atPom.contains("<artifactId>${m}</artifactId>") : "acceptance-tests must depend on ${m}"
}
assert text('service-orders/pom.xml').contains('<artifactId>domain-service-orders</artifactId>')               : 'service-orders must depend on domain-service-orders'
assert text('service-notifications/pom.xml').contains('<artifactId>domain-service-notifications</artifactId>') : 'service-notifications must depend on domain-service-notifications'
['service-orders', 'service-notifications'].each { m ->
    assert text("${m}/pom.xml").contains('<artifactId>common-testing</artifactId>') : "${m} missing common-testing TEST dep"
}

// ── Spring Boot wiring (#4) ──────────────────────────────────────────────────

def appConfig = "app/src/main/java/${p}/app/config"
check("${appConfig}/Application.java")
check("${appConfig}/AppServletInitializer.java")
def appMain = text("${appConfig}/Application.java")
assert appMain.contains('@SpringBootApplication')       : 'Application must be @SpringBootApplication'
assert appMain.contains('@EnableTransactionManagement') : 'Application must enable transaction management'
assert appMain.contains('@Import({')                    : 'Application must @Import module configs'
assert appMain.contains('import com.example.full.domain.service.orders.config.DomainServiceOrdersConfiguration;') \
    : 'Application must import each module config class'
assert appMain.contains('    DomainServiceOrdersConfiguration.class,') \
    : 'Application @Import block must reference config classes by simple name (shorter lines)'
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
assert scanIface.contains('Anchor class for basePackageClasses component scanning.') : 'marker must carry the anchor Javadoc'
def cdConfig = text("common-domain/src/main/java/${p}/common/domain/config/CommonDomainConfiguration.java")
assert cdConfig.contains('@ComponentScan(basePackageClasses = CommonDomainComponentScan.class)') \
    : 'config class must @ComponentScan its module marker interface'
assert cdConfig.contains("import ${p.replace('/', '.')}.common.domain.CommonDomainComponentScan;") \
    : 'config class must import its module marker interface'

// parent imports the Spring Boot BOM; app pulls the starters
assert parentPom.contains('<artifactId>spring-boot-starter-parent</artifactId>') : 'parent must inherit spring-boot-starter-parent'
assert parentPom.contains('<version>4.1.0</version>')                            : 'parent must pin Spring Boot 4.1.0'
assert appPom.contains('<artifactId>spring-boot-starter</artifactId>')           : 'app must depend on the core spring-boot-starter'
assert appPom.contains('<artifactId>spring-boot-starter-webmvc</artifactId>')    : 'app must depend on spring-boot-starter-webmvc'
assert appPom.contains('<artifactId>spring-boot-starter-actuator</artifactId>')  : 'app must depend on spring-boot-starter-actuator'
assert appPom.contains('<artifactId>spring-boot-admin-starter-client</artifactId>') : 'app must depend on spring-boot-admin-starter-client'
assert appPom.contains('<artifactId>spring-boot-starter-data-jpa</artifactId>')   : 'app must depend on spring-boot-starter-data-jpa'

// ── #4–#7 parent-managed and inherited Spring dependencies ───────────────────
// #4 dependencyManagement manages spring-core, excluding commons-logging
def dmSection = (parentPom =~ /(?s)<dependencyManagement>.*?<\/dependencyManagement>/)[0]
assert dmSection.contains('<artifactId>spring-core</artifactId>') : '#4: dependencyManagement must manage spring-core'
assert dmSection =~ /(?s)<artifactId>spring-core<\/artifactId>\s*<exclusions>\s*<exclusion>\s*<groupId>commons-logging<\/groupId>/ \
    : '#4: spring-core must exclude commons-logging'
// datasource-proxy-spring-boot-starter and spring-boot-admin-starter-client are third-party (not in
// the Spring Boot BOM), so each needs its own managed version, pinned via a property
assert dmSection.contains('<artifactId>datasource-proxy-spring-boot-starter</artifactId>')  : 'dependencyManagement must manage datasource-proxy-spring-boot-starter'
assert dmSection.contains('<artifactId>spring-boot-admin-starter-client</artifactId>')      : 'dependencyManagement must manage spring-boot-admin-starter-client'
assert parentPom.contains('<datasource-proxy-spring-boot-starter.version>')  : 'parent must declare a datasource-proxy-spring-boot-starter.version property'
assert parentPom.contains('<spring-boot-admin-starter-client.version>')      : 'parent must declare a spring-boot-admin-starter-client.version property'
// #5 parent <dependencies> adds spring-boot-starter-test (test scope) for every module, no commons-logging
assert parentPom.contains('<artifactId>spring-boot-starter-test</artifactId>') : '#5: parent must add spring-boot-starter-test for all modules'
assert parentPom =~ /(?s)<artifactId>spring-boot-starter-test<\/artifactId>\s*<scope>test<\/scope>/ : '#5: spring-boot-starter-test must be test-scoped'
// #6 parent <dependencies> adds spring-boot-starter-logging + jspecify (compile scope) for every module
assert parentPom.contains('<artifactId>spring-boot-starter-logging</artifactId>') : '#6: parent must add spring-boot-starter-logging for all modules'
assert parentPom.contains('<artifactId>jspecify</artifactId>')                    : '#6: parent must add jspecify for all modules'
// #7 common-testing declares spring-boot-starter-test at compile scope
assert raw('common-testing/pom.xml') =~ /(?s)<artifactId>spring-boot-starter-test<\/artifactId>\s*<scope>compile<\/scope>/ \
    : '#7: common-testing must declare spring-boot-starter-test at compile scope'
assert raw('common-testing/pom.xml') =~ /(?s)<artifactId>spring-boot-starter-graphql-test<\/artifactId>\s*<scope>compile<\/scope>/ \
    : 'common-testing must declare spring-boot-starter-graphql-test at compile scope (graphql presentation present)'

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
// plain service (business-logic) modules declare no Spring dependency directly (inherited via
// common-domain); domain-rest/domain-service-orders now DO (spring-boot-starter-validation), so
// they are intentionally excluded from this list.
['service-orders', 'service-notifications'].each { m ->
    assert !text("${m}/pom.xml").contains('org.springframework') : "${m} must not declare a Spring dependency directly"
}

// ── package-info Javadoc conventions (#6 infrastructure, #7 GraphQL) ─────────

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

// ── This change set: #1 presentation→services, #2 service→integration, #4 / #5 / #7 ──

// #1: every presentation module depends on all service modules
['presentation-rest', 'presentation-graphql'].each { pres ->
    ['service-orders', 'service-notifications'].each { svc ->
        assert text("${pres}/pom.xml").contains("<artifactId>${svc}</artifactId>") : "#1: ${pres} must depend on ${svc}"
    }
}

// #2: these named service areas share no name with any integration, so they take no integration dep
['service-orders', 'service-notifications'].each { svc ->
    assert !text("${svc}/pom.xml").contains('<artifactId>integration-') : "#2: ${svc} must not take a non-matching integration"
}

// #4: domain-service-* describe the service tier (with the area name), not "the application"
assert text("domain-service-orders/src/main/java/${p}/domain/service/orders/package-info.java").contains('Domain classes for the orders service tier.') : '#4: domain-service-orders package-info'

// #5: acceptance-tests and common-testing are managed in dependencyManagement at test scope
['acceptance-tests', 'common-testing'].each { m ->
    assert dmBlock =~ /(?s)<artifactId>${aid}-${m}<\/artifactId>\s*<version>[^<]+<\/version>\s*<scope>test<\/scope>/ : "#5: ${m} must be managed at <scope>test</scope>"
}
// acceptance-tests must never appear as a <dependency> of any other module
modules.findAll { it != 'acceptance-tests' }.each { m ->
    assert !text("${m}/pom.xml").contains('<artifactId>acceptance-tests</artifactId>') : "${m} must not depend on acceptance-tests"
}

// #7: GraphQL presentation uses resolver wording, not request-handling
assert text("presentation-graphql/src/main/java/${p}/presentation/graphql/package-info.java").contains('Resolver classes for the GraphQL presentation tier.') : '#7: presentation-graphql main package-info'
assert !text('presentation-graphql/pom.xml').contains('Request-handling') : '#7: presentation-graphql must not use request-handling wording'

// ── appName-derived pom name/description ─────────────────────────────────────

assert parentPom.contains("<name>${appName} Parent</name>")                            : 'parent <name> must be "<appName> Parent"'
assert parentPom.contains("<description>${appName}'s parent POM.</description>")        : "parent <description> must be \"<appName>'s parent POM.\""
assert text('app/pom.xml') != null && raw('app/pom.xml').contains("<name>${appName} App</name>") : 'app <name> must be "<appName> App"'
assert raw('app/pom.xml').contains("<description>${appName}'s application configuration including building its deployment assembly.</description>") \
    : 'app <description> must use the appName-derived sentence'
assert raw('acceptance-tests/pom.xml').contains("<name>${appName} Acceptance Tests</name>")               : 'acceptance-tests <name> wrong'
assert raw('acceptance-tests/pom.xml').contains("<description>${appName}'s acceptance tests.</description>") : 'acceptance-tests <description> wrong'
assert raw('common-testing/pom.xml').contains("<name>${appName} Common Testing</name>")                    : 'common-testing <name> wrong'
assert raw('common-testing/pom.xml').contains("<description>${appName}'s common testing.</description>")   : 'common-testing <description> wrong'
assert raw('domain-graphql/pom.xml').contains("<name>${appName} Domain GraphQL</name>")                     : 'domain-graphql <name> wrong'
assert raw('domain-graphql/pom.xml').contains("<description>${appName}'s GraphQL presentation tier domain classes.</description>") : 'domain-graphql <description> wrong'
assert raw('domain-rest/pom.xml').contains("<name>${appName} Domain REST</name>")                           : 'domain-rest <name> wrong'
assert raw('domain-rest/pom.xml').contains("<description>${appName}'s REST presentation tier domain classes.</description>")       : 'domain-rest <description> wrong'
assert raw('presentation-graphql/pom.xml').contains("<name>${appName} Presentation GraphQL</name>")         : 'presentation-graphql <name> wrong'
assert raw('presentation-graphql/pom.xml').contains("<description>${appName}'s GraphQL presentation tier.</description>")          : 'presentation-graphql <description> wrong'
assert raw('domain-service-orders/pom.xml').contains("<name>${appName} Domain Service Orders</name>")       : 'domain-service-orders <name> wrong'
assert raw('domain-service-orders/pom.xml').contains("<description>${appName}'s orders service tier domain classes.</description>") : 'domain-service-orders <description> wrong'
assert raw('service-orders/pom.xml').contains("<name>${appName} Service Orders</name>")                     : 'service-orders <name> wrong'
assert raw('service-orders/pom.xml').contains("<description>${appName}'s orders service tier (business logic).</description>")     : 'service-orders <description> wrong'
// presentation-rest / common-domain / per-integration modules keep their prior (non-appName) description
assert !raw('presentation-rest/pom.xml').contains('<name>')          : 'presentation-rest has no dedicated appName <name> requirement'
assert !raw('common-domain/pom.xml').contains('<name>')              : 'common-domain has no dedicated appName <name> requirement'

// spring.application.name uses the appName "property" form: spaces removed, lowercased
def appProps = text('app/src/main/resources/application.properties')
assert appProps.contains('spring.application.name=fulltest') : 'spring.application.name must be appName with spaces removed and lowercased'

// ── acceptance-test infrastructure: AT base classes, AT config, logback ──────

def atConfigClass = 'FullTestAcceptanceTestConfiguration'
check("acceptance-tests/src/test/java/${p}/at/config/${atConfigClass}.java")
def atConfig = text("acceptance-tests/src/test/java/${p}/at/config/${atConfigClass}.java")
assert atConfig.contains('@Configuration')                                   : 'AT configuration class must be @Configuration'
assert atConfig.contains("Configuration class for ${appName} ATs.")          : 'AT configuration class Javadoc must name the app'

check('acceptance-tests/src/test/resources/logback-spring.xml')
def logbackSpring = text('acceptance-tests/src/test/resources/logback-spring.xml')
assert logbackSpring.contains('<include resource="org/springframework/boot/logging/logback/base.xml" />') \
    : 'logback-spring.xml must include the Boot base logback config'

check("acceptance-tests/src/test/java/${p}/at/AppRestAcceptanceTestBase.java")
def appRestAt = text("acceptance-tests/src/test/java/${p}/at/AppRestAcceptanceTestBase.java")
assert appRestAt.contains("Base test class for all ${appName} REST acceptance tests.") : 'AppRestAcceptanceTestBase Javadoc must name the app'
assert appRestAt.contains('extends RestAcceptanceTestBase')                            : 'AppRestAcceptanceTestBase must extend RestAcceptanceTestBase'
assert appRestAt.contains("@ContextConfiguration(classes = { ${atConfigClass}.class,")  : 'AppRestAcceptanceTestBase must @ContextConfiguration with the AT config class first'
assert appRestAt.contains('DomainServiceOrdersConfiguration.class')                    : 'AppRestAcceptanceTestBase @ContextConfiguration must include every generated module config (reachable transitively via app)'
assert appRestAt.contains('@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class, XADataSourceAutoConfiguration.class })') \
    : 'AppRestAcceptanceTestBase must exclude DataSourceAutoConfiguration/XADataSourceAutoConfiguration (database integration present)'

check("acceptance-tests/src/test/java/${p}/at/AppGraphQlAcceptanceTestBase.java")
def appGraphQlAt = text("acceptance-tests/src/test/java/${p}/at/AppGraphQlAcceptanceTestBase.java")
assert appGraphQlAt.contains("Base test class for all ${appName} GraphQL acceptance tests.") : 'AppGraphQlAcceptanceTestBase Javadoc must name the app'
assert appGraphQlAt.contains('extends GraphQlAcceptanceTestBase')                            : 'AppGraphQlAcceptanceTestBase must extend GraphQlAcceptanceTestBase'

// RestAcceptanceTestBase / GraphQlAcceptanceTestBase: shared base classes in common-testing
check("common-testing/src/main/java/${p}/common/testing/RestAcceptanceTestBase.java")
def restAtBase = text("common-testing/src/main/java/${p}/common/testing/RestAcceptanceTestBase.java")
assert restAtBase.contains('@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)') : 'RestAcceptanceTestBase must be a random-port @SpringBootTest'

check("common-testing/src/main/java/${p}/common/testing/GraphQlAcceptanceTestBase.java")
def graphQlAtBase = text("common-testing/src/main/java/${p}/common/testing/GraphQlAcceptanceTestBase.java")
assert graphQlAtBase.contains('@AutoConfigureHttpGraphQlTester') : 'GraphQlAcceptanceTestBase must @AutoConfigureHttpGraphQlTester'

// ── app: application.properties + per-environment profile files ─────────────

['ci', 'dev', 'local', 'prod', 'qa', 'uat'].each { profile ->
    def f = new File(basedir, "app/src/main/resources/application-${profile}.properties")
    assert f.exists()     : "Missing profile properties file: application-${profile}.properties"
    assert f.text.isEmpty() : "application-${profile}.properties must be empty"
}
assert appProps.contains('decorator.datasource.enabled=true')       : 'application.properties must configure SQL logging'
assert appProps.contains('management.endpoints.web.exposure.include=*') : 'application.properties must expose all actuator endpoints'

_buildLog.append("\n=== PASSED ===\n")
true
