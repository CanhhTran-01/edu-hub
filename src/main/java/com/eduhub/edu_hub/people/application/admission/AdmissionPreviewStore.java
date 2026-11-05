package com.eduhub.edu_hub.people.application.admission;

import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class AdmissionPreviewStore {
    private final Map<UUID, AdmissionBatchState> batches = new ConcurrentHashMap<>();

    public void put(AdmissionBatchState batch) {
        batches.put(batch.getId(), batch);
    }

    public AdmissionBatchState get(UUID id) {
        AdmissionBatchState batch = batches.get(id);
        if (batch == null) throw new BaseException(ErrorCode.ADMISSION_BATCH_NOT_FOUND);
        return batch;
    }
}
