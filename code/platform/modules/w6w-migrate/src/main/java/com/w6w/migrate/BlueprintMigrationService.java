package com.w6w.migrate;

import com.w6w.governance.EligibilityChecker;
import com.w6w.governance.EligibilityScorecard;
import com.w6w.compiler.CwmLinter;
import com.w6w.compiler.CwmToBpmnCompiler;
import com.w6w.migrate.mapper.BlueprintToCwmMapper;
import com.w6w.migrate.model.BlueprintIntermediateModel;
import com.w6w.migrate.pdf.PdfBlueprintExtractor;
import com.w6w.migrate.report.ConversionReport;
import com.w6w.model.WorkflowDefinition;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Orchestrates source adapter, CWM draft mapping and the Tier 1 eligibility gate. */
public class BlueprintMigrationService {
    private final PdfBlueprintExtractor pdfExtractor;
    private final BlueprintToCwmMapper mapper;
    private final EligibilityChecker eligibilityChecker;

    public BlueprintMigrationService() {
        this(new PdfBlueprintExtractor(), new BlueprintToCwmMapper(), new EligibilityChecker());
    }

    public BlueprintMigrationService(PdfBlueprintExtractor pdfExtractor,
                                     BlueprintToCwmMapper mapper,
                                     EligibilityChecker eligibilityChecker) {
        this.pdfExtractor = pdfExtractor;
        this.mapper = mapper;
        this.eligibilityChecker = eligibilityChecker;
    }

    public List<CaseMigration> migratePdf(File source) throws IOException {
        if (source == null || !source.isFile()) throw new IllegalArgumentException("Blueprint PDF must be an existing file");
        if (!source.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new UnsupportedOperationException("No adapter is registered for " + source.getName()
                    + "; this build accepts Blueprint PDF files. Proprietary .blueprint binaries are not decoded.");
        }
        BlueprintIntermediateModel model = pdfExtractor.extract(source);
        return migrateModel(model);
    }

    public List<CaseMigration> migrateModel(BlueprintIntermediateModel model) {
        if (model == null) throw new IllegalArgumentException("Blueprint intermediate model is required");
        if (model.getCaseTypes().isEmpty()) throw new IllegalArgumentException("No supported case types were found in the Blueprint model");
        List<CaseMigration> results = new ArrayList<>();
        for (var caseType : model.getCaseTypes()) {
            ConversionReport report = new ConversionReport(model.getBlueprintId(), caseType.getName());
            WorkflowDefinition draft = mapper.mapToWorkflow(caseType, model.getBlueprintId(), report, model.getPersonas());
            EligibilityScorecard scorecard = eligibilityChecker.check(draft);
            if (report.getUnsupportedCount() > 0) {
                scorecard.upgradeTo(EligibilityScorecard.TierDecision.REJECT,
                        "REJECT-MG-01: " + report.getUnsupportedCount()
                                + " unsupported construct(s) prevent a safe runnable draft; see conversion findings");
            }
            if (scorecard.getDecision() != EligibilityScorecard.TierDecision.REJECT) {
                CwmLinter.LintResult lint = new CwmLinter().lint(draft);
                if (lint.hasErrors()) {
                    scorecard.upgradeTo(EligibilityScorecard.TierDecision.REJECT,
                            "REJECT-MG-02: generated CWM is not runnable until validation errors are resolved: "
                                    + lint.getIssues().stream().filter(issue -> "ERROR".equals(issue.getLevel()))
                                    .map(CwmLinter.LintIssue::getMessage).findFirst().orElse("unknown validation error"));
                } else {
                    try {
                        new CwmToBpmnCompiler().compile(draft);
                    } catch (RuntimeException e) {
                        scorecard.upgradeTo(EligibilityScorecard.TierDecision.REJECT,
                                "REJECT-MG-03: generated CWM cannot compile: " + e.getMessage());
                    }
                }
            }
            report.setEligibility(scorecard.getDecision().name(), scorecard.getReasons());
            boolean rejected = scorecard.getDecision() == EligibilityScorecard.TierDecision.REJECT;
            results.add(new CaseMigration(rejected ? null : draft, report, scorecard, !rejected));
        }
        return results;
    }

    public record CaseMigration(WorkflowDefinition draft, ConversionReport report,
                                EligibilityScorecard eligibility, boolean generated) { }
}
