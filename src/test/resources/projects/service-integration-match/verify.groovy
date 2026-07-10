// ── Test: service-integration-match ──────────────────────────────────────────
// appName=Svc Int Match Test  integrations=database:orders,rest:billing  serviceAreas=orders,reporting
// presentationTypes=,
// Covers (#2): a named service area depends on the integration module sharing its name
//   (service-orders -> integration-db-orders); a service area with no name match takes no
//   integration (service-reporting); and an integration with no matching service area
//   (integration-rest-billing) is not pulled into an unrelated service.

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
def aid = 'svc-int-match-test'
def appName = 'Svc Int Match Test'
def raw = { String rel ->
    new File(basedir, rel).text
}
def text = { String rel ->
    raw(rel).replace("<artifactId>${aid}-", '<artifactId>')
}

check('parent/pom.xml')
assert !new File(basedir, 'pom.xml').exists() : "Root pom.xml must not exist"

def modules = [
    'acceptance-tests', 'app', 'common-domain', 'common-testing',
    'domain-db-orders', 'domain-rest-billing',
    'domain-service-orders', 'domain-service-reporting',
    'integration-db-orders', 'integration-rest-billing',
    'service-orders', 'service-reporting',
]
modules.each { m -> check("${m}/pom.xml") }
modules.each { m ->
    assert raw("${m}/pom.xml").contains("<artifactId>${aid}-${m}</artifactId>") \
        : "${m}/pom.xml must declare its own artifactId as '${aid}-${m}'"
}

// ── #2: named service area ↔ integration sharing its name ─────────────────────
def ordersSvc = text('service-orders/pom.xml')
assert ordersSvc.contains('<artifactId>integration-db-orders</artifactId>')     : 'service-orders must depend on integration-db-orders (shared name)'
assert ordersSvc.contains('<artifactId>domain-service-orders</artifactId>')     : 'service-orders must depend on its own domain-service module'
assert !ordersSvc.contains('<artifactId>integration-rest-billing</artifactId>') : 'service-orders must not take the unrelated integration-rest-billing'
assert ordersSvc.contains('<artifactId>common-testing</artifactId>')           : 'service-orders missing common-testing TEST dep'

def reportingSvc = text('service-reporting/pom.xml')
assert reportingSvc.contains('<artifactId>domain-service-reporting</artifactId>') : 'service-reporting must depend on its own domain-service module'
assert !reportingSvc.contains('<artifactId>integration-')                         : 'service-reporting has no name match, so it takes no integration'
assert reportingSvc.contains('<artifactId>common-testing</artifactId>')           : 'service-reporting missing common-testing TEST dep'

// domain-service-* modules: spring-boot-starter-validation, but NOT common-testing (no domain
// module depends on common-testing)
['domain-service-orders', 'domain-service-reporting'].each { m ->
    assert !text("${m}/pom.xml").contains('<artifactId>common-testing</artifactId>')                : "${m} must not depend on common-testing"
    assert text("${m}/pom.xml").contains('<artifactId>spring-boot-starter-validation</artifactId>') : "${m} missing spring-boot-starter-validation dep"
}

// only the database integration gets the SQL logging dependency
assert text('integration-db-orders/pom.xml').contains('<artifactId>datasource-proxy-spring-boot-starter</artifactId>')      : 'integration-db-orders missing datasource-proxy dep'
assert !text('integration-rest-billing/pom.xml').contains('<artifactId>datasource-proxy-spring-boot-starter</artifactId>') : 'integration-rest-billing must not get the datasource-proxy dep'

// common-testing depends on every domain module, including the named service-area domain modules.
// No domain module depends back on common-testing (only service/service-{name} does).
def commonTestingPom = text('common-testing/pom.xml')
['domain-db-orders', 'domain-rest-billing', 'domain-service-orders', 'domain-service-reporting'].each { m ->
    assert commonTestingPom.contains("<artifactId>${m}</artifactId>") : "common-testing missing domain dep: ${m}"
}

// acceptance-tests: common-testing TEST dep + datasource-proxy (sql logging)
def atPom = text('acceptance-tests/pom.xml')
assert atPom.contains('<artifactId>common-testing</artifactId>')                       : 'acceptance-tests missing common-testing TEST dep'
assert atPom.contains('<artifactId>datasource-proxy-spring-boot-starter</artifactId>') : 'acceptance-tests missing datasource-proxy dep'
assert !atPom.contains('<artifactId>spring-graphql-test</artifactId>') : 'acceptance-tests must not add spring-graphql-test (no graphql presentation)'

// parent dependencyManagement manages exactly the sibling modules; appName-derived name/description
def parentPom = raw('parent/pom.xml')
def dmBlock = (parentPom =~ /(?s)<dependencyManagement>.*?<\/dependencyManagement>/)[0]
def dmIds = dmBlock.findAll(/<artifactId>[^<]+<\/artifactId>/).collect { it.replaceAll(/<\/?artifactId>/, '') }
def dmModuleIds = dmIds.findAll { it.startsWith("${aid}-") }.collect { it.substring(aid.length() + 1) }
assert dmModuleIds.sort() == modules.sort() : 'parent dependencyManagement must manage exactly the sibling modules'
assert parentPom.contains("<name>${appName} Parent</name>")                     : 'parent <name> must be "<appName> Parent"'
assert parentPom.contains("<description>${appName}'s parent POM.</description>") : "parent <description> must be \"<appName>'s parent POM.\""

// no presentation types at all: no domain-{type}/presentation-{type} modules, no AT base classes
assert !new File(basedir, 'domain-rest').exists()         : 'no presentation modules must be created'
assert !new File(basedir, 'presentation-rest').exists()   : 'no presentation modules must be created'
def p = 'com/example/svcintmatch'
assert !new File(basedir, "acceptance-tests/src/test/java/${p}/at/AppRestAcceptanceTestBase.java").exists() \
    : 'AppRestAcceptanceTestBase must not be generated (no presentation types)'

// src scaffolding present in every module
modules.each { m ->
    ['src/main/java', 'src/main/resources', 'src/test/java', 'src/test/resources'].each { dir ->
        check("${m}/${dir}")
    }
}

_buildLog.append("\n=== PASSED ===\n")
true
