package com.example.watermanagement.service;

import com.example.watermanagement.dto.DatabaseRestoreStage;

import java.io.InputStream;
import java.nio.file.Path;

public interface DatabaseSnapshotService {

    Path createVerifiedSnapshot(String purpose);

    void validateDatabase(Path database);

    DatabaseRestoreStage stageRestore(InputStream input, boolean desktopMode);

    Path resolveStagedDatabase(String token);
}
