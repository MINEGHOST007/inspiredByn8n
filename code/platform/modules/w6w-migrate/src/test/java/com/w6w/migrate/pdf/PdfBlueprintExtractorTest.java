package com.w6w.migrate.pdf;

import com.w6w.migrate.model.BlueprintIntermediateModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PdfBlueprintExtractorTest {

    @Test
    void extractsAllCaseTypesStagesAndDataFieldsFromPegaBlueprintPdf() throws Exception {
        File pdfFile = new File("../../../../Pega Blueprint - Travel Management Customer Service.pdf");
        if (!pdfFile.exists()) pdfFile = new File("../../../Pega Blueprint - Travel Management Customer Service.pdf");
        assertThat(pdfFile).exists();

        BlueprintIntermediateModel model = new PdfBlueprintExtractor().extract(pdfFile);

        if (Boolean.getBoolean("updatePdfGolden")) {
            File goldenFile = new File("../../../../fixtures/migration/blueprint/sample-travel-management/expected-model.json");
            goldenFile.getParentFile().mkdirs();
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(goldenFile, model);
        }

        assertThat(model.getBlueprintId()).isEqualTo("BP-1766322");
        assertThat(model.getApplicationName()).isEqualTo("Travel Management Customer Service");
        assertThat(model.getPersonas()).contains("Guest", "Service Agent", "Property Manager", "Compliance Officer");

        Map<String, BlueprintIntermediateModel.IntermediateCaseType> cases = model.getCaseTypes().stream()
                .collect(Collectors.toMap(BlueprintIntermediateModel.IntermediateCaseType::getName, c -> c));
        assertThat(cases.keySet()).containsExactlyInAnyOrder(
                "Complaint Resolution", "Partner Onboarding", "Refund Processing");

        var complaint = cases.get("Complaint Resolution");
        assertThat(complaint.getStages()).extracting("name").contains(
                "Intake", "Investigate", "Propose Resolution", "Fulfillment", "Wrap Up", "Close", "Reject Complaint");
        assertThat(complaint.getDataFields()).extracting("name").contains("Complaint Description", "Guest Name", "Assigned Agent");
        assertThat(complaint.getStages()).allSatisfy(stage -> assertThat(stage.getSteps()).isNotEmpty());

        var partner = cases.get("Partner Onboarding");
        assertThat(partner.getStages()).extracting("name").contains(
                "App Intake", "Doc Verify", "Compliance Check", "Contract Agree", "System Activate");
        assertThat(partner.getDataFields()).extracting("name").contains("Contact Person", "Partner Name", "Contact Email");

        var refund = cases.get("Refund Processing");
        assertThat(refund.getStages()).extracting("name").contains(
                "Request Intake", "Eligibility Check", "Refund Approval", "Disbursement", "Closure", "Refund Denied");
        assertThat(refund.getDataFields()).hasSizeGreaterThanOrEqualTo(13)
                .extracting("name").contains("Approval Status", "Refund Amount", "Refund Type");
        assertThat(refund.getBusinessRules()).extracting("name").contains("Check Policy Adheren", "Identify Missing Info");
    }
}
