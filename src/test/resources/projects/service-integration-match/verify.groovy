// ── Test: service-integration-match ──────────────────────────────────────────
// integrations=database:orders,rest:billing  serviceAreas=orders,reporting  presentationTypes=,
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

def reportingSvc = text('service-reporting/pom.xml')
assert reportingSvc.contains('<artifactId>domain-service-reporting</artifactId>') : 'service-reporting must depend on its own domain-service module'
assert !reportingSvc.contains('<artifactId>integration-')                         : 'service-reporting has no name match, so it takes no integration'

// parent dependencyManagement manages exactly the sibling modules
def parentPom = raw('parent/pom.xml')
def dmBlock = (parentPom =~ /(?s)<dependencyManagement>.*?<\/dependencyManagement>/)[0]
def dmIds = dmBlock.findAll(/<artifactId>[^<]+<\/artifactId>/).collect { it.replaceAll(/<\/?artifactId>/, '') }
def dmModuleIds = dmIds.findAll { it.startsWith("${aid}-") }.collect { it.substring(aid.length() + 1) }
assert dmModuleIds.sort() == modules.sort() : 'parent dependencyManagement must manage exactly the sibling modules'

// src scaffolding present in every module
modules.each { m ->
    ['src/main/java', 'src/main/resources', 'src/test/java', 'src/test/resources'].each { dir ->
        check("${m}/${dir}")
    }
}

_buildLog.append("\n=== PASSED ===\n")
true
