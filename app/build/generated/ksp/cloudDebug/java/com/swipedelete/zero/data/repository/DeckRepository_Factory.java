package com.swipedelete.zero.data.repository;

import com.swipedelete.zero.data.local.DeckSessionDao;
import com.swipedelete.zero.domain.scanner.DeckBuilder;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
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
public final class DeckRepository_Factory implements Factory<DeckRepository> {
  private final Provider<DeckBuilder> deckBuilderProvider;

  private final Provider<DeckSessionDao> sessionDaoProvider;

  public DeckRepository_Factory(Provider<DeckBuilder> deckBuilderProvider,
      Provider<DeckSessionDao> sessionDaoProvider) {
    this.deckBuilderProvider = deckBuilderProvider;
    this.sessionDaoProvider = sessionDaoProvider;
  }

  @Override
  public DeckRepository get() {
    return newInstance(deckBuilderProvider.get(), sessionDaoProvider.get());
  }

  public static DeckRepository_Factory create(Provider<DeckBuilder> deckBuilderProvider,
      Provider<DeckSessionDao> sessionDaoProvider) {
    return new DeckRepository_Factory(deckBuilderProvider, sessionDaoProvider);
  }

  public static DeckRepository newInstance(DeckBuilder deckBuilder, DeckSessionDao sessionDao) {
    return new DeckRepository(deckBuilder, sessionDao);
  }
}
