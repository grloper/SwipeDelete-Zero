package com.swipedelete.zero.di;

import com.swipedelete.zero.data.local.AppDatabase;
import com.swipedelete.zero.data.local.CloudUploadDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class DatabaseModule_ProvideCloudUploadDaoFactory implements Factory<CloudUploadDao> {
  private final Provider<AppDatabase> dbProvider;

  public DatabaseModule_ProvideCloudUploadDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public CloudUploadDao get() {
    return provideCloudUploadDao(dbProvider.get());
  }

  public static DatabaseModule_ProvideCloudUploadDaoFactory create(
      Provider<AppDatabase> dbProvider) {
    return new DatabaseModule_ProvideCloudUploadDaoFactory(dbProvider);
  }

  public static CloudUploadDao provideCloudUploadDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideCloudUploadDao(db));
  }
}
