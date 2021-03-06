package org.opencb.opencga.core.models.clinical;

import org.opencb.opencga.core.models.AclParams;

public class ClinicalAnalysisAclUpdateParams extends AclParams {

    private String clinicalAnalysis;

    public ClinicalAnalysisAclUpdateParams() {
    }

    public ClinicalAnalysisAclUpdateParams(String permissions, Action action, String clinicalAnalysis) {
        super(permissions, action);
        this.clinicalAnalysis = clinicalAnalysis;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ClinicalAnalysisAclUpdateParams{");
        sb.append("clinicalAnalysis='").append(clinicalAnalysis).append('\'');
        sb.append(", permissions='").append(permissions).append('\'');
        sb.append(", action=").append(action);
        sb.append('}');
        return sb.toString();
    }

    public String getClinicalAnalysis() {
        return clinicalAnalysis;
    }

    public ClinicalAnalysisAclUpdateParams setClinicalAnalysis(String clinicalAnalysis) {
        this.clinicalAnalysis = clinicalAnalysis;
        return this;
    }

    public ClinicalAnalysisAclUpdateParams setPermissions(String permissions) {
        super.setPermissions(permissions);
        return this;
    }

    public ClinicalAnalysisAclUpdateParams setAction(Action action) {
        super.setAction(action);
        return this;
    }

}
