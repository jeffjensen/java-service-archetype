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

def text = { String rel ->
    new File(basedir, rel).text
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

// ── 4. Child modules reference parent/pom.xml ────────────────────────────────

modules.each { m ->
    assert text("${m}/pom.xml").contains('<relativePath>../parent/pom.xml</relativePath>') \
        : "${m}/pom.xml missing relativePath"
}

modules.each { m ->
    assert text("${m}/pom.xml").contains("<artifactId>${m}</artifactId>") \
        : "${m}/pom.xml must declare its own artifactId as '${m}'"
}

// ── 5. Source tree uses 'package' (org.mycompany.service), NOT 'groupId' ──────

def p = 'org/mycompany/service'
def pkg = p.replace('/', '.')

[
    "common-domain/src/main/java/${p}/common/domain/package-info.java",
    "common-domain/src/test/java/${p}/common/domain/package-info.java",
    "common-testing/src/main/java/${p}/common/testing/package-info.java",
    "domain-db-users/src/main/java/${p}/domain/db/users/package-info.java",
    "domain-db-users/src/test/java/${p}/domain/db/users/package-info.java",
    "integration-db-users/src/main/java/${p}/integration/db/users/package-info.java",
    "integration-db-users/src/test/java/${p}/integration/db/users/package-info.java",
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

assert text("common-domain/src/main/java/${p}/common/domain/package-info.java").contains("package ${pkg}.common.domain;")    : 'package-info.java must use the package property, not groupId'
assert text("common-testing/src/main/java/${p}/common/testing/package-info.java").contains("package ${pkg}.common.testing;") : 'common-testing package-info.java must use the package property'
assert text("service/src/main/java/${p}/service/package-info.java").contains("package ${pkg}.service;")                      : 'service package-info.java must use the package property'
assert text("app/src/main/java/${p}/app/package-info.java").contains("package ${pkg}.app;")                                  : 'app package-info.java must use the package property'
assert text("acceptance-tests/src/test/java/${p}/at/package-info.java").contains("package ${pkg}.at;")       : 'acceptance-tests package-info.java must use the package property'
assert new File(basedir, "common-testing/src/test/java/${p}/common/testing/package-info.java").exists()                     : 'common-testing must have test Java package-info.java'
assert new File(basedir, "acceptance-tests/src/main/java/${p}/at/package-info.java").exists()                       : 'acceptance-tests must have main Java package-info.java'

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
assert text('domain-rest/pom.xml').contains('<artifactId>common-domain</artifactId>')            : 'domain-rest missing common-domain dep'
assert text('presentation-rest/pom.xml').contains('<artifactId>domain-rest</artifactId>')        : 'presentation-rest missing domain-rest dep'
assert text('service/pom.xml').contains('<artifactId>common-domain</artifactId>')                : 'service missing common-domain dep'


// ── Failsafe plugin wiring ──────────────────────────────────────

assert text('acceptance-tests/pom.xml').contains('<artifactId>maven-failsafe-plugin</artifactId>') \
    : 'acceptance-tests must configure maven-failsafe-plugin'
modules.findAll { it.startsWith('integration-') }.each { m ->
    assert text("${m}/pom.xml").contains('<artifactId>maven-failsafe-plugin</artifactId>') \
        : "${m} must configure maven-failsafe-plugin"
}

_buildLog.append("\n=== PASSED ===\n")
true
