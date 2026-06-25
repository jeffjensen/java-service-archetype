// ── Test: uppercase-types ─────────────────────────────────────────────────────
// integrations=DATABASE:Users,REST:Orders  serviceAreas=,  presentationTypes=REST
// Covers: integration type and name inputs are normalised to lowercase;
//         'DATABASE' is abbreviated to 'db'; all generated module names are lowercase
//         regardless of what case the user supplied

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
def aid = 'upper-test'
def raw = { String rel ->
    new File(basedir, rel).text
}
def text = { String rel ->
    raw(rel).replace("<artifactId>${aid}-", '<artifactId>')
}

// ── 1. parent/ module exists; no root pom.xml ────────────────────────────────

check('parent/pom.xml')
assert !new File(basedir, 'pom.xml').exists() : "Root pom.xml must not exist"

// ── 2. All expected sibling modules exist (all lowercase) ─────────────────────

def modules = [
    'acceptance-tests', 'app', 'common-domain', 'common-testing',
    'domain-service',
    'domain-db-users',
    'domain-rest',
    'domain-rest-orders',
    'integration-db-users',
    'integration-rest-orders',
    'presentation-rest',
    'service',
]
modules.each { m -> check("${m}/pom.xml") }

// ── 3. parent/pom.xml completeness, packaging, sort order ────────────────────

def parentPom = raw('parent/pom.xml')
modules.each { m ->
    assert parentPom.contains("<module>../${m}</module>")             : "parent <modules> missing: ../$m"
    assert parentPom.contains("<artifactId>${aid}-${m}</artifactId>") : "parent <dependencyManagement> missing: $m"
}
assert parentPom.contains('<packaging>pom</packaging>') : "parent must have pom packaging"

// POM XML is case-sensitive — uppercase names in the source would appear verbatim here
assert !parentPom.contains('<module>../domain-db-Users</module>')        : "'Users' must be lowercased to 'users'"
assert !parentPom.contains('<module>../domain-DATABASE-users</module>')  : "'DATABASE' must not appear verbatim in module name"
assert !parentPom.contains('<module>../domain-database-users</module>')  : "'database' must be abbreviated to 'db'"
assert !parentPom.contains('<module>../domain-REST-orders</module>')     : "'REST' must be lowercased to 'rest'"
assert !parentPom.contains('<module>../domain-REST</module>')            : "presentation type 'REST' must be lowercased"
assert !parentPom.contains('<module>../presentation-REST</module>')      : "presentation module must use lowercase type"
assert !parentPom.contains("<artifactId>${aid}-domain-db-Users</artifactId>") : "'Users' must be lowercased in dependencyManagement"

def moduleOrder = modules.sort().collect { "<module>../${it}</module>" }
def moduleBlock = parentPom.replaceAll(/(?s).*<modules>(.*?)<\/modules>.*/, '$1')
assert moduleBlock.findAll(/<module>[^<]+<\/module>/) == moduleOrder : "parent <modules> not sorted"

def dmBlock = (parentPom =~ /(?s)<dependencyManagement>.*?<\/dependencyManagement>/)[0]
def dmIds = dmBlock.findAll(/<artifactId>[^<]+<\/artifactId>/).collect { it.replaceAll(/<\/?artifactId>/, '') }
def dmModuleIds = dmIds.findAll { it.startsWith("${aid}-") }.collect { it.substring(aid.length() + 1) }
assert dmModuleIds == modules.sort()        : "parent <dependencyManagement> not sorted alphabetically"
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

// ── 5. Source skeletons (paths reflect lowercased names) ─────────────────────

def p = 'com/example/upper'
def pkg = p.replace('/', '.')
[
    "common-domain/src/main/java/${p}/common/domain/package-info.java",
    "common-domain/src/test/java/${p}/common/domain/package-info.java",
    "common-testing/src/main/java/${p}/common/testing/package-info.java",
    "domain-db-users/src/main/java/${p}/domain/db/users/package-info.java",
    "domain-db-users/src/test/java/${p}/domain/db/users/package-info.java",
    "integration-db-users/src/main/java/${p}/integration/db/users/package-info.java",
    "integration-db-users/src/test/java/${p}/integration/db/users/package-info.java",
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

assert text("common-domain/src/main/java/${p}/common/domain/package-info.java").contains("package ${pkg}.common.domain;")       : 'common-domain package-info.java has wrong package declaration'
assert text("common-testing/src/main/java/${p}/common/testing/package-info.java").contains("package ${pkg}.common.testing;")    : 'common-testing package-info.java has wrong package declaration'
assert text("domain-db-users/src/main/java/${p}/domain/db/users/package-info.java").contains("package ${pkg}.domain.db.users;") : 'domain-db-users package-info.java has wrong package declaration'
assert text("domain-rest-orders/src/main/java/${p}/domain/rest/orders/package-info.java").contains("package ${pkg}.domain.rest.orders;") : 'domain-rest-orders package-info.java has wrong package declaration'
assert text("service/src/main/java/${p}/service/package-info.java").contains("package ${pkg}.service;")                         : 'service package-info.java has wrong package declaration'
assert text("app/src/main/java/${p}/app/package-info.java").contains("package ${pkg}.app;")                                     : 'app package-info.java has wrong package declaration'
assert text("acceptance-tests/src/test/java/${p}/at/package-info.java").contains("package ${pkg}.at;")          : 'acceptance-tests package-info.java has wrong package declaration'
assert new File(basedir, "common-testing/src/test/java/${p}/common/testing/package-info.java").exists()                        : 'common-testing must have test Java package-info.java'
assert new File(basedir, "acceptance-tests/src/main/java/${p}/at/package-info.java").exists()                          : 'acceptance-tests must have main Java package-info.java'

// ── 6. Dependency wiring ─────────────────────────────────────────────────────

assert text('domain-db-users/pom.xml').contains('<artifactId>common-domain</artifactId>')          : 'domain-db-users missing common-domain dep'
assert text('integration-db-users/pom.xml').contains('<artifactId>domain-db-users</artifactId>')   : 'integration-db-users missing domain dep'
assert text('domain-rest-orders/pom.xml').contains('<artifactId>common-domain</artifactId>')       : 'domain-rest-orders missing common-domain dep'
assert text('integration-rest-orders/pom.xml').contains('<artifactId>domain-rest-orders</artifactId>') : 'integration-rest-orders missing domain dep'
assert text('domain-rest/pom.xml').contains('<artifactId>common-domain</artifactId>')              : 'domain-rest missing common-domain dep'
assert text('presentation-rest/pom.xml').contains('<artifactId>domain-rest</artifactId>')          : 'presentation-rest missing domain-rest dep'
assert text('service/pom.xml').contains('<artifactId>common-domain</artifactId>')                  : 'service missing common-domain dep'

def appPom = text('app/pom.xml')
['common-domain', 'domain-db-users', 'domain-rest', 'domain-rest-orders',
 'integration-db-users', 'integration-rest-orders', 'presentation-rest', 'service'].each { dep ->
    assert appPom.contains("<artifactId>${dep}</artifactId>") : "app missing dep: $dep"
}
assert !appPom.contains('<artifactId>acceptance-tests</artifactId>') : 'app must not depend on acceptance-tests'
assert !appPom.contains('<artifactId>common-testing</artifactId>')   : 'app must not depend on common-testing'
assert appPom.count('<artifactId>app</artifactId>') == 1            : 'app must not depend on itself'

def atPom = text('acceptance-tests/pom.xml')
['app', 'common-domain', 'domain-db-users', 'domain-rest', 'domain-rest-orders'].each { dep ->
    assert atPom.contains("<artifactId>${dep}</artifactId>") : "acceptance-tests missing dep: $dep"
}
assert !atPom.contains('<artifactId>integration-db-users</artifactId>')    : 'acceptance-tests must not include integration-db-users'
assert !atPom.contains('<artifactId>integration-rest-orders</artifactId>') : 'acceptance-tests must not include integration-rest-orders'
assert !atPom.contains('<artifactId>service</artifactId>')                 : 'acceptance-tests must not depend on service'

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
