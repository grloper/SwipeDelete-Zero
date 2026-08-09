package com.swipedelete.zero.di;

import com.swipedelete.zero.data.local.AppDatabase;
import com.swipedelete.zero.data.local.KeptFileDao;
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
public final class DatabaseModule_ProvideKeptFileDaoFactory implements Factory<KeptFileDao> {
  private final Provider<AppDatabase> dbProvider;

  public DatabaseModule_ProvideKeptFileDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public KeptFileDao get() {
    return provideKeptFileDao(dbProvider.get());
  }

  public static DatabaseModule_ProvideKeptFileDaoFactory create(Provider<AppDatabase> dbProvider) {
    return new DatabaseModule_ProvideKeptFileDaoFactory(dbProvider);
  }

  public static KeptFileDao provideKeptFileDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideKeptFileDao(db));
  }
}
