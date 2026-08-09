package com.swipedelete.zero.domain.setup;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class SigningIdentityReader_Factory implements Factory<SigningIdentityReader> {
  private final Provider<Context> contextProvider;

  public SigningIdentityReader_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public SigningIdentityReader get() {
    return newInstance(contextProvider.get());
  }

  public static SigningIdentityReader_Factory create(Provider<Context> contextProvider) {
    return new SigningIdentityReader_Factory(contextProvider);
  }

  public static SigningIdentityReader newInstance(Context context) {
    return new SigningIdentityReader(context);
  }
}
