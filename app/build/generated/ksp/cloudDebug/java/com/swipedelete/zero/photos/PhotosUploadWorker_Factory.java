package com.swipedelete.zero.photos;

import android.content.Context;
import androidx.work.WorkerParameters;
import com.swipedelete.zero.data.local.BackedUpFileDao;
import com.swipedelete.zero.data.local.CloudUploadDao;
import com.swipedelete.zero.data.local.StagedFileDao;
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

  private final Provider<BackedUpFileDao> backedUpFileDaoProvider;

  private final Provider<StagedFileDao> stagedFileDaoProvider;

  private final Provider<PhotosUploader> uploaderProvider;

  public PhotosUploadWorker_Factory(Provider<CloudUploadDao> uploadDaoProvider,
      Provider<BackedUpFileDao> backedUpFileDaoProvider,
      Provider<StagedFileDao> stagedFileDaoProvider, Provider<PhotosUploader> uploaderProvider) {
    this.uploadDaoProvider = uploadDaoProvider;
    this.backedUpFileDaoProvider = backedUpFileDaoProvider;
    this.stagedFileDaoProvider = stagedFileDaoProvider;
    this.uploaderProvider = uploaderProvider;
  }

  public PhotosUploadWorker get(Context appContext, WorkerParameters params) {
    return newInstance(appContext, params, uploadDaoProvider.get(), backedUpFileDaoProvider.get(), stagedFileDaoProvider.get(), uploaderProvider.get());
  }

  public static PhotosUploadWorker_Factory create(Provider<CloudUploadDao> uploadDaoProvider,
      Provider<BackedUpFileDao> backedUpFileDaoProvider,
      Provider<StagedFileDao> stagedFileDaoProvider, Provider<PhotosUploader> uploaderProvider) {
    return new PhotosUploadWorker_Factory(uploadDaoProvider, backedUpFileDaoProvider, stagedFileDaoProvider, uploaderProvider);
  }

  public static PhotosUploadWorker newInstance(Context appContext, WorkerParameters params,
      CloudUploadDao uploadDao, BackedUpFileDao backedUpFileDao, StagedFileDao stagedFileDao,
      PhotosUploader uploader) {
    return new PhotosUploadWorker(appContext, params, uploadDao, backedUpFileDao, stagedFileDao, uploader);
  }
}
