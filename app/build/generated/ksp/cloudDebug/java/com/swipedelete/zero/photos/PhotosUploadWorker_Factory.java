package com.swipedelete.zero.photos;

import android.content.Context;
import androidx.work.WorkerParameters;
import com.swipedelete.zero.data.local.CloudUploadDao;
import com.swipedelete.zero.data.local.StagedFileDao;
import com.swipedelete.zero.data.repository.BackupRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class PhotosUploadWorker_Factory {
  private final Provider<CloudUploadDao> uploadDaoProvider;

  private final Provider<BackupRepository> backupRepositoryProvider;

  private final Provider<StagedFileDao> stagedFileDaoProvider;

  private final Provider<PhotosUploader> uploaderProvider;

  public PhotosUploadWorker_Factory(Provider<CloudUploadDao> uploadDaoProvider,
      Provider<BackupRepository> backupRepositoryProvider,
      Provider<StagedFileDao> stagedFileDaoProvider, Provider<PhotosUploader> uploaderProvider) {
    this.uploadDaoProvider = uploadDaoProvider;
    this.backupRepositoryProvider = backupRepositoryProvider;
    this.stagedFileDaoProvider = stagedFileDaoProvider;
    this.uploaderProvider = uploaderProvider;
  }

  public PhotosUploadWorker get(Context appContext, WorkerParameters params) {
    return newInstance(appContext, params, uploadDaoProvider.get(), backupRepositoryProvider.get(), stagedFileDaoProvider.get(), uploaderProvider.get());
  }

  public static PhotosUploadWorker_Factory create(Provider<CloudUploadDao> uploadDaoProvider,
      Provider<BackupRepository> backupRepositoryProvider,
      Provider<StagedFileDao> stagedFileDaoProvider, Provider<PhotosUploader> uploaderProvider) {
    return new PhotosUploadWorker_Factory(uploadDaoProvider, backupRepositoryProvider, stagedFileDaoProvider, uploaderProvider);
  }

  public static PhotosUploadWorker newInstance(Context appContext, WorkerParameters params,
      CloudUploadDao uploadDao, BackupRepository backupRepository, StagedFileDao stagedFileDao,
      PhotosUploader uploader) {
    return new PhotosUploadWorker(appContext, params, uploadDao, backupRepository, stagedFileDao, uploader);
  }
}
