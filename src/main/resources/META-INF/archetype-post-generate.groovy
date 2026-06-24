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

/** Lower-cases the first character of a string, used to splice a description into a sentence. */
def lcFirst = { String s ->
    (s == null || s.isEmpty()) ? s : Character.toLowerCase(s.charAt(0)).toString() + s.substring(1)
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
    // Test-package Javadoc reuses the main wording, prefixed to mark it as the tests for that code.
    def testDesc = 'Tests for ' + lcFirst(description.replaceAll(/\.\s*$/, '')) + '.'
    testSubs.each { sub ->
        def fullPkg = (pkgPath + '/' + sub).replace('/', '.')
        def dir = "${module}/src/test/java/${pkgPath}/${sub}"
        ensureDir(base, dir)
        writeFile(base, "${dir}/package-info.java", """\
/**
 * ${testDesc}
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

/** Builds a child-module pom.xml string (2-space indent).
 *  A dependency entry is either a String (an internal module artifactId, inheriting this project's
 *  groupId and BOM-managed version) or a Map [groupId:…, artifactId:…, scope:…] for an external
 *  dependency. Dependencies are grouped into a "<!-- TEST -->" section (scope 'test', emitted first)
 *  and a "<!-- PROD -->" section (everything else); both headers are always present. */
def modulePom = { String gId, String parentAId, String ver,
                   String aId, String desc, List deps = [], String buildSection = '' ->
    def depsSection = ''
    if (deps) {
        def renderDep = { dep ->
            def dGroup = (dep instanceof Map) ? dep.groupId : gId
            def dArtifact = (dep instanceof Map) ? dep.artifactId : dep
            def dScope = (dep instanceof Map && dep.scope) ? "\n      <scope>${dep.scope}</scope>" : ''
            """\
    <dependency>
      <groupId>${dGroup}</groupId>
      <artifactId>${dArtifact}</artifactId>${dScope}
    </dependency>"""
        }
        def isTest = { it instanceof Map && it.scope == 'test' }
        def testLines = deps.findAll(isTest).collect(renderDep).join('\n')
        def prodLines = deps.findAll { !isTest(it) }.collect(renderDep).join('\n')
        def section = ['    <!-- TEST -->']
        if (testLines) section << testLines
        section << ''
        section << '    <!-- PROD -->'
        if (prodLines) section << prodLines
        depsSection = "\n  <dependencies>\n${section.join('\n')}\n  </dependencies>\n"
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

// acceptance-tests depends on app + all domain modules (including each service area's domain module)
def atDeps = (['app', 'common-domain'] + intDomainMods + svcDomainMods + presDomainMods).sort().unique()

// ─── Resolve Project Root ─────────────────────────────────────────────────────

def projectDir = new File(request.outputDirectory)
def subDir     = new File(projectDir, artifactId)
if (subDir.isDirectory()) projectDir = subDir

// ─── Spring configuration class generator ─────────────────────────────────────
// When includeSpring is true, every module the app composes carries a @Configuration
// class in its `.config` sub-package (the app's Application class @Imports them all)
// and declares spring-context. Everything here is skipped when includeSpring is false.

def SPRING_CONTEXT = [groupId: 'org.springframework', artifactId: 'spring-context']
def springCtx = includeSpring ? [SPRING_CONTEXT] : []   // appended to module deps only when Spring is enabled
def configFqns = []

/** Writes a @Configuration class into {module}/…/{mainSub}/config and records its FQN; no-op when Spring is off. */
def configClass = { String module, String mainSub ->
    if (!includeSpring) return null
    def className = module.split('-').collect { it.capitalize() }.join('') + 'Configuration'
    def pkgFq = (pkgPath + '/' + mainSub + '/config').replace('/', '.')
    def dir = "${module}/src/main/java/${pkgPath}/${mainSub}/config"
    writeFile(projectDir, "${dir}/${className}.java", """\
package ${pkgFq};

import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the ${module} module.
 */
@Configuration
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

def dmXml = allModules.collect { mod ->
    """\
      <dependency>
        <groupId>${groupId}</groupId>
        <artifactId>${mod}</artifactId>
        <version>\${project.version}</version>
      </dependency>"""
}.join('\n')

def parentInherit = includeSpring ? """\
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.0.6</version>
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
    <modernizer-maven-plugin.version>3.4.0</modernizer-maven-plugin.version>
    <versions-maven-plugin.version>2.18.0</versions-maven-plugin.version>
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
        springCtx))
srcTree(projectDir, 'common-domain', pkgPath,
    ['common/domain'], ['common/domain'],
    'Domain classes common to all modules.')
configClass('common-domain', 'common/domain')

// ─── common-testing ───────────────────────────────────────────────────────────

writeFile(projectDir, 'common-testing/pom.xml',
    modulePom(groupId, artifactId, version,
        'common-testing',
        'Testing infrastructure common to all test modules.',
        ['common-domain']))
srcTree(projectDir, 'common-testing', pkgPath,
    ['common/testing'], ['common/testing'],
    'Test infrastructure common to all modules.')

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
            ['common-domain'] + springCtx))
    srcTree(projectDir, domMod, pkgPath,
        [domSub], [domSub],
        "Domain classes for the ${intg.name} ${disp} integration.")
    configClass(domMod, domSub)

    writeFile(projectDir, "${implMod}/pom.xml",
        modulePom(groupId, artifactId, version,
            implMod,
            "Integration classes (DAOs, repositories) for the ${intg.name} ${disp} data source.",
            ['common-domain', domMod] + springCtx,
            failsafeSection))
    srcTree(projectDir, implMod, pkgPath,
        [implSub], [implSub],
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

    def svcDesc = svcMod == 'service' ? 'Business logic for the application.'
                                      : "Business logic for the ${label} service area."
    def domDesc = svcMod == 'service' ? 'Domain classes for the application.'
                                      : "Domain classes for the ${label} service area."

    // Domain classes owned by this service area.
    writeFile(projectDir, "${domMod}/pom.xml",
        modulePom(groupId, artifactId, version,
            domMod, domDesc,
            ['common-domain'] + springCtx))
    srcTree(projectDir, domMod, pkgPath, [domPkg], [domPkg], domDesc)
    configClass(domMod, domPkg)

    // Business logic, depending on its own service-area domain module.
    writeFile(projectDir, "${svcMod}/pom.xml",
        modulePom(groupId, artifactId, version,
            svcMod, svcDesc,
            ['common-domain', domMod] + springCtx))
    srcTree(projectDir, svcMod, pkgPath, [svcPkg], [svcPkg], svcDesc)
    configClass(svcMod, svcPkg)
}

// ─── Presentation: domain-{type} and presentation-{type} ──────────────────────

presentations.each { pType ->
    def domMod  = "domain-${pType}"
    def implMod = "presentation-${pType}"
    def disp    = typeDisplay(pType)

    writeFile(projectDir, "${domMod}/pom.xml",
        modulePom(groupId, artifactId, version,
            domMod,
            "Domain classes for the ${disp} presentation tier.",
            ['common-domain'] + springCtx))
    srcTree(projectDir, domMod, pkgPath,
        ["domain/${pType}"], ["domain/${pType}"],
        "Domain classes for the ${disp} presentation tier.")
    configClass(domMod, "domain/${pType}")

    writeFile(projectDir, "${implMod}/pom.xml",
        modulePom(groupId, artifactId, version,
            implMod,
            "Request-handling classes (controllers) for the ${disp} presentation tier.",
            ['common-domain', domMod] + springCtx))
    srcTree(projectDir, implMod, pkgPath,
        ["presentation/${pType}"], ["presentation/${pType}"],
        "Request-handling classes for the ${disp} presentation tier.")
    configClass(implMod, "presentation/${pType}")
}

// ─── app ─────────────────────────────────────────────────────────────────────

def appStarters = includeSpring ? [
    [groupId: 'org.springframework.boot', artifactId: 'spring-boot-starter-web'],
    [groupId: 'org.springframework.boot', artifactId: 'spring-boot-starter-data-jpa'],
] : []
writeFile(projectDir, 'app/pom.xml',
    modulePom(groupId, artifactId, version,
        'app',
        'Application assembly — produces the runnable artifact.',
        appDeps + appStarters))
srcTree(projectDir, 'app', pkgPath, ['app'], ['app'],
    'Application assembly and entry point.')

if (includeSpring) {
// Spring Boot entry-point classes live in the app's .config package and @Import every
// module's @Configuration class so the whole tree is assembled into one context.
def appConfigPkg = (pkgPath + '/app/config').replace('/', '.')
def importsBlock = configFqns.sort().collect { "    ${it}.class," }.join('\n')
writeFile(projectDir, "app/src/main/java/${pkgPath}/app/config/Application.java", """\
package ${appConfigPkg};

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Spring Boot main application class for running standalone in Boot configured embedded container. Not used with WAR
 * deployment.
 */
@SpringBootApplication
@EnableTransactionManagement
@Import({
${importsBlock}
})
public class Application {
    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
""")
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
}

// ─── acceptance-tests ─────────────────────────────────────────────────────────

writeFile(projectDir, 'acceptance-tests/pom.xml',
    modulePom(groupId, artifactId, version,
        'acceptance-tests',
        'Functional acceptance tests for the application.',
        atDeps,
        failsafeSection))
srcTree(projectDir, 'acceptance-tests', pkgPath, ['at'], ['at'],
    'Functional acceptance tests for the application.')
