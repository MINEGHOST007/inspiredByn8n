package com.w6w.compiler;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;
import org.flowable.dmn.engine.DmnEngine;
import org.flowable.dmn.engine.DmnEngineConfiguration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionTableCompilerTest {
    @Test
    void sampleTableParsesAndCompilesToDeterministicDmn() throws Exception {
        Path source = Path.of("../../../workflows/domains/customer-service/refund-processing/decisions/refund-eligibility.yaml");
        if (!source.toFile().exists()) source = Path.of("../../workflows/domains/customer-service/refund-processing/decisions/refund-eligibility.yaml");
        assertThat(source).exists();
        DecisionTableCompiler compiler = new DecisionTableCompiler();
        var table = compiler.parse(source);
        String first = compiler.compile(table);
        String second = compiler.compile(table);
        assertThat(first).isEqualTo(second)
                .contains("<decision id=\"refund-eligibility\"", "http://www.omg.org/spec/DMN/20151101", "hitPolicy=\"FIRST\"", "purchaseAmount", "&lt;= 500", "&gt; 500", "<text>false</text>");
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.newDocumentBuilder().parse(new ByteArrayInputStream(first.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void generatedDmnExecutesInTheLocalFlowableDecisionEngine() throws Exception {
        Path source = Path.of("../../../workflows/domains/customer-service/refund-processing/decisions/refund-eligibility.yaml");
        if (!source.toFile().exists()) source = Path.of("../../workflows/domains/customer-service/refund-processing/decisions/refund-eligibility.yaml");
        String dmn = new DecisionTableCompiler().compile(new DecisionTableCompiler().parse(source));
        DmnEngine engine = DmnEngineConfiguration.createStandaloneInMemDmnEngineConfiguration()
                .setDatabaseSchemaUpdate(DmnEngineConfiguration.DB_SCHEMA_UPDATE_TRUE).buildDmnEngine();
        try {
            engine.getDmnRepositoryService().createDeployment().name("refund decision test")
                    .addString("refund-eligibility.dmn", dmn).deploy();
            Map<String, Object> eligible = engine.getDmnDecisionService().createExecuteDecisionBuilder()
                    .decisionKey("refund-eligibility").variable("purchaseAmount", 250).executeWithSingleResult();
            Map<String, Object> ineligible = engine.getDmnDecisionService().createExecuteDecisionBuilder()
                    .decisionKey("refund-eligibility").variable("purchaseAmount", 900).executeWithSingleResult();
            assertThat(eligible).containsEntry("eligible", true);
            assertThat(ineligible).containsEntry("eligible", false);
        } finally {
            engine.close();
        }
    }
}
