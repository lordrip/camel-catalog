/**
 * @fileoverview
 * This module exports the versions of Camel catalogs and Kamelets used in the project.
 * It includes versions for Main, Quarkus, and SpringBoot catalogs.
 *
 * Camel JBang commands to list available catalogs:
 * - Main: `camel version list --fresh --runtime=main`
 * - Quarkus: `camel version list --fresh --runtime=quarkus`
 * - Spring Boot: `camel version list --fresh --runtime=spring-boot`
 *
 * For Red Hat versions, add `--repo=redhat` to the commands.
 * - Main: `camel version list --fresh --runtime=main --repo=redhat`
 * - Quarkus: `camel version list --fresh --runtime=quarkus --repo=redhat``
 * - Spring Boot: `camel version list --fresh --runtime=spring-boot --repo=redhat``
 */

const CATALOGS = {
  // https://repo1.maven.org/maven2/org/apache/camel/camel-catalog/
  // https://maven.repository.redhat.com/ga/org/apache/camel/camel-catalog/
  Main: ['4.13.0', '4.10.6', '4.8.8', '4.10.3.redhat-00025', '4.8.5.redhat-00008', '4.4.0.redhat-00046'],
  // https://repo1.maven.org/maven2/org/apache/camel/quarkus/camel-quarkus-catalog/
  // https://maven.repository.redhat.com/ga/org/apache/camel/quarkus/camel-quarkus-catalog/
  Quarkus: ['3.24.0', '3.20.2', '3.15.4', '3.20.0.redhat-00003', '3.15.0.redhat-00010', '3.8.0.redhat-00018'],
  // https://repo1.maven.org/maven2/org/apache/camel/springboot/camel-catalog-provider-springboot/
  // https://maven.repository.redhat.com/ga/org/apache/camel/springboot/camel-catalog-provider-springboot/
  SpringBoot: ['4.13.0', '4.10.6', '4.8.8', '4.10.3.redhat-00023', '4.8.5.redhat-00008', '4.4.0.redhat-00039'],
};

const KAMELETS_VERSION = '4.13.0';

module.exports = { CATALOGS, KAMELETS_VERSION };
