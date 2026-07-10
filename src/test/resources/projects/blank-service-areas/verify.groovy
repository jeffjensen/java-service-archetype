// ── Test: blank-service-areas ─────────────────────────────────────────────────
// appName=Blank Areas Test  integrations=database:main  serviceAreas=" , ,"  presentationTypes=rest
// Covers: the serviceModules.isEmpty() fallback — a non-empty serviceAreas that
//         contains only blank entries after trimming; all produce "service-" (8 chars,
//         not > 8) so the list is empty, triggering fallback to a single plain "service"
//         module. Distinct from single-service-fallback which takes the
//         serviceAreasInput.isEmpty() branch.

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
def aid = 'blank-areas-test'
def appName = 'Blank Areas Test'
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
    'domain-db-main', 'domain-rest',
    'integration-db-main',
    'presentation-rest',
    'service',
]
modules.each { m -> check("${m}/pom.xml") }

// ── 3. Blank entries fall back to plain "service"; no "service-" modules ──────

assert new File(basedir, 'service/pom.xml').exists() \
    : "All-blank serviceAreas must fall back to plain 'service' module"
assert !new File(basedir, 'service-').exists() \
    : "No 'service-' prefixed module should exist when all entries are blank"

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

def p = 'com/example/blankareas'
def pkg = p.replace('/', '.')
[
    "common-domain/src/main/java/${p}/common/domain/package-info.java",
    "common-testing/src/main/java/${p}/common/testing/package-info.java",
    "domain-service/src/main/java/${p}/domain/service/package-info.java",
    "domain-db-main/src/main/java/${p}/domain/db/main/package-info.java",
    "integration-db-main/src/main/java/${p}/integration/db/main/package-info.java",
    "domain-rest/src/main/java/${p}/domain/rest/package-info.java",
    "presentation-rest/src/main/java/${p}/presentation/rest/package-info.java",
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

// ── 7. Dependency wiring ─────────────────────────────────────────────────────

assert text('domain-db-main/pom.xml').contains('<artifactId>common-domain</artifactId>')        : 'domain-db-main missing common-domain dep'
assert text('integration-db-main/pom.xml').contains('<artifactId>domain-db-main</artifactId>') : 'integration-db-main missing domain-db-main dep'
assert text('integration-db-main/pom.xml').contains('<artifactId>datasource-proxy-spring-boot-starter</artifactId>') \
    : 'integration-db-main (a database integration) missing datasource-proxy dep'
assert text('domain-rest/pom.xml').contains('<artifactId>common-domain</artifactId>')           : 'domain-rest missing common-domain dep'
assert text('domain-rest/pom.xml').contains('<artifactId>domain-service</artifactId>')          : 'domain-rest missing domain-service dep (the service domain deps)'
assert text('presentation-rest/pom.xml').contains('<artifactId>domain-rest</artifactId>')       : 'presentation-rest missing domain-rest dep'
assert text('service/pom.xml').contains('<artifactId>common-domain</artifactId>')               : 'service missing common-domain dep'
assert text('service/pom.xml').contains('<artifactId>common-testing</artifactId>')              : 'service missing common-testing TEST dep'
assert !text('domain-service/pom.xml').contains('<artifactId>common-testing</artifactId>')                : 'domain-service must not depend on common-testing (no domain module does)'
assert text('domain-service/pom.xml').contains('<artifactId>spring-boot-starter-validation</artifactId>') : 'domain-service missing spring-boot-starter-validation dep'
assert text('common-testing/pom.xml').contains('<artifactId>domain-db-main</artifactId>')       : 'common-testing missing integration domain dep'
assert text('common-testing/pom.xml').contains('<artifactId>domain-service</artifactId>')       : 'common-testing must depend on domain-service'
assert text('common-testing/pom.xml').contains('<artifactId>domain-rest</artifactId>')          : 'common-testing must depend on domain-rest'

def appPom = text('app/pom.xml')
['common-domain', 'domain-db-main', 'domain-rest',
 'integration-db-main', 'presentation-rest', 'service'].each { dep ->
    assert appPom.contains("<artifactId>${dep}</artifactId>") : "app missing dep: $dep"
}
assert appPom.contains('<artifactId>spring-boot-starter</artifactId>')             : 'app must depend on the core spring-boot-starter'
assert appPom.contains('<artifactId>spring-boot-starter-webmvc</artifactId>')      : 'app must depend on spring-boot-starter-webmvc'
assert appPom.contains('<artifactId>spring-boot-starter-actuator</artifactId>')    : 'app must depend on spring-boot-starter-actuator'
assert appPom.contains('<artifactId>spring-boot-admin-starter-client</artifactId>'): 'app must depend on spring-boot-admin-starter-client'
assert appPom.contains('<artifactId>spring-boot-starter-data-jpa</artifactId>')    : 'app must depend on spring-boot-starter-data-jpa'

def atPom = text('acceptance-tests/pom.xml')
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

// ── Acceptance-test infrastructure (REST only) ───────────────────────────────

check("acceptance-tests/src/test/java/${p}/at/AppRestAcceptanceTestBase.java")
check("common-testing/src/main/java/${p}/common/testing/RestAcceptanceTestBase.java")
assert !new File(basedir, "acceptance-tests/src/test/java/${p}/at/AppGraphQlAcceptanceTestBase.java").exists() \
    : 'AppGraphQlAcceptanceTestBase must not be generated (no graphql presentation)'

_buildLog.append("\n=== PASSED ===\n")
true
