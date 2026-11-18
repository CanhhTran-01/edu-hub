package com.eduhub.edu_hub.scheduling.api.dto.request;

import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassExportColumn;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class ExportModuleClassStudentsRequest {
    @NotEmpty
    private List<@NotNull ModuleClassExportColumn> columns = List.of(ModuleClassExportColumn.values());
}
