package com.theoryinpractise.datatyper.model;

import java.util.Objects;

public final class Field {

  private String name;

  private String type;

  public Field(String name, String type) {
    this.name = name;
    this.type = type;
  }

  public String name() {
    return name;
  }

  public String type() {
    return type;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Field field = (Field) o;
    return Objects.equals(name, field.name) && Objects.equals(type, field.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, type);
  }
}
