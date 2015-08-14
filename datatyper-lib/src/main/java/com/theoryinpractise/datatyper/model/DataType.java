package com.theoryinpractise.datatyper.model;

import java.util.List;
import java.util.Objects;

public final class DataType {

  private String name;

  private List<Field> fields;

  public DataType(String name, List<Field> fields) {
    this.name = name;
    this.fields = fields;
  }

  public String name() {
    return name;
  }

  public List<Field> fields() {
    return fields;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DataType dataType = (DataType) o;
    return Objects.equals(name, dataType.name) &&
        Objects.equals(fields, dataType.fields);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, fields);
  }

}
