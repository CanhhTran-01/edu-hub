package com.eduhub.edu_hub.people.application.admission;

import com.eduhub.edu_hub.people.api.dto.AdmissionErrorResponse;
import com.eduhub.edu_hub.people.domain.model.AdministrativeClass;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
public class AdmissionBatchState {
    private final UUID id;
    private final String fileName;
    private final int totalRows;
    private final List<AdmissionCandidate> candidates;
    private final List<AdmissionErrorResponse> errors;
    private final List<AdministrativeClass> classes;
    private final List<String> manualAdjustments = new java.util.ArrayList<>();
    @Setter private List<String> duplicateNameWarnings;
    @Setter private List<String> capacityWarnings;
    @Setter private boolean confirmed;

    public AdmissionBatchState(UUID id, String fileName, int totalRows, List<AdmissionCandidate> candidates,
            List<AdmissionErrorResponse> errors, List<AdministrativeClass> classes,
            List<String> duplicateNameWarnings, List<String> capacityWarnings) {
        this.id = id;
        this.fileName = fileName;
        this.totalRows = totalRows;
        this.candidates = candidates;
        this.errors = errors;
        this.classes = classes;
        this.duplicateNameWarnings = duplicateNameWarnings;
        this.capacityWarnings = capacityWarnings;
    }
}
