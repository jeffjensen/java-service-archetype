// ── Test: spring-off ──────────────────────────────────────────────────────────
// integrations=database:users  serviceAreas=,  presentationTypes=rest  includeSpring=false
// Covers: includeSpring=false omits ALL Spring artifacts (parent inheritance,
//         @Configuration classes, spring-context, app starters, and the
//         Application/AppServletInitializer classes) while still generating the
//         full module tree, package-info files, and domain-service wiring.

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
def aid = 'spring-off-test'
def raw = { String rel ->
    new File(basedir, rel).text
}
def text = { String rel ->
    raw(rel).replace("<artifactId>${aid}-", '<artifactId>')
}

// ── 1. parent/ module exists; no root pom.xml ────────────────────────────────

check('parent/pom.xml')
assert !new File(basedir, 'pom.xml').exists() : "Root pom.xml must not exist"

// ── 2. The full module tree is still generated ───────────────────────────────

def modules = [
    'acceptance-tests', 'app', 'common-domain', 'common-testing',
    'domain-db-users', 'domain-rest', 'domain-service',
    'integration-db-users', 'presentation-rest', 'service',
]
modules.each { m -> check("${m}/pom.xml") }

// instruction 2: every module declares its artifactId as "<aid>-<dir name>"
modules.each { m ->
    assert raw("${m}/pom.xml").contains("<artifactId>${aid}-${m}</artifactId>") \
        : "${m}/pom.xml must declare its own artifactId as '${aid}-${m}'"
}

// ── 3. parent/pom.xml: no Spring inheritance; sibling-only dependencyManagement ─

def parentPom = raw('parent/pom.xml')
assert parentPom.contains('<packaging>pom</packaging>') : 'parent must have pom packaging'
assert !parentPom.contains('<parent>')                  : 'includeSpring=false: parent must not inherit a parent POM'
assert !parentPom.contains('spring-boot')               : 'includeSpring=false: no spring-boot references in parent'
assert !parentPom.contains('spring-core')               : 'includeSpring=false: no spring-core in parent dependencyManagement (#4)'
assert !parentPom.contains('jspecify')                  : 'includeSpring=false: no jspecify in parent dependencies (#6)'

def moduleOrder = modules.sort().collect { "<module>../${it}</module>" }
def moduleBlock = parentPom.replaceAll(/(?s).*<modules>(.*?)<\/modules>.*/, '$1')
assert moduleBlock.findAll(/<module>[^<]+<\/module>/) == moduleOrder : 'parent <modules> not sorted'

def dmBlock = (parentPom =~ /(?s)<dependencyManagement>.*?<\/dependencyManagement>/)[0]
def dmIds = dmBlock.findAll(/<artifactId>[^<]+<\/artifactId>/).collect { it.replaceAll(/<\/?artifactId>/, '') }
def dmModuleIds = dmIds.findAll { it.startsWith("${aid}-") }.collect { it.substring(aid.length() + 1) }
assert dmModuleIds.sort() == modules.sort() : 'parent <dependencyManagement> must list exactly the sibling modules'

// ── 4. No Spring entry-point classes and no @Configuration / .config packages ─

def p = 'com/example/springoff'
assert !new File(basedir, "app/src/main/java/${p}/app/config/Application.java").exists()           : 'includeSpring=false: no Application.java'
assert !new File(basedir, "app/src/main/java/${p}/app/config/AppServletInitializer.java").exists() : 'includeSpring=false: no AppServletInitializer.java'
assert !new File(basedir, "app/src/main/java/${p}/app/config").exists()                            : 'includeSpring=false: no app .config package'
assert !new File(basedir, "common-domain/src/main/java/${p}/common/domain/config").exists()        : 'includeSpring=false: no common-domain .config package'
assert !new File(basedir, "service/src/main/java/${p}/service/config").exists()                    : 'includeSpring=false: no service .config package'
assert !new File(basedir, "integration-db-users/src/main/java/${p}/integration/db/users/config").exists() : 'includeSpring=false: no integration .config package'
assert !new File(basedir, "domain-service/src/main/java/${p}/domain/service/config").exists()      : 'includeSpring=false: no domain-service .config package'

// ── 5. No Spring dependencies anywhere ───────────────────────────────────────

def appPom = text('app/pom.xml')
assert !appPom.contains('spring-boot') : 'includeSpring=false: app must not reference spring-boot'
modules.each { m ->
    assert !text("${m}/pom.xml").contains('org.springframework') : "includeSpring=false: ${m} must not reference org.springframework"
}
// common-domain has no other dependencies, so without spring-context it has no <dependencies> block
assert !text('common-domain/pom.xml').contains('<dependencies>') : 'includeSpring=false: common-domain must have no dependencies'

// ── 6. Non-Spring features remain: package-info (both roots) + domain-service wiring ─

check("common-domain/src/main/java/${p}/common/domain/package-info.java")
check("common-domain/src/test/java/${p}/common/domain/package-info.java")
check("app/src/main/java/${p}/app/package-info.java")
check("app/src/test/java/${p}/app/package-info.java")
check("domain-service/src/main/java/${p}/domain/service/package-info.java")
check("domain-service/src/test/java/${p}/domain/service/package-info.java")
assert text('service/pom.xml').contains('<artifactId>domain-service</artifactId>')               : 'service must still depend on its domain-service module'
assert text('domain-service/pom.xml').contains('<artifactId>common-domain</artifactId>')         : 'domain-service must depend on common-domain'
assert text('integration-db-users/pom.xml').contains('<artifactId>domain-db-users</artifactId>') : 'integration-db-users must depend on its domain module'
assert text('presentation-rest/pom.xml').contains('<artifactId>domain-rest</artifactId>')        : 'presentation-rest must depend on domain-rest'

// ── 7. src scaffolding + failsafe wiring unchanged ───────────────────────────

modules.each { m ->
    ['src/main/java', 'src/main/resources', 'src/test/java', 'src/test/resources'].each { dir ->
        check("${m}/${dir}")
    }
}
assert text('acceptance-tests/pom.xml').contains('<artifactId>maven-failsafe-plugin</artifactId>')     : 'acceptance-tests must configure maven-failsafe-plugin'
assert text('integration-db-users/pom.xml').contains('<artifactId>maven-failsafe-plugin</artifactId>') : 'integration module must configure maven-failsafe-plugin'

// ── dependency lists are split into TEST and PROD sections (TEST first) ──────

def svcPom = text('service/pom.xml')
assert svcPom.contains('<!-- TEST -->') : 'dependency list must carry a TEST section header'
assert svcPom.contains('<!-- PROD -->') : 'dependency list must carry a PROD section header'
assert svcPom.indexOf('<!-- TEST -->') < svcPom.indexOf('<!-- PROD -->')                           : 'TEST section must come before PROD'
assert svcPom.indexOf('<!-- PROD -->') < svcPom.indexOf('<artifactId>domain-service</artifactId>') : 'compile-scope deps must sit under PROD'

_buildLog.append("\n=== PASSED ===\n")
true
