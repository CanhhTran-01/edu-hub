package com.eduhub.edu_hub.people.application.admission;

import com.eduhub.edu_hub.people.domain.model.AdministrativeClass;
import com.eduhub.edu_hub.people.domain.model.Student;
import java.util.List;

public interface ClassAssignmentStrategy {
    AssignmentResult assign(List<AdmissionCandidate> candidates, List<ClassState> classes);
    record ClassState(AdministrativeClass administrativeClass, List<Student> existingStudents) {}
    record AssignmentResult(List<String> duplicateNameWarnings, List<String> capacityWarnings) {}
}
