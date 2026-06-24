// ── Test: multi-service ───────────────────────────────────────────────────────
// integrations=database:main  serviceAreas=orders,inventory  presentationTypes=rest
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

def text = { String rel ->
    new File(basedir, rel).text
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

// ── 5. Child modules reference parent/pom.xml ────────────────────────────────

modules.each { m ->
    assert text("${m}/pom.xml").contains('<relativePath>../parent/pom.xml</relativePath>') \
        : "${m}/pom.xml missing relativePath"
}

modules.each { m ->
    assert text("${m}/pom.xml").contains("<artifactId>${m}</artifactId>") \
        : "${m}/pom.xml must declare its own artifactId as '${m}'"
}

// ── 6. Source skeletons ───────────────────────────────────────────────────────

def p = 'com/example/multisvc'
def pkg = p.replace('/', '.')
[
    "common-domain/src/main/java/${p}/common/domain/package-info.java",
    "common-domain/src/test/java/${p}/common/domain/package-info.java",
    "common-testing/src/main/java/${p}/common/testing/package-info.java",
    "domain-db-main/src/main/java/${p}/domain/db/main/package-info.java",
    "domain-db-main/src/test/java/${p}/domain/db/main/package-info.java",
    "integration-db-main/src/main/java/${p}/integration/db/main/package-info.java",
    "integration-db-main/src/test/java/${p}/integration/db/main/package-info.java",
    "domain-rest/src/main/java/${p}/domain/rest/package-info.java",
    "domain-rest/src/test/java/${p}/domain/rest/package-info.java",
    "presentation-rest/src/main/java/${p}/presentation/rest/package-info.java",
    "presentation-rest/src/test/java/${p}/presentation/rest/package-info.java",
    "service-orders/src/main/java/${p}/service/orders/package-info.java",
    "service-orders/src/test/java/${p}/service/orders/package-info.java",
    "service-inventory/src/main/java/${p}/service/inventory/package-info.java",
    "service-inventory/src/test/java/${p}/service/inventory/package-info.java",
    "app/src/main/java/${p}/app/package-info.java",
    "app/src/test/java/${p}/app/package-info.java",
    "acceptance-tests/src/test/java/${p}/at/package-info.java",
].each { check(it) }

assert text("common-domain/src/main/java/${p}/common/domain/package-info.java").contains("package ${pkg}.common.domain;")       : 'common-domain package-info.java has wrong package declaration'
assert text("common-testing/src/main/java/${p}/common/testing/package-info.java").contains("package ${pkg}.common.testing;")    : 'common-testing package-info.java has wrong package declaration'
assert text("service-inventory/src/main/java/${p}/service/inventory/package-info.java").contains("package ${pkg}.service.inventory;") : 'service-inventory package-info.java has wrong package declaration'
assert text("service-orders/src/main/java/${p}/service/orders/package-info.java").contains("package ${pkg}.service.orders;")    : 'service-orders package-info.java has wrong package declaration'
assert text("app/src/main/java/${p}/app/package-info.java").contains("package ${pkg}.app;")                                     : 'app package-info.java has wrong package declaration'
assert text("acceptance-tests/src/test/java/${p}/at/package-info.java").contains("package ${pkg}.at;")          : 'acceptance-tests package-info.java has wrong package declaration'
assert new File(basedir, "common-testing/src/test/java/${p}/common/testing/package-info.java").exists()                        : 'common-testing must have test Java package-info.java'
assert new File(basedir, "acceptance-tests/src/main/java/${p}/at/package-info.java").exists()                          : 'acceptance-tests must have main Java package-info.java'

// ── 7. Service and domain module common-domain wiring ────────────────────────

assert text('service-orders/pom.xml').contains('<artifactId>common-domain</artifactId>')    : 'service-orders missing common-domain dep'
assert text('service-inventory/pom.xml').contains('<artifactId>common-domain</artifactId>') : 'service-inventory missing common-domain dep'
assert text('domain-db-main/pom.xml').contains('<artifactId>common-domain</artifactId>')    : 'domain-db-main missing common-domain dep'
assert text('domain-rest/pom.xml').contains('<artifactId>common-domain</artifactId>')       : 'domain-rest missing common-domain dep'

// ── 8. app depends on both service modules ────────────────────────────────────

def appPom = text('app/pom.xml')
['service-orders', 'service-inventory', 'common-domain',
 'domain-db-main', 'domain-rest', 'integration-db-main', 'presentation-rest'].each { dep ->
    assert appPom.contains("<artifactId>${dep}</artifactId>") : "app missing dep: $dep"
}
assert !appPom.contains('<artifactId>service</artifactId>')           : "app must not reference non-existent plain 'service'"
assert !appPom.contains('<artifactId>acceptance-tests</artifactId>') : 'app must not depend on acceptance-tests'

// ── 9. acceptance-tests deps ──────────────────────────────────────────────────

def atPom = text('acceptance-tests/pom.xml')
['app', 'common-domain', 'domain-db-main', 'domain-rest'].each { dep ->
    assert atPom.contains("<artifactId>${dep}</artifactId>") : "acceptance-tests missing dep: $dep"
}
assert !atPom.contains('<artifactId>service-orders</artifactId>')    : 'acceptance-tests must not depend on service-orders'
assert !atPom.contains('<artifactId>service-inventory</artifactId>') : 'acceptance-tests must not depend on service-inventory'

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
}
assert text('service-orders/pom.xml').contains('<artifactId>domain-service-orders</artifactId>')       : 'service-orders must depend on domain-service-orders'
assert text('service-inventory/pom.xml').contains('<artifactId>domain-service-inventory</artifactId>') : 'service-inventory must depend on domain-service-inventory'
assert parentPom.contains('<artifactId>spring-boot-starter-parent</artifactId>') : 'parent must inherit spring-boot-starter-parent'

_buildLog.append("\n=== PASSED ===\n")
true
