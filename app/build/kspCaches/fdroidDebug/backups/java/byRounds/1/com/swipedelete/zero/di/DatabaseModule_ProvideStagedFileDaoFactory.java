package com.swipedelete.zero.di;

import com.swipedelete.zero.data.local.AppDatabase;
import com.swipedelete.zero.data.local.StagedFileDao;
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
public final class DatabaseModule_ProvideStagedFileDaoFactory implements Factory<StagedFileDao> {
  private final Provider<AppDatabase> dbProvider;

  public DatabaseModule_ProvideStagedFileDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public StagedFileDao get() {
    return provideStagedFileDao(dbProvider.get());
  }

  public static DatabaseModule_ProvideStagedFileDaoFactory create(
      Provider<AppDatabase> dbProvider) {
    return new DatabaseModule_ProvideStagedFileDaoFactory(dbProvider);
  }

  public static StagedFileDao provideStagedFileDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideStagedFileDao(db));
  }
}
