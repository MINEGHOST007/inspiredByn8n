package com.w6w.compiler;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;

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
                .contains("<decision id=\"refund-eligibility\"", "hitPolicy=\"FIRST\"", "purchaseAmount", "&lt;= 500", "&gt; 500", "<text>false</text>");
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.newDocumentBuilder().parse(new ByteArrayInputStream(first.getBytes(StandardCharsets.UTF_8)));
    }
}
