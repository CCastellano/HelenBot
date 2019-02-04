package com.helen;

import javax.annotation.Nonnull;
import javax.annotation.meta.TypeQualifierDefault;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;

@Documented @Nonnull @TypeQualifierDefault({
      ElementType.FIELD,
      ElementType.METHOD,
      ElementType.PARAMETER
})
public @interface DefaultNotNull {}