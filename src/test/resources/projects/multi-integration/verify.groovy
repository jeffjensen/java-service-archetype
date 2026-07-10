// ── Test: multi-integration ───────────────────────────────────────────────────
// appName=Multi Integration Test  integrations=database:users,rest:orders,jms:events
// serviceAreas=,  presentationTypes=rest
// Covers: multiple integration types, db abbreviation, non-db types as-is,
//         integration domain-rest-orders vs presentation domain-rest (no collision)

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
def aid = 'multi-integration-test'
def appName = 'Multi Integration Test'
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
    'domain-service',
    'domain-db-users',
    'domain-jms-events',
    'domain-rest',
    'domain-rest-orders',
    'integration-db-users',
    'integration-jms-events',
    'integration-rest-orders',
    'presentation-rest',
    'service'
]
modules.each { m -> check("${m}/pom.xml") }

// ── 3. Type abbreviations ─────────────────────────────────────────────────────

assert !new File(basedir, 'domain-database-users').exists()    : "'database' must be abbreviated to 'db'"
assert !new File(basedir, 'integration-database-users').exists(): "'database' must be abbreviated to 'db'"

// ── 4. domain-rest (presentation) and domain-rest-orders (integration) are distinct ──

assert text('domain-rest/pom.xml').contains('<artifactId>domain-rest</artifactId>')               : 'domain-rest artifactId wrong'
assert text('domain-rest-orders/pom.xml').contains('<artifactId>domain-rest-orders</artifactId>') : 'domain-rest-orders artifactId wrong'

// ── 5. parent/pom.xml completeness and sort order ────────────────────────────

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

// ── 6. Child modules reference parent/pom.xml ────────────────────────────────

modules.each { m ->
    assert text("${m}/pom.xml").contains('<relativePath>../parent/pom.xml</relativePath>') \
        : "${m}/pom.xml missing relativePath"
}

modules.each { m ->
    assert raw("${m}/pom.xml").contains("<artifactId>${aid}-${m}</artifactId>") \
        : "${m}/pom.xml must declare its own artifactId as '${aid}-${m}'"
}

// ── 7. Source skeletons for each integration type — package-info.java only under src/main/java ──

def p = 'com/example/multiint'
def pkg = p.replace('/', '.')
[
    "common-domain/src/main/java/${p}/common/domain/package-info.java",
    "common-testing/src/main/java/${p}/common/testing/package-info.java",
    "domain-db-users/src/main/java/${p}/domain/db/users/package-info.java",
    "integration-db-users/src/main/java/${p}/integration/db/users/package-info.java",
    "domain-rest-orders/src/main/java/${p}/domain/rest/orders/package-info.java",
    "integration-rest-orders/src/main/java/${p}/integration/rest/orders/package-info.java",
    "domain-jms-events/src/main/java/${p}/domain/jms/events/package-info.java",
    "integration-jms-events/src/main/java/${p}/integration/jms/events/package-info.java",
    "domain-rest/src/main/java/${p}/domain/rest/package-info.java",
    "presentation-rest/src/main/java/${p}/presentation/rest/package-info.java",
    "service/src/main/java/${p}/service/package-info.java",
    "app/src/main/java/${p}/app/package-info.java",
    "acceptance-tests/src/main/java/${p}/at/package-info.java",
].each { check(it) }

assert text("common-domain/src/main/java/${p}/common/domain/package-info.java").contains("package ${pkg}.common.domain;")    : 'common-domain package-info.java has wrong package declaration'
assert text("common-testing/src/main/java/${p}/common/testing/package-info.java").contains("package ${pkg}.common.testing;") : 'common-testing package-info.java has wrong package declaration'
assert text("domain-db-users/src/main/java/${p}/domain/db/users/package-info.java").contains("package ${pkg}.domain.db.users;") : 'domain-db-users package-info.java has wrong package declaration'
assert text("service/src/main/java/${p}/service/package-info.java").contains("package ${pkg}.service;")                      : 'service package-info.java has wrong package declaration'
assert text("app/src/main/java/${p}/app/package-info.java").contains("package ${pkg}.app;")                                  : 'app package-info.java has wrong package declaration'
assert text("acceptance-tests/src/main/java/${p}/at/package-info.java").contains("package ${pkg}.at;")                       : 'acceptance-tests package-info.java has wrong package declaration'
assert !new File(basedir, "acceptance-tests/src/test/java/${p}/at/package-info.java").exists() : 'acceptance-tests must not have a test-side package-info.java'

// ── 8. Integration module dependencies ───────────────────────────────────────

assert text('integration-db-users/pom.xml').contains('<artifactId>domain-db-users</artifactId>')       : 'integration-db-users missing domain dep'
assert text('integration-rest-orders/pom.xml').contains('<artifactId>domain-rest-orders</artifactId>') : 'integration-rest-orders missing domain dep'
assert text('integration-jms-events/pom.xml').contains('<artifactId>domain-jms-events</artifactId>')   : 'integration-jms-events missing domain dep'

assert text('domain-db-users/pom.xml').contains('<artifactId>common-domain</artifactId>')    : 'domain-db-users missing common-domain dep'
assert text('domain-rest-orders/pom.xml').contains('<artifactId>common-domain</artifactId>') : 'domain-rest-orders missing common-domain dep'
assert text('domain-jms-events/pom.xml').contains('<artifactId>common-domain</artifactId>')  : 'domain-jms-events missing common-domain dep'
assert text('domain-rest/pom.xml').contains('<artifactId>common-domain</artifactId>')        : 'domain-rest (presentation) missing common-domain dep'
assert text('domain-rest/pom.xml').contains('<artifactId>domain-service</artifactId>')       : 'domain-rest (presentation) missing domain-service dep (the service domain deps)'
assert text('service/pom.xml').contains('<artifactId>common-domain</artifactId>')            : 'service missing common-domain dep'

// only the database integration gets the SQL logging dependency
assert text('integration-db-users/pom.xml').contains('<artifactId>datasource-proxy-spring-boot-starter</artifactId>')      : 'integration-db-users missing datasource-proxy dep'
assert !text('integration-rest-orders/pom.xml').contains('<artifactId>datasource-proxy-spring-boot-starter</artifactId>') : 'integration-rest-orders must not get the datasource-proxy dep'
assert !text('integration-jms-events/pom.xml').contains('<artifactId>datasource-proxy-spring-boot-starter</artifactId>')  : 'integration-jms-events must not get the datasource-proxy dep'

// rest integration is a client (spring-boot-starter-restclient), not the servlet web server;
// the rest presentation is the server (spring-boot-starter-web)
def restIntegPom = text('integration-rest-orders/pom.xml')
assert restIntegPom.contains('<artifactId>spring-boot-starter-restclient</artifactId>') : 'rest integration must use spring-boot-starter-restclient'
assert !restIntegPom.contains('<artifactId>spring-boot-starter-web</artifactId>')       : 'rest integration must not pull the servlet web starter'
assert text('integration-db-users/pom.xml').contains('<artifactId>spring-boot-starter-data-jpa</artifactId>') : 'database integration must use spring-boot-starter-data-jpa'
assert text('presentation-rest/pom.xml').contains('<artifactId>spring-boot-starter-web</artifactId>')         : 'rest presentation must use spring-boot-starter-web'

// ── 9. app and acceptance-tests ───────────────────────────────────────────────

def appPom = text('app/pom.xml')
['common-domain', 'domain-db-users', 'domain-jms-events', 'domain-rest',
 'domain-rest-orders', 'integration-db-users', 'integration-jms-events',
 'integration-rest-orders', 'presentation-rest', 'service'].each { dep ->
    assert appPom.contains("<artifactId>${dep}</artifactId>") : "app missing dep: $dep"
}
assert !appPom.contains('<artifactId>acceptance-tests</artifactId>') : 'app must not depend on acceptance-tests'
assert !appPom.contains('<artifactId>common-testing</artifactId>')   : 'app must not depend on common-testing'
assert appPom.contains('<artifactId>spring-boot-starter-data-jpa</artifactId>') : 'app must depend on spring-boot-starter-data-jpa (database integration present)'

def atPom = text('acceptance-tests/pom.xml')
['app', 'common-domain', 'domain-db-users', 'domain-jms-events',
 'domain-rest', 'domain-rest-orders'].each { dep ->
    assert atPom.contains("<artifactId>${dep}</artifactId>") : "acceptance-tests missing dep: $dep"
}
assert !atPom.contains('<artifactId>integration-db-users</artifactId>')    : 'acceptance-tests must not include integration-db-users'
assert !atPom.contains('<artifactId>integration-rest-orders</artifactId>') : 'acceptance-tests must not include integration-rest-orders'
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

_buildLog.append("\n=== PASSED ===\n")
true
