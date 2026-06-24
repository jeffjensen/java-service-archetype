import java.io.File

// ─── Helpers ──────────────────────────────────────────────────────────────────

/** Returns the abbreviation used in module/package names for the given integration type. */
def typeAbbrev = { String t ->
    t.trim().toLowerCase() == 'database' ? 'db' : t.trim().toLowerCase()
}

def ensureDir = { File base, String rel ->
    // File(parent, child) normalises separators on all platforms
    new File(base, rel).mkdirs()
}

def writeFile = { File base, String rel, String text ->
    def f = new File(base, rel)
    f.parentFile.mkdirs()
    // Explicit UTF-8 so the XML declaration <?xml … encoding="UTF-8"?> is accurate
    f.withWriter('UTF-8') { w -> w.write(text) }
}

def srcTree = { File base, String module, String pkgPath,
                List<String> mainSubs, List<String> testSubs, String description ->
    ensureDir(base, "${module}/src/main/java")
    mainSubs.each { sub ->
        def fullPkg = (pkgPath + '/' + sub).replace('/', '.')
        def dir = "${module}/src/main/java/${pkgPath}/${sub}"
        ensureDir(base, dir)
        writeFile(base, "${dir}/package-info.java", """\
/**
 * ${description}
 */
package ${fullPkg};
""")
    }
    ensureDir(base, "${module}/src/main/resources")
    ensureDir(base, "${module}/src/test/java")
    testSubs.each { sub ->
        def fullPkg = (pkgPath + '/' + sub).replace('/', '.')
        def dir = "${module}/src/test/java/${pkgPath}/${sub}"
        ensureDir(base, dir)
        writeFile(base, "${dir}/package-info.java", """\
/**
 * ${description}
 */
package ${fullPkg};
""")
    }
    ensureDir(base, "${module}/src/test/resources")
}

def failsafeSection = """\
  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-failsafe-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
"""

/** Builds a child-module pom.xml string (2-space indent). */
def modulePom = { String gId, String parentAId, String ver,
                   String aId, String desc, List<String> deps = [], String buildSection = '' ->
    def depsSection = ''
    if (deps) {
        def lines = deps.collect { dep ->
            """\
    <dependency>
      <groupId>${gId}</groupId>
      <artifactId>${dep}</artifactId>
    </dependency>"""
        }.join('\n')
        depsSection = "\n  <dependencies>\n${lines}\n  </dependencies>\n"
    }

    """\
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>${gId}</groupId>
    <artifactId>${parentAId}</artifactId>
    <version>${ver}</version>
    <relativePath>../parent/pom.xml</relativePath>
  </parent>

  <artifactId>${aId}</artifactId>
  <description>${desc}</description>
${depsSection}${buildSection}</project>
"""
}

// ─── Read Properties ──────────────────────────────────────────────────────────

def groupId    = request.groupId
def artifactId = request.artifactId
def version    = request.version
def props      = request.properties

def pkg     = props.getProperty('package') ?: groupId
def pkgPath = pkg.replace('.', '/')

def integrationsInput      = (props.getProperty('integrations')      ?: '').trim()
def serviceAreasInput      = (props.getProperty('serviceAreas')      ?: '').trim()
def presentationTypesInput = (props.getProperty('presentationTypes') ?: 'rest').trim()

// ─── Parse Integrations ───────────────────────────────────────────────────────
//   Input format: "type:name[,type:name,...]"   e.g. "database:users,rest:orders"

def integrations = []
if (integrationsInput) {
    integrations = integrationsInput.split(',').collectMany { entry ->
        def parts = entry.trim().split(':')
        if (parts.length < 2 || !parts[0].trim() || !parts[1].trim()) return []
        def rawType = parts[0].trim().toLowerCase()
        def name    = parts[1].trim().toLowerCase()
        [[rawType: rawType, abbrev: typeAbbrev(rawType), name: name]]
    }
}

// ─── Parse Service Areas ──────────────────────────────────────────────────────

def serviceModules
if (serviceAreasInput.isEmpty()) {
    serviceModules = ['service']
} else {
    serviceModules = serviceAreasInput.split(',')
        .collect { "service-${it.trim().toLowerCase()}" }
        .findAll { it.length() > 'service-'.length() }
    if (serviceModules.isEmpty()) serviceModules = ['service']
}

// ─── Parse Presentation Types ─────────────────────────────────────────────────
//   Input format: "type[,type,...]"   e.g. "rest,graphql"

def presentations = presentationTypesInput
    ? presentationTypesInput.split(',').collect { it.trim().toLowerCase() }.findAll { it }
    : []

// ─── Compute All Module Names ─────────────────────────────────────────────────

def intDomainMods  = integrations.collect  { "domain-${it.abbrev}-${it.name}" }
def intImplMods    = integrations.collect  { "integration-${it.abbrev}-${it.name}" }
def presDomainMods = presentations.collect { "domain-${it}" }
def presImplMods   = presentations.collect { "presentation-${it}" }

def allModules = (
    ['acceptance-tests', 'app', 'common-domain', 'common-testing'] +
    intDomainMods + intImplMods +
    serviceModules +
    presDomainMods + presImplMods
).sort()

// app depends on all non-test, non-self modules
def appDeps = allModules.findAll { it != 'acceptance-tests' && it != 'common-testing' && it != 'app' }

// acceptance-tests depends on app + all domain modules
def atDeps = (['app', 'common-domain'] + intDomainMods + presDomainMods).sort().unique()

// ─── Resolve Project Root ─────────────────────────────────────────────────────

def projectDir = new File(request.outputDirectory)
def subDir     = new File(projectDir, artifactId)
if (subDir.isDirectory()) projectDir = subDir

// ─── parent/pom.xml ───────────────────────────────────────────────────────────
// Modules listed with "../" relative path; child modules use relativePath back here.
// The placeholder pom.xml at the project root (generated by the archetype engine)
// is deleted so that "parent/" is the sole POM entry point for the project.

def modulesXml = allModules.collect { "    <module>../${it}</module>" }.join('\n')

def dmXml = allModules.collect { mod ->
    """\
      <dependency>
        <groupId>${groupId}</groupId>
        <artifactId>${mod}</artifactId>
        <version>\${project.version}</version>
      </dependency>"""
}.join('\n')

def parentPom = """\
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>${groupId}</groupId>
  <artifactId>${artifactId}</artifactId>
  <version>${version}</version>
  <packaging>pom</packaging>

  <name>${artifactId}</name>

  <modules>
${modulesXml}
  </modules>

  <properties>
    <!-- CONFIG -->
    <java.version>21</java.version>
    <maven.compiler.release>\${java.version}</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>

    <!-- VERSIONS -->
    <maven-compiler-plugin.version>3.13.0</maven-compiler-plugin.version>
    <maven-failsafe-plugin.version>3.5.5</maven-failsafe-plugin.version>
    <maven-jar-plugin.version>3.4.2</maven-jar-plugin.version>
    <maven-surefire-plugin.version>3.5.5</maven-surefire-plugin.version>
  </properties>

  <dependencyManagement>
    <dependencies>
${dmXml}
    </dependencies>
  </dependencyManagement>

  <build>
    <pluginManagement>
      <plugins>
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-compiler-plugin</artifactId>
          <version>\${maven-compiler-plugin.version}</version>
        </plugin>
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-failsafe-plugin</artifactId>
          <version>\${maven-failsafe-plugin.version}</version>
          <configuration>
            <redirectTestOutputToFile>true</redirectTestOutputToFile>
            <!-- alphabetical on even hours, reverse alphabetical on odd hours -->
            <runOrder>hourly</runOrder>
            <!-- limit heap for any forked test processes -->
            <argLine>-XX:MaxRAMPercentage=5.0</argLine>
          </configuration>
          <executions>
            <execution>
              <goals>
                <goal>integration-test</goal>
                <goal>verify</goal>
              </goals>
            </execution>
          </executions>
        </plugin>
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-jar-plugin</artifactId>
          <version>\${maven-jar-plugin.version}</version>
          <configuration>
            <skipIfEmpty>true</skipIfEmpty>
          </configuration>
        </plugin>
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-surefire-plugin</artifactId>
          <version>\${maven-surefire-plugin.version}</version>
          <configuration>
            <redirectTestOutputToFile>true</redirectTestOutputToFile>
            <!-- alphabetical on even hours, reverse alphabetical on odd hours -->
            <runOrder>hourly</runOrder>
            <!-- limit heap for any forked test processes -->
            <argLine>-XX:MaxRAMPercentage=5.0</argLine>
          </configuration>
        </plugin>
      </plugins>
    </pluginManagement>
  </build>
</project>
"""

writeFile(projectDir, 'parent/pom.xml', parentPom)

// Remove the placeholder pom.xml that the archetype engine wrote at the project root
new File(projectDir, 'pom.xml').delete()

// ─── common-domain ────────────────────────────────────────────────────────────

writeFile(projectDir, 'common-domain/pom.xml',
    modulePom(groupId, artifactId, version,
        'common-domain',
        'Domain classes common to all modules'))
srcTree(projectDir, 'common-domain', pkgPath,
    ['common/domain'], ['common/domain'],
    'Domain classes common to all modules.')

// ─── common-testing ───────────────────────────────────────────────────────────

writeFile(projectDir, 'common-testing/pom.xml',
    modulePom(groupId, artifactId, version,
        'common-testing',
        'Testing utilities common to all test modules',
        ['common-domain']))
srcTree(projectDir, 'common-testing', pkgPath,
    ['common/testing'], [],
    'Test utilities common to all modules.')

// ─── Integration: domain-{type}-{name} and integration-{type}-{name} ──────────

integrations.each { intg ->
    def domMod  = "domain-${intg.abbrev}-${intg.name}"
    def implMod = "integration-${intg.abbrev}-${intg.name}"

    writeFile(projectDir, "${domMod}/pom.xml",
        modulePom(groupId, artifactId, version,
            domMod,
            "Domain classes for ${intg.rawType} data source: ${intg.name}",
            ['common-domain']))
    srcTree(projectDir, domMod, pkgPath,
        ["domain/${intg.abbrev}/${intg.name}"],
        ["domain/${intg.abbrev}/${intg.name}"],
        "Domain classes for the ${intg.name} ${intg.rawType} integration.")

    writeFile(projectDir, "${implMod}/pom.xml",
        modulePom(groupId, artifactId, version,
            implMod,
            "Integration classes (DAOs, repositories) for ${intg.rawType}: ${intg.name}",
            ['common-domain', domMod],
            failsafeSection))
    srcTree(projectDir, implMod, pkgPath,
        ["integration/${intg.abbrev}/${intg.name}"],
        ["integration/${intg.abbrev}/${intg.name}"],
        "Integration classes for the ${intg.name} ${intg.rawType} data source.")
}

// ─── Service modules ──────────────────────────────────────────────────────────

serviceModules.each { svcMod ->
    def label  = svcMod == 'service' ? 'primary' : svcMod.replaceFirst('service-', '')
    def svcPkg = svcMod == 'service' ? 'service'
                                      : "service/${svcMod.replaceFirst('service-', '')}"

    writeFile(projectDir, "${svcMod}/pom.xml",
        modulePom(groupId, artifactId, version,
            svcMod,
            "Business logic for the ${label} service area",
            ['common-domain']))
    def svcDesc = svcMod == 'service' ? 'Business logic for the application.'
                                      : "Business logic for the ${label} service area."
    srcTree(projectDir, svcMod, pkgPath, [svcPkg], [svcPkg], svcDesc)
}

// ─── Presentation: domain-{type} and presentation-{type} ──────────────────────

presentations.each { pType ->
    def domMod  = "domain-${pType}"
    def implMod = "presentation-${pType}"

    writeFile(projectDir, "${domMod}/pom.xml",
        modulePom(groupId, artifactId, version,
            domMod,
            "Domain classes for ${pType} presentation tier",
            ['common-domain']))
    srcTree(projectDir, domMod, pkgPath,
        ["domain/${pType}"], ["domain/${pType}"],
        "Domain classes for the ${pType} presentation tier.")

    writeFile(projectDir, "${implMod}/pom.xml",
        modulePom(groupId, artifactId, version,
            implMod,
            "Request handling classes (controllers) for ${pType}",
            ['common-domain', domMod]))
    srcTree(projectDir, implMod, pkgPath,
        ["presentation/${pType}"], ["presentation/${pType}"],
        "Request-handling classes for the ${pType} presentation tier.")
}

// ─── app ─────────────────────────────────────────────────────────────────────

writeFile(projectDir, 'app/pom.xml',
    modulePom(groupId, artifactId, version,
        'app',
        'Application assembly — produces the runnable artifact',
        appDeps))
srcTree(projectDir, 'app', pkgPath, ['app'], ['app'],
    'Application assembly and entry point.')

// ─── acceptance-tests ─────────────────────────────────────────────────────────

writeFile(projectDir, 'acceptance-tests/pom.xml',
    modulePom(groupId, artifactId, version,
        'acceptance-tests',
        'Functional acceptance tests for the application',
        atDeps,
        failsafeSection))
srcTree(projectDir, 'acceptance-tests', pkgPath, [], ['at'],
    'Functional acceptance tests for the application.')
