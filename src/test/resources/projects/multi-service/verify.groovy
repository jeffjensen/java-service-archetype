// ── Test: multi-service ───────────────────────────────────────────────────────
// appName=Multi Service Test  integrations=database:main  serviceAreas=orders,inventory
// presentationTypes=rest
// Covers: named serviceAreas path, multiple named service modules,
//         no plain "service" module when names are provided

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
def aid = 'multi-service-test'
def appName = 'Multi Service Test'
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
    'domain-service-inventory',
    'domain-service-orders',
    'domain-db-main', 'domain-rest',
    'integration-db-main',
    'presentation-rest',
    'service-inventory',
    'service-orders',
]
modules.each { m -> check("${m}/pom.xml") }

// ── 3. Plain "service" module must NOT exist ──────────────────────────────────

assert !new File(basedir, 'service').exists() \
    : "With named serviceAreas, plain 'service' module must not be created"

// ── 4. parent/pom.xml completeness and sort order ────────────────────────────

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

// ── 5. Child modules reference parent/pom.xml ────────────────────────────────

modules.each { m ->
    assert text("${m}/pom.xml").contains('<relativePath>../parent/pom.xml</relativePath>') \
        : "${m}/pom.xml missing relativePath"
}

modules.each { m ->
    assert raw("${m}/pom.xml").contains("<artifactId>${aid}-${m}</artifactId>") \
        : "${m}/pom.xml must declare its own artifactId as '${aid}-${m}'"
}

// ── 6. Source skeletons — package-info.java only under src/main/java ────────

def p = 'com/example/multisvc'
def pkg = p.replace('/', '.')
[
    "common-domain/src/main/java/${p}/common/domain/package-info.java",
    "common-testing/src/main/java/${p}/common/testing/package-info.java",
    "domain-db-main/src/main/java/${p}/domain/db/main/package-info.java",
    "integration-db-main/src/main/java/${p}/integration/db/main/package-info.java",
    "domain-rest/src/main/java/${p}/domain/rest/package-info.java",
    "presentation-rest/src/main/java/${p}/presentation/rest/package-info.java",
    "service-orders/src/main/java/${p}/service/orders/package-info.java",
    "service-inventory/src/main/java/${p}/service/inventory/package-info.java",
    "app/src/main/java/${p}/app/package-info.java",
    "acceptance-tests/src/main/java/${p}/at/package-info.java",
].each { check(it) }

assert text("common-domain/src/main/java/${p}/common/domain/package-info.java").contains("package ${pkg}.common.domain;")       : 'common-domain package-info.java has wrong package declaration'
assert text("common-testing/src/main/java/${p}/common/testing/package-info.java").contains("package ${pkg}.common.testing;")    : 'common-testing package-info.java has wrong package declaration'
assert text("service-inventory/src/main/java/${p}/service/inventory/package-info.java").contains("package ${pkg}.service.inventory;") : 'service-inventory package-info.java has wrong package declaration'
assert text("service-orders/src/main/java/${p}/service/orders/package-info.java").contains("package ${pkg}.service.orders;")    : 'service-orders package-info.java has wrong package declaration'
assert text("app/src/main/java/${p}/app/package-info.java").contains("package ${pkg}.app;")                                     : 'app package-info.java has wrong package declaration'
assert text("acceptance-tests/src/main/java/${p}/at/package-info.java").contains("package ${pkg}.at;")                          : 'acceptance-tests package-info.java has wrong package declaration'
assert !new File(basedir, "acceptance-tests/src/test/java/${p}/at/package-info.java").exists() : 'acceptance-tests must not have a test-side package-info.java'

// ── 7. Service and domain module common-domain wiring ────────────────────────

assert text('service-orders/pom.xml').contains('<artifactId>common-domain</artifactId>')    : 'service-orders missing common-domain dep'
assert text('service-inventory/pom.xml').contains('<artifactId>common-domain</artifactId>') : 'service-inventory missing common-domain dep'
assert text('domain-db-main/pom.xml').contains('<artifactId>common-domain</artifactId>')    : 'domain-db-main missing common-domain dep'
assert text('domain-rest/pom.xml').contains('<artifactId>common-domain</artifactId>')       : 'domain-rest missing common-domain dep'
// domain-rest ("the service domain deps") depends on both named service areas' domain modules
['domain-service-orders', 'domain-service-inventory'].each { svcDom ->
    assert text('domain-rest/pom.xml').contains("<artifactId>${svcDom}</artifactId>") : "domain-rest must depend on ${svcDom}"
}

// appName-derived names/descriptions fold in the area name
assert raw('service-orders/pom.xml').contains("<name>${appName} Service Orders</name>")         : 'service-orders <name> must include the area name'
assert raw('service-orders/pom.xml').contains("<description>${appName}'s orders service tier (business logic).</description>") : 'service-orders <description> wrong'
assert raw('service-inventory/pom.xml').contains("<name>${appName} Service Inventory</name>")   : 'service-inventory <name> must include the area name'
assert raw('domain-service-orders/pom.xml').contains("<name>${appName} Domain Service Orders</name>") : 'domain-service-orders <name> must include the area name'

// ── 8. app depends on both service modules ────────────────────────────────────

def appPom = text('app/pom.xml')
['service-orders', 'service-inventory', 'common-domain',
 'domain-db-main', 'domain-rest', 'integration-db-main', 'presentation-rest'].each { dep ->
    assert appPom.contains("<artifactId>${dep}</artifactId>") : "app missing dep: $dep"
}
assert !appPom.contains('<artifactId>service</artifactId>')           : "app must not reference non-existent plain 'service'"
assert !appPom.contains('<artifactId>acceptance-tests</artifactId>') : 'app must not depend on acceptance-tests'
assert appPom.contains('<artifactId>spring-boot-starter-data-jpa</artifactId>') : 'app must depend on spring-boot-starter-data-jpa (database integration present)'

// ── 9. acceptance-tests deps ──────────────────────────────────────────────────

def atPom = text('acceptance-tests/pom.xml')
['app', 'common-domain', 'domain-db-main', 'domain-rest'].each { dep ->
    assert atPom.contains("<artifactId>${dep}</artifactId>") : "acceptance-tests missing dep: $dep"
}
assert !atPom.contains('<artifactId>service-orders</artifactId>')    : 'acceptance-tests must not depend on service-orders'
assert !atPom.contains('<artifactId>service-inventory</artifactId>') : 'acceptance-tests must not depend on service-inventory'
assert atPom.contains('<artifactId>common-testing</artifactId>')                       : 'acceptance-tests missing common-testing TEST dep'
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

// ── #8 service-area domain modules + wiring ──────────────────────────────────

['domain-service-orders', 'domain-service-inventory'].each { m ->
    check("${m}/pom.xml")
    assert text("${m}/pom.xml").contains('<artifactId>common-domain</artifactId>') : "${m} missing common-domain dep"
    assert !text("${m}/pom.xml").contains('<artifactId>common-testing</artifactId>') : "${m} must not depend on common-testing (no domain module does)"
    assert text("${m}/pom.xml").contains('<artifactId>spring-boot-starter-validation</artifactId>') : "${m} missing spring-boot-starter-validation dep"
}
assert text('service-orders/pom.xml').contains('<artifactId>domain-service-orders</artifactId>')       : 'service-orders must depend on domain-service-orders'
assert text('service-inventory/pom.xml').contains('<artifactId>domain-service-inventory</artifactId>') : 'service-inventory must depend on domain-service-inventory'
['service-orders', 'service-inventory'].each { m ->
    assert text("${m}/pom.xml").contains('<artifactId>common-testing</artifactId>') : "${m} missing common-testing TEST dep"
}
assert parentPom.contains('<artifactId>spring-boot-starter-parent</artifactId>') : 'parent must inherit spring-boot-starter-parent'

// common-testing depends on every domain module, including the named service-area domain modules.
// No domain module depends back on common-testing (only service/service-{name} does).
assert text('common-testing/pom.xml').contains('<artifactId>domain-db-main</artifactId>') : 'common-testing missing integration domain dep'
['domain-service-orders', 'domain-service-inventory', 'domain-rest'].each { m ->
    assert text('common-testing/pom.xml').contains("<artifactId>${m}</artifactId>") : "common-testing missing domain dep: ${m}"
}

_buildLog.append("\n=== PASSED ===\n")
true
