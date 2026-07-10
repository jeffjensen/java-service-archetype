// ── Test: package-differs-from-groupid ────────────────────────────────────────
// groupId=com.example  package=org.mycompany.service
// Covers: source tree uses the 'package' property, not groupId; generated
//         package-info.java declarations match 'package', not 'groupId'

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
def aid = 'pkg-diff-test'
def appName = 'Pkg Diff Test'
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
    'domain-db-users', 'domain-rest',
    'integration-db-users',
    'presentation-rest',
    'service',
]
modules.each { m -> check("${m}/pom.xml") }

// ── 3. parent/pom.xml completeness and sort order ────────────────────────────

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

// ── 5. Source tree uses 'package' (org.mycompany.service), NOT 'groupId' ──────
// package-info.java only under src/main/java (see basic/verify.groovy for the exhaustive check).

def p = 'org/mycompany/service'
def pkg = p.replace('/', '.')

[
    "common-domain/src/main/java/${p}/common/domain/package-info.java",
    "common-testing/src/main/java/${p}/common/testing/package-info.java",
    "domain-db-users/src/main/java/${p}/domain/db/users/package-info.java",
    "integration-db-users/src/main/java/${p}/integration/db/users/package-info.java",
    "domain-rest/src/main/java/${p}/domain/rest/package-info.java",
    "presentation-rest/src/main/java/${p}/presentation/rest/package-info.java",
    "service/src/main/java/${p}/service/package-info.java",
    "app/src/main/java/${p}/app/package-info.java",
    "acceptance-tests/src/main/java/${p}/at/package-info.java",
].each { check(it) }

assert text("common-domain/src/main/java/${p}/common/domain/package-info.java").contains("package ${pkg}.common.domain;")    : 'package-info.java must use the package property, not groupId'
assert text("common-testing/src/main/java/${p}/common/testing/package-info.java").contains("package ${pkg}.common.testing;") : 'common-testing package-info.java must use the package property'
assert text("service/src/main/java/${p}/service/package-info.java").contains("package ${pkg}.service;")                      : 'service package-info.java must use the package property'
assert text("app/src/main/java/${p}/app/package-info.java").contains("package ${pkg}.app;")                                  : 'app package-info.java must use the package property'
assert text("acceptance-tests/src/main/java/${p}/at/package-info.java").contains("package ${pkg}.at;")                       : 'acceptance-tests package-info.java must use the package property'
assert !new File(basedir, "acceptance-tests/src/test/java/${p}/at/package-info.java").exists() : 'acceptance-tests must not have a test-side package-info.java'

// ── 6. groupId-based source paths must NOT exist ─────────────────────────────

assert !new File(basedir, 'common-domain/src/main/java/com').exists() \
    : "Source tree must use package (org.mycompany.service), not groupId (com.example)"
assert !new File(basedir, 'service/src/main/java/com').exists() \
    : "Source tree must use package, not groupId"

// ── 7. src directory scaffolding ─────────────────────────────────────────────

modules.each { m ->
    ['src/main/java', 'src/main/resources', 'src/test/java', 'src/test/resources'].each { dir ->
        check("${m}/${dir}")
    }
}

// ── 8. Dependency wiring ─────────────────────────────────────────────────────

assert text('domain-db-users/pom.xml').contains('<artifactId>common-domain</artifactId>')        : 'domain-db-users missing common-domain dep'
assert text('integration-db-users/pom.xml').contains('<artifactId>domain-db-users</artifactId>') : 'integration-db-users missing domain dep'
assert text('integration-db-users/pom.xml').contains('<artifactId>datasource-proxy-spring-boot-starter</artifactId>') \
    : 'integration-db-users (a database integration) missing datasource-proxy dep'
assert text('domain-rest/pom.xml').contains('<artifactId>common-domain</artifactId>')            : 'domain-rest missing common-domain dep'
assert text('domain-rest/pom.xml').contains('<artifactId>domain-service</artifactId>')           : 'domain-rest missing domain-service dep (the service domain deps)'
assert text('presentation-rest/pom.xml').contains('<artifactId>domain-rest</artifactId>')        : 'presentation-rest missing domain-rest dep'
assert text('service/pom.xml').contains('<artifactId>common-domain</artifactId>')                : 'service missing common-domain dep'
assert text('service/pom.xml').contains('<artifactId>common-testing</artifactId>')               : 'service missing common-testing TEST dep'
assert !text('domain-service/pom.xml').contains('<artifactId>common-testing</artifactId>')                  : 'domain-service must not depend on common-testing (no domain module does)'
assert text('domain-service/pom.xml').contains('<artifactId>spring-boot-starter-validation</artifactId>')   : 'domain-service missing spring-boot-starter-validation dep'
assert text('common-testing/pom.xml').contains('<artifactId>domain-service</artifactId>') : 'common-testing must depend on domain-service'
assert text('common-testing/pom.xml').contains('<artifactId>domain-rest</artifactId>')    : 'common-testing must depend on domain-rest'
assert text('acceptance-tests/pom.xml').contains('<artifactId>common-testing</artifactId>')                       : 'acceptance-tests missing common-testing TEST dep'
assert text('acceptance-tests/pom.xml').contains('<artifactId>datasource-proxy-spring-boot-starter</artifactId>') : 'acceptance-tests missing datasource-proxy dep'


// ── Failsafe plugin wiring ──────────────────────────────────────

assert text('acceptance-tests/pom.xml').contains('<artifactId>maven-failsafe-plugin</artifactId>') \
    : 'acceptance-tests must configure maven-failsafe-plugin'
modules.findAll { it.startsWith('integration-') }.each { m ->
    assert text("${m}/pom.xml").contains('<artifactId>maven-failsafe-plugin</artifactId>') \
        : "${m} must configure maven-failsafe-plugin"
}

_buildLog.append("\n=== PASSED ===\n")
true
