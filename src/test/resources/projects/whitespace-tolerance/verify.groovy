// ── Test: whitespace-tolerance ────────────────────────────────────────────────
// integrations="database : main , rest : orders"  singleService=y  presentationTypes=rest
// Covers: spaces around ':' and ',' are trimmed; generated module names are clean;
//         no modules with embedded spaces or double-hyphen artifacts

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
    'domain-db-main',
    'domain-rest',
    'domain-rest-orders',
    'integration-db-main',
    'integration-rest-orders',
    'presentation-rest',
    'service',
]
modules.each { m -> check("${m}/pom.xml") }

// ── 3. No modules with embedded spaces or malformed names ─────────────────────

assert !new File(basedir, 'domain- database-main').exists()   : "Spaces must be trimmed from type segment"
assert !new File(basedir, 'domain-database-main').exists()    : "'database' must be abbreviated to 'db'"
assert !new File(basedir, 'domain-rest- orders').exists()     : "Spaces must be trimmed from name segment"
assert !new File(basedir, 'domain- rest-orders').exists()     : "Spaces must be trimmed from type segment"

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

def p = 'com/example/wstest'
def pkg = p.replace('/', '.')
[
    "common-domain/src/main/java/${p}/common/domain/package-info.java",
    "common-domain/src/test/java/${p}/common/domain/package-info.java",
    "common-testing/src/main/java/${p}/common/testing/package-info.java",
    "domain-db-main/src/main/java/${p}/domain/db/main/package-info.java",
    "domain-db-main/src/test/java/${p}/domain/db/main/package-info.java",
    "integration-db-main/src/main/java/${p}/integration/db/main/package-info.java",
    "integration-db-main/src/test/java/${p}/integration/db/main/package-info.java",
    "domain-rest-orders/src/main/java/${p}/domain/rest/orders/package-info.java",
    "domain-rest-orders/src/test/java/${p}/domain/rest/orders/package-info.java",
    "integration-rest-orders/src/main/java/${p}/integration/rest/orders/package-info.java",
    "integration-rest-orders/src/test/java/${p}/integration/rest/orders/package-info.java",
    "domain-rest/src/main/java/${p}/domain/rest/package-info.java",
    "domain-rest/src/test/java/${p}/domain/rest/package-info.java",
    "presentation-rest/src/main/java/${p}/presentation/rest/package-info.java",
    "presentation-rest/src/test/java/${p}/presentation/rest/package-info.java",
    "service/src/main/java/${p}/service/package-info.java",
    "service/src/test/java/${p}/service/package-info.java",
    "app/src/main/java/${p}/app/package-info.java",
    "app/src/test/java/${p}/app/package-info.java",
    "acceptance-tests/src/test/java/${p}/at/package-info.java",
].each { check(it) }

assert text("common-domain/src/main/java/${p}/common/domain/package-info.java").contains("package ${pkg}.common.domain;")    : 'common-domain package-info.java has wrong package declaration'
assert text("common-testing/src/main/java/${p}/common/testing/package-info.java").contains("package ${pkg}.common.testing;") : 'common-testing package-info.java has wrong package declaration'
assert text("domain-rest-orders/src/main/java/${p}/domain/rest/orders/package-info.java").contains("package ${pkg}.domain.rest.orders;") : 'domain-rest-orders package-info.java has wrong package declaration'
assert text("service/src/main/java/${p}/service/package-info.java").contains("package ${pkg}.service;")                      : 'service package-info.java has wrong package declaration'
assert text("app/src/main/java/${p}/app/package-info.java").contains("package ${pkg}.app;")                                  : 'app package-info.java has wrong package declaration'
assert text("acceptance-tests/src/test/java/${p}/at/package-info.java").contains("package ${pkg}.at;")       : 'acceptance-tests package-info.java has wrong package declaration'
assert !new File(basedir, "common-testing/src/test/java/${p}/common/testing/package-info.java").exists()                     : 'common-testing must not have test Java package-info.java'
assert !new File(basedir, "acceptance-tests/src/main/java/${p}/at/package-info.java").exists()                       : 'acceptance-tests must not have main Java package-info.java'

// ── 7. Dependency wiring ─────────────────────────────────────────────────────

assert text('domain-db-main/pom.xml').contains('<artifactId>common-domain</artifactId>')          : 'domain-db-main missing common-domain dep'
assert text('domain-rest-orders/pom.xml').contains('<artifactId>common-domain</artifactId>')      : 'domain-rest-orders missing common-domain dep'
assert text('integration-db-main/pom.xml').contains('<artifactId>domain-db-main</artifactId>')    : 'integration-db-main missing domain dep'
assert text('integration-rest-orders/pom.xml').contains('<artifactId>domain-rest-orders</artifactId>') : 'integration-rest-orders missing domain dep'
assert text('domain-rest/pom.xml').contains('<artifactId>common-domain</artifactId>')             : 'domain-rest missing common-domain dep'
assert text('presentation-rest/pom.xml').contains('<artifactId>domain-rest</artifactId>')         : 'presentation-rest missing domain-rest dep'
assert text('service/pom.xml').contains('<artifactId>common-domain</artifactId>')                 : 'service missing common-domain dep'

def appPom = text('app/pom.xml')
['common-domain', 'domain-db-main', 'domain-rest', 'domain-rest-orders',
 'integration-db-main', 'integration-rest-orders', 'presentation-rest', 'service'].each { dep ->
    assert appPom.contains("<artifactId>${dep}</artifactId>") : "app missing dep: $dep"
}

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

_buildLog.append("\n=== PASSED ===\n")
true
