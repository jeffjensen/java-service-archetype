import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

// ─── Helpers ──────────────────────────────────────────────────────────────────

/** Returns the abbreviation used in module/package names for the given integration type. */
def typeAbbrev = { String t ->
    t.trim().toLowerCase() == 'database' ? 'db' : t.trim().toLowerCase()
}

/** Returns the human-readable display name used in prose (Javadoc, pom descriptions) for a type. */
def typeDisplay = { String t ->
    def s = t.trim().toLowerCase()
    s == 'graphql' ? 'GraphQL' : s
}

/** Returns the identifier-cased display name used in appName-derived <name>/<description> pom
 *  values for a type: the conventional acronym casing for known types (REST, GraphQL), or the
 *  type capitalized otherwise. */
def typeDisplayName = { String t ->
    def s = t.trim().toLowerCase()
    if (s == 'rest') return 'REST'
    if (s == 'graphql') return 'GraphQL'
    s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1)
}

/** Renders appName for use in a class name: spaces removed, e.g. "Order Service" -> "OrderService". */
def appNameClass = { String s -> s.replace(' ', '') }

/** Renders appName for use in a generated property value: spaces removed and lowercased,
 *  e.g. "Order Service" -> "orderservice". */
def appNameProperty = { String s -> s.replace(' ', '').toLowerCase() }

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

// Java rejects two package-info.java files compiled for the same package, which a src/main/java +
// src/test/java pair would be if both carried one. Only src/main/java gets a package-info.java;
// src/test/java packages are still scaffolded (empty) for test classes to land in.
def srcTree = { File base, String module, String pkgPath,
                List<String> subs, String description ->
    ensureDir(base, "${module}/src/main/java")
    subs.each { sub ->
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
    subs.each { sub -> ensureDir(base, "${module}/src/test/java/${pkgPath}/${sub}") }
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

/** Builds a child-module pom.xml string (2-space indent). `prefix` is the project artifactId;
 *  every module's artifactId is rendered as "{prefix}-{aId}" and the inherited parent as
 *  "{prefix}-parent".
 *  A dependency entry is a String (an internal module's bare name, rendered as "{prefix}-{name}"
 *  with this project's groupId and BOM-managed version), or a Map: `[module:…, label:…]` is an
 *  internal module reference like the String form but with a `label`, while
 *  `[groupId:…, artifactId:…, scope:…, label:…, exclusions:[[groupId:…, artifactId:…], …]]` is an
 *  external dependency. `label` renders as a "<!-- label -->" comment above the first entry of
 *  each contiguous run sharing that label (e.g. several external deps grouped under "spring").
 *  Dependencies are grouped into a "<!-- TEST -->" section (scope 'test', emitted first) and a
 *  "<!-- PROD -->" section (everything else); both headers are always present.
 *  `name` is optional and renders a `<name>` element before `<description>` when given. */
def modulePom = { String gId, String prefix, String ver,
                   String aId, String desc, List deps = [], String buildSection = '', String name = null ->
    def depsSection = ''
    if (deps) {
        def renderDep = { dep ->
            def isModuleRef = dep instanceof Map && dep.module
            def dGroup    = (dep instanceof Map && !isModuleRef) ? dep.groupId : gId
            def dArtifact = isModuleRef ? "${prefix}-${dep.module}" : ((dep instanceof Map) ? dep.artifactId : "${prefix}-${dep}")
            def dScope = (dep instanceof Map && dep.scope) ? "\n      <scope>${dep.scope}</scope>" : ''
            def dExclusions = ''
            if (dep instanceof Map && dep.exclusions) {
                def exLines = dep.exclusions.collect { ex ->
                    """\
        <exclusion>
          <groupId>${ex.groupId}</groupId>
          <artifactId>${ex.artifactId}</artifactId>
        </exclusion>"""
                }.join('\n')
                dExclusions = "\n      <exclusions>\n${exLines}\n      </exclusions>"
            }
            """\
    <dependency>
      <groupId>${dGroup}</groupId>
      <artifactId>${dArtifact}</artifactId>${dScope}${dExclusions}
    </dependency>"""
        }
        def renderGroup = { List groupDeps ->
            def lines = []
            def prevLabel = null
            groupDeps.each { dep ->
                def label = (dep instanceof Map) ? dep.label : null
                if (label && label != prevLabel) lines << "    <!-- ${label} -->"
                lines << renderDep(dep)
                prevLabel = label
            }
            lines.join('\n')
        }
        def isTest = { it instanceof Map && it.scope == 'test' }
        def testLines = renderGroup(deps.findAll(isTest))
        def prodLines = renderGroup(deps.findAll { !isTest(it) })
        def section = ['    <!-- TEST -->']
        if (testLines) section << testLines
        section << ''
        section << '    <!-- PROD -->'
        if (prodLines) section << prodLines
        depsSection = "\n  <dependencies>\n${section.join('\n')}\n  </dependencies>\n"
    }

    // #3: a blank line separates <dependencies> from <build> when both are present.
    def buildPart = buildSection ? "\n${buildSection}" : ''
    def nameElement = name ? "\n  <name>${name}</name>" : ''

    """\
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>${gId}</groupId>
    <artifactId>${prefix}-parent</artifactId>
    <version>${ver}</version>
    <relativePath>../parent/pom.xml</relativePath>
  </parent>

  <artifactId>${prefix}-${aId}</artifactId>${nameElement}
  <description>${desc}</description>
${depsSection}${buildPart}</project>
"""
}

// ─── Read Properties ──────────────────────────────────────────────────────────

def groupId    = request.groupId
def artifactId = request.artifactId
def version    = request.version
def props      = request.properties

def pkg     = props.getProperty('package') ?: groupId
def pkgPath = pkg.replace('.', '/')

def appName = (props.getProperty('appName') ?: '').trim()

def integrationsInput      = (props.getProperty('integrations')      ?: '').trim()
def serviceAreasInput      = (props.getProperty('serviceAreas')      ?: '').trim()
def presentationTypesInput = (props.getProperty('presentationTypes') ?: 'rest').trim()
def includeSpring          = ((props.getProperty('includeSpring') ?: 'true').trim().toLowerCase() != 'false')

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
def svcDomainMods  = serviceModules.collect { "domain-${it}" }
def presDomainMods = presentations.collect { "domain-${it}" }
def presImplMods   = presentations.collect { "presentation-${it}" }

def allModules = (
    ['acceptance-tests', 'app', 'common-domain', 'common-testing'] +
    intDomainMods + intImplMods +
    serviceModules + svcDomainMods +
    presDomainMods + presImplMods
).sort()

// app depends on all non-test, non-self modules
def appDeps = allModules.findAll { it != 'acceptance-tests' && it != 'common-testing' && it != 'app' }

// acceptance-tests depends on app + all domain modules (including each service area's domain
// module); all of them are internal modules, labeled "this app" in the generated pom.
def atDeps = (['app', 'common-domain'] + intDomainMods + svcDomainMods + presDomainMods).sort().unique()
    .collect { [module: it, label: 'this app'] }

// Composition flags, needed both early (common-testing, integration modules) and late (app,
// acceptance-tests) in generation.
def hasDatabaseIntegration = integrations.any { it.rawType == 'database' }
def hasRestPresentation    = presentations.contains('rest')
def hasGraphqlPresentation = presentations.contains('graphql')

// ─── Resolve Project Root ─────────────────────────────────────────────────────

def projectDir = new File(request.outputDirectory)
def subDir     = new File(projectDir, artifactId)
if (subDir.isDirectory()) projectDir = subDir

// ─── Spring dependency selection ──────────────────────────────────────────────
// Each composed module declares the most specific Spring Boot starter that is useful
// for its role rather than a blanket low-level dependency. common-domain (which every
// other module depends on) carries the core spring-boot-starter, so the "plain" modules
// — domain-* and the service modules — inherit the @Configuration / @ComponentScan API
// transitively and declare no Spring dependency of their own. Presentation (server-side)
// and integration (client-side) modules add a role-specific starter on top: a 'rest'
// presentation serves endpoints via spring-boot-starter-web, while a 'rest' integration
// consumes an external API via spring-boot-starter-restclient (no servlet web stack). All
// gated on includeSpring; with Spring off no module references Spring at all.

def STARTER              = [groupId: 'org.springframework.boot', artifactId: 'spring-boot-starter']
def STARTER_WEB          = [groupId: 'org.springframework.boot', artifactId: 'spring-boot-starter-web']
def STARTER_WEBMVC       = [groupId: 'org.springframework.boot', artifactId: 'spring-boot-starter-webmvc']
def STARTER_ACTUATOR     = [groupId: 'org.springframework.boot', artifactId: 'spring-boot-starter-actuator']
def STARTER_DATA_JPA     = [groupId: 'org.springframework.boot', artifactId: 'spring-boot-starter-data-jpa']
def STARTER_GRAPHQL      = [groupId: 'org.springframework.boot', artifactId: 'spring-boot-starter-graphql']
def STARTER_GRAPHQL_TEST = [groupId: 'org.springframework.boot', artifactId: 'spring-boot-starter-graphql-test']
def STARTER_RESTCLIENT   = [groupId: 'org.springframework.boot', artifactId: 'spring-boot-starter-restclient']
def STARTER_VALIDATION   = [groupId: 'org.springframework.boot', artifactId: 'spring-boot-starter-validation']
def SPRING_BOOT_ADMIN_CLIENT = [groupId: 'de.codecentric', artifactId: 'spring-boot-admin-starter-client']
def DATASOURCE_PROXY_STARTER = [groupId: 'com.github.gavlyukovskiy', artifactId: 'datasource-proxy-spring-boot-starter']

/** Role-specific starter for an integration (client-side) module, or null when none fits
 *  (those modules rely on the core starter inherited transitively via common-domain). */
def integrationStarter = { String type ->
    switch (type?.trim()?.toLowerCase()) {
        case 'database': return STARTER_DATA_JPA
        case 'rest':     return STARTER_RESTCLIENT   // consumes an external REST API; no servlet web stack
        case 'graphql':  return STARTER_GRAPHQL
        default:         return null
    }
}

/** Role-specific starter for a presentation (server-side) module, or null when none fits. */
def presentationStarter = { String type ->
    switch (type?.trim()?.toLowerCase()) {
        case 'rest':     return STARTER_WEB          // serves REST endpoints over servlet MVC
        case 'graphql':  return STARTER_GRAPHQL
        default:         return null
    }
}

/** Wraps a resolved starter into a module deps list: a one-element list when Spring is on
 *  and a starter was resolved, otherwise empty. */
def starterDeps = { starter ->
    (includeSpring && starter) ? [starter] : []
}

// common-domain's own Spring dependency: the core starter, which provides the
// @Configuration API to every dependent module transitively (empty when Spring is off).
def commonSpring = includeSpring ? [STARTER] : []

// ─── Spring configuration class generator ─────────────────────────────────────
// When includeSpring is true, every module the app composes carries a @Configuration
// class in its `.config` sub-package (the app's Application class @Imports them all).
// The module root package also gets a marker "{Module}ComponentScan" interface that the
// @Configuration targets via @ComponentScan(basePackageClasses=…) — a type-safe anchor
// for scanning the whole module. Skipped entirely when includeSpring is false.

def configFqns = []

/** Writes a {Module}ComponentScan marker interface into the module root package and a
 *  @Configuration class (anchored to it via @ComponentScan) into {module}/…/{mainSub}/config,
 *  recording the config FQN; no-op when Spring is off. */
def configClass = { String module, String mainSub ->
    if (!includeSpring) return null
    def baseName  = module.split('-').collect { it.capitalize() }.join('')
    def className = baseName + 'Configuration'
    def scanName  = baseName + 'ComponentScan'

    // Marker interface in the module root package — the @ComponentScan basePackageClasses anchor.
    def rootPkgFq = (pkgPath + '/' + mainSub).replace('/', '.')
    writeFile(projectDir, "${module}/src/main/java/${pkgPath}/${mainSub}/${scanName}.java", """\
package ${rootPkgFq};

/**
 * Anchor class for basePackageClasses component scanning.
 */
public interface ${scanName} {
}
""")

    def pkgFq = (pkgPath + '/' + mainSub + '/config').replace('/', '.')
    def dir = "${module}/src/main/java/${pkgPath}/${mainSub}/config"
    def imports = [
        "import ${rootPkgFq}.${scanName};",
        'import org.springframework.context.annotation.ComponentScan;',
        'import org.springframework.context.annotation.Configuration;',
    ].sort().join('\n')
    writeFile(projectDir, "${dir}/${className}.java", """\
package ${pkgFq};

${imports}

/**
 * Spring configuration for the ${module} module.
 */
@Configuration
@ComponentScan(basePackageClasses = ${scanName}.class)
public class ${className} {
}
""")
    def fqn = "${pkgFq}.${className}".toString()
    configFqns << fqn
    return fqn
}

// ─── parent/pom.xml ───────────────────────────────────────────────────────────
// Modules listed with "../" relative path; child modules use relativePath back here.
// The placeholder pom.xml at the project root (generated by the archetype engine)
// is deleted so that "parent/" is the sole POM entry point for the project.

def modulesXml = allModules.collect { "    <module>../${it}</module>" }.join('\n')

// #5: acceptance-tests and common-testing are test-only artifacts — managed in the TEST section
// with <scope>test</scope>; every other module is managed (default/compile) in the PROD section.
def DM_TEST_MODULES = ['acceptance-tests', 'common-testing']
def dmEntry = { String mod ->
    def scopeLine = (mod in DM_TEST_MODULES) ? '\n        <scope>test</scope>' : ''
    """\
      <dependency>
        <groupId>${groupId}</groupId>
        <artifactId>${artifactId}-${mod}</artifactId>
        <version>\${project.version}</version>${scopeLine}
      </dependency>"""
}
def dmTestXml = allModules.findAll { it in DM_TEST_MODULES }.collect(dmEntry).join('\n')
def dmProdXml = allModules.findAll { !(it in DM_TEST_MODULES) }.collect(dmEntry).join('\n')

// ─── Parent-pom dependency blocks (instructions 4–6) ──────────────────────────
// All gated on includeSpring so a Spring-off project carries no Spring artifacts.
//   #4 dependencyManagement PROD: manage spring-core, excluding commons-logging.
//   #5 dependencies TEST: spring-boot-starter-test (test scope, all submodules), no commons-logging.
//   #6 dependencies PROD: spring-boot-starter-logging + jspecify (compile scope, all submodules).
def springCoreDm = !includeSpring ? '' : '\n' + [
    '      <dependency>',
    '        <groupId>org.springframework</groupId>',
    '        <artifactId>spring-core</artifactId>',
    '        <exclusions>',
    '          <exclusion>',
    '            <groupId>commons-logging</groupId>',
    '            <artifactId>commons-logging</artifactId>',
    '          </exclusion>',
    '        </exclusions>',
    '      </dependency>',
].join('\n')

// datasource-proxy-spring-boot-starter and spring-boot-admin-starter-client aren't part of the
// Spring Boot BOM (third-party groupIds), so — like spring-core above — they need their own
// dependencyManagement entry with an explicit version, pinned via a property.
def datasourceProxyDm = !includeSpring ? '' : '\n' + [
    '      <dependency>',
    '        <groupId>com.github.gavlyukovskiy</groupId>',
    '        <artifactId>datasource-proxy-spring-boot-starter</artifactId>',
    '        <version>${datasource-proxy-spring-boot-starter.version}</version>',
    '      </dependency>',
].join('\n')

def springBootAdminDm = !includeSpring ? '' : '\n' + [
    '      <dependency>',
    '        <groupId>de.codecentric</groupId>',
    '        <artifactId>spring-boot-admin-starter-client</artifactId>',
    '        <version>${spring-boot-admin-starter-client.version}</version>',
    '      </dependency>',
].join('\n')

def datasourceProxyVersionProp = includeSpring ? '    <datasource-proxy-spring-boot-starter.version>2.0.1</datasource-proxy-spring-boot-starter.version>\n' : ''
def springBootAdminVersionProp = includeSpring ? '    <spring-boot-admin-starter-client.version>4.1.1</spring-boot-admin-starter-client.version>\n' : ''

def parentDependencies = !includeSpring ? '' : '\n' + [
    '  <dependencies>',
    '    <!-- TEST -->',
    '    <!-- spring -->',
    '    <dependency>',
    '      <groupId>org.springframework.boot</groupId>',
    '      <artifactId>spring-boot-starter-test</artifactId>',
    '      <scope>test</scope>',
    '      <exclusions>',
    '        <exclusion>',
    '          <groupId>commons-logging</groupId>',
    '          <artifactId>commons-logging</artifactId>',
    '        </exclusion>',
    '      </exclusions>',
    '    </dependency>',
    '',
    '    <!-- PROD -->',
    '    <dependency>',
    '      <groupId>org.springframework.boot</groupId>',
    '      <artifactId>spring-boot-starter-logging</artifactId>',
    '    </dependency>',
    '    <dependency>',
    '      <groupId>org.jspecify</groupId>',
    '      <artifactId>jspecify</artifactId>',
    '    </dependency>',
    '  </dependencies>',
].join('\n') + '\n'

def parentInherit = includeSpring ? """\
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.1.0</version>
    <relativePath/>
  </parent>

""" : ''

def parentPom = """\
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

${parentInherit}  <groupId>${groupId}</groupId>
  <artifactId>${artifactId}-parent</artifactId>
  <version>${version}</version>
  <packaging>pom</packaging>

  <name>${appName} Parent</name>

  <description>${appName}'s parent POM.</description>

  <modules>
${modulesXml}
  </modules>

  <properties>
    <!-- CONFIG -->
    <java.version>21</java.version>
    <maven.compiler.release>\${java.version}</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>

    <!-- VERSIONS -->
${datasourceProxyVersionProp}    <maven-compiler-plugin.version>3.13.0</maven-compiler-plugin.version>
    <maven-failsafe-plugin.version>3.5.5</maven-failsafe-plugin.version>
    <maven-jar-plugin.version>3.4.2</maven-jar-plugin.version>
    <maven-surefire-plugin.version>3.5.5</maven-surefire-plugin.version>
    <modernizer-maven-plugin.version>3.4.0</modernizer-maven-plugin.version>
${springBootAdminVersionProp}    <versions-maven-plugin.version>2.18.0</versions-maven-plugin.version>
  </properties>

  <dependencyManagement>
    <dependencies>
      <!-- TEST -->
${dmTestXml}

      <!-- PROD -->
${dmProdXml}${springCoreDm}${datasourceProxyDm}${springBootAdminDm}
    </dependencies>
  </dependencyManagement>
${parentDependencies}
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
        <plugin>
          <groupId>org.gaul</groupId>
          <artifactId>modernizer-maven-plugin</artifactId>
          <version>\${modernizer-maven-plugin.version}</version>
          <configuration>
            <javaVersion>\${java.version}</javaVersion>
          </configuration>
          <executions>
            <execution>
              <id>modernizer</id>
              <phase>verify</phase>
              <goals>
                <goal>modernizer</goal>
              </goals>
            </execution>
          </executions>
        </plugin>
        <plugin>
          <groupId>org.codehaus.mojo</groupId>
          <artifactId>versions-maven-plugin</artifactId>
          <version>\${versions-maven-plugin.version}</version>
          <configuration>
            <ruleSet>
              <ignoreVersions>
                <ignoreVersion>
                  <type>regex</type>
                  <version>(?i).*-(alpha|beta|m|rc)([-.]?\\d+)?</version>
                </ignoreVersion>
              </ignoreVersions>
              <rules>
                <rule>
                  <groupId>com.graphql-java</groupId>
                  <artifactId>graphql-java-extended-scalars</artifactId>
                  <ignoreVersions>
                    <!-- ignore old versions starting with 2018, 2019, 2020, 2021, 2022, or 2023 that
                    semantically are newer than current version scheme -->
                    <ignoreVersion>
                      <type>regex</type>
                      <version>^20(1[8-9]|2[0-3]).*</version>
                    </ignoreVersion>
                  </ignoreVersions>
                </rule>
              </rules>
            </ruleSet>
          </configuration>
        </plugin>
      </plugins>
    </pluginManagement>
    <plugins>
      <plugin>
        <groupId>org.gaul</groupId>
        <artifactId>modernizer-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
"""

writeFile(projectDir, 'parent/pom.xml', parentPom)

// Remove the placeholder pom.xml that the archetype engine wrote at the project root
new File(projectDir, 'pom.xml').delete()

// ─── Version-update helper scripts ────────────────────────────────────────────
// Convenience wrappers around `mvn versions:update-properties` so all property
// versions can be bumped to their latest in one command. Written into parent/
// (alongside parent/pom.xml, where the version properties live) for both Linux
// (LF, +x) and Windows (CRLF). Built from explicit line lists so the line
// endings are correct regardless of this script's own endings.

writeFile(projectDir, 'parent/mvn-update-properties-versions.sh', [
    '#!/bin/bash',
    '# Updates property versions to the latest found',
    'FILE=target/version-updates-applied.log',
    'mkdir -p target',
    'rm -f $FILE',
    'echo "Writing output to file $FILE"',
    'echo "Applying version updates specified as properties..."',
    'echo "======================================== PROPERTIES" >> $FILE',
    'mvn versions:update-properties >> $FILE',
    '',
].join('\n'))

writeFile(projectDir, 'parent/mvn-update-properties-versions.ps1', [
    '# Updates property versions to the latest found',
    '$FILE = "target/version-updates-applied.log"',
    'New-Item -ItemType Directory -Force -Path "target" | Out-Null',
    'if (Test-Path $FILE) { Remove-Item -Force $FILE }',
    'Write-Host "Writing output to file $FILE"',
    'Write-Host "Applying version updates specified as properties..."',
    '"======================================== PROPERTIES" | Out-File -FilePath $FILE -Append -Encoding utf8',
    '& mvn versions:update-properties | Out-File -FilePath $FILE -Append -Encoding utf8',
    '',
].join('\r\n'))

// Set the exec bit on the Linux script. POSIX filesystems only; on Windows
// (non-POSIX) this throws UnsupportedOperationException and we fall back to the
// best-effort Java executable flag (a no-op on Windows, but harmless).
def shFile = new File(projectDir, 'parent/mvn-update-properties-versions.sh')
try {
    Files.setPosixFilePermissions(shFile.toPath(),
        PosixFilePermissions.fromString('rwxr-xr-x'))
} catch (UnsupportedOperationException ignored) {
    shFile.setExecutable(true, false)
}

// ─── common-domain ────────────────────────────────────────────────────────────

writeFile(projectDir, 'common-domain/pom.xml',
    modulePom(groupId, artifactId, version,
        'common-domain',
        'Domain classes common to all modules.',
        commonSpring))
srcTree(projectDir, 'common-domain', pkgPath,
    ['common/domain'],
    'Domain classes common to all modules.')
configClass('common-domain', 'common/domain')

// ─── common-testing ───────────────────────────────────────────────────────────

// common-testing carries spring-boot-starter-test at compile scope (it is test-support code that
// other modules compile their tests against), overriding the test-scoped copy every module inherits
// from the parent (#5). No commons-logging exclusion is needed here — the parent already excludes it.
// It also depends on every domain module. No domain module depends back on common-testing (only
// service/domain-service's *business-logic* sibling, service/service-{name}, does), so this stays
// acyclic.
def allDomainMods = (intDomainMods + svcDomainMods + presDomainMods).sort()
def commonTestingDeps = ['common-domain'] + allDomainMods
if (includeSpring) {
    commonTestingDeps << [groupId: 'org.springframework.boot', artifactId: 'spring-boot-starter-test',
                          scope: 'compile']
    // Needed at compile scope for GraphQlAcceptanceTestBase (below) to reference
    // @AutoConfigureHttpGraphQlTester / HttpGraphQlTester.
    if (hasGraphqlPresentation) commonTestingDeps << (STARTER_GRAPHQL_TEST + [scope: 'compile'])
}
writeFile(projectDir, 'common-testing/pom.xml',
    modulePom(groupId, artifactId, version,
        'common-testing',
        "${appName}'s common testing.",
        commonTestingDeps,
        '',
        "${appName} Common Testing"))
srcTree(projectDir, 'common-testing', pkgPath,
    ['common/testing'],
    'Test infrastructure common to all modules.')

// Shared acceptance-test base classes: exercise the application on a real running server (random
// port), one per presentation type actually generated. AppRestAcceptanceTestBase /
// AppGraphQlAcceptanceTestBase (generated in acceptance-tests) extend these directly.
if (includeSpring && hasRestPresentation) {
writeFile(projectDir, "common-testing/src/main/java/${pkgPath}/common/testing/RestAcceptanceTestBase.java", """\
package ${pkg}.common.testing;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Base test class for REST acceptance tests, exercising the application on a real running server.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class RestAcceptanceTestBase {

    @LocalServerPort
    private int port;

    /**
     * Returns a REST test client bound to this test's running server instance.
     *
     * @return a REST test client for issuing requests against the running application.
     */
    protected RestTestClient restTestClient() {
        return RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }
}
""")
}
if (includeSpring && hasGraphqlPresentation) {
writeFile(projectDir, "common-testing/src/main/java/${pkgPath}/common/testing/GraphQlAcceptanceTestBase.java", """\
package ${pkg}.common.testing;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.HttpGraphQlTester;

/**
 * Base test class for GraphQL acceptance tests, exercising the application on a real running server.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureHttpGraphQlTester
public abstract class GraphQlAcceptanceTestBase {

    @Autowired
    protected HttpGraphQlTester graphQlTester;
}
""")
}

// ─── Integration: domain-{type}-{name} and integration-{type}-{name} ──────────

integrations.each { intg ->
    def domMod  = "domain-${intg.abbrev}-${intg.name}"
    def implMod = "integration-${intg.abbrev}-${intg.name}"
    def disp    = typeDisplay(intg.rawType)
    def domSub  = "domain/${intg.abbrev}/${intg.name}"
    def implSub = "integration/${intg.abbrev}/${intg.name}"

    writeFile(projectDir, "${domMod}/pom.xml",
        modulePom(groupId, artifactId, version,
            domMod,
            "Domain classes for the ${intg.name} ${disp} data source.",
            ['common-domain']))
    srcTree(projectDir, domMod, pkgPath,
        [domSub],
        "Domain classes for the ${intg.name} ${disp} integration.")
    configClass(domMod, domSub)

    // SQL logging via datasource-proxy applies only to database integrations.
    def sqlLoggingDep = (includeSpring && intg.rawType == 'database') ? [DATASOURCE_PROXY_STARTER + [label: 'sql logging']] : []
    writeFile(projectDir, "${implMod}/pom.xml",
        modulePom(groupId, artifactId, version,
            implMod,
            "Integration classes (DAOs, repositories) for the ${intg.name} ${disp} data source.",
            ['common-domain', domMod] + starterDeps(integrationStarter(intg.rawType)) + sqlLoggingDep,
            failsafeSection))
    srcTree(projectDir, implMod, pkgPath,
        [implSub],
        "Integration classes for the ${intg.name} ${disp} data source.")
    configClass(implMod, implSub)
}

// ─── Service modules ──────────────────────────────────────────────────────────

serviceModules.each { svcMod ->
    def label  = svcMod == 'service' ? 'primary' : svcMod.replaceFirst('service-', '')
    def svcPkg = svcMod == 'service' ? 'service'
                                      : "service/${label}"
    def domMod = "domain-${svcMod}"
    def domPkg = "domain/${svcPkg}"

    def svcPackageDesc = svcMod == 'service' ? 'Business logic for the application.'
                                      : "Business logic for the ${label} service area."
    // #4: domain-service modules describe the service tier (plus the area name when named).
    def domPackageDesc = svcMod == 'service' ? 'Domain classes for the service tier.'
                                      : "Domain classes for the ${label} service tier."

    // Named service areas fold the area name into the appName-derived pom name/description: mid-
    // sentence for prose ("...'s orders service tier..."), trailing for the <name> identifier
    // ("...Service Orders"), matching the service-{name}/domain-service-{name} directory convention.
    def areaInfix  = svcMod == 'service' ? '' : " ${label}"
    def areaSuffix = svcMod == 'service' ? '' : " ${label.capitalize()}"

    // Domain classes owned by this service area. No domain module depends on common-testing.
    def domDeps = ['common-domain']
    if (includeSpring) domDeps << (STARTER_VALIDATION + [label: 'spring'])
    writeFile(projectDir, "${domMod}/pom.xml",
        modulePom(groupId, artifactId, version,
            domMod, "${appName}'s${areaInfix} service tier domain classes.",
            domDeps, '', "${appName} Domain Service${areaSuffix}"))
    srcTree(projectDir, domMod, pkgPath, [domPkg], domPackageDesc)
    configClass(domMod, domPkg)

    // Business logic, depending on its own service-area domain module and (#2) the integration
    // module(s) it consumes: a named service area takes the integration sharing its name, while the
    // single default 'service' module takes every integration module.
    def svcIntegrations = svcMod == 'service'
        ? intImplMods
        : integrations.findAll { it.name == label }.collect { "integration-${it.abbrev}-${it.name}" }
    def svcDeps = ['common-domain', domMod] + svcIntegrations
    if (includeSpring) svcDeps << [module: 'common-testing', scope: 'test']
    writeFile(projectDir, "${svcMod}/pom.xml",
        modulePom(groupId, artifactId, version,
            svcMod, "${appName}'s${areaInfix} service tier (business logic).",
            svcDeps, '', "${appName} Service${areaSuffix}"))
    srcTree(projectDir, svcMod, pkgPath, [svcPkg], svcPackageDesc)
    configClass(svcMod, svcPkg)
}

// ─── Presentation: domain-{type} and presentation-{type} ──────────────────────

presentations.each { pType ->
    def domMod   = "domain-${pType}"
    def implMod  = "presentation-${pType}"
    def disp     = typeDisplay(pType)
    def dispName = typeDisplayName(pType)

    // Presentation-tier domain classes (DTOs, etc.) map to/from the service-tier domain objects,
    // hence the dependency on every domain-service* module ("the service domain deps").
    def domDeps = ['common-domain'] + svcDomainMods
    if (includeSpring) domDeps << (STARTER_VALIDATION + [label: 'spring'])
    writeFile(projectDir, "${domMod}/pom.xml",
        modulePom(groupId, artifactId, version,
            domMod,
            "${appName}'s ${dispName} presentation tier domain classes.",
            domDeps, '', "${appName} Domain ${dispName}"))
    srcTree(projectDir, domMod, pkgPath,
        ["domain/${pType}"],
        "Domain classes for the ${disp} presentation tier.")
    configClass(domMod, "domain/${pType}")

    // #7: GraphQL presentation classes are resolvers, not request-handling controllers.
    def implDesc = pType == 'graphql'
        ? "Resolver classes for the ${disp} presentation tier."
        : "Request-handling classes (controllers) for the ${disp} presentation tier."
    def implPkgDesc = pType == 'graphql'
        ? "Resolver classes for the ${disp} presentation tier."
        : "Request-handling classes for the ${disp} presentation tier."
    // presentation-graphql carries an appName-derived pom name/description; other presentation
    // types (no dedicated doc requirement yet) keep the generic role-based description.
    def implPomName = pType == 'graphql' ? "${appName} Presentation GraphQL" : null
    def implPomDesc = pType == 'graphql' ? "${appName}'s GraphQL presentation tier." : implDesc
    // #1: every presentation module depends on all service modules.
    writeFile(projectDir, "${implMod}/pom.xml",
        modulePom(groupId, artifactId, version,
            implMod, implPomDesc,
            ['common-domain', domMod] + serviceModules + starterDeps(presentationStarter(pType)),
            '', implPomName))
    srcTree(projectDir, implMod, pkgPath,
        ["presentation/${pType}"],
        implPkgDesc)
    configClass(implMod, "presentation/${pType}")
}

// ─── app ─────────────────────────────────────────────────────────────────────

// Every Spring-enabled app is always a monitorable web service — core starter, servlet web stack,
// actuator, and Spring Boot Admin registration — regardless of presentationTypes, since actuator
// needs a web server to expose its endpoints over HTTP. A 'database' integration additionally
// gives it JPA + transactions (so Application carries @EnableTransactionManagement, which needs
// spring-tx from spring-boot-starter-data-jpa). All gated on includeSpring.
def appStarters = []
if (includeSpring) {
    appStarters << (STARTER + [label: 'spring'])
    appStarters << (STARTER_WEBMVC + [label: 'spring'])
    appStarters << (STARTER_ACTUATOR + [label: 'spring'])
    appStarters << (SPRING_BOOT_ADMIN_CLIENT + [label: 'spring'])
    if (hasDatabaseIntegration) appStarters << STARTER_DATA_JPA
}
writeFile(projectDir, 'app/pom.xml',
    modulePom(groupId, artifactId, version,
        'app',
        "${appName}'s application configuration including building its deployment assembly.",
        appDeps + appStarters, '', "${appName} App"))
srcTree(projectDir, 'app', pkgPath, ['app'],
    'Application assembly and entry point.')

if (includeSpring) {
// Spring Boot entry-point classes live in the app's .config package and @Import every
// module's @Configuration class so the whole tree is assembled into one context. Each config
// class gets its own import statement so the @Import list itself can use short simple names
// (module names are unique, so the simple class names never collide).
def appConfigPkg = (pkgPath + '/app/config').replace('/', '.')
def baseImports = [
    'org.springframework.boot.SpringApplication',
    'org.springframework.boot.autoconfigure.SpringBootApplication',
    'org.springframework.context.annotation.Import',
]
// @EnableTransactionManagement requires spring-tx (pulled in by spring-boot-starter-data-jpa),
// so it is only emitted when a database integration puts that starter on the app's classpath.
if (hasDatabaseIntegration) baseImports << 'org.springframework.transaction.annotation.EnableTransactionManagement'
def importStatements = (baseImports + configFqns).sort().collect { "import ${it};" }.join('\n')
def importsBlock     = configFqns.sort().collect { "    ${it.substring(it.lastIndexOf('.') + 1)}.class," }.join('\n')
def txAnnotation = hasDatabaseIntegration ? '@EnableTransactionManagement\n' : ''
writeFile(projectDir, "app/src/main/java/${pkgPath}/app/config/Application.java", """\
package ${appConfigPkg};

${importStatements}

/**
 * Spring Boot main application class for running standalone in Boot configured embedded container. Not used with WAR
 * deployment.
 */
@SpringBootApplication
${txAnnotation}@Import({
${importsBlock}
})
public class Application {
    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
""")
// The app is always a servlet web application (spring-boot-starter-webmvc, above), so
// AppServletInitializer (WAR deployment) is always generated alongside it.
writeFile(projectDir, "app/src/main/java/${pkgPath}/app/config/AppServletInitializer.java", """\
package ${appConfigPkg};

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * web.xml configuration replacement for WAR deployment.
 */
public class AppServletInitializer extends SpringBootServletInitializer {
    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    protected SpringApplicationBuilder configure(final SpringApplicationBuilder builder) {
        log.info("Starting application");
        return builder.sources(Application.class);
    }
}
""")

// JPA and SQL-logging properties only make sense when a database integration puts
// spring-boot-starter-data-jpa / datasource-proxy-spring-boot-starter on app's classpath.
def jpaOpenInViewLine = hasDatabaseIntegration ? 'spring.jpa.open-in-view=false\n' : ''
def metricsBlock = hasDatabaseIntegration ? """\
### Metrics
jdbc.datasource-proxy.enabled=true
spring.jpa.properties.hibernate.generate_statistics=true

""" : ''
def jpaBlock = hasDatabaseIntegration ? """\
## JPA
spring.jpa.show-sql=false
spring.jpa.hibernate.ddl-auto=none

""" : ''
def sqlLoggingBlock = hasDatabaseIntegration ? """\

### SQL logging
decorator.datasource.enabled=true
decorator.datasource.datasource-proxy.format-sql=true
logging.level.net.ttddyy.dsproxy.listener=TRACE
""" : ''

writeFile(projectDir, 'app/src/main/resources/application.properties', """\
## App
spring.application.name=${appNameProperty(appName)}
${jpaOpenInViewLine}spring.main.banner-mode=off

## Actuators
### enable all endpoints
management.endpoints.web.exposure.include=*

### show health check details
management.endpoint.health.show-details=always
management.endpoint.health.show-components=always

#### display env values (only do this in local or dev environments to avoid display secrets)
management.endpoint.env.show-values=always

### enable extra auto-configured InfoContributors
management.info.env.enabled=true
management.info.java.enabled=true
management.info.os.enabled=true
management.info.process.enabled=true

### display all git info
management.info.git.mode=full

### tracing
management.tracing.sampling.probability=1.0

#### disable open telemetry metric export
management.otlp.metrics.export.enabled=false

${metricsBlock}## Spring Boot Admin
spring.boot.admin.client.enabled=true
spring.boot.admin.client.url=TODO SET ME

${jpaBlock}## Logging
logging.include-application-name=false
logging.level.root=INFO
logging.file.name=TODO SET ME.log
logging.pattern.file=%date %-10thread %-5level %-100.100logger %mdc: %marker: %msg%n
logging.logback.rollingpolicy.max-file-size=10gb
logging.logback.rollingpolicy.max-history=8
logging.structured.json.context.prefix=labels
logging.level.org.springframework.transaction.interceptor=TRACE
${sqlLoggingBlock}""")

['ci', 'dev', 'local', 'prod', 'qa', 'uat'].each { profile ->
    writeFile(projectDir, "app/src/main/resources/application-${profile}.properties", '')
}
}

// ─── acceptance-tests ─────────────────────────────────────────────────────────

// TEST: common-testing (+ spring-graphql-test when a graphql presentation needs GraphQL test
// support). PROD: every internal module it depends on ("this app"), + sql logging support.
def atModuleDeps = [[module: 'common-testing', scope: 'test', label: 'this app']]
if (includeSpring && hasGraphqlPresentation) {
    atModuleDeps << [groupId: 'org.springframework.graphql', artifactId: 'spring-graphql-test',
                     scope: 'test', label: 'spring']
}
atModuleDeps += atDeps
if (includeSpring && hasDatabaseIntegration) atModuleDeps << (DATASOURCE_PROXY_STARTER + [label: 'sql logging'])

writeFile(projectDir, 'acceptance-tests/pom.xml',
    modulePom(groupId, artifactId, version,
        'acceptance-tests',
        "${appName}'s acceptance tests.",
        atModuleDeps,
        failsafeSection, "${appName} Acceptance Tests"))
// The actual *AT test classes live under src/test/java (no package-info.java there, see srcTree);
// this main-side package-info is acceptance-tests' only one, so it describes the functional tests
// themselves rather than "supporting infrastructure for" them.
srcTree(projectDir, 'acceptance-tests', pkgPath, ['at'],
    'Functional acceptance tests for the application.')

if (includeSpring) {
def atPkg       = (pkgPath + '/at').replace('/', '.')
def atConfigPkg = (pkgPath + '/at/config').replace('/', '.')
def atConfigClassName = "${appNameClass(appName)}AcceptanceTestConfiguration"
def atConfigFqn = "${atConfigPkg}.${atConfigClassName}".toString()

writeFile(projectDir, "acceptance-tests/src/test/java/${pkgPath}/at/config/${atConfigClassName}.java", """\
package ${atConfigPkg};

import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for ${appName} ATs.
 */
@Configuration
public class ${atConfigClassName} {
}
""")

writeFile(projectDir, 'acceptance-tests/src/test/resources/logback-spring.xml', """\
<?xml version="1.0" encoding="UTF-8"?>

<!-- This file exists to prevent this module's Spring runtime from finding the same named file in the app module dependency which configures some things that affect ATs. Therefore, this file is now the primary logging configuration for ATs. -->

<configuration>
  <include resource="org/springframework/boot/logging/logback/base.xml" />
</configuration>
""")

// Both AT base classes @ContextConfiguration against the acceptance-test config plus every
// generated module's @Configuration class: acceptance-tests depends on app, which transitively
// depends on every other module, so the whole composition is already on its test classpath.
def contextClasses = [atConfigFqn] + configFqns.sort()
def simpleName = { String fqn -> fqn.substring(fqn.lastIndexOf('.') + 1) }
def contextClassesBlock = contextClasses.collect { simpleName(it) + '.class' }.join(', ')
// DataSourceAutoConfiguration / XADataSourceAutoConfiguration only need excluding — and are only
// guaranteed to be on the classpath — when a database integration pulls in spring-boot-jdbc.
def dbExcludeFqns = hasDatabaseIntegration ? [
    'org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration',
    'org.springframework.boot.jdbc.autoconfigure.XADataSourceAutoConfiguration',
] : []
def enableAutoConfigLine = dbExcludeFqns
    ? "@EnableAutoConfiguration(exclude = { ${dbExcludeFqns.collect { simpleName(it) + '.class' }.join(', ')} })\n"
    : ''
def dbExcludeImports = dbExcludeFqns ? (['org.springframework.boot.autoconfigure.EnableAutoConfiguration'] + dbExcludeFqns) : []

if (hasRestPresentation) {
def imports = (['org.springframework.test.context.ContextConfiguration', "${pkg}.common.testing.RestAcceptanceTestBase".toString()]
    + dbExcludeImports + contextClasses).sort().collect { "import ${it};" }.join('\n')
writeFile(projectDir, "acceptance-tests/src/test/java/${pkgPath}/at/AppRestAcceptanceTestBase.java", """\
package ${atPkg};

${imports}

/**
 * Base test class for all ${appName} REST acceptance tests.
 */
@ContextConfiguration(classes = { ${contextClassesBlock} })
${enableAutoConfigLine}public abstract class AppRestAcceptanceTestBase extends RestAcceptanceTestBase {
}
""")
}

if (hasGraphqlPresentation) {
def imports = (['org.springframework.test.context.ContextConfiguration', "${pkg}.common.testing.GraphQlAcceptanceTestBase".toString()]
    + dbExcludeImports + contextClasses).sort().collect { "import ${it};" }.join('\n')
writeFile(projectDir, "acceptance-tests/src/test/java/${pkgPath}/at/AppGraphQlAcceptanceTestBase.java", """\
package ${atPkg};

${imports}

/**
 * Base test class for all ${appName} GraphQL acceptance tests.
 */
@ContextConfiguration(classes = { ${contextClassesBlock} })
${enableAutoConfigLine}public abstract class AppGraphQlAcceptanceTestBase extends GraphQlAcceptanceTestBase {
}
""")
}
}
