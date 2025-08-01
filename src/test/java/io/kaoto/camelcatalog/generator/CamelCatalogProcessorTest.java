/*
 * Copyright (C) 2023 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.kaoto.camelcatalog.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kaoto.camelcatalog.generators.ComponentGenerator;
import io.kaoto.camelcatalog.generators.EIPGenerator;
import io.kaoto.camelcatalog.generators.EntityGenerator;
import io.kaoto.camelcatalog.generators.FunctionsGenerator;
import io.kaoto.camelcatalog.maven.CamelCatalogVersionLoader;
import io.kaoto.camelcatalog.maven.KaotoMavenVersionManager;
import io.kaoto.camelcatalog.model.CatalogRuntime;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.yaml.YamlRoutesBuilderLoader;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CamelCatalogProcessorTest {
    private static final List<String> ALLOWED_ENUM_TYPES = List.of("integer", "number", "string");
    private final CamelCatalogProcessor processor;

    private final ObjectNode componentCatalog;
    private final ObjectNode dataFormatCatalog;
    private final ObjectNode languageCatalog;
    private final ObjectNode modelCatalog;
    private final ObjectNode processorCatalog;
    private final ObjectNode entityCatalog;
    private final ObjectNode loadBalancerCatalog;
    private final ObjectNode functionsCatalog;

    CamelCatalogProcessorTest() throws Exception {
        CamelCatalog catalog = new DefaultCamelCatalog();
        ObjectMapper jsonMapper = new ObjectMapper();
        var is = YamlRoutesBuilderLoader.class.getClassLoader().getResourceAsStream("schema/camelYamlDsl.json");
        ObjectNode yamlDslSchema = (ObjectNode) jsonMapper.readTree(is);
        var openapiSpecIS = getClass().getClassLoader().getResourceAsStream("kubernetes-api-v1-openapi.json");
        if (openapiSpecIS == null) {
            throw new Exception("Failed to load kubernetes-api-v1-openapi.json");
        }
        var openapiSpec = new String(openapiSpecIS.readAllBytes(), StandardCharsets.UTF_8);
        CamelYamlDslSchemaProcessor schemaProcessor = new CamelYamlDslSchemaProcessor(jsonMapper, yamlDslSchema);
        CamelCatalogVersionLoader camelCatalogVersionLoader = new CamelCatalogVersionLoader(CatalogRuntime.Main, true);
        camelCatalogVersionLoader.loadCamelYamlDsl(catalog.getCatalogVersion());
        camelCatalogVersionLoader.loadKubernetesSchema();
        camelCatalogVersionLoader.loadLocalSchemas();
        camelCatalogVersionLoader.loadKaotoPatterns();

        ComponentGenerator componentGenerator = new ComponentGenerator(catalog, CatalogRuntime.Main);
        EIPGenerator eipGenerator = new EIPGenerator(catalog, jsonMapper.writeValueAsString(yamlDslSchema),
                camelCatalogVersionLoader.getKaotoPatterns());
        EntityGenerator entityGenerator = new EntityGenerator(
                catalog,
                jsonMapper.writeValueAsString(yamlDslSchema),
                openapiSpec,
                camelCatalogVersionLoader.getLocalSchemas()
        );
        FunctionsGenerator functionsGenerator = new FunctionsGenerator(
                catalog,
                camelCatalogVersionLoader
        );

        this.processor = new CamelCatalogProcessor(
                catalog,
                jsonMapper,
                schemaProcessor,
                CatalogRuntime.Main,
                camelCatalogVersionLoader
        );

        this.componentCatalog = (ObjectNode) jsonMapper.readTree(Util.getPrettyJSON(componentGenerator.generate()));
        this.dataFormatCatalog = (ObjectNode) jsonMapper.readTree(this.processor.getDataFormatCatalog());
        this.languageCatalog = (ObjectNode) jsonMapper.readTree(this.processor.getLanguageCatalog());
        this.modelCatalog = (ObjectNode) jsonMapper.readTree(this.processor.getModelCatalog());
        this.processorCatalog = (ObjectNode) jsonMapper.readTree(Util.getPrettyJSON(eipGenerator.generate()));
        this.entityCatalog = (ObjectNode) jsonMapper.readTree(Util.getPrettyJSON(entityGenerator.generate()));
        this.loadBalancerCatalog = (ObjectNode) jsonMapper.readTree(this.processor.getLoadBalancerCatalog());
        this.functionsCatalog = (ObjectNode) jsonMapper.readTree(Util.getPrettyJSON(functionsGenerator.generate()));
    }

    @Test
    void testProcessCatalog() throws Exception {
        var catalogMap = processor.processCatalog();
        assertEquals(Util.getPrettyJSON(this.componentCatalog), catalogMap.get("components"));
        assertEquals(processor.getDataFormatCatalog(), catalogMap.get("dataformats"));
        assertEquals(processor.getLanguageCatalog(), catalogMap.get("languages"));
        assertEquals(processor.getModelCatalog(), catalogMap.get("models"));
        assertEquals(Util.getPrettyJSON(this.processorCatalog), catalogMap.get("patterns"));
        assertEquals(Util.getPrettyJSON(this.entityCatalog), catalogMap.get("entities"));
        assertEquals(processor.getLoadBalancerCatalog(), catalogMap.get("loadbalancers"));
    }

    @Test
    void testGetComponentCatalog() throws Exception {
        assertTrue(componentCatalog.size() > 300);

        var directModel = componentCatalog
                .withObject("/direct")
                .withObject("/component");
        assertEquals("Direct", directModel.get("title").asText());

        var googleCalendarSchema = componentCatalog
                .withObject("/google-calendar")
                .withObject("/propertiesSchema");
        var scopesProperty = googleCalendarSchema.withObject("/properties").withObject("/scopes");
        assertEquals("array", scopesProperty.get("type").asText());
        assertEquals("string", scopesProperty.withObject("/items").get("type").asText());

        var gdSchema = componentCatalog
                .withObject("/google-drive")
                .withObject("/propertiesSchema");
        var gdScopesProperty = gdSchema.withObject("/properties").withObject("/scopes");
        assertEquals("array", gdScopesProperty.get("type").asText());
        assertEquals("string", gdScopesProperty.withObject("/items").get("type").asText());
        var gdSPProperty = gdSchema.withObject("/properties").withObject("/schedulerProperties");
        assertEquals("object", gdSPProperty.get("type").asText());

        var sqlSchema = componentCatalog
                .withObject("/sql")
                .withObject("/propertiesSchema");
        var sqlDSProperty = sqlSchema.withObject("/properties").withObject("/dataSource");
        assertEquals("string", sqlDSProperty.get("type").asText());
        assertEquals("bean:javax.sql.DataSource", sqlDSProperty.get("format").asText());
        var sqlBEHProperty = sqlSchema.withObject("/properties").withObject("/bridgeErrorHandler");
        assertFalse(sqlBEHProperty.has("default"));

        var activeMQSchema = componentCatalog
                .withObject("/activemq")
                .withObject("/propertiesSchema");
        var destinationTypeProperty = activeMQSchema.withObject("/properties").withObject("/destinationType");
        assertEquals("queue", destinationTypeProperty.get("default").asText());

        var smbSchema = componentCatalog
                .withObject("/smb")
                .withObject("/propertiesSchema");
        var smbUsernameProperty = smbSchema.withObject("/properties").withObject("/username");
        assertEquals("password", smbUsernameProperty.get("format").asText());
        var smbPasswordProperty = smbSchema.withObject("/properties").withObject("/password");
        assertEquals("password", smbPasswordProperty.get("format").asText());

        var cxfSchema = componentCatalog
                .withObject("/cxf")
                .withObject("/propertiesSchema");
        var cxfContinuationTimeout = cxfSchema.withObject("/properties").withObject("/continuationTimeout");
        assertEquals("duration", cxfContinuationTimeout.get("format").asText());
    }

    @Test
    void testComponentEnumParameter() throws Exception {
        checkEnumParameters(componentCatalog);
    }

    private void checkEnumParameters(ObjectNode catalog) throws Exception {
        for (var entry : catalog.properties()) {
            var name = entry.getKey();
            var component = entry.getValue();
            for (var prop : component.withObject("/propertiesSchema").withObject("/properties").properties()) {
                var propName = prop.getKey();
                var property = prop.getValue();
                if (property.has("enum")) {
                    assertTrue(ALLOWED_ENUM_TYPES.contains(property.get("type").asText()), name + ":" + propName);
                    checkEnumDuplicate(name, propName, property.withArray("/enum"));
                }
            }
        }
    }

    private void checkEnumDuplicate(String entityName, String propertyName, ArrayNode enumArray) {
        Set<String> names = new HashSet<>();
        for (var enumValue : enumArray) {
            var name = enumValue.asText();
            if (names.contains(name)) {
                fail(String.format("Duplicate enum value [%s] in [%s/%s]", name, entityName, propertyName));
            }
            names.add(name);
        }
    }

    @Test
    void testGetDataFormatCatalog() throws Exception {
        var customModel = dataFormatCatalog
                .withObject("/custom")
                .withObject("/model");
        assertEquals("model", customModel.get("kind").asText());
        assertEquals("Custom", customModel.get("title").asText());
        var customProperties = dataFormatCatalog
                .withObject("/custom")
                .withObject("/properties");
        assertEquals("Ref", customProperties.withObject("/ref").get("displayName").asText());
        var customPropertiesSchema = dataFormatCatalog
                .withObject("/custom")
                .withObject("/propertiesSchema");
        assertEquals("Custom", customPropertiesSchema.get("title").asText());
        var refProperty = customPropertiesSchema.withObject("/properties").withObject("/ref");
        assertEquals("Ref", refProperty.get("title").asText());
        var customPropertiesSchemaRequiredFields = customPropertiesSchema.withArray("/required");
        assertFalse(customPropertiesSchemaRequiredFields.isEmpty());
        assertEquals(1, customPropertiesSchemaRequiredFields.size(), "Size should be 1");
    }

    @Test
    void testRestProcessors() throws Exception {
        var restGetProcessorSchema = processorCatalog
                .withObject("/get")
                .withObject("propertiesSchema");
        var restPostProcessorSchema = processorCatalog
                .withObject("/post")
                .withObject("propertiesSchema");
        var restPutProcessorSchema = processorCatalog
                .withObject("/put")
                .withObject("propertiesSchema");
        var restDeleteProcessorSchema = processorCatalog
                .withObject("/delete")
                .withObject("propertiesSchema");
        var restHeadProcessorSchema = processorCatalog
                .withObject("/head")
                .withObject("propertiesSchema");
        var restPatchProcessorSchema = processorCatalog
                .withObject("/patch")
                .withObject("propertiesSchema");

        assertFalse(restGetProcessorSchema.isEmpty(), "get processor schema should not be empty");
        assertFalse(restPostProcessorSchema.isEmpty(), "post processor schema should not be empty");
        assertFalse(restPutProcessorSchema.isEmpty(), "put processor schema should not be empty");
        assertFalse(restDeleteProcessorSchema.isEmpty(), "delete processor schema should not be empty");
        assertFalse(restHeadProcessorSchema.isEmpty(), "head processor schema should not be empty");
        assertFalse(restPatchProcessorSchema.isEmpty(), "patch processor schema should not be empty");
    }

    @Test
    void testDataFormatEnumParameter() throws Exception {
        checkEnumParameters(dataFormatCatalog);
    }

    @Test
    void testGetLanguageCatalog() throws Exception {
        assertFalse(languageCatalog.has("file"));
        var languageModel = languageCatalog
                .withObject("/language")
                .withObject("/model");
        assertEquals("model", languageModel.get("kind").asText());
        assertEquals("Language", languageModel.get("title").asText());
        var languageProperties = languageCatalog
                .withObject("/language")
                .withObject("/properties");
        assertEquals("Language", languageProperties.withObject("/language").get("displayName").asText());
        var languagePropertiesSchema = languageCatalog
                .withObject("/language")
                .withObject("/propertiesSchema");
        assertEquals("Language", languagePropertiesSchema.get("title").asText());
        var languageProperty = languagePropertiesSchema.withObject("/properties").withObject("/language");
        assertEquals("Language", languageProperty.get("title").asText());
        var languagePropertiesSchemaRequiredFields = languagePropertiesSchema.withArray("/required");
        assertFalse(languagePropertiesSchemaRequiredFields.isEmpty());
        assertEquals(2, languagePropertiesSchemaRequiredFields.size(), "Size should be 2");
        assertEquals("expression", languagePropertiesSchemaRequiredFields.get(0).asText());
        assertEquals("language", languagePropertiesSchemaRequiredFields.get(1).asText());
    }

    @Test
    void testLanguageEnumParameter() throws Exception {
        checkEnumParameters(languageCatalog);
    }

    @Test
    void testGetModelCatalog() throws Exception {
        assertTrue(modelCatalog.size() > 200);
        var aggregateModel = modelCatalog
                .withObject("/aggregate")
                .withObject("/model");
        assertEquals("model", aggregateModel.get("kind").asText());
        assertEquals("Aggregate", aggregateModel.get("title").asText());
    }

    @Test
    void testModelEnumParameter() throws Exception {
        checkEnumParameters(modelCatalog);
    }

    @Test
    void testGetPatternCatalog() throws Exception {
        assertTrue(processorCatalog.size() > 65 && processorCatalog.size() < 80);

        var choiceModel = processorCatalog.withObject("/choice").withObject("/model");
        assertEquals("choice", choiceModel.get("name").asText());

        var aggregateSchema = processorCatalog.withObject("/aggregate").withObject("/propertiesSchema");
        var aggregationStrategy = aggregateSchema.withObject("/properties").withObject("/aggregationStrategy");
        assertEquals("string", aggregationStrategy.get("type").asText());

        var toDSchema = processorCatalog.withObject("/toD").withObject("/propertiesSchema");
        var uri = toDSchema.withObject("/properties").withObject("/uri");
        assertEquals("string", uri.get("type").asText());
        assertFalse(toDSchema.withObject("/properties").has("/parameters"));

        var toSchema = processorCatalog.withObject("/to").withObject("/propertiesSchema");
        var toUri = toSchema.withObject("/properties").withObject("/uri");
        assertEquals("string", toUri.get("type").asText());
        assertFalse(toSchema.withObject("/properties").has("/parameters"));

        var beanSchema = processorCatalog.withObject("/bean").withObject("/propertiesSchema");
        assertFalse(beanSchema.has("definitions"));
    }

    @Test
    void testRouteConfigurationCatalog() throws Exception {
        List.of("intercept", "interceptFrom", "interceptSendToEndpoint", "onCompletion", "onException")
                .forEach(name -> assertTrue(entityCatalog.has(name), name));
    }

    @Test
    void testPatternEnumParameter() throws Exception {
        checkEnumParameters(processorCatalog);
    }

    @Test
    void testGetEntityCatalog() throws Exception {
        List.of(
                "bean",
                "errorHandler",
                "from",
                "intercept",
                "interceptFrom",
                "interceptSendToEndpoint",
                "onCompletion",
                "onException",
                "routeConfiguration",
                "route",
                "routeTemplate",
                "templatedRoute",
                "restConfiguration",
                "rest").forEach(name -> assertTrue(entityCatalog.has(name), name));
        var beans = entityCatalog.withObject("/bean");
        var beansScript = beans.withObject("/propertiesSchema")
                .withObject("/definitions")
                .withObject("/org.apache.camel.model.BeanFactoryDefinition")
                .withObject("/properties")
                .withObject("/script");
        assertEquals("Script", beansScript.get("title").asText());
        var from = entityCatalog.withObject("/from");
        var uri = from.withObject("/propertiesSchema")
                .withObject("/properties")
                .withObject("/uri");
        assertEquals("group:common", uri.get("$comment").asText());
        var restConfiguration = entityCatalog.withObject("/restConfiguration");
        var apiComponent = restConfiguration.withObject("/propertiesSchema")
                .withObject("/properties")
                .withObject("/apiComponent");
        assertEquals("Api Component", apiComponent.get("title").asText());
    }

    @Test
    void testEntityEnumParameter() throws Exception {
        checkEnumParameters(entityCatalog);
    }

    @Test
    void testGetLoadBalancerCatalog() throws Exception {
        assertFalse(loadBalancerCatalog.isEmpty());
        var failoverModel = loadBalancerCatalog.withObject("/failoverLoadBalancer/model");
        assertEquals("failoverLoadBalancer", failoverModel.get("name").asText());
        var failoverSchema = loadBalancerCatalog.withObject("/failoverLoadBalancer/propertiesSchema");
        var failoverSchemaRequiredFields = failoverSchema.withArray("/required");
        assertTrue(failoverSchemaRequiredFields.isEmpty());
        var maximumFailoverAttempts = failoverSchema.withObject("/properties/maximumFailoverAttempts");
        assertEquals("string", maximumFailoverAttempts.get("type").asText());
        assertEquals("-1", maximumFailoverAttempts.get("default").asText());

        var roundRobinSchema = loadBalancerCatalog.withObject("/roundRobinLoadBalancer/propertiesSchema");
        var roundRobinSchemaRequiredFields = roundRobinSchema.withArray("/required");
        assertTrue(roundRobinSchemaRequiredFields.isEmpty());
        var roundRobinId = roundRobinSchema.withObject("/properties/id");
        assertEquals("string", roundRobinId.get("type").asText());

        var customModel = loadBalancerCatalog.withObject("/customLoadBalancer/model");
        assertEquals("Custom Load Balancer", customModel.get("title").asText());
        var customSchema = loadBalancerCatalog.withObject("/customLoadBalancer/propertiesSchema");
        var customSchemaRequiredFields = customSchema.withArray("/required");
        assertFalse(customSchemaRequiredFields.isEmpty());
        assertEquals(1, customSchemaRequiredFields.size(), "Size should be 1");
        assertEquals("ref", customSchemaRequiredFields.get(0).asText());
        assertEquals("Custom Load Balancer", customSchema.get("title").asText());
        var customRef = customSchema.withObject("/properties/ref");
        assertEquals("Ref", customRef.get("title").asText());
    }

    @Test
    void testLoadBalancerEnumParameter() throws Exception {
        checkEnumParameters(loadBalancerCatalog);
    }

    @Test
    void testGetFunctionsCatalog() {
        assertFalse(functionsCatalog.isEmpty());
        assertTrue(functionsCatalog.has("simple"), "Functions catalog should contain 'simple' language");

        var simpleFunctions = functionsCatalog.withObject("/simple");
        assertFalse(simpleFunctions.isEmpty(), "Simple functions should not be empty");

        var simpleFunction = simpleFunctions.withObject("/uuid(type)");
        assertEquals("Generate UUID", simpleFunction.get("displayName").asText(),
                "Function name should be 'Generate UUID'");
    }
}
